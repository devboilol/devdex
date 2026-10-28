# DevDex UI system

## Direction

DevDex is an Android desktop environment, not a phone launcher skin. The visual system should feel calm, legible, and deliberately desktop-first: strong hierarchy, restrained surfaces, clear focus states, and compact window controls.

The first pass establishes shared tokens and refreshes the first-run desktop surface, Settings header, dock/app-drawer surfaces, and window-control visibility behavior. Existing feature screens keep their current navigation and view IDs so this pass does not rewrite service, permission, widget, or window-management contracts.

## Design tokens

- **Primary:** indigo (`#5155D9` in light theme, a softened lavender in dark theme).
- **Secondary:** cool slate for supporting actions and containers.
- **Accent:** cyan/teal for tertiary information and status.
- **Light surfaces:** pale blue-gray canvas with near-white content surfaces.
- **Dark surfaces:** graphite-blue canvas and layered slate surfaces, rather than pure black.
- **Shape language:** larger corners for the dock and desktop onboarding surface; tighter corners for app-drawer search and preference rows.
- **Typography:** one strong page title, short supporting copy, and compact uppercase labels only for small section cues.

The default and night themes share the same semantic token names. UI components should refer to theme attributes instead of hard-coded white, black, or brand colors wherever possible.

## Lawnchair-inspired patterns

Borrow interaction ideas, not Lawnchair's identity or assets:
- predictable app search and a visually distinct search field;
- consistent icon sizing and alignment;
- preference surfaces with restrained borders and clear touch feedback;
- configurable, low-friction launcher interactions.

DevDex keeps its own desktop model, dock, floating-window controls, task integration, and branding. Do not copy Lawnchair code or artwork without checking the exact upstream license and attribution requirements.

## Stability boundaries

The interface refresh must not change stored preference keys, window/task mapping, Shizuku/Root pathways, dock service lifecycle, accessibility configuration, widget persistence, or existing view IDs unless their consumers are updated in the same change.

## App identity migration

- Starting with the next candidate, the installed Android `applicationId` changes from `com.youki.dex` to `com.devboi.dex`.
- This is a new Android app identity, not an in-place upgrade of the older package. The old and new packages may coexist; Android will not automatically transfer app-private preferences, databases, permissions, or the selected default Home component.
- Keep the Kotlin/Java source namespace as `com.youki.dex` for this migration. It is separate from the installed `applicationId`, and changing every source package is a larger refactor that should not be bundled into this change.
- Do not claim old settings/data are preserved. Test the new package as a fresh install and keep the old installation until the user confirms the new build is usable.

## Device acceptance checklist

On the Redmi Pad SE 8.7 running HyperOS, test both portrait and landscape:
1. First-run card shows when setup is required and disappears entirely after the dock service connects.
2. Settings title/subtitle and permission shortcut do not overlap system insets or each other.
3. App drawer search, result taps, and keyboard search remain functional.
4. Dock and app drawer remain readable in both light and dark system themes.
5. Window controls do not flicker; Resize opens its size menu; Close targets only the selected window.
6. Dialogs, permission prompts, share sheets, notification shade, and app popups do not receive DevDex's control group.
7. Switching back to HyperOS Launcher does not break the dock or change the behavior of windows opened by DevDex.

Passing CI is necessary, not sufficient. OEM window-management behavior still needs on-device validation.
