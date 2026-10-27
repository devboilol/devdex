package com.youki.dex.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import com.youki.dex.R

/**
 * WindowControlsManager — compact [ □ ] [ × ] controls for windowed apps.
 * Fullscreen activities deliberately receive no overlay controls.
 *
 * Why this exists: Android's own freeform caption bar is only drawn by some
 * ROMs / Android 15 desktop mode. On everything else a window had no
 * minimize / maximize / close at all. This class draws a compact overlay
 * pill centered against each app window's top edge using the
 * AccessibilityService window list (flagRetrieveInteractiveWindows is
 * already enabled in accessibility_service.xml).
 *
 *  • close    → removes the task through AppUtils.closeTask
 *  • resize   → toggles between maximized and standard window bounds.
 *
 * Preference "window_controls_enabled" (default true) switches it off live.
 * Everything is wrapped defensively: a failure here must never take the
 * dock down.
 */
class WindowControlsManager(
    private val service: AccessibilityService,
    private val ctx: Context,
    private val windowManager: WindowManager,
    private val prefs: SharedPreferences,
    private val displayId: () -> Int,
    private val dockHeight: () -> Int,
    /** resolves (packageName, nthWindowOfThatPackage) -> taskId or -1 */
    private val taskIdFor: (String, Int) -> Int,

    private val bringToFront: (Int) -> Unit,
    private val io: (() -> Unit) -> Unit
) {

    private class Pill(
        val view: LinearLayout,
        val params: WindowManager.LayoutParams,
        val maxBtn: IconButton,
        val opacityBtn: IconButton
    ) {
        var pkg: String = ""
        var windowBounds = Rect()
        var taskId = -1
        var taskIndex = 0
        var isMax = false
        var shownAtX = Int.MIN_VALUE
        var shownAtY = Int.MIN_VALUE
        var missingSince = 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pills = HashMap<Int, Pill>()
    private val pkgCache = HashMap<Int, String>()
    // Cache caption detection per accessibility window. Native Android
    // desktop captions already have Close/Maximize/Minimize buttons, so
    // drawing a second overlay on those windows creates duplicate controls.
    private val nativeCaptionCache = HashMap<Int, Boolean>()
    private val minimized = HashMap<Int, Rect>()
    private val homeMinimized = HashSet<Int>()
    private var pending = false
    private var destroyed = false

    private val updateRunnable = Runnable {
        pending = false
        if (!destroyed) updateNow()
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** Call on every windows-changed / config-changed event; coalesced to one update per frame. */
    fun scheduleUpdate(immediate: Boolean = false) {
        if (destroyed) return
        if (pending) {
            if (!immediate) return
            handler.removeCallbacks(updateRunnable)
            pending = false
        }
        pending = true
        // A separate overlay cannot become a literal child of another app's
        // window. Refreshing on the next frame minimizes visible trailing while
        // the user drags/resizes freeform windows.
        if (immediate) handler.post(updateRunnable)
        else handler.postDelayed(updateRunnable, 16)
    }

    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        removeAll()
        minimized.clear()
        homeMinimized.clear()
        launchablePackageCache.clear()
    }

    /** If [taskId] was minimized by this manager, restore it and return true. */
    fun restoreIfMinimized(taskId: Int): Boolean {
        val bounds = minimized.remove(taskId) ?: return false
        if (homeMinimized.remove(taskId)) {
            // The first minimize attempt was denied by the ROM, so Home was
            // used as a non-destructive fallback. The task retained its bounds.
            handler.post { bringToFront(taskId); scheduleUpdate() }
            return true
        }
        io {
            val restored = AppUtils.resizeTaskToBounds(ctx, taskId, bounds)
            handler.post {
                if (!restored) Toast.makeText(ctx, R.string.window_resize_failed, Toast.LENGTH_SHORT).show()
                bringToFront(taskId)
                scheduleUpdate()
            }
        }
        return true
    }

    // ───────────────────────────────────────────────────────────────────────

    private fun canDraw(): Boolean = try { Settings.canDrawOverlays(ctx) } catch (e: Exception) { false }

    @Suppress("DEPRECATION")
    private fun readWindows(): List<AccessibilityWindowInfo> = try {
        val id = displayId()
        if (Build.VERSION.SDK_INT >= 30) {
            service.windowsOnAllDisplays?.get(id) ?: emptyList()
        } else if (id == 0) {
            service.windows ?: emptyList()
        } else emptyList()
    } catch (e: Exception) { emptyList() }

    private class Cand(val id: Int, val layer: Int, val bounds: Rect, val pkg: String)

    /**
     * Packages used for OS-owned surfaces, permission prompts and transient
     * system UI must never receive app-window controls. Resolve the Home
     * package dynamically as users may switch back to HyperOS Launcher.
     */
    private val excludedSurfacePackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.intentresolver",
        "com.google.android.intentresolver",
        "com.miui.securitycenter",
        "com.miui.packageinstaller",
        "com.miui.global.packageinstaller",
        "com.google.android.gms",
        "com.google.android.googlequicksearchbox"
    )

    private val excludedSurfacePrefixes = listOf(
        "com.android.systemui.",
        "com.android.permissioncontroller.",
        "com.google.android.permissioncontroller.",
        "com.android.packageinstaller.",
        "com.google.android.packageinstaller.",
        "com.miui.securitycenter.",
        "com.miui.packageinstaller.",
        "com.miui.global.packageinstaller."
    )
    // Updates can be triggered rapidly while a window is dragged. Cache only
    // positive launcher-entry lookups so PackageManager is not queried on every frame.
    private val launchablePackageCache = HashSet<String>()

    private fun isEligibleWindowPackage(pkg: String, homePackage: String): Boolean {
        if (pkg.isBlank() || pkg == ctx.packageName || pkg == homePackage) return false
        if (pkg in excludedSurfacePackages || excludedSurfacePrefixes.any(pkg::startsWith)) return false
        if (launchablePackageCache.contains(pkg)) return true
        val launchable = try {
            // A real app's primary launcher activity is the baseline. This
            // excludes many non-launchable helper/popup packages by design.
            ctx.packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }
        if (launchable) launchablePackageCache.add(pkg)
        return launchable
    }

    private fun area(bounds: Rect): Long =
        bounds.width().coerceAtLeast(0).toLong() * bounds.height().coerceAtLeast(0).toLong()

    /**
     * A dialog/popup is a child surface when a lower-layer window from the same
     * package contains it, or substantially overlaps it while being clearly
     * larger. Separate freeform instances of one app are retained when their
     * bounds are independent or similar-sized.
     */
    private fun isNestedSameAppWindow(candidate: Cand, other: Cand): Boolean {
        if (candidate.id == other.id || candidate.pkg != other.pkg) return false
        if (other.layer >= candidate.layer) return false
        val candidateArea = area(candidate.bounds)
        val otherArea = area(other.bounds)
        if (candidateArea <= 0L || otherArea <= candidateArea) return false

        // Even a nearly full-window dialog should be treated as a child surface
        // when its bounds are contained by a slightly larger same-app window.
        val strictlyLargerContainer = other.bounds.contains(candidate.bounds) &&
            (other.bounds.width() > candidate.bounds.width() ||
                other.bounds.height() > candidate.bounds.height())
        if (strictlyLargerContainer) return true

        if (otherArea * 100L < candidateArea * 125L) return false

        val intersection = Rect()
        if (!intersection.setIntersect(other.bounds, candidate.bounds)) return false
        val overlapArea = area(intersection)
        return overlapArea * 100L >= candidateArea * 60L
    }

    @Suppress("DEPRECATION")
    private fun updateNow() {
        if (!prefs.getBoolean("window_controls_enabled", true) || !canDraw()) {
            removeAll(); return
        }
        try {
            val dm = DeviceUtils.getDisplayMetrics(ctx, displayId())
            val dispW = dm.widthPixels
            val dispH = dm.heightPixels
            val homePackage = try {
                AppUtils.getCurrentLauncher(ctx.packageManager)
            } catch (e: Exception) {
                ""
            }

            val cands = ArrayList<Cand>()
            // Keep geometry for visible app/system/IME/divider windows, including
            // excluded system surfaces and popup windows. This list only hides
            // the main window's controls when a higher layer covers the pill.
            val windowBlockers = ArrayList<Cand>()
            val seenIds = HashSet<Int>()
            val windows = readWindows()
            for (w in windows) {
                try {
                    val isApplicationWindow = w.type == AccessibilityWindowInfo.TYPE_APPLICATION
                    val canObscureControls = isApplicationWindow ||
                        w.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
                        w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                        w.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
                    if (!canObscureControls) continue
                    val b = Rect(); w.getBoundsInScreen(b)
                    if (b.width() <= 0 || b.height() <= 0) continue
                    // completely off-screen (minimized) → no controls
                    if (!Rect.intersects(b, Rect(0, 0, dispW, dispH))) continue
                    // Geometry from app and system surfaces blocks controls when
                    // they overlap the pill. Only application windows themselves
                    // are eligible to receive a control group; system/input-method
                    // windows are blockers only.
                    windowBlockers.add(Cand(w.id, w.layer, Rect(b), ""))
                    if (!isApplicationWindow) continue
                    // Avoid decorating unrelated fullscreen activities, but preserve
                    // controls for a window DevDex was already managing. In particular,
                    // the "Full screen" preset must not make the Close button disappear.
                    if (b.width() >= dispW * 0.94f && b.height() >= dispH * 0.94f) {
                        seenIds.add(w.id)
                        val root = try { w.root } catch (e: Exception) { null }
                        val livePkg = root?.packageName?.toString()
                        try { root?.recycle() } catch (e: Exception) {}
                        // Only preserve controls for this exact window when it
                        // expands to fullscreen. Package-wide matching allowed a
                        // fullscreen dialog/prompt from the same app to inherit
                        // controls meant for its underlying main window.
                        val alreadyManagedWindow = !livePkg.isNullOrBlank() &&
                            isEligibleWindowPackage(livePkg, homePackage) &&
                            pills[w.id]?.pkg == livePkg
                        if (alreadyManagedWindow) {
                            cands.add(Cand(w.id, w.layer, b, livePkg!!))
                        } else {
                            pkgCache.remove(w.id)
                            nativeCaptionCache.remove(w.id)
                        }
                        continue
                    }
                    // Window IDs can be recycled by Android between apps. Always
                    // re-read the live package, and invalidate caption detection
                    // when an ID now belongs to a different package; stale cached
                    // identities were one source of orphan close buttons.
                    // Accessibility roots can briefly be null while HyperOS switches a
                    // task between fullscreen and freeform. Do not tear down a working
                    // control pill for one transient null package name.
                    var root: android.view.accessibility.AccessibilityNodeInfo? =
                        try { w.root } catch (e: Exception) { null }
                    val livePkg = root?.packageName?.toString()
                    if (livePkg.isNullOrBlank()) {
                        val previous = pills[w.id]
                        if (previous != null && previous.pkg.isNotBlank() &&
                            previous.pkg != ctx.packageName
                        ) {
                            seenIds.add(w.id)
                            pkgCache[w.id] = previous.pkg
                            cands.add(Cand(w.id, w.layer, b, previous.pkg))
                        } else {
                            pkgCache.remove(w.id)
                            nativeCaptionCache.remove(w.id)
                        }
                        try { root?.recycle() } catch (e: Exception) {}
                        root = null
                        continue
                    }
                    if (pkgCache[w.id] != livePkg) nativeCaptionCache.remove(w.id)
                    pkgCache[w.id] = livePkg
                    seenIds.add(w.id)

                    // Never decorate DevDex, Home, or OS-owned transient surfaces.
                    // This intentionally prefers missing a marginal app over
                    // placing Close/Resize over a permission prompt or system popup.
                    if (!isEligibleWindowPackage(livePkg, homePackage)) {
                        try { root?.recycle() } catch (e: Exception) {}
                        root = null
                        nativeCaptionCache.remove(w.id)
                        continue
                    }

                    val hasNativeCaption = if (!nativeCaptionCache.containsKey(w.id)) {
                        if (root != null) {
                            val detected = hasNativeCaptionControls(root, b)
                            root = null // hasNativeCaptionControls recycles the node tree
                            nativeCaptionCache[w.id] = detected
                            detected
                        } else {
                            false
                        }
                    } else {
                        try { root?.recycle() } catch (e: Exception) {}
                        root = null
                        nativeCaptionCache[w.id] == true
                    }
                    if (hasNativeCaption) continue
                    cands.add(Cand(w.id, w.layer, b, livePkg))
                } finally {
                    try { w.recycle() } catch (e: Exception) {}
                }
            }
            pkgCache.keys.retainAll(seenIds)
            nativeCaptionCache.keys.retainAll(seenIds)

            // Package index is assigned only after child popups/dialogs and
            // system surfaces have been removed, so controls map to main app
            // windows rather than counting transient overlays as separate tasks.
            val topLevelCandidates = cands.filter { candidate ->
                !cands.any { other -> isNestedSameAppWindow(candidate, other) }
            }
            val perPkgCount = HashMap<String, Int>()
            val seenTaskIds = HashSet<Int>()
            val sorted = topLevelCandidates.sortedByDescending { it.layer }   // top-most first
            val pillH = dp(30)
            val controlOpacity = (prefs.getString("window_controls_opacity", "0.9")
                ?.toFloatOrNull() ?: 0.9f).coerceIn(0.15f, 1f)
            val dockHeightPx = dockHeight().coerceIn(0, dispH)
            val dockSlop = dp(8)
            val dockBounds = if (DockPositionUtils.get(prefs) == DockPositionUtils.Position.TOP) {
                Rect(0, 0, dispW, (dockHeightPx + dockSlop).coerceAtMost(dispH))
            } else {
                Rect(0, (dispH - dockHeightPx - dockSlop).coerceAtLeast(0), dispW, dispH)
            }

            val alive = HashSet<Int>()
            for (c in sorted) {
                val idx = perPkgCount.getOrDefault(c.pkg, 0)
                perPkgCount[c.pkg] = idx + 1
                val resolvedTaskId = taskIdFor(c.pkg, idx)
                // Window/task IDs can temporarily become unavailable while Android
                // changes windowing mode. Reuse the last known task for this same
                // package/window slot rather than removing the whole control group.
                val previousPill = pills[c.id]?.takeIf { it.pkg == c.pkg }
                    ?: pills.values.firstOrNull { it.pkg == c.pkg && it.taskIndex == idx }
                val taskId = if (resolvedTaskId >= 0) resolvedTaskId
                    else previousPill?.taskId?.takeIf { it >= 0 } ?: -1

                // A missing task ID must not hide the visible controls. HyperOS may
                // withhold getRunningTasks() results even while its freeform task is
                // on screen. Keep the pill and resolve the task through Shizuku/shell
                // only when the user invokes Close or Resize.
                if (taskId >= 0 && !seenTaskIds.add(taskId)) continue

                val pill = pills.getOrPut(c.id) { createPill() }
                alive.add(c.id)
                pill.missingSince = 0L
                pill.pkg = c.pkg
                pill.windowBounds = Rect(c.bounds)
                // Apply to the complete control group so the background and both icons
                // fade together. Stored as a string by SliderPreference.
                pill.view.alpha = controlOpacity
                pill.taskId = taskId
                pill.taskIndex = idx
                val statusish = DeviceUtils.getStatusBarHeight(ctx)
                pill.isMax = c.bounds.width() >= dispW - dp(8) &&
                    c.bounds.top <= statusish + dp(8) &&
                    c.bounds.height() >= dispH - dockHeight() - statusish - dp(24)
                pill.maxBtn.kind = if (pill.isMax) IconButton.RESTORE else IconButton.MAXIMIZE

                pill.view.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val pw = pill.view.measuredWidth
                // Keep one compact control group centered on this window.
                // Place it on the title-bar/top edge rather than above the
                // window so it tracks both portrait and landscape geometry.
                val x = (c.bounds.left + (c.bounds.width() - pw) / 2)
                    .coerceIn(0, (dispW - pw).coerceAtLeast(0))
                val desiredY = (c.bounds.top + dp(2)).coerceIn(0, (dispH - pillH).coerceAtLeast(0))
                val desiredRect = Rect(x, desiredY, x + pw, desiredY + pillH)
                // If the normal title-bar position intersects the dock, move the
                // controls just outside the dock's actual exclusion bounds. Using
                // dockHeight alone here left an 8dp overlap and hid the whole pill.
                val y = if (Rect.intersects(dockBounds, desiredRect)) {
                    if (DockPositionUtils.get(prefs) == DockPositionUtils.Position.TOP) {
                        (dockBounds.bottom + dp(2)).coerceIn(0, (dispH - pillH).coerceAtLeast(0))
                    } else {
                        (dockBounds.top - pillH - dp(2)).coerceIn(0, (dispH - pillH).coerceAtLeast(0))
                    }
                } else desiredY

                val pillRect = Rect(x, y, x + pw, y + pillH)
                val stillUnderDock = Rect.intersects(dockBounds, pillRect)
                val coveredByWindow = windowBlockers.any { o ->
                    o.id != c.id && o.layer > c.layer && Rect.intersects(o.bounds, pillRect)
                }
                val covered = stillUnderDock || coveredByWindow

                pill.view.visibility = if (covered) View.GONE else View.VISIBLE
                pill.params.x = x
                pill.params.y = y
                if (pill.view.windowToken == null) {
                    safeAdd(pill)
                } else if (pill.shownAtX != x || pill.shownAtY != y) {
                    try { windowManager.updateViewLayout(pill.view, pill.params) } catch (e: Exception) {}
                }
                pill.shownAtX = x; pill.shownAtY = y
                pill.maxBtn.invalidate()
            }

            // Keep controls through short HyperOS accessibility gaps, then clean
            // up stale windows. Re-schedule during the grace period because some
            // ROMs do not emit another accessibility event after a transient gap.
            val now = System.currentTimeMillis()
            var retryCleanup = false
            val iter = pills.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                if (e.key in alive) {
                    e.value.missingSince = 0L
                } else {
                    if (e.value.missingSince == 0L) e.value.missingSince = now
                    if (now - e.value.missingSince >= 1200L) {
                        safeRemove(e.value)
                        iter.remove()
                    } else {
                        retryCleanup = true
                    }
                }
            }
            if (retryCleanup) handler.postDelayed({ scheduleUpdate(immediate = true) }, 250)
            // forget minimized tasks that no longer exist anywhere is not possible
            // without a task list here — they are cleared when restored/closed.
        } catch (e: Exception) {
            // never let window-decoration problems crash the dock
        }
    }

    /**
     * Android desktop/freeform captions expose standard view IDs on builds
     * that publish their caption controls into the window's accessibility
     * tree. Detect the close button plus either maximize or minimize before
     * adding our overlay. OEMs that do not expose these IDs use the custom
     * controls as before.
     */
    private fun hasNativeCaptionControls(
        root: android.view.accessibility.AccessibilityNodeInfo,
        windowBounds: Rect
    ): Boolean {
        val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        var hasClose = false
        var hasResize = false
        var visited = 0
        queue.add(root)
        try {
            while (queue.isNotEmpty() && visited < 180) {
                val node = queue.removeFirst()
                visited++
                try {
                    val id = node.viewIdResourceName
                        ?.substringAfterLast('/')
                        ?.lowercase()
                    val nodeBounds = Rect()
                    node.getBoundsInScreen(nodeBounds)
                    val inCaptionBand = nodeBounds.top >= windowBounds.top - dp(4) &&
                        nodeBounds.top <= windowBounds.top + dp(72) &&
                        nodeBounds.bottom <= windowBounds.top + dp(104)
                    if (inCaptionBand && node.isVisibleToUser && node.isClickable && node.isEnabled) {
                        when (id) {
                            "close_window", "caption_close_window" -> hasClose = true
                            "maximize_window", "caption_maximize_window",
                            "minimize_window", "caption_minimize_window" -> hasResize = true
                        }
                    }
                    if (hasClose && hasResize) return true
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        queue.addLast(child)
                    }
                } finally {
                    try { node.recycle() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            // Caption detection is advisory only; fall back to custom controls.
        } finally {
            while (queue.isNotEmpty()) {
                try { queue.removeFirst().recycle() } catch (e: Exception) {}
            }
        }
        return false
    }

    private fun removeAll() {
        pills.values.forEach { safeRemove(it) }
        pills.clear()
    }

    private fun safeAdd(p: Pill) {
        if (!canDraw()) return
        try { windowManager.addView(p.view, p.params) } catch (e: Exception) {}
    }

    private fun safeRemove(p: Pill) {
        try { if (p.view.windowToken != null) windowManager.removeView(p.view) } catch (e: Exception) {}
    }

    // ───────────────────────────────────────────────────────────────────────

    private fun createPill(): Pill {
        // Resize + close share one compact group. Opacity is its own adjacent
        // icon group so it cannot be mistaken for a resize action.
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val controlsGroup = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6202124"))
                cornerRadius = dp(15).toFloat()
            }
            setPadding(dp(3), 0, dp(3), 0)
        }
        val opacityGroup = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6202124"))
                cornerRadius = dp(15).toFloat()
            }
        }
        val btnW = dp(34)
        val btnH = dp(30)
        val max = IconButton(ctx, IconButton.MAXIMIZE).apply {
            contentDescription = ctx.getString(R.string.window_resize)
        }
        val opacity = IconButton(ctx, IconButton.OPACITY).apply {
            contentDescription = ctx.getString(R.string.window_controls_opacity_title)
        }
        val close = IconButton(ctx, IconButton.CLOSE).apply {
            contentDescription = ctx.getString(R.string.close)
        }
        controlsGroup.addView(max, LinearLayout.LayoutParams(btnW, btnH))
        controlsGroup.addView(close, LinearLayout.LayoutParams(btnW, btnH))
        opacityGroup.addView(opacity, LinearLayout.LayoutParams(btnW, btnH))
        container.addView(controlsGroup, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, btnH
        ))
        container.addView(opacityGroup, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, btnH
        ).apply { marginStart = dp(4) })

        val type = if (DeviceUtils.isPureHarmonyOS()) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val pill = Pill(container, lp, max, opacity)
        max.setOnClickListener { showResizeMenu(pill) }
        opacity.setOnClickListener { showOpacityPicker(pill) }
        close.setOnClickListener { onClose(pill) }
        return pill
    }

    private fun onClose(p: Pill) {
        val originalTaskId = p.taskId
        val pkg = p.pkg
        val taskIndex = p.taskIndex
        minimized.remove(originalTaskId)
        homeMinimized.remove(originalTaskId)
        io {
            // Android may hide third-party freeform tasks from ActivityManager.
            // Resolve through ShellManager/Shizuku/Root before deciding that no
            // task can be closed. Never force-stop the package here: Close Window
            // must not terminate the whole app session.
            var taskId = if (originalTaskId != -1) originalTaskId
                else AppUtils.findTaskIdViaPrivilegedShell(ctx, pkg, taskIndex)
            var closed = taskId != -1 && AppUtils.closeTask(ctx, taskId)
            // The task list can be stale while Android is recreating a window.
            // If removing the mapped task failed, resolve it again by package and
            // retry once before surfacing a failure to the user.
            if (!closed) {
                val refreshedTaskId = AppUtils.findTaskIdViaPrivilegedShell(ctx, pkg, taskIndex)
                if (refreshedTaskId >= 0 && refreshedTaskId != taskId) {
                    taskId = refreshedTaskId
                    closed = AppUtils.closeTask(ctx, taskId)
                }
            }
            handler.post {
                if (!closed) {
                    val shizuku = ShizukoManager.getInstance(ctx)
                    if (!shizuku.hasPermission) {
                        // Running Shizuku doesn't automatically authorize DevDex.
                        // Ask Shizuku to grant this app access, rather than
                        // presenting a generic failure and leaving the user stuck.
                        shizuku.requestPermission()
                        Toast.makeText(ctx, R.string.shizuku_permission_required_for_window_close, Toast.LENGTH_LONG).show()
                    } else if (taskId == -1) {
                        Toast.makeText(ctx, R.string.window_task_not_found, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(ctx, R.string.window_control_privilege_required, Toast.LENGTH_LONG).show()
                    }
                }
                handler.postDelayed({ scheduleUpdate() }, 350)
            }
        }
    }

    private fun showResizeMenu(p: Pill) {
        // The menu remains available even when ActivityManager temporarily hides task IDs.
        // Resize menu contains sizing presets only. Opacity has its own adjacent icon.
        val menu = PopupMenu(ctx, p.maxBtn)
        menu.menu.add(0, 25, 0, ctx.getString(R.string.window_resize_25))
        menu.menu.add(0, 50, 1, ctx.getString(R.string.window_resize_50))
        menu.menu.add(0, 100, 2, ctx.getString(R.string.window_resize_fullscreen))
        menu.setOnMenuItemClickListener { item ->
            onResizePreset(p, item.itemId)
            true
        }
        try {
            menu.show()
        } catch (e: Exception) {
            Toast.makeText(ctx, R.string.window_resize_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Opacity slider anchored to the floating controls, not a separate Settings page. */
    private fun showOpacityPicker(p: Pill) {
        val current = ((prefs.getString("window_controls_opacity", "0.9")
            ?.toFloatOrNull() ?: 0.9f) * 100f).toInt().coerceIn(15, 100)

        val title = TextView(ctx).apply {
            text = ctx.getString(R.string.window_controls_opacity_title)
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        val value = TextView(ctx).apply {
            text = "$current%"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.END
        }
        val slider = SeekBar(ctx).apply {
            max = 85 // 15% minimum, so the controls can never become completely invisible.
            progress = current - 15
            contentDescription = ctx.getString(R.string.window_controls_opacity_title)
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0202124"))
                cornerRadius = dp(12).toFloat()
            }
            addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(value, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) })
            addView(slider, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
        }

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = (progress + 15).coerceIn(15, 100)
                value.text = "$percent%"
                val opacity = percent / 100f
                prefs.edit().putString(
                    "window_controls_opacity",
                    String.format(Locale.US, "%.2f", opacity)
                ).apply()
                pills.values.forEach { it.view.alpha = opacity }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val popup = PopupWindow(
            content,
            dp(240),
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(12).toFloat()
            })
            elevation = dp(8).toFloat()
        }
        try {
            popup.showAsDropDown(p.view, -dp(116), dp(4))
        } catch (e: Exception) {
            try {
                popup.showAsDropDown(p.opacityBtn, -dp(100), dp(4))
            } catch (ignored: Exception) {
                Toast.makeText(ctx, R.string.window_resize_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onResizePreset(p: Pill, percent: Int) {
        val originalTaskId = p.taskId
        val pkg = p.pkg
        val taskIndex = p.taskIndex
        val dh = dockHeight()
        val targetDisplayId = displayId()
        io {
            var taskId = if (originalTaskId >= 0) originalTaskId
                else AppUtils.findTaskIdViaPrivilegedShell(ctx, pkg, taskIndex)
            var resized = taskId >= 0 &&
                AppUtils.resizeTaskToPreset(ctx, taskId, percent, dh, targetDisplayId)

            // ActivityManager may give a stale ID during a windowing transition.
            // Re-resolve the package once before declaring the resize unsupported.
            if (!resized) {
                val refreshedTaskId = AppUtils.findTaskIdViaPrivilegedShell(ctx, pkg, taskIndex)
                if (refreshedTaskId >= 0 && refreshedTaskId != taskId) {
                    taskId = refreshedTaskId
                    resized = AppUtils.resizeTaskToPreset(ctx, taskId, percent, dh, targetDisplayId)
                }
            }
            handler.post {
                if (taskId >= 0) p.taskId = taskId
                if (!resized) {
                    Toast.makeText(ctx, R.string.window_resize_failed, Toast.LENGTH_SHORT).show()
                }
                handler.postDelayed({ scheduleUpdate(immediate = true) }, 300)
            }
        }
    }

    private fun onMinimize(p: Pill) {
        val taskId = p.taskId
        if (taskId == -1) return
        val b = Rect(p.windowBounds)
        val dm = DeviceUtils.getDisplayMetrics(ctx, displayId())
        minimized[taskId] = Rect(b)
        // park the window just below the bottom edge, keeping its size
        val parked = Rect(b.left, dm.heightPixels + dp(8), b.right, dm.heightPixels + dp(8) + b.height())
        io {
            val parkedSuccessfully = AppUtils.resizeTaskToBounds(ctx, taskId, parked)
            handler.post {
                if (!parkedSuccessfully) {
                    // Don't leave the task visible and claim minimize succeeded.
                    // Home is the unprivileged fallback; the task itself stays alive.
                    homeMinimized.add(taskId)
                    bringToFront(taskId)
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
                handler.postDelayed({ scheduleUpdate() }, 300)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────

    /** Small canvas-drawn icon button — no drawable resources needed. */
    private class IconButton(context: Context, var kind: Int) : View(context) {
        companion object {
            const val MINIMIZE = 0
            const val MAXIMIZE = 1
            const val RESTORE = 2
            const val CLOSE = 3
            const val OPACITY = 4
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 1.6f
            strokeCap = Paint.Cap.ROUND
        }

        init { isClickable = true; isFocusable = false }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val s = context.resources.displayMetrics.density * 5f
            paint.color = if (kind == CLOSE && isPressed) Color.parseColor("#FF6B6B") else Color.WHITE
            when (kind) {
                MINIMIZE -> canvas.drawLine(cx - s, cy + s * 0.6f, cx + s, cy + s * 0.6f, paint)
                MAXIMIZE -> canvas.drawRect(cx - s, cy - s, cx + s, cy + s, paint)
                RESTORE -> {
                    canvas.drawRect(cx - s, cy - s * 0.4f, cx + s * 0.4f, cy + s, paint)
                    canvas.drawLine(cx - s * 0.4f, cy - s * 0.4f, cx - s * 0.4f, cy - s, paint)
                    canvas.drawLine(cx - s * 0.4f, cy - s, cx + s, cy - s, paint)
                    canvas.drawLine(cx + s, cy - s, cx + s, cy + s * 0.4f, paint)
                    canvas.drawLine(cx + s, cy + s * 0.4f, cx + s * 0.4f, cy + s * 0.4f, paint)
                }
                OPACITY -> {
                    canvas.drawCircle(cx, cy, s, paint)
                    canvas.drawLine(cx, cy - s, cx, cy + s, paint)
                    canvas.drawLine(cx - s, cy, cx + s, cy, paint)
                }
                else -> {
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, paint)
                    canvas.drawLine(cx - s, cy + s, cx + s, cy - s, paint)
                }
            }
        }

        override fun setPressed(pressed: Boolean) { super.setPressed(pressed); invalidate() }
    }
}
