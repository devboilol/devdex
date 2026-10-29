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
        if (FreeformLaunchHelper.tryLaunchFreeform(launcher, intent, item)) {
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
            android:authorities="\${applicationId}.shizuku"
            android:enabled="true"
            android:exported="true"
            android:multiprocess="false"
            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
"""
    manifest_file.write_text(manifest.replace(close_tag, provider + close_tag, 1), encoding="utf-8")

aidl_file.parent.mkdir(parents=True, exist_ok=True)
aidl_file.write_text("""package com.android.launcher3.touch;

interface IFreeformShell {
    String exec(String command);
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
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.ItemInfo;

import java.util.Locale;

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

    static boolean tryLaunchFreeform(Launcher launcher, Intent intent, ItemInfo item) {
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
                    toast(launcher, "Grant Lawnchair permission in Shizuku, then tap the app again.");
                    return true;
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
            String command = "am start --windowingMode 5 --user " + userId
                    + " -n " + target + " 2>&1";
            LaunchRequest request = new LaunchRequest(launcher, command);

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
        } catch (Exception e) {
            synchronized (LOCK) {
                sBinding = false;
                sPendingLaunch = null;
            }
            Log.e(TAG, "Could not bind Shizuku UserService", e);
            toast(launcher, "Could not start the freeform helper. Check Shizuku.");
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
            if (pending != null && shell != null) {
                runLaunch(pending, shell);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sShell = null;
            synchronized (LOCK) {
                sBinding = false;
            }
        }
    };

    private static void runLaunch(LaunchRequest request, IFreeformShell shell) {
        Thread thread = new Thread(() -> {
            try {
                String result = shell.exec(request.command);
                String lower = result == null ? "" : result.toLowerCase(Locale.ROOT);
                if (result == null || lower.contains("error") || lower.contains("exception")
                        || lower.contains("permission denial")) {
                    Log.e(TAG, "Freeform launch response: " + result);
                    toast(request.launcher, "HyperOS rejected the freeform launch. See Logcat.");
                } else {
                    Log.d(TAG, "Freeform launch response: " + result);
                }
            } catch (Exception e) {
                Log.e(TAG, "Freeform shell command failed", e);
                toast(request.launcher, "Freeform launch failed. Check Shizuku and Logcat.");
                sShell = null;
            }
        }, "LawnchairFreeformLaunch");
        thread.setDaemon(true);
        thread.start();
    }

    private static void toast(Launcher launcher, String message) {
        MAIN.post(() -> Toast.makeText(
                launcher.getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    private static final class LaunchRequest {
        final Launcher launcher;
        final String command;

        LaunchRequest(Launcher launcher, String command) {
            this.launcher = launcher;
            this.command = command;
        }
    }
}
""", encoding="utf-8")

service_file.parent.mkdir(parents=True, exist_ok=True)
service_file.write_text("""package com.android.launcher3.touch;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Shizuku UserService. Executes only the validated am-start command prepared by the launcher. */
public final class FreeformShellService extends IFreeformShell.Stub {
    private static final String TAG = "LawnchairFreeformShell";

    public FreeformShellService() {}

    @Override
    public String exec(String command) {
        if (command == null
                || !command.matches("am start --windowingMode 5 --user [0-9]+ -n [A-Za-z0-9_.$/]+ 2>&1")) {
            return "error: rejected command";
        }

        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\\n');
                }
            }
            int exitCode = process.waitFor();
            String result = "exit=" + exitCode + " " + output;
            if (exitCode != 0) {
                Log.e(TAG, result);
            } else {
                Log.d(TAG, result);
            }
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
}
""", encoding="utf-8")

print("Applied minimal Lawnchair + Shizuku UserService freeform launch patch.")
print("No DevDex Engine, taskbar, overlay UI, custom title bar, or window manager was added.")
