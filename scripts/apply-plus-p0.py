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

# Existing installs may retain an explicit false from an earlier alpha.
# Use Lawnchair's own DataStore migration to enable freeform once, then preserve
# the user's subsequent choice.
replace_once(
    "lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt",
    """    override val preferencesDataStore = context.preferencesDataStore
""",
    """    override val preferencesDataStore = context.preferencesDataStore

    init {
        scope.launch {
            preferencesDataStore.edit { prefs ->
                val key = booleanPreferencesKey("devdex_plus_desktop_mode")
                val migratedKey = booleanPreferencesKey("devdex_plus_desktop_mode_migrated")
                if (prefs[migratedKey] != true) {
                    prefs[key] = true
                    prefs[migratedKey] = true
                }
            }
        }
    }
""",
)

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

# DevDex Plus: initialize a narrow Shizuku bridge for the system freeform flags.
# Do not reuse ShizukoManager.grantAll() here: it grants many unrelated permissions.
setup_class = root / "lawnchair/src/app/lawnchair/DevDexPlusFreeformSetup.kt"
setup_class.write_text(r'''package app.lawnchair

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DevDex Plus freeform bootstrap. The launcher can request freeform per launch,
 * but some Android builds ignore that request until their global freeform flags
 * have been enabled through Shizuku. Only these windowing flags are changed here.
 */
internal object DevDexPlusFreeformSetup {
    private const val TAG = "DevDexPlusFreeform"
    private const val REQUEST_CODE = 0xDD52
    private const val PREFS_NAME = "devdex_plus_shizuku_setup"
    private const val KEY_AUTO_PROMPTED = "permission_prompted"

    @Volatile private var initialized = false
    @Volatile private var feedbackAfterPermission = false
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val commandRunning = AtomicBoolean(false)
    private var newProcessMethod: Method? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        // Do not request permissions or run shell commands during launcher startup.
        // A Shizuku permission activity launched from Application.onCreate can leave
        // the HOME task in a blank state on some ROMs. Setup is explicitly user-triggered.
        if (::appContext.isInitialized) {
            Log.i(TAG, "Shizuku binder connected; freeform setup is user-triggered")
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.i(TAG, "Shizuku binder disconnected")
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    applyFreeformFlags(feedbackAfterPermission)
                } else {
                    feedbackAfterPermission = false
                    toast("Shizuku permission was denied. Use the setup action to try again.")
                }
            }
        }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
    }

    /** Called by the explicit settings action, so a denied permission can be retried. */
    fun request(context: Context) {
        initialize(context)
        if (!isAvailable()) {
            openShizuku()
            return
        }

        if (hasPermission()) {
            applyFreeformFlags(true)
        } else {
            feedbackAfterPermission = true
            try {
                Shizuku.requestPermission(REQUEST_CODE)
            } catch (e: Exception) {
                Log.w(TAG, "Could not request Shizuku permission", e)
                toast("Could not contact Shizuku. Start its service and retry.")
            }
        }
    }

    private fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    private fun hasPermission(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    private fun openShizuku() {
        try {
            val intent = appContext.packageManager
                .getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                toast("Start Shizuku, then return here and run freeform setup again.")
            } else {
                toast("Shizuku is not installed. Install and start it before setting up floating windows.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open Shizuku", e)
            toast("Start Shizuku, then retry freeform setup.")
        }
    }

    private fun applyFreeformFlags(showFeedback: Boolean) {
        if (!hasPermission() || !commandRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                val sdk = Build.VERSION.SDK_INT
                val commands = buildList {
                    add("settings put global enable_freeform_support 1")
                    add("settings put global force_desktop_mode_on_external_displays 0")
                    if (sdk >= Build.VERSION_CODES.S) {
                        add("wm set-multi-window-config --freeformWindowManagement true")
                    }
                    if (sdk == 35) {
                        add("wm set-multi-window-config --supportsDesktopWindowing true")
                        add("wm set-multi-window-config --enableDesktopMode true")
                    }
                }

                commands.forEach { command ->
                    val output = runShell(command)
                    if (!output.isNullOrBlank()) {
                        Log.i(TAG, "Output for command '$command': $output")
                    }
                }

                val enabled = try {
                    Settings.Global.getInt(
                        appContext.contentResolver,
                        "enable_freeform_support",
                        0,
                    ) == 1
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify freeform setting", e)
                    false
                }

                if (showFeedback) {
                    mainHandler.post {
                        val message = if (enabled) {
                            "Freeform support configured. Test an app icon; HyperOS may still override window requests."
                        } else {
                            "Shizuku ran the setup, but Android did not confirm freeform support. This ROM may block it."
                        }
                        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Freeform setup failed", e)
                if (showFeedback) toast("Freeform setup failed. Check that Shizuku permission is granted.")
            } finally {
                feedbackAfterPermission = false
                commandRunning.set(false)
            }
        }
    }

    @Suppress("DiscouragedPrivateApi")
    private fun newProcess(command: String): ShizukuRemoteProcess {
        val method = newProcessMethod ?: Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).also {
            it.isAccessible = true
            newProcessMethod = it
        }
        return method.invoke(null, arrayOf("sh", "-c", command), null, null) as ShizukuRemoteProcess
    }

    private fun runShell(command: String): String? {
        if (!hasPermission()) return null
        return try {
            val process = newProcess(command)
            var stderr = ""
            val stderrReader = Thread {
                stderr = runCatching {
                    process.errorStream.bufferedReader().use { it.readText().trim() }
                }.getOrDefault("")
            }
            stderrReader.start()
            val stdout = process.inputStream.bufferedReader().use { it.readText().trim() }
            stderrReader.join(1000)
            runCatching { process.destroy() }
            val combined = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
            if (combined.isBlank()) null else combined
        } catch (e: Exception) {
            Log.w(TAG, "Shell command failed: $command", e)
            e.message
        }
    }

    private fun toast(message: String) {
        mainHandler.post {
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
''', encoding="utf-8")

# Keep Shizuku completely out of Lawnchair startup. DevDexPlusFreeformSetup
# initializes lazily only after the user taps the explicit setup action.

# A manual setup action lets users retry after denying the Shizuku permission or
# start Shizuku when its service is not currently running.
replace_once(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/HomeScreenPreferences.kt",
    "import app.lawnchair.LawnchairApp\n",
    "import app.lawnchair.DevDexPlusFreeformSetup\nimport app.lawnchair.LawnchairApp\n",
)
replace_once(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/HomeScreenPreferences.kt",
    """            SwitchPreference(
                adapter = prefs2.devDexPlusDesktopMode.getAdapter(),
                label = stringResource(id = R.string.devdex_plus_desktop_mode),
                description = stringResource(id = R.string.devdex_plus_desktop_mode_description),
            )
""",
    """            SwitchPreference(
                adapter = prefs2.devDexPlusDesktopMode.getAdapter(),
                label = stringResource(id = R.string.devdex_plus_desktop_mode),
                description = stringResource(id = R.string.devdex_plus_desktop_mode_description),
            )
            ClickablePreference(
                label = stringResource(id = R.string.devdex_plus_freeform_setup),
                subtitle = stringResource(id = R.string.devdex_plus_freeform_setup_description),
                onClick = { DevDexPlusFreeformSetup.request(context) },
            )
""",
)

strings = root / "lawnchair/res/values/strings.xml"
data = strings.read_text(encoding="utf-8")
if 'name="devdex_plus_freeform_setup"' not in data:
    data = data.replace(
        "</resources>",
        """    <string name="devdex_plus_freeform_setup">Set up floating windows</string>
    <string name="devdex_plus_freeform_setup_description">Use Shizuku to enable Android freeform support. Some HyperOS builds may still override window requests.</string>
</resources>""",
        1,
    )
    strings.write_text(data, encoding="utf-8")

print("Applied DevDex Plus freeform launch, Shizuku setup, and settings integration.")
