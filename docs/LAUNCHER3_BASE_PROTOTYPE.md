# DevDex Plus: Launcher3 Android 16 Base Prototype

## Purpose

This is an isolated prototype branch for evaluating an AOSP-derived Launcher3 base instead of the current Lawnchair-based integration. It does not replace main or devdex-plus/integration.

## Upstream baseline

- Repository: yuchuangu85/Launcher3
- Source branch represented by the pinned commit: Launcher3-16-s2-release
- Pinned commit: a000136df789859f893402ba0d7ebda86808305a
- The upstream README identifies this source as Android 16 S2 and provides a Gradle build with Quickstep. This is a third-party Gradle port of AOSP Launcher3, not Google's official AOSP build entry point.
- Source: https://github.com/yuchuangu85/Launcher3/tree/Launcher3-16-s2-release

## What the prototype changes

1. Builds the AospWithQuickstepDebug variant with package ID com.devboi.dex.
2. Imports the existing DevDex app source tree as a :devdexengine Android library.
3. Keeps Launcher3's LauncherApplication lifecycle through DevDexPlusApplication and enables Shizuku multiprocess support before provider initialization.
4. Removes DevDex's legacy HOME intent filters from the engine manifest so Quickstep remains the single Home owner.
5. Patches Quickstep's app-launch options to request freeform for app and deep-shortcut launches, with centered 75%-sized initial bounds.
6. Respects the global launch_mode=fullscreen preference unless always_floating=true. A devdex_plus_freeform_launch preference flag defaults to enabled.
7. Imports DevDex's PerfectServer, AppUtils, WindowControlsManager and accessibility/Shizuku integration.

## Deliberate limitations

- Per-app remembered launch modes are not yet bridged into Quickstep. The prototype only respects the global fullscreen/always-floating policy.
- Freeform still depends on HyperOS accepting the request and the system freeform configuration being enabled. A successful Gradle build does not prove every app will open in a floating window.
- The CI artifact is debug-signed and may not update an installed package signed with another key.
- No existing branch is merged automatically. Keep this branch experimental until device testing succeeds.

## Test checklist

1. Confirm CI completes assembleAospWithQuickstepDebug and manifest verification.
2. Install the APK on Redmi Pad SE 8.7 Android 16 and select DevDex Plus as Home.
3. Grant Shizuku to package com.devboi.dex and enable the DevDex accessibility service and overlay permission.
4. Launch Settings, Chrome and two tasks belonging to the same app. Confirm initial launches are floating where supported.
5. Test drag, resize, maximize/restore, minimize/restore, close and dock/taskbar task state.
6. Test global fullscreen mode, always-floating, app switching, Home, reboot and Shizuku reconnect.
7. Capture adb logcat and dumpsys activity activities when a window opens fullscreen or a control appears to succeed without affecting the actual task.

## Go/no-go

Keep the Launcher3 base only if it builds reproducibly and preserves DevDex window actions on the physical Redmi tablet. If this build port or its framework dependencies become a maintenance burden, compare the result against the Lawnchair integration before committing to the fork.
