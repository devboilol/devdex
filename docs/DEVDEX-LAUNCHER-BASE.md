# DevDex Launcher Base Decision

**Status:** Minimal freeform launch prototype; CI and device validation pending.

## Decision

Use **Lawnchair 16** as the source-code foundation for DevDex Plus. Lawnchair is an open-source Android home launcher based on AOSP Launcher3. Use the Xiaomi System Launcher build `release-4.50.0.1181-1118-04291922` on the Redmi Pad SE 8.7 only as a visual/behavioral reference; it is proprietary and is not the source-code base.

## Pinned upstream baseline

- Repository: https://github.com/LawnchairLauncher/lawnchair
- Branch at pin time: `16-dev`
- Commit: `2d072998677ff198a0abc3f19c22af9ad32b5574`
- Submodules: checked out recursively at the revisions pinned by that commit.
- Build variant: `assembleLawnWithQuickstepGithubDebug`
- Patch: [patch-lawnchair-freeform.py](../scripts/patch-lawnchair-freeform.py)
- CI workflow: [build-lawnchair-baseline.yml](../.github/workflows/build-lawnchair-baseline.yml)

The patch changes Lawnchair's app-click path for ordinary app icons. Because HyperOS can block hidden `ActivityOptions.setLaunchWindowingMode()` calls made by regular apps, the prototype uses a small Shizuku shell bridge to run `am start --windowingMode 5` for the target component. Shizuku must be active and authorized; otherwise Lawnchair falls back to its normal launch path. This does not add the DevDex Engine, overlay, custom title bar, taskbar, or a separate window manager. HyperOS determines window size/position; this prototype does not control bounds.

## Promotion gate

Before considering this prototype usable, confirm the patched APK:

1. Builds successfully in CI.
2. Installs and starts on the Redmi Pad SE 8.7.
3. Shows the home workspace and app drawer without a blank screen or startup crash.
4. Launches ordinary app icons into real freeform windows, not merely smaller/full-screen activities. Deep shortcuts remain on Lawnchair's default path.
5. Can switch between multiple apps, return Home, and launch an app fullscreen only if a later explicit setting is added.
6. Survives rotation and a device reboot smoke test, as supported by the tablet firmware.

Record device/OS version, APK SHA-256, and any Logcat crash output for that test.

## Integration rules

- Xiaomi System Launcher is a reference only. Do not use its APK as the project base.
- Keep the existing Lawnchair-plus-DevDex experiment and the Open Android DeX experiment separate from this baseline.
- Make one narrowly scoped change per commit and run the build after each change.
- Freeform depends on the firmware exposing the feature and Shizuku shell permission being granted. If the command is rejected, confirm the device's freeform support before adding any more code.
- Preserve upstream license notices and follow the applicable upstream license obligations when distributing derivatives.

## Device test note

Required setup: start Shizuku (wireless debugging is sufficient), grant its permission to the Lawnchair Freeform APK, then tap an ordinary app icon again. With non-root Shizuku, activation must be repeated after reboot. Freeform remains firmware-dependent. Record the HyperOS/Android version and inspect `dumpsys activity` / `dumpsys window` after launch; confirm the task reports freeform rather than fullscreen.
