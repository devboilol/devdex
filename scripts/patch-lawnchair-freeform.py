#!/usr/bin/env python3
"""Patch a pinned Lawnchair checkout for freeform app launches via Shizuku UserService.

Adds only a small app-launch bridge. No DevDex Engine, taskbar, overlay UI,
custom title bar, or separate window manager is added.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch-lawnchair-freeform.py LAWNCHAIR_DIR")

root = Path(sys.argv[1]).resolve()
launcher_file = root / "src/com/android/launcher3/touch/ItemClickHandler.java"
build_file = root / "build.gradle"
manifest_file = root / "AndroidManifest-common.xml"
aidl_file = root / "lawnchair/aidl/com/android/launcher3/touch/IFreeformShell.aidl"
helper_file = root / "src/com/android/launcher3/touch/FreeformLaunchHelper.java"
service_file = root / "src/com/android/launcher3/touch/FreeformShellService.java"

for path in (launcher_file, build_file, manifest_file):
    if not path.is_file():
        raise SystemExit(f"Required Lawnchair source file not found: {path}")

launcher = launcher_file.read_text(encoding="utf-8")
if "DEVDEX_FREEFORM_APP_LAUNCH" not in launcher:
    launch_anchor = """        if (v != null && launcher.supportsAdaptiveIconAnimation(v)
                && !item.shouldUseBackgroundAnimation()) {"""
    if launcher.count(launch_anchor) != 1:
        raise SystemExit(
            "Could not uniquely find ItemClickHandler app-launch animation anchor; "
            f"found {launcher.count(launch_anchor)}"
        )
    launch_patch = """        // DEVDEX_FREEFORM_APP_LAUNCH: request a shell-authorized freeform launch.
        if (FreeformLaunchHelper.tryLaunchFreeform(launcher, v, intent, item)) {
            return;
        }

        if (v != null && launcher.supportsAdaptiveIconAnimation(v)
                && !item.shouldUseBackgroundAnimation()) {"""
    launcher_file.write_text(launcher.replace(launch_anchor, launch_patch, 1), encoding="utf-8")

build = build_file.read_text(encoding="utf-8")
if "DEVDEX_FREEFORM_SHIZUKU_DEPENDENCIES" not in build:
    build += """
\n// DEVDEX_FREEFORM_SHIZUKU_DEPENDENCIES: minimal shell bridge for freeform launches.
dependencies {
    implementation "dev.rikka.shizuku:api:13.1.5"
    implementation "dev.rikka.shizuku:provider:13.1.5"
}

// DEVDEX_FREEFORM_SHIZUKU_LICENSE: upstream Shizuku API artifacts declare this license URL.
licensee {
    allowUrl("https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE")
}
"""
    build_file.write_text(build, encoding="utf-8")

manifest = manifest_file.read_text(encoding="utf-8")
if "rikka.shizuku.ShizukuProvider" not in manifest:
    close_tag = "</application>"
    if manifest.count(close_tag) != 1:
        raise SystemExit("Could not uniquely find </application> in AndroidManifest-common.xml")
    provider = """        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            android:authorities="\x24{applicationId}.shizuku"
            android:enabled="true"
            android:exported="true"
            android:multiprocess="false"
            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
"""
    manifest_file.write_text(manifest.replace(close_tag, provider + close_tag, 1), encoding="utf-8")

aidl_file.parent.mkdir(parents=True, exist_ok=True)
aidl_file.write_text("""package com.android.launcher3.touch;

interface IFreeformShell {
    String startFreeform(String component, int userId);
}
""", encoding="utf-8")

helper_file.parent.mkdir(parents=True, exist_ok=True)
helper_file.write_text("""package com.android.launcher3.touch;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.ItemInfo;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/** Minimal Shizuku UserService bridge for requesting Android freeform mode. */
final class FreeformLaunchHelper {
    private static final String TAG = "LawnchairFreeform";
    private static final int PERMISSION_REQUEST_CODE = 6751;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static volatile IFreeformShell sShell;
    private static boolean sBinding;
    private static LaunchRequest sPendingLaunch;
    private static boolean sShizukuHintShown;

    private FreeformLaunchHelper() {}

    static boolean tryLaunchFreeform(Launcher launcher, View view, Intent intent, ItemInfo item) {
        if (intent == null || item == null
                || item.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            return false;
        }

        ComponentName component = intent.getComponent();
        if (component == null) {
            return false;
        }

        try {
            if (!Shizuku.pingBinder()) {
                if (!sShizukuHintShown) {
                    sShizukuHintShown = true;
                    toast(launcher, "Start Shizuku to use freeform windows. Launching normally for now.");
                }
                return false;
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (!Shizuku.shouldShowRequestPermissionRationale()) {
                    Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
                    toast(launcher, "Grant Lawnchair permission in Shizuku. Opening this app normally for now.");
                    return false;
                }
                toast(launcher, "Allow Lawnchair in Shizuku to use freeform windows.");
                return false;
            }

            String target = component.flattenToShortString();
            if (!target.matches("[A-Za-z0-9_.$/]+")) {
                Log.w(TAG, "Rejected unexpected component: " + target);
                return false;
            }

            int userId = item.user != null ? item.user.getIdentifier() : 0;
            LaunchRequest request = new LaunchRequest(launcher, view, intent, item, target, userId);

            AbstractFloatingView.closeAllOpenViews(launcher);
            launcher.getStateManager().goToState(LauncherState.NORMAL);

            IFreeformShell shell = sShell;
            if (shell != null && shell.asBinder().isBinderAlive()) {
                runLaunch(request, shell);
            } else {
                bindAndLaunch(launcher, request);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Could not request freeform launch", e);
            toast(launcher, "Freeform launch failed. Launching normally.");
            return false;
        }
    }

    private static void bindAndLaunch(Launcher launcher, LaunchRequest request) {
        synchronized (LOCK) {
            sPendingLaunch = request;
            if (sBinding) {
                return;
            }
            sBinding = true;
        }

        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(launcher, FreeformShellService.class))
                    .processNameSuffix("lawnchair_freeform")
                    .tag("lawnchair_freeform")
                    .daemon(false);
            Shizuku.bindUserService(args, CONNECTION);
            // Do not leave an app icon click swallowed if the Shizuku service never connects.
            MAIN.postDelayed(() -> {
                LaunchRequest timedOut = null;
                synchronized (LOCK) {
                    if (sBinding && sPendingLaunch == request) {
                        sBinding = false;
                        sPendingLaunch = null;
                        timedOut = request;
                    }
                }
                if (timedOut != null) {
                    fallbackNormally(timedOut, "Timed out waiting for Shizuku UserService");
                }
            }, 3000);
        } catch (Exception e) {
            synchronized (LOCK) {
                sBinding = false;
                sPendingLaunch = null;
            }
            Log.e(TAG, "Could not bind Shizuku UserService", e);
            fallbackNormally(request, "Could not bind Shizuku UserService");
        }
    }

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IFreeformShell shell = IFreeformShell.Stub.asInterface(service);
            sShell = shell;
            LaunchRequest pending;
            synchronized (LOCK) {
                sBinding = false;
                pending = sPendingLaunch;
                sPendingLaunch = null;
            }
            if (pending != null) {
                if (shell != null) {
                    runLaunch(pending, shell);
                } else {
                    fallbackNormally(pending, "Shizuku UserService connected without a binder");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sShell = null;
            LaunchRequest pending;
            synchronized (LOCK) {
                sBinding = false;
                pending = sPendingLaunch;
                sPendingLaunch = null;
            }
            if (pending != null) {
                fallbackNormally(pending, "Shizuku UserService disconnected before launch");
            }
        }
    };

    private static void runLaunch(LaunchRequest request, IFreeformShell shell) {
        // The service must not be allowed to consume the click forever.
        MAIN.postDelayed(() -> fallbackNormally(
                request, "Timed out waiting for a visible freeform launch"), 9000);
        Thread thread = new Thread(() -> {
            try {
                String result = shell.startFreeform(request.component, request.userId);
                if (request.completed.get()) {
                    return;
                }
                String lower = result == null ? "" : result.toLowerCase(Locale.ROOT);
                if (result == null || !lower.startsWith("exit=0") || lower.contains("error")
                        || lower.contains("exception") || lower.contains("permission denial")) {
                    Log.e(TAG, "Freeform launch response: " + result);
                    fallbackNormally(request, "Shell command did not launch the app: " + result);
                } else {
                    request.completed.compareAndSet(false, true);
                    Log.d(TAG, "Freeform launch response: " + result);
                }
            } catch (Exception e) {
                Log.e(TAG, "Freeform shell command failed", e);
                fallbackNormally(request, "Freeform shell command failed");
                sShell = null;
            }
        }, "LawnchairFreeformLaunch");
        thread.setDaemon(true);
        thread.start();
    }

    private static void fallbackNormally(LaunchRequest request, String reason) {
        if (request == null || !request.completed.compareAndSet(false, true)) {
            return;
        }
        Log.e(TAG, "Falling back to normal app launch: " + reason);
        MAIN.post(() -> {
            Toast.makeText(request.launcher.getApplicationContext(),
                    "Freeform failed. Opening the app normally.", Toast.LENGTH_LONG).show();
            try {
                request.launcher.startActivitySafely(null, request.intent, request.item);
            } catch (Exception e) {
                Log.e(TAG, "Normal app launch fallback also failed", e);
            }
        });
    }

    private static void toast(Launcher launcher, String message) {
        MAIN.post(() -> Toast.makeText(
                launcher.getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    private static final class LaunchRequest {
        final Launcher launcher;
        final View view;
        final Intent intent;
        final ItemInfo item;
        final String component;
        final int userId;
        final AtomicBoolean completed = new AtomicBoolean(false);

        LaunchRequest(Launcher launcher, View view, Intent intent, ItemInfo item,
                String component, int userId) {
            this.launcher = launcher;
            this.view = view;
            this.intent = intent;
            this.item = item;
            this.component = component;
            this.userId = userId;
        }
    }
}
""", encoding="utf-8")

service_file.parent.mkdir(parents=True, exist_ok=True)
service_file.write_text("""package com.android.launcher3.touch;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService. Executes only the validated am-start command prepared by the launcher. */
public final class FreeformShellService extends IFreeformShell.Stub {
    private static final String TAG = "LawnchairFreeformShell";

    public FreeformShellService() {}

    @Override
    public String startFreeform(String component, int userId) {
        if (component == null || !component.matches("[A-Za-z0-9_.$/]+") || userId < 0) {
            return "error: rejected component or user";
        }

        Process process = null;
        try {
            process = new ProcessBuilder(
                    "am", "start", "--windowingMode", "5", "--user",
                    Integer.toString(userId), "-n", component)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "error: am start timed out";
            }
            int exitCode = process.exitValue();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\\n');
                }
            }
            if (exitCode != 0) {
                String result = "error: am start exit=" + exitCode + " " + output;
                Log.e(TAG, result);
                return result;
            }

            String verification = verifyFreeformTask(component);
            if (!verification.startsWith("verified:")) {
                String result = "error: " + verification + "; am output=" + output;
                Log.e(TAG, result);
                return result;
            }

            String result = "exit=0 " + output + " " + verification;
            Log.d(TAG, result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Shell execution failed", e);
            return "exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * HyperOS may accept am start but leave the task fullscreen or hidden.
     * Confirm the target appears in the activity dump near a freeform-mode marker;
     * otherwise report failure so the launcher can open it normally.
     */
    private String verifyFreeformTask(String component) {
        Process process = null;
        try {
            process = new ProcessBuilder("dumpsys", "activity", "activities")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder dump = new StringBuilder();
            Process dumpProcess = process;
            Thread dumpReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(dumpProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        dump.append(line).append('\\n');
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Could not read activity dump", e);
                }
            }, "LawnchairFreeformDumpReader");
            dumpReader.setDaemon(true);
            dumpReader.start();

            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                dumpReader.join(300);
                return "activity dump timed out";
            }
            dumpReader.join(500);

            int slash = component.indexOf('/');
            if (slash <= 0 || slash == component.length() - 1) {
                return "invalid target component";
            }
            String packageName = component.substring(0, slash);
            String className = component.substring(slash + 1);
            String expandedClass = className.startsWith(".")
                    ? packageName + className
                    : className;
            String expandedComponent = packageName + "/" + expandedClass;
            String dumpText = dump.toString().toLowerCase(Locale.ROOT);
            int pos = dumpText.indexOf(component.toLowerCase(Locale.ROOT));
            if (pos < 0) {
                pos = dumpText.indexOf(expandedComponent.toLowerCase(Locale.ROOT));
            }
            if (pos < 0) {
                return "target activity not found in activity dump";
            }

            int start = Math.max(0, pos - 3000);
            int end = Math.min(dumpText.length(), pos + 3000);
            String vicinity = dumpText.substring(start, end);
            if (!vicinity.contains("freeform")
                    && !vicinity.contains("windowingmode=5")
                    && !vicinity.contains("windowingmode: 5")
                    && !vicinity.contains("mwindowingmode=5")) {
                return "target task not confirmed in freeform mode";
            }
            return "verified: target task is in freeform mode";
        } catch (Exception e) {
            Log.e(TAG, "Could not verify freeform task", e);
            return "activity verification failed: " + e.getClass().getSimpleName();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
""", encoding="utf-8")

print("Applied minimal Lawnchair + Shizuku UserService freeform launch patch.")
print("No DevDex Engine, taskbar, overlay UI, custom title bar, or window manager was added.")
