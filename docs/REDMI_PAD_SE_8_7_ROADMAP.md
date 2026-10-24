# DevDex roadmap: Redmi Pad SE 8.7

**Scope:** Redmi Pad SE 8.7 / 8.7 4G, with firmware-specific capability checks.  
**Research snapshot:** 24 October 2026. Android/HyperOS builds vary by region. Verify the exact version under Settings → About tablet before diagnosing a device-specific behavior.

## Research findings that affect architecture

- Xiaomi support pages conflict across variants and firmware: one FAQ documents Parallel Windows, while a separate REDMI Pad SE 8.7 4G product FAQ says Split screen is not supported. If the actual tablet only shows Horizontal app layout under Features for tablets and has no Parallel windows entry, do not claim that native split-screen is available on that ROM. Sources: https://www.mi.com/global/support/faq/details/KA-492516/ and https://www.mi.com/ph/support/faq/details/KA-257495/
- Android documents that ActivityOptions.setLaunchBounds() is ignored when the system lacks FEATURE_FREEFORM_WINDOW_MANAGEMENT (or the relevant PiP feature). DevDex must verify capability and never treat a successful Kotlin call as proof that a real window was created. Source: https://developer.android.com/reference/android/app/ActivityOptions#setLaunchBounds(android.graphics.Rect)
- When freeform is not advertised, expose an explicit, reversible compatibility option in Settings → Advanced. With Shizuku/Root authorization, it may set Android's experimental enable_freeform_support and force_resizable_activities globals, preserving previous values and requiring reboot. ROM vendors can still ignore or block these settings, so show a clear failure instead of claiming success.
- Xiaomi's generic HyperOS Workstation guide describes a desktop-like multi-window mode, but it is not proof that the Redmi Pad SE 8.7 exposes every Workstation feature. Do not build against that assumption. Source: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2034
- A community firmware index lists HyperOS 3.1 / Android 16 builds for some Redmi Pad SE 8.7 regions in 2026; release timing and build numbers vary by region. Treat this as a hint, not a substitute for checking the tablet itself. Source: https://mirom.ezbox.idv.tw/en/phone/flare/

## P0 — stability gate before new polish

### Current audit

| Capability | Current state | Decision |
|---|---|---|
| Desktop home, dock, app drawer | Already present in DevDex | Keep stable; avoid redesign until the window system passes tests |
| Fullscreen app launch | Previously forced a hidden windowing mode even for ordinary fullscreen launches | Use Android's normal launch path on the built-in display; pass special options only when needed |
| Real freeform windows | Requested through launch options; some ROMs hide the feature | Probe capability; expose explicit experimental compatibility settings and reboot guidance when supported |
| Close / resize overlay | Existing compact square + X pill | Never hide the pill only because a task ID lookup briefly fails |
| 25%, 50%, fullscreen presets | Existing task resize commands | Resolve missing/stale task IDs on demand through ShellManager / Shizuku / root and show a clear failure state |
| Title bar with app name + drag area | Not yet a complete custom title bar; current custom overlay is only controls | Implement only when a usable native caption is absent |
| Multi-window task tracking | Accessibility windows + public task list; task visibility can be restricted by OEMs | Track package/window identity separately from task ID and recover across transient event gaps |
| Shizuku / freeform diagnostics | Connection and permission primitives exist; need a unified user-facing status | Distinguish server not running, permission missing, binder lost, command rejected, and freeform unsupported |
| Restore state | Service UI initialization has retry logic; window geometry persistence is incomplete | Persist bounds per task/package and recover overlays after a service reconnect |

### Order of work

1. **Launch safety:** fullscreen launches use Android's normal path. Do not silently change global windowing settings as a side effect of granting unrelated permissions. The user may explicitly enable experimental freeform compatibility in Advanced settings; store and restore the original global values and explain that a reboot is required.
2. **Window-control invariants:** a live floating window should keep its control pill even if Accessibility temporarily returns a null root or ActivityManager hides the task ID. Resolve the task when the user invokes an action. Use a short grace period before deleting a control whose window genuinely vanished.
3. **Dock collision:** reposition controls outside dock bounds rather than setting the whole pill to GONE.
4. **Capability probe:** read OS/API/build, FEATURE_FREEFORM_WINDOW_MANAGEMENT, experimental freeform setting values, Shizuku connection + grant state, and actual task behavior. Distinguish "system feature absent" from "Shizuku not running", "permission denied", "reboot required", and "ROM rejected the mode".
5. **Title bar / movement:** when the OS does not provide visible, interactive caption controls, add a lightweight title bar containing app label, drag region, resize/menu button, and X. Throttle move updates; do not issue a shell resize command for every raw pointer event.
6. **Persistence / recovery:** store task bounds when they change, restore only if the same package/task can be identified, and never force-stop an app as a fallback for a failed close/resize command.

### P0 acceptance tests on the physical tablet

- Launch ordinary fullscreen apps repeatedly without duplicate start requests or freeform launch options.
- Test any two compatible, unrelated third-party apps, and verify that both are simultaneously visible as separate windows; do not hard-code a specific package pair as the acceptance criterion.
- Open several floating-capable apps and confirm square/X controls remain visible after focus changes, resizing, dock show/hide, and transient service events.
- Exercise 25%, 50%, and fullscreen 10 times each; verify the task actually changes mode/bounds and surface a reason on failure.
- Stop/restart Shizuku or revoke DevDex's Shizuku permission while windows are open. Controls should remain visible; actions should fail with a specific state and recover after reconnecting.
- Test a browser, a productivity app, a messaging/AI app, system Settings, file manager, video player, and one game. Apps that reject resize/freeform must report the limitation rather than enter a crash/retry loop or silently replace the existing window.
- Test both dock positions, status-bar reveal, rotation changes, and window bounds near every screen edge.

## P1 — after the P0 gate is green

1. **Alt+Tab / app switching:** onKeyEvent already handles a few Alt shortcuts, but there is no complete Alt+Tab cycle. Use the tracked visible-task list and do not consume unrelated key events.
2. **Window Overview:** lightweight overlay listing known windows with focus / restore actions, built on the same resilient task identity layer as X and resize.
3. **Split Screen:** prefer Android/HyperOS-supported split-screen behavior; treat it as best-effort and expose Xiaomi's supported-app limitation.
4. **Snap Windows:** some tiled resize actions already exist in app/task menus. Add edge hit-testing only after title-bar drag works reliably.
5. **Mouse / keyboard:** support secondary mouse click and shortcuts through explicit tested input paths. Keep touch interactions first-class.
6. **Dock auto-hide:** reuse the existing pin/unpin, handle, and hover-hide behavior rather than duplicating it.
7. **Remember window positions:** desktop icon positions are persisted already; add separate persisted task bounds for app windows.
8. **Status reporting:** show which app is focused, which tasks are known, and whether actions are unavailable due to missing permission or ROM limits.

## P2 — once the desktop is reliable

- Workspaces and named saved layouts.
- Task/resource manager with a low-resource mode.
- Additional desktop gestures and minor UI customization.
- Grouped app launch layouts only after task identity and restore behavior are reliable.

## Explicitly out of scope for this device-first build

- Wired USB-C video output.
- Four or five large windows as a guaranteed workload.
- Heavy transition effects.
- Virtual desktops before real freeform, task tracking, and recovery are stable.

## Compatibility principle

**Do not turn a launch request into a false success.** Prefer freeform windows when available. When the ROM hides freeform, offer a reversible, explicit compatibility path with a reboot requirement; then verify task/window behavior. If the ROM still blocks multi-window, report that clearly instead of pretending native Parallel Windows exists.
