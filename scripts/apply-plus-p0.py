from pathlib import Path
import sys

root = Path(sys.argv[1])

def replace_once(rel, old, new):
    path = root / rel
    data = path.read_text(encoding="utf-8")
    if new.strip() in data:
        return
    if old not in data:
        raise SystemExit(f"Patch anchor missing in {rel}: {old[:120]!r}")
    path.write_text(data.replace(old, new, 1), encoding="utf-8")

replace_once(
    "lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt",
    """    val isHotseatEnabled = preference(
        key = booleanPreferencesKey(name = "pref_show_hotseat"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_hotseat),
    )

    val hotseatQsbProvider = preference(""",
    """    val isHotseatEnabled = preference(
        key = booleanPreferencesKey(name = "pref_show_hotseat"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_hotseat),
    )

    /** DevDex Plus: request freeform launches for ordinary app icons. */
    val devDexPlusDesktopMode = preference(
        key = booleanPreferencesKey(name = "devdex_plus_desktop_mode"),
        defaultValue = true,
    )

    /** One-time initialization flag for Desktop Mode default. */
    val devDexPlusDesktopModeInitialized = preference(
        key = booleanPreferencesKey(name = "devdex_plus_desktop_mode_initialized"),
        defaultValue = false,
    )

    /** One-time migration to default-enable Desktop Mode on existing installs. */
    val devDexPlusDesktopModeMigrated = preference(
        key = booleanPreferencesKey(name = "devdex_plus_desktop_mode_migrated"),
        defaultValue = false,
    )

    val hotseatQsbProvider = preference(""",
)

replace_once(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/HomeScreenPreferences.kt",
    """        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
""",
    """        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            SwitchPreference(
                adapter = prefs2.devDexPlusDesktopMode.getAdapter(),
                label = stringResource(id = R.string.devdex_plus_desktop_mode),
                description = stringResource(id = R.string.devdex_plus_desktop_mode_description),
            )
""",
)

strings = root / "lawnchair/res/values/strings.xml"
data = strings.read_text(encoding="utf-8")
if 'name="devdex_plus_desktop_mode"' not in data:
    strings.write_text(data.replace(
        "</resources>",
        """    <string name="devdex_plus_desktop_mode">DevDex Plus desktop mode</string>
    <string name="devdex_plus_desktop_mode_description">Freeform window launches are enabled by default. If apps still open fullscreen, your Android firmware may block freeform mode.</string>
</resources>""",
        1
    ), encoding="utf-8")

quickstep = root / "quickstep/src/com/android/launcher3/uioverrides/QuickstepLauncher.java"
data = quickstep.read_text(encoding="utf-8")
def insert_after(anchor, addition):
    global data
    if addition.strip() in data:
        return
    if anchor not in data:
        raise SystemExit(f"QuickstepLauncher patch anchor missing: {anchor!r}")
    data = data.replace(anchor, anchor + addition, 1)

if "import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;" not in data:
    insert_after(
        "import static android.app.ActivityTaskManager.INVALID_TASK_ID;\n",
        "import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;\n",
    )
if "import android.graphics.Rect;" not in data:
    insert_after("package com.android.launcher3.uioverrides;\n\n",
                 "import android.graphics.Rect;\n")
if "import android.view.WindowInsets;" not in data:
    insert_after("import android.view.View;\n", "import android.view.WindowInsets;\n")
if "import app.lawnchair.preferences2.PreferenceManager2;" not in data:
    insert_after(
        "import app.lawnchair.compat.LawnchairQuickstepCompat;\n",
        "import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;\nimport app.lawnchair.preferences2.PreferenceManager2;\n",
    )

launch_anchor = """        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : DEFAULT_DISPLAY);
"""
launch_addition = """
        // DevDex Plus P0: preserve Lawnchair animations while requesting a freeform
        // task for app icons when Desktop Mode is enabled. ROM support is required.
        if (isDevDexPlusDesktopModeEnabled()
                && item != null
                && item.getTargetPackage() != null
                && (item.itemType == ITEM_TYPE_APPLICATION
                    || item.itemType == ITEM_TYPE_DEEP_SHORTCUT)) {
            View decorView = getWindow().getDecorView();
            activityOptions.options.setLaunchBounds(getDevDexPlusLaunchBounds(decorView));
            activityOptions.options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        }
"""
insert_after(launch_anchor, launch_addition)

method_anchor = """    @Override
    public ActivityOptionsWrapper makeDefaultActivityOptions(int splashScreenStyle) {
"""
helpers = """    private boolean isDevDexPlusDesktopModeEnabled() {
        PreferenceManager2 prefs2 = PreferenceManager2.INSTANCE.get(this);
        return PreferenceCacheExtensionsKt.firstCached(
                prefs2.getDevDexPlusDesktopMode(), prefs2);
    }

    /** Center a three-quarter-size initial window inside the available display area. */
    private Rect getDevDexPlusLaunchBounds(View decorView) {
        int displayWidth = decorView.getWidth();
        int displayHeight = decorView.getHeight();
        if (displayWidth <= 0 || displayHeight <= 0) {
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            displayWidth = metrics.widthPixels;
            displayHeight = metrics.heightPixels;
        }

        int insetLeft = 0;
        int insetTop = 0;
        int insetRight = 0;
        int insetBottom = 0;
        WindowInsets insets = decorView.getRootWindowInsets();
        if (insets != null) {
            insetLeft = Math.max(0, insets.getSystemWindowInsetLeft());
            insetTop = Math.max(0, insets.getSystemWindowInsetTop());
            insetRight = Math.max(0, insets.getSystemWindowInsetRight());
            insetBottom = Math.max(0, insets.getSystemWindowInsetBottom());
        }

        int usableWidth = Math.max(1, displayWidth - insetLeft - insetRight);
        int usableHeight = Math.max(1, displayHeight - insetTop - insetBottom);
        int width = Math.max(1, usableWidth * 3 / 4);
        int height = Math.max(1, usableHeight * 3 / 4);
        int left = insetLeft + (usableWidth - width) / 2;
        int top = insetTop + (usableHeight - height) / 2;
        return new Rect(left, top, left + width, top + height);
    }

"""
if "private boolean isDevDexPlusDesktopModeEnabled()" not in data:
    if method_anchor not in data:
        raise SystemExit("QuickstepLauncher method insertion anchor missing")
    data = data.replace(method_anchor, helpers + method_anchor, 1)
quickstep.write_text(data, encoding="utf-8")
print("Applied DevDex Plus P0 preference and freeform launch source changes.")
