package com.youki.dex.utils

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import android.view.Display
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Best-effort adoption of foreground apps that were launched outside DevDex.
 *
 * Accessibility events do not reveal which launcher started an Activity, so
 * this deliberately operates on an eligible third-party app when it becomes
 * foreground, rather than claiming to know the launch source. It is opt-in
 * because changing another app's task windowing mode needs a compatible ROM
 * and may require Shizuku/Root or another privileged shell route.
 */
internal class ExternalLaunchAdoptionManager(
    private val service: AccessibilityService,
    private val prefs: SharedPreferences,
    private val dockHeightPx: () -> Int,
    private val onWindowAdopted: () -> Unit
) {
    companion object {
        const val PREF_KEY = "adopt_external_app_windows"
        private const val TAG = "DevDexExternalAdoption"
        private const val ADOPTION_DELAY_MS = 700L
        private const val PACKAGE_COOLDOWN_MS = 3_000L
        private const val MAX_ADOPTED_TASK_IDS = 64

        private val excludedPackages = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.intentresolver",
            "com.miui.securitycenter",
            "com.miui.packageinstaller"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val pendingPackages = HashSet<String>()
    private val pendingDevDexPackages = HashMap<String, Long>()
    private val lastAttemptAt = HashMap<String, Long>()
    private val adoptedTaskIds = LinkedHashMap<Int, String>()
    private val devDexManagedTaskIds = LinkedHashMap<Int, String>()
    private var lastObservedPackage: String? = null

    @Volatile
    private var destroyed = false

    fun onForegroundPackage(packageName: String?) {
        if (destroyed) return
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank()) return

        // Track every observed package, including Home and system surfaces, even
        // while this opt-in feature is disabled. This lets us distinguish a likely
        // Home-icon launch from a switch back to an app via Recents/notifications.
        val previousPackage = synchronized(lock) {
            val previous = lastObservedPackage
            lastObservedPackage = pkg
            previous
        }
        if (!isEnabled() || !isEligiblePackage(pkg)) return

        val launcherPackage = try {
            AppUtils.getCurrentLauncher(service.packageManager)
        } catch (e: Exception) {
            ""
        }
        // A TYPE_WINDOW_STATE_CHANGED event does not encode the launch source.
        // Home -> app is only a heuristic, but it avoids repeatedly reopening an
        // app every time the user returns to it from Recents or another surface.
        if (launcherPackage.isBlank() || previousPackage != launcherPackage) return

        val now = System.currentTimeMillis()
        val maySchedule = synchronized(lock) {
            val lastAttempt = lastAttemptAt[pkg] ?: 0L
            if (pendingDevDexPackages.containsKey(pkg) || pendingPackages.contains(pkg) ||
                now - lastAttempt < PACKAGE_COOLDOWN_MS
            ) {
                false
            } else {
                pendingPackages.add(pkg)
                lastAttemptAt[pkg] = now
                true
            }
        }
        if (!maySchedule) return

        scope.launch {
            try {
                delay(ADOPTION_DELAY_MS)
                if (destroyed || !isEnabled() || !isLatestForeground(pkg)) return@launch

                // First replay the stock launch through the same ActivityOptions
                // freeform path that works for apps opened by DevDex. Shell task
                // conversion alone is rejected on some HyperOS builds; the native
                // launch-options route is the fallback that does not require
                // Shizuku/Root just to request freeform.
                val launchRequested = requestFreeformLaunch(pkg)
                if (launchRequested) delay(450L)

                if (destroyed || !isEnabled() || !isLatestForeground(pkg)) return@launch

                var taskId = resolveTaskId(pkg)
                if (taskId < 0) {
                    // Some ROMs publish Accessibility events before their task list
                    // catches up. Retry lookup once after the transition settles.
                    delay(350L)
                    if (destroyed || !isEnabled() || !isLatestForeground(pkg)) return@launch
                    taskId = resolveTaskId(pkg)
                }

                var resized = false
                if (taskId >= 0) {
                    resized = AppUtils.resizeTaskToPreset(
                        service,
                        taskId,
                        50,
                        dockHeightPx(),
                        Display.DEFAULT_DISPLAY
                    )
                    if (resized) {
                        synchronized(lock) {
                            adoptedTaskIds[taskId] = pkg
                            trimTaskCache(adoptedTaskIds)
                        }
                        Log.i(TAG, "Adopted foreground task $taskId ($pkg) as a freeform window")
                    } else {
                        Log.w(TAG, "Task resize rejected for $taskId ($pkg); launch-options fallback requested=$launchRequested")
                    }
                } else {
                    Log.w(TAG, "No task ID available after freeform launch request for $pkg")
                }

                // The launch-options request itself may have changed the task mode
                // even if HyperOS refuses the follow-up shell resize. Refresh the
                // dock/task controls in either case and leave the result to device
                // verification rather than falsely reporting a guaranteed success.
                if (launchRequested || resized) {
                    withContext(Dispatchers.Main) {
                        if (!destroyed && isEnabled()) onWindowAdopted()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "External app adoption failed for $pkg", e)
            } finally {
                synchronized(lock) { pendingPackages.remove(pkg) }
            }
        }
    }

    private fun isLatestForeground(packageName: String): Boolean =
        synchronized(lock) { lastObservedPackage == packageName }

    private fun requestFreeformLaunch(packageName: String): Boolean {
        return try {
            val launchIntent = service.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            val component = launchIntent.component ?: return false
            val launcherApps = service.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val options = AppUtils.makeActivityOptions(
                service,
                "freeform",
                dockHeightPx(),
                Display.DEFAULT_DISPLAY
            )
            launcherApps.startMainActivity(
                component,
                Process.myUserHandle(),
                null,
                options.toBundle()
            )
            Log.i(TAG, "Requested freeform ActivityOptions launch for $packageName")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not request freeform ActivityOptions launch for $packageName", e)
            false
        }
    }

    /**
     * Mark an app that DevDex itself is about to launch. This avoids the
     * accessibility event from immediately converting a task whose windowing
     * mode was already deliberately chosen in DevDex's launch menu.
     */
    fun noteDevDexLaunch(packageName: String?) {
        val pkg = packageName?.trim().orEmpty()
        if (destroyed || !isEnabled() || !isEligiblePackage(pkg)) return

        val stamp = System.currentTimeMillis()
        synchronized(lock) { pendingDevDexPackages[pkg] = stamp }
        scope.launch {
            try {
                var taskId = -1
                for (attempt in 0 until 3) {
                    if (destroyed) return@launch
                    delay(if (attempt == 0) 550L else 400L)
                    taskId = resolveTaskId(pkg)
                    if (taskId >= 0) break
                }
                synchronized(lock) {
                    if (pendingDevDexPackages[pkg] == stamp) {
                        pendingDevDexPackages.remove(pkg)
                    }
                    if (taskId >= 0) {
                        devDexManagedTaskIds[taskId] = pkg
                        trimTaskCache(devDexManagedTaskIds)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Could not register DevDex-launched task for $pkg", e)
                synchronized(lock) {
                    if (pendingDevDexPackages[pkg] == stamp) {
                        pendingDevDexPackages.remove(pkg)
                    }
                }
            }
        }
    }

    fun destroy() {
        destroyed = true
        scope.cancel()
        synchronized(lock) {
            pendingPackages.clear()
            pendingDevDexPackages.clear()
            lastObservedPackage = null
            lastAttemptAt.clear()
            adoptedTaskIds.clear()
            devDexManagedTaskIds.clear()
        }
    }

    private fun trimTaskCache(cache: LinkedHashMap<Int, String>) {
        while (cache.size > MAX_ADOPTED_TASK_IDS) {
            val oldest = cache.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            } else break
        }
    }

    private fun isEnabled(): Boolean =
        prefs.getBoolean(PREF_KEY, false)

    private fun isEligiblePackage(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == service.packageName) return false

        return try {
            val launcherPackage = AppUtils.getCurrentLauncher(service.packageManager)
            if (pkg == launcherPackage || pkg in excludedPackages) return false
            if (pkg.startsWith("com.android.systemui.") ||
                pkg.startsWith("com.google.android.permissioncontroller.") ||
                pkg.startsWith("com.miui.securitycenter.")
            ) return false

            // Ignore transient overlays, keyboards, and service-only packages
            // that do not provide a normal launcher entry point.
            service.packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveTaskId(pkg: String): Int {
        try {
            val activityManager =
                service.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.getRunningTasks(50)
            tasks.firstOrNull { it.topActivity?.packageName == pkg }?.let { return it.id }
            tasks.firstOrNull {
                it.baseActivity?.packageName == pkg || it.origActivity?.packageName == pkg
            }?.let { return it.id }
        } catch (e: Exception) {
            Log.d(TAG, "Public task lookup unavailable for $pkg", e)
        }

        return try {
            AppUtils.findTaskIdViaPrivilegedShell(service, pkg)
        } catch (e: Exception) {
            Log.d(TAG, "Privileged task lookup unavailable for $pkg", e)
            -1
        }
    }
}
