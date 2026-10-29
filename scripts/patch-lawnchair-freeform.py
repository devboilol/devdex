#!/usr/bin/env python3
"""Patch a pinned Lawnchair checkout for freeform app launches via Shizuku.

This intentionally adds no DevDex Engine, overlays, taskbar, window chrome, or
separate window manager. When Shizuku is unavailable, Lawnchair keeps its normal
fullscreen launch path.
"""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch-lawnchair-freeform.py LAWNCHAIR_DIR")

root = Path(sys.argv[1]).resolve()
launcher_file = root / "src/com/android/launcher3/touch/ItemClickHandler.java"
build_file = root / "build.gradle"
manifest_file = root / "AndroidManifest-common.xml"

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
    launch_patch = """        // DEVDEX_FREEFORM_APP_LAUNCH: run the app as shell so HyperOS can
        // accept windowingMode=freeform; preserve Lawnchair fallback when unavailable.
        if (FreeformLaunchHelper.tryLaunchFreeform(launcher, intent, item)) {
            return;
        }

        if (v != null && launcher.supportsAdaptiveIconAnimation(v)
                && !item.shouldUseBackgroundAnimation()) {"""
    launcher = launcher.replace(launch_anchor, launch_patch, 1)
    launcher_file.write_text(launcher, encoding="utf-8")

# This is the only Shizuku dependency used by the freeform bridge. Keep it in a
# separate root dependency block so the upstream build structure stays untouched.
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

# Shizuku's provider attaches its binder in the Lawnchair process.
manifest = manifest_file.read_text(encoding="utf-8")
if "rikka.shizuku.ShizukuProvider" not in manifest:
    close_tag = "</application>"
    if manifest.count(close_tag) != 1:
        raise SystemExit("Could not uniquely find </application> in AndroidManifest-common.xml")
    provider = """        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            android:authorities="${applicationId}.shizuku"
            android:enabled="true"
            android:exported="true"
            android:multiprocess="false"
            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
"""
    manifest = manifest.replace(close_tag, provider + close_tag, 1)
    manifest_file.write_text(manifest, encoding="utf-8")

helper_file = root / "src/com/android/launcher3/touch/FreeformLaunchHelper.java"
helper_file.parent.mkdir(parents=True, exist_ok=True)
helper_file.write_text("""package com.android.launcher3.touch;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.model.data.ItemInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

import rikka.shizuku.Shizuku;

/** Minimal bridge: use Shizuku shell identity to request a real freeform launch. */
final class FreeformLaunchHelper {
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 6751;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean sShizukuHintShown;

    private FreeformLaunchHelper() {}

    /**
     * @return true when this click was consumed (freeform launch or permission prompt);
     *         false to continue Lawnchair's ordinary launch path.
     */
    static boolean tryLaunchFreeform(Launcher launcher, Intent intent, ItemInfo item) {
        if (intent == null || item == null
                || item.itemType != com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
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
                    toast(launcher, "Start Shizuku to open apps in freeform windows. Launching normally for now.");
                }
                return false;
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (!Shizuku.shouldShowRequestPermissionRationale()) {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
                    toast(launcher, "Grant Lawnchair permission in Shizuku, then tap the app again.");
                    return true;
                }
                toast(launcher, "Allow Lawnchair in Shizuku to use freeform windows. Launching normally for now.");
                return false;
            }

            String target = component.flattenToShortString();
            // Components are package/class names; reject anything unexpected before shell use.
            if (!target.matches("[A-Za-z0-9_.$/]+")) {
                return false;
            }

            int userId = item.user != null ? item.user.getIdentifier() : 0;
            String command = "am start --windowingMode 5 --user " + userId
                    + " -n " + target + " 2>&1";

            AbstractFloatingView.closeAllOpenViews(launcher);
            launcher.getStateManager().goToState(LauncherState.NORMAL);

            Thread thread = new Thread(() -> {
                StringBuilder output = new StringBuilder();
                int exitCode = -1;
                try {
                    Process process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append('\\n');
                        }
                    }
                    exitCode = process.waitFor();
                } catch (Exception e) {
                    output.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
                }

                String result = output.toString();
                String lower = result.toLowerCase(Locale.ROOT);
                if (exitCode != 0 || lower.contains("error") || lower.contains("exception")) {
                    toast(launcher, "Freeform launch failed on this HyperOS build. See Logcat for details.");
                    android.util.Log.e("LawnchairFreeform",
                            "am start exit=" + exitCode + " output=" + result);
                } else {
                    android.util.Log.d("LawnchairFreeform", "am start output=" + result);
                }
            }, "LawnchairFreeformLaunch");
            thread.setDaemon(true);
            thread.start();
            return true;
        } catch (Exception e) {
            android.util.Log.e("LawnchairFreeform", "Unable to request freeform launch", e);
            toast(launcher, "Freeform could not start. Launching normally.");
            return false;
        }
    }

    private static void toast(Launcher launcher, String message) {
        MAIN.post(() -> Toast.makeText(launcher.getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }
}
""", encoding="utf-8")

print("Applied minimal Lawnchair + Shizuku freeform launch patch.")
print("No DevDex Engine, overlay, taskbar, title bar, or custom window manager was added.")
