# Window controls: main-window-only policy

## Goal

DevDex's custom Close / Resize / Opacity control group should decorate the main top-level window of an eligible app, not every Accessibility window that happens to expose the same package name.

## Implementation policy

- Skip DevDex itself, the active Home launcher, Android System UI, Settings, permission controllers, package installers, Intent Resolver surfaces, and known MIUI/Google system prompt packages.
- Only consider packages that expose a normal launcher entry point.
- Before assigning per-package task indexes, drop a candidate when a larger, lower-layer window from the same package contains it or substantially overlaps it. This prevents app dialogs, dropdowns, and popup surfaces from receiving their own control group while preserving similarly sized, independent freeform instances.
- Keep a separate geometry-only list of visible application, system, input-method, and split-screen-divider windows. These surfaces can hide the main window's pill when they overlap it, but never receive controls themselves.
- When a previously managed app window becomes fullscreen, preserve its controls only for that same Accessibility window ID. A different fullscreen window from the same package must not inherit the old window's controls.
- Prefer hiding controls on ambiguous/system surfaces over placing Close or Resize over a permission prompt.

## Device acceptance checklist

Test on the Redmi Pad SE 8.7 with the dock and Accessibility service enabled:

1. Open a normal app in a freeform window. Exactly one control group should appear on the app's main window.
2. Open that app's menu, dropdown, confirmation dialog, and in-app popup. The popup should not receive a duplicate control group; the main window should retain its own controls.
3. Trigger Android/HyperOS permission dialogs, share sheets, package installer prompts, notification shade, and other system UI surfaces. None should receive DevDex's Close/Resize controls.
4. Open two independent freeform instances of the same app if that app supports them. Each independent top-level window should retain controls; nested dialogs should not.
5. Maximize and restore a window that DevDex was already managing. Its control group should follow that same window; another fullscreen window from the same package must not inherit controls.
6. Check control hit targets with both touch and a mouse. The pill must not cover system permission choices or common app actions.

## Known limitation

AccessibilityWindowInfo does not expose a universal parent-activity/task identity for every OEM popup. This policy uses package eligibility, window layers, and geometry as conservative heuristics. Some ambiguous or unusual popups may be hidden or misclassified; device testing remains required.
