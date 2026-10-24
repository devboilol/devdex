# DevDex 3.5.0-rc.1

**Status:** Release Candidate for testing, not a Stable release.

## Included in this candidate

- Desktop-first onboarding and first-run language selection (System default, English, Vietnamese).
- Compact window controls for windowed apps, with fullscreen activities intended to remain free of DevDex control overlays.
- Desktop/taskbar styling refinements and status-bar preference.
- Settings export/import with typed JSON backups and legacy `.sdp` restore compatibility.
- First-class notes on Shizuku/Root requirements, freeform-window variability and security-sensitive app conflicts.

## Test focus

1. Install on the Redmi Pad SE 8.7 and record the exact HyperOS/Android build.
2. Verify an upgrade over the previous DevDex APK without uninstalling; confirm the package ID remains `com.youki.dex` and preferences survive.
3. Keep two apps visible and usable concurrently; test task switching, resizing, close controls and fullscreen apps.
4. Verify the status bar defaults to visible, opacity is independent from resize, and controls survive focus changes and Shizuku reconnect.
5. Check settings export/restore, reboot recovery, and the SHA-256 checksum from the Actions artifact.

## Known limits / release caution

- Actual freeform and multi-window behavior depends on Android version and OEM firmware.
- CI success does not prove physical-device behavior or update-in-place; complete the checklist in [Release Candidate flow](RELEASE_CANDIDATE_FLOW.md).
- The universal always-on-top Pin Window feature is not included.
- Do not use this RC as the primary launcher on a device relied on for banking, identity verification, government services or payments until its overlay behavior has been tested.

## Build identity

The workflow artifact contains the exact commit SHA, Actions run ID, version code, version name and SHA-256 checksum. Use those metadata values when reporting a bug.
