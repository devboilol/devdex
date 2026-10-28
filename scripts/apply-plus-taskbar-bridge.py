#!/usr/bin/env python3
"""Apply the DevDex Plus taskbar / Lawnchair App Drawer integration."""

from pathlib import Path
import sys

root = Path(sys.argv[1])


def replace_once(path: Path, old: str, new: str, marker: str | None = None) -> None:
    data = path.read_text(encoding="utf-8")
    if marker and marker in data:
        return
    if new.strip() in data:
        return
    if old not in data:
        raise SystemExit(f"Patch anchor missing in {path}: {old[:140]!r}")
    path.write_text(data.replace(old, new, 1), encoding="utf-8")


# 1. DevDex taskbar: bridge the Apps button and taskbar visibility to Lawnchair.
perfect_server = root / "devdexengine/src/main/java/com/youki/dex/services/PerfectServer.kt"

replace_once(
    perfect_server,
    'const val DOCK_SERVICE_ACTION = "dock_service_action"\n',
    '''const val DOCK_SERVICE_ACTION = "dock_service_action"
private const val ACTION_DEVDEX_PLUS_TASKBAR_BRIDGE = "com.devboi.dex.action.TASKBAR_BRIDGE"
private const val ACTION_DEVDEX_PLUS_OPEN_LAWNCHAIR_DRAWER = "com.devboi.dex.action.OPEN_LAWNCHAIR_DRAWER"
private const val ACTION_DEVDEX_PLUS_HOME_VISIBLE = "devdex_plus_home_visible"
private const val ACTION_DEVDEX_PLUS_HOME_HIDDEN = "devdex_plus_home_hidden"
''',
)

replace_once(
    perfect_server,
    "    private var isPinned = false\n",
    "    private var isPinned = false\n    private var devDexPlusHomeVisible = false\n",
)

replace_once(
    perfect_server,
    '''                if (direction == Direction.UP) {
                    if (!isPinned) pinDock() else if (!appMenuVisible) showAppMenu()
                } else if (direction == Direction.DOWN) {
                    if (appMenuVisible) hideAppMenu() else unpinDock()
''',
    '''                if (direction == Direction.UP) {
                    if (!isPinned) pinDock() else if (!appMenuVisible) requestLawnchairAppDrawer()
                } else if (direction == Direction.DOWN) {
                    if (appMenuVisible) hideAppMenu() else unpinDock()
''',
)

replace_once(
    perfect_server,
    '''            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra("action")) {
                    LAUNCHER_RESUMED -> pinDock()
''',
    '''            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra("action")) {
                    ACTION_DEVDEX_PLUS_HOME_VISIBLE -> hideForLawnchairHome()
                    ACTION_DEVDEX_PLUS_HOME_HIDDEN -> showForForegroundApp()
                    LAUNCHER_RESUMED -> pinDock()
''',
)

replace_once(
    perfect_server,
    '''        ContextCompat.registerReceiver(this, launcherReceiver, IntentFilter(LAUNCHER_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
''',
    '''        val launcherFilter = IntentFilter(LAUNCHER_ACTION).apply {
            addAction(ACTION_DEVDEX_PLUS_TASKBAR_BRIDGE)
        }
        ContextCompat.registerReceiver(this, launcherReceiver, launcherFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
''',
)

replace_once(
    perfect_server,
    '''    private fun showDock() {
        dock.visibility = View.VISIBLE
''',
    '''    private fun hideForLawnchairHome() {
        // Lawnchair now owns Home; keep the taskbar pinned over app windows,
        // but explicitly hide it while the launcher surface is visible.
        isPinned = true
        pinBtn.setImageResource(R.drawable.ic_pin)
        devDexPlusHomeVisible = true
        dockHandler.removeCallbacksAndMessages(null)
        if (appMenuVisible) hideAppMenu()
        dockLayout.animate().cancel()
        dockLayout.visibility = View.GONE
        dock.visibility = View.GONE
        dockHandle.visibility = View.GONE
    }

    private fun showForForegroundApp() {
        devDexPlusHomeVisible = false
        if (!::dock.isInitialized || !::dockLayout.isInitialized) return
        showDock()
    }

    private fun showDock() {
        if (devDexPlusHomeVisible) return
        dock.visibility = View.VISIBLE
''',
)

replace_once(
    perfect_server,
    '''    private fun toggleAppMenu() {
        if (appMenuVisible) {
            hideAppMenu()
        } else {
            // ✅ FIX: ignore reopen if panel was just closed by outside-touch within debounce window
            val sinceClose = System.currentTimeMillis() - appMenuLastClosedAt
            if (sinceClose < PANEL_DEBOUNCE_MS) return
            showAppMenu()
        }
    }
''',
    '''    private fun toggleAppMenu() {
        requestLawnchairAppDrawer()
    }

    private fun requestLawnchairAppDrawer() {
        if (appMenuVisible) hideAppMenu()
        sendBroadcast(
            Intent(ACTION_DEVDEX_PLUS_OPEN_LAWNCHAIR_DRAWER)
                .setPackage(packageName)
        )
    }
''',
)

# 2. Lawnchair Application: receive the internal request to open All Apps.
lawnchair_app = root / "lawnchair/src/app/lawnchair/LawnchairApp.kt"
data = lawnchair_app.read_text(encoding="utf-8")
for old, new in [
    ("import android.content.ComponentName\n", "import android.content.BroadcastReceiver\nimport android.content.ComponentName\n"),
    ("import android.content.Intent\n", "import android.content.Intent\nimport android.content.IntentFilter\n"),
    ("import androidx.core.content.FileProvider\n", "import androidx.core.content.ContextCompat\nimport androidx.core.content.FileProvider\n"),
]:
    if new.strip() not in data:
        if old not in data:
            raise SystemExit(f"Import anchor missing in {lawnchair_app}: {old.strip()}")
        data = data.replace(old, new, 1)

constants = '''internal const val DEVDEX_PLUS_TASKBAR_BRIDGE_ACTION = "com.devboi.dex.action.TASKBAR_BRIDGE"
internal const val DEVDEX_PLUS_OPEN_LAWNCHAIR_DRAWER_ACTION = "com.devboi.dex.action.OPEN_LAWNCHAIR_DRAWER"
internal const val DEVDEX_PLUS_OPEN_ALL_APPS_EXTRA = "com.devboi.dex.extra.OPEN_ALL_APPS"
internal const val DEVDEX_PLUS_HOME_VISIBLE = "devdex_plus_home_visible"
internal const val DEVDEX_PLUS_HOME_HIDDEN = "devdex_plus_home_hidden"

'''
if "DEVDEX_PLUS_OPEN_LAWNCHAIR_DRAWER_ACTION" not in data:
    class_anchor = "class LawnchairApp : LauncherApplication() {\n"
    if class_anchor not in data:
        raise SystemExit("LawnchairApp class anchor missing")
    data = data.replace(class_anchor, constants + class_anchor, 1)

receiver = '''class LawnchairApp : LauncherApplication() {
    private val devDexPlusDrawerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val launchIntent = Intent(context, LawnchairLauncher::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(DEVDEX_PLUS_OPEN_ALL_APPS_EXTRA, true)
            }
            context.startActivity(launchIntent)
        }
    }

'''
if "private val devDexPlusDrawerReceiver" not in data:
    data = data.replace("class LawnchairApp : LauncherApplication() {\n", receiver, 1)

reg_anchor = '''        super.onCreate()
        instance = this
'''
reg_new = '''        super.onCreate()
        instance = this
        ContextCompat.registerReceiver(
            this,
            devDexPlusDrawerReceiver,
            IntentFilter(DEVDEX_PLUS_OPEN_LAWNCHAIR_DRAWER_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
'''
if "devDexPlusDrawerReceiver," not in data:
    if reg_anchor not in data:
        raise SystemExit("LawnchairApp onCreate anchor missing")
    data = data.replace(reg_anchor, reg_new, 1)
lawnchair_app.write_text(data, encoding="utf-8")

# 3. Lawnchair Launcher Activity: route drawer requests and announce foreground state.
lawnchair_launcher = root / "lawnchair/src/app/lawnchair/LawnchairLauncher.kt"
data = lawnchair_launcher.read_text(encoding="utf-8")

create_anchor = '''        super.onCreate(savedInstanceState)

        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)
'''
create_new = '''        super.onCreate(savedInstanceState)
        openDevDexPlusDrawerIfRequested(intent)

        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)
'''
if "openDevDexPlusDrawerIfRequested(intent)" not in data:
    if create_anchor not in data:
        raise SystemExit("LawnchairLauncher onCreate anchor missing")
    data = data.replace(create_anchor, create_new, 1)

new_intent_anchor = '''    override fun onNewIntent(intent: Intent?) {
'''
new_intent_new = '''    private fun openDevDexPlusDrawerIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(DEVDEX_PLUS_OPEN_ALL_APPS_EXTRA, false) != true) return
        intent.removeExtra(DEVDEX_PLUS_OPEN_ALL_APPS_EXTRA)
        dragLayer.post {
            stateManager.goToState(LauncherState.ALL_APPS)
        }
    }

    private fun sendDevDexPlusTaskbarBridge(action: String) {
        sendBroadcast(
            Intent(DEVDEX_PLUS_TASKBAR_BRIDGE_ACTION)
                .setPackage(packageName)
                .putExtra("action", action)
        )
    }

    override fun onNewIntent(intent: Intent?) {
'''
if "private fun openDevDexPlusDrawerIfRequested" not in data:
    if new_intent_anchor not in data:
        raise SystemExit("LawnchairLauncher onNewIntent anchor missing")
    data = data.replace(new_intent_anchor, new_intent_new, 1)

new_intent_end = '''        super.onNewIntent(intent)
    }

    override fun collectStateHandlers'''
new_intent_end_new = '''        super.onNewIntent(intent)
        openDevDexPlusDrawerIfRequested(intent)
    }

    override fun collectStateHandlers'''
if "openDevDexPlusDrawerIfRequested(intent)" not in data[data.find("override fun onNewIntent"):data.find("override fun collectStateHandlers")]:
    if new_intent_end not in data:
        raise SystemExit("LawnchairLauncher onNewIntent end anchor missing")
    data = data.replace(new_intent_end, new_intent_end_new, 1)

resume_anchor = '''    override fun onResume() {
        super.onResume()
        restartIfPending()
'''
resume_new = '''    override fun onPause() {
        super.onPause()
        sendDevDexPlusTaskbarBridge(DEVDEX_PLUS_HOME_HIDDEN)
    }

    override fun onResume() {
        super.onResume()
        sendDevDexPlusTaskbarBridge(DEVDEX_PLUS_HOME_VISIBLE)
        restartIfPending()
'''

print("Applied DevDex Plus taskbar, App Drawer, and launcher-visibility bridge.")
