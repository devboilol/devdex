# DevDex Launcher Base Decision

**Status:** Accepted; baseline build/runtime validation pending.

## Decision

Use **Lawnchair 16** as the source-code foundation for DevDex Plus. Lawnchair is an open-source Android home launcher based on AOSP Launcher3. Use the Xiaomi System Launcher build `release-4.50.0.1181-1118-04291922` on the Redmi Pad SE 8.7 only as a visual/behavioral reference; it is proprietary and is not the source-code base.

## Pinned upstream baseline

- Repository: https://github.com/LawnchairLauncher/lawnchair
- Branch at pin time: `16-dev`
- Commit: `2d072998677ff198a0abc3f19c22af9ad32b5574`
- Submodules: checked out recursively at the revisions pinned by that commit.
- Build variant: `assembleLawnWithQuickstepGithubDebug`
- CI workflow: [build-lawnchair-baseline.yml](../.github/workflows/build-lawnchair-baseline.yml)

The baseline workflow must build upstream source without DevDex patches. Its APK is a source/build artifact, not proof of runtime compatibility with the Redmi tablet.

## Promotion gate

Do not integrate DevDex Engine, Shizuku, window management, or new freeform logic until the unmodified APK:

1. Builds successfully in CI.
2. Installs and starts on the Redmi Pad SE 8.7.
3. Shows the home workspace and app drawer without a blank screen or startup crash.
4. Can launch normal apps and return Home reliably.
5. Survives rotation/orientation changes and a device reboot smoke test, as supported by the tablet firmware.

Record device/OS version, APK SHA-256, and any Logcat crash output for that test.

## Integration rules

- Xiaomi System Launcher is a reference only. Do not use its APK as the project base.
- Keep the existing Lawnchair-plus-DevDex experiment and the Open Android DeX experiment separate from this baseline.
- Make one narrowly scoped change per commit and run the baseline build after each change.
- First test freeform behavior on the device/firmware. Then add it to the app-launch path. Integrate the existing DevDex Engine and Shizuku only after this passes.
- Preserve upstream license notices and follow the applicable upstream license obligations when distributing derivatives.
