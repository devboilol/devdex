# Lawnchair-inspired patterns / Các ý tưởng tham khảo Lawnchair

## Decision / Quyết định

DevDex will **borrow interaction and visual ideas**, not merge or embed the Lawnchair launcher. The current product goal is to keep the HyperOS Home app in place while DevDex's dock and window controls operate alongside it.

Lawnchair 16's public README describes Material 3 Expressive theming, At a Glance/Smartspacer, QuickSwitch integration with Android Recents, global search, and icon/font/color customization. Lawnchair's 15 beta release notes also describe app-drawer folders and automatic app categorization. See the [Lawnchair 16 README](https://github.com/LawnchairLauncher/lawnchair/blob/16-dev/README.md), [release notes](https://github.com/LawnchairLauncher/lawnchair/releases), and [license](https://github.com/LawnchairLauncher/lawnchair/blob/16-dev/LICENSE.txt).

## Patterns to adapt / Các yếu tố nên học hỏi

### 1. Clear, search-first app drawer — first small UI step

DevDex already has an app-drawer search field, a favorites row, and inline calculator handling. Improve the existing experience rather than introducing a second launcher/search backend. The first isolated polish is a visible clear-query affordance while typing, keeping focus in the field after the user clears it.

### 2. Material 3 Expressive surfaces — later, after device validation

Evaluate a consistent shape, spacing, and color system for the app drawer, dock panels, and settings. Use Android dynamic colors only where supported and preserve a deliberate fallback for older Android versions and the existing desktop theme. Do not repaint every surface at once.

### 3. App organization — roadmap candidate

Explore manual folders or useful grouping only after validating the external-launch window experiment. DevDex's current desktop workspace and app drawer have different jobs; folder organization must not make running tasks harder to identify or switch between.

## Explicitly out of scope for this iteration / Chưa làm trong đợt này

- Do not merge the entire Launcher3/Lawnchair codebase into DevDex.
- Do not replace the user's default Home app or attempt to take over Android Recents. Lawnchair's QuickSwitch integration has separate system/root requirements; it does not solve task windowing by itself.
- Do not copy upstream source files in this iteration. If source code is considered for a later port, review the exact files and dependencies, preserve required Apache-2.0 notices, and verify license compatibility and attribution before merging.
- Do not adopt visual or behavioral changes that obscure the dock, hide window controls, or interfere with Android's secure/system UI.

## Acceptance order / Thứ tự kiểm thử

1. Validate the opt-in **Auto-adopt external app windows** experiment on the Redmi Pad SE 8.7 with ordinary third-party apps.
2. Confirm the normal HyperOS launcher remains usable and the dock/window controls survive app switches.
3. Verify the clearable app-drawer search works with keyboard and touch, without losing the existing favorites and calculator behavior.
4. Only then evaluate broader Material 3 styling or folder organization.

This is a design/implementation note, not an official Lawnchair integration and not an endorsement by the Lawnchair maintainers.
