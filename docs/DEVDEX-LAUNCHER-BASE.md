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

The patch touches only `QuickstepLauncher.getActivityLaunchOptions`: app icons and deep shortcuts request `WINDOWING_MODE_FREEFORM` with centered bounds at about 82% of display width and 78% of display height. Lawnchair's existing launch flow is retained. No DevDex Engine, Shizuku, overlay, custom title bar, or separate window manager is added. The generated APK proves compilation only, not runtime compatibility with the Redmi tablet.

## Promotion gate

Before considering this prototype usable, confirm the patched APK:

1. Builds successfully in CI.
2. Installs and starts on the Redmi Pad SE 8.7.
3. Shows the home workspace and app drawer without a blank screen or startup crash.
4. Launches ordinary app icons and deep shortcuts into real freeform windows, not merely smaller/full-screen activities.
5. Can switch between multiple apps, return Home, and launch an app fullscreen only if a later explicit setting is added.
6. Survives rotation and a device reboot smoke test, as supported by the tablet firmware.

Record device/OS version, APK SHA-256, and any Logcat crash output for that test.

## Integration rules

- Xiaomi System Launcher is a reference only. Do not use its APK as the project base.
- Keep the existing Lawnchair-plus-DevDex experiment and the Open Android DeX experiment separate from this baseline.
- Make one narrowly scoped change per commit and run the build after each change.
- Freeform depends on HyperOS allowing Android's standard freeform windowing mode. If the firmware rejects launch mode/bounds, diagnose device configuration before adding other libraries or services.
- Preserve upstream license notices and follow the applicable upstream license obligations when distributing derivatives.

## Device test note

Android's standard launcher options cannot create freeform support if the firmware does not expose it. Record the HyperOS/Android version and inspect `dumpsys activity` / `dumpsys window` after launching an app. Do not treat `setLaunchBounds()` alone as proof that an app is in freeform mode.
