#!/usr/bin/env python3
"""Apply the minimal DevDex freeform launch patch to a pinned Lawnchair checkout.

This deliberately does not import DevDex Engine, Shizuku, overlays, or window chrome.
It only requests Android freeform mode and centered bounds for app/shortcut launches.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch-lawnchair-freeform.py LAWNCHAIR_DIR")

root = Path(sys.argv[1]).resolve()
source = root / "quickstep/src/com/android/launcher3/uioverrides/QuickstepLauncher.java"
if not source.is_file():
    raise SystemExit(f"QuickstepLauncher.java not found: {source}")

text = source.read_text(encoding="utf-8")

if "DEVDEX_FREEFORM_APP_LAUNCH" in text:
    print("Lawnchair freeform patch already applied.")
    raise SystemExit(0)

static_anchor = "import static android.app.ActivityTaskManager.INVALID_TASK_ID;\n"
if static_anchor not in text:
    raise SystemExit("Could not find QuickstepLauncher static-import anchor")
text = text.replace(
    static_anchor,
    static_anchor + "import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;\n",
    1,
)

rect_anchor = "import android.graphics.Rect;\n"
if rect_anchor not in text:
    raise SystemExit("Could not find QuickstepLauncher Rect import")
text = text.replace(rect_anchor, "import android.graphics.Point;\n" + rect_anchor, 1)

launch_anchor = """        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : DEFAULT_DISPLAY);
        Utilities.allowBGLaunch(activityOptions.options);
"""
if text.count(launch_anchor) != 1:
    raise SystemExit(
        "Expected exactly one getActivityLaunchOptions launch-display block; "
        f"found {text.count(launch_anchor)}"
    )

launch_patch = """        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : DEFAULT_DISPLAY);

        // DEVDEX_FREEFORM_APP_LAUNCH: keep Lawnchair's launch path, request only
        // standard Android freeform windows for app icons and deep shortcuts.
        ItemInfo launchItem = item != null ? item
                : (v != null && v.getTag() instanceof ItemInfo ? (ItemInfo) v.getTag() : null);
        if (launchItem != null
                && (launchItem.itemType == ITEM_TYPE_APPLICATION
                    || launchItem.itemType == ITEM_TYPE_DEEP_SHORTCUT)
                && v != null && v.getDisplay() != null) {
            Point displaySize = new Point();
            v.getDisplay().getRealSize(displaySize);
            int displayWidth = Math.max(1, displaySize.x);
            int displayHeight = Math.max(1, displaySize.y);
            int windowWidth = Math.max(1, Math.round(displayWidth * 0.82f));
            int windowHeight = Math.max(1, Math.round(displayHeight * 0.78f));
            int left = Math.max(0, (displayWidth - windowWidth) / 2);
            int top = Math.max(0, (displayHeight - windowHeight) / 2);
            activityOptions.options.setLaunchBounds(
                    new Rect(left, top, left + windowWidth, top + windowHeight));
            activityOptions.options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        }

        Utilities.allowBGLaunch(activityOptions.options);
"""
text = text.replace(launch_anchor, launch_patch, 1)
source.write_text(text, encoding="utf-8")
print(f"Patched freeform app launches in {source}")
