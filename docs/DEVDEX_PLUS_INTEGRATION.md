# DevDex Plus integration plan

## Goal

Build **DevDex Plus in this repository** (devboilol/devdex) as one installable app. Keep main as the stable DevDex line and develop the integration on devdex-plus/integration.

The resulting Home screen, App Drawer, search, widgets, and launcher preferences come from Lawnchair. DevDex supplies the desktop/window engine, taskbar, window controls, Shizuku integration, and device-specific behavior.

## Product decisions

- One packaged APK, not two separately installed apps joined by an overlay.
- Target application ID: com.devboi.dex for the Plus build.
- Lawnchair owns Home and App Drawer. The DevDex taskbar must never open the old DevDex app drawer.
- The taskbar shows recent/running tasks and quick-open shortcuts, plus Back, Home, and Recents controls.
- Keep per-task identity where possible; package name alone is insufficient when one app has multiple tasks/windows.
- Hide the taskbar while Lawnchair Home or App Drawer is visible, to avoid showing two launcher surfaces.
- Preserve DevDex's existing floating-window controls and Shizuku/accessibility path where compatible with the single-APK design.
- Keep the normal DevDex line and main unchanged until the Plus branch builds and passes device testing.

## Architecture implication

Lawnchair is a full Android launcher source tree, not a normal Maven dependency. Its build config pulls in Launcher3/Quickstep-related source and several supporting Android modules, and it currently targets a newer Android SDK/toolchain than the existing DevDex app. Simply adding a dependency or copying the earlier freeform-launch patch into DevDex will not create the integrated product.

The integration must bring the Lawnchair source/build structure into this repository and reconcile it with DevDex's existing app:
1. Make the Lawnchair launcher the single HOME/App Drawer implementation.
2. Refactor DevDex's window engine and supporting services into an internal module or otherwise integrate them into the same app.
3. Reconcile manifest entries and the Application subclass without dropping DevDex initialization (including Shizuku multi-process setup) or Lawnchair initialization.
4. Merge permissions, resources, SDK/build plugins, packaging, and license notices.
5. Replace the existing DevDex drawer entry point with the taskbar behavior described above.
6. Add the freeform launch integration at Lawnchair's app-launch path, with guarded fallback when the ROM refuses freeform.
7. Build in CI, then validate installation/upgrade and behavior on the Redmi Pad SE 8.7 / HyperOS.

## Staged acceptance checks

### P0 — Source/build integration
- The repository builds one DevDex Plus APK.
- Package/application ID is com.devboi.dex.
- Lawnchair Home and App Drawer work, including normal app launching.
- Existing DevDex services and settings still initialize.

### P1 — Taskbar and task model
- Taskbar shows running/recent tasks and pinned quick-open apps.
- Back/Home/Recents controls work.
- Tapping a task targets the task/instance rather than indiscriminately launching the package.
- Taskbar is hidden on Home/App Drawer and shown over supported foreground apps.

### P2 — Floating windows
- Launcher app launches can request freeform bounds/windowing mode where the ROM supports them.
- Existing DevDex resize, move, close, and window-control behavior remains functional.
- Unsupported freeform launches fall back cleanly without trapping the user.

## Licensing and attribution

Preserve DevDex's GPL-3.0 license and all applicable Lawnchair/AOSP Apache-2.0 notices, plus notices for imported dependencies. Before distributing the combined build, review the final source layout and license notices rather than assuming that a code merge removes upstream obligations.

## Current status

This branch starts from DevDex main. The previous P0 patch was drafted against a Lawnchair checkout directly and is exploratory; it is **not** the integrated DevDex Plus implementation and should not be applied blindly to this repository. No build/device-test claim is made by this document.
