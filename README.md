# DevDex

<p align="center"><img src="https://raw.githubusercontent.com/devboilol/devdex/main/app/src/main/res/drawable/devdex_icon.webp" width="100" alt="DevDex Logo"/></p>

<h1 align="center">DevDex</h1>

<p align="center">A Windows-inspired desktop experience for Android.</p>

---

## What is DevDex?

DevDex turns an Android device into a desktop-style environment with a taskbar, app dock, movable and resizable app windows, desktop shortcuts, and window-management controls. It can run as the home launcher or as an on-demand overlay.

Some advanced operations require Root or Shizuku. Most desktop interface features do not.

> [!WARNING]
> **Use DevDex on a secondary device, preferably a tablet.** DevDex uses Android Accessibility and overlay capabilities and may interfere with banking, government, identity, payment, enterprise-authentication, or other security-sensitive apps. Such apps may block access or refuse to run while overlays or desktop controls are active. Do not rely on DevDex on your primary device for critical secure workflows. Review each permission before granting it.

## First-run experience

- Choose **System default**, **English**, or **Vietnamese** during setup.
- Starts at the desktop with a minimal taskbar and centered app icons, using a Windows 11-inspired visual direction.
- Export and import settings from **Settings → Advanced → Backup/Restore Preferences** using a typed JSON backup. Older `.sdp` backups remain supported.
- Windowed apps show two controls: **maximize/restore** and **close**. Fullscreen activities should not receive the DevDex control overlay.

## Features

### Desktop and windows
- Accessibility-based desktop overlay, usable as a launcher or on demand.
- Centered floating dock or classic edge-pinned dock, with dock presets.
- Movable and resizable app windows, maximize and snap actions.
- Multiple app windows grouped under a dock icon.
- Hot corners, multi-display support, mouse right-click, and keyboard shortcuts.
- Taskbar running-app controls and dock context actions, including closing an app.

### Apps and personalization
- App drawer with search, sorting, hide-app options, deep shortcuts, and icon-pack support.
- Per-app launch mode and optional forced landscape.
- Material You or custom dock colors, transparency, fonts, text scaling, and custom sounds.
- Desktop grid and live-wallpaper features supported by the upstream codebase.

### Advanced features
- Some system-level operations, such as custom display settings or task/window actions, may require Root or Shizuku.
- Accessibility remains a manual permission step; carefully review any permissions requested by the app.

## Quick start

1. Install the APK and open DevDex.
2. Complete setup and choose a language.
3. Review the requested permissions. Enable Accessibility only if you understand and want the overlay features.
4. Use DevDex as your launcher or start it on demand using the available shortcut or Quick Settings tile.

## Settings backup

Open **Settings → Advanced → Backup/Restore Preferences** to export or import a JSON settings backup. The export supports common preference types and restore remains compatible with older `.sdp` backups. Treat backup files as personal configuration data and store them somewhere appropriate.

## Compatibility and limitations

- Designed for Android 8.0 and newer; behavior varies by device and Android build.
- Root or Shizuku is needed only for selected advanced operations.
- Some manufacturers disable freeform/floating windows by default.
- Fullscreen detection and app-window controls depend on Android activity behavior and OEM window management, so verify them on your device.
- Always-on-top pinning for arbitrary apps is not implemented through Android's ordinary public app APIs.

## Safety and privacy

DevDex uses Accessibility and overlay capabilities to provide its desktop experience. These capabilities can conflict with apps that protect sensitive screens or reject overlays. Keep a regular launcher available, review permissions, and test on a secondary device rather than a phone you depend on for banking, identity verification, government services, or payments.

## License and upstream attribution

DevDex continues an open-source Android desktop launcher codebase. Original copyright notices and applicable license terms are preserved. See [LICENSE](LICENSE) and the source files for attribution details.

---

For current development and bug reports, use [GitHub Issues](https://github.com/devboilol/devdex/issues).
