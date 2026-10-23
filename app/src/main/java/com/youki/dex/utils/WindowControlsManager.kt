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
import android.widget.Toast

/**
 * WindowControlsManager — Windows-style [ – ] [ □ ] [ × ] buttons for every
 * app window, including fullscreen apps.
 *
 * Why this exists: Android's own freeform caption bar is only drawn by some
 * ROMs / Android 15 desktop mode. On everything else a window had no
 * minimize / maximize / close at all. This class draws a compact overlay
 * pill centered against each app window's top edge using the
 * AccessibilityService window list (flagRetrieveInteractiveWindows is
 * already enabled in accessibility_service.xml).
 *
 *  • close    → `am task remove <id>` (AppUtils.closeTask); if no task id is
 *               known the app is force-stopped through the shell instead
 *  • maximize → AppUtils.resizeTask(..., "maximized") / back to "standard"
 *  • minimize → tries to park the task off-screen; if the ROM blocks task
 *               resizing, falls back to Android Home and restores from the dock.
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
    private val forceStop: (String) -> Boolean,
    private val bringToFront: (Int) -> Unit,
    private val io: (() -> Unit) -> Unit
) {

    private class Pill(
        val view: LinearLayout,
        val params: WindowManager.LayoutParams,
        val maxBtn: IconButton
    ) {
        var pkg: String = ""
        var windowBounds = Rect()
        var taskId = -1
        var isMax = false
        var shownAtX = Int.MIN_VALUE
        var shownAtY = Int.MIN_VALUE
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

    /** Call on every windows-changed / config-changed event; throttled internally. */
    fun scheduleUpdate() {
        if (destroyed || pending) return
        pending = true
        // Coalesce window-change events to roughly two frames on a 60 Hz
        // display. 120 ms made the controls visibly trail moving windows.
        handler.postDelayed(updateRunnable, 40)
    }

    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        removeAll()
        minimized.clear()
        homeMinimized.clear()
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

    @Suppress("DEPRECATION")
    private fun updateNow() {
        if (!prefs.getBoolean("window_controls_enabled", true) || !canDraw()) {
            removeAll(); return
        }
        try {
            val dm = DeviceUtils.getDisplayMetrics(ctx, displayId())
            val dispW = dm.widthPixels
            val dispH = dm.heightPixels

            val cands = ArrayList<Cand>()
            val seenIds = HashSet<Int>()
            val windows = readWindows()
            for (w in windows) {
                try {
                    if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                    val b = Rect(); w.getBoundsInScreen(b)
                    if (b.width() <= 0 || b.height() <= 0) continue
                    // completely off-screen (minimized) → no controls
                    if (!Rect.intersects(b, Rect(0, 0, dispW, dispH))) continue
                    // Fullscreen apps also get controls, anchored at the
                    // top center. This uses the same layout in portrait and
                    // landscape and avoids creating a second special case.
                    var pkg = pkgCache[w.id]
                    if (pkg == null || !nativeCaptionCache.containsKey(w.id)) {
                        val root = w.root
                        if (pkg == null) {
                            pkg = root?.packageName?.toString()
                            if (pkg != null) pkgCache[w.id] = pkg
                        }
                        if (root != null && !nativeCaptionCache.containsKey(w.id)) {
                            // hasNativeCaptionControls() owns and recycles the
                            // root node together with the child nodes it visits.
                            nativeCaptionCache[w.id] = hasNativeCaptionControls(root, b)
                        } else {
                            try { root?.recycle() } catch (e: Exception) {}
                        }
                    }
                    if (pkg.isNullOrEmpty() || pkg == ctx.packageName) continue
                    seenIds.add(w.id)
                    // Native/system caption buttons take precedence when they
                    // are exposed through the accessibility tree. This keeps
                    // one control set instead of stacking a second close button.
                    if (nativeCaptionCache[w.id] == true) continue
                    cands.add(Cand(w.id, w.layer, b, pkg))
                } finally {
                    try { w.recycle() } catch (e: Exception) {}
                }
            }
            pkgCache.keys.retainAll(seenIds)
            nativeCaptionCache.keys.retainAll(seenIds)

            // per-package index → so two windows of one app map to two tasks
            val perPkgCount = HashMap<String, Int>()
            val seenTaskIds = HashSet<Int>()
            val seenUnresolvedPackages = HashSet<String>()
            val sorted = cands.sortedByDescending { it.layer }   // top-most first (matches recent-task order)
            val pillH = dp(30)
            val gap = dp(2)

            val alive = HashSet<Int>()
            for (c in sorted) {
                val idx = perPkgCount.getOrDefault(c.pkg, 0)
                perPkgCount[c.pkg] = idx + 1
                val taskId = taskIdFor(c.pkg, idx)

                // Dialogs/popups can appear as extra TYPE_APPLICATION windows
                // for the same task. Only one control group should be drawn
                // for each real task; if the task ID is inaccessible, show
                // one group per package rather than duplicating Close buttons.
                if (taskId != -1) {
                    if (!seenTaskIds.add(taskId)) continue
                } else {
                    if (!seenUnresolvedPackages.add(c.pkg)) continue
                }

                val pill = pills.getOrPut(c.id) { createPill() }
                alive.add(c.id)
                pill.pkg = c.pkg
                pill.windowBounds = Rect(c.bounds)
                pill.taskId = taskId
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
                val y = (c.bounds.top + dp(2)).coerceIn(0, (dispH - pillH).coerceAtLeast(0))

                // hide the pill when a window that is above this one overlaps it
                val pillRect = Rect(x, y, x + pw, y + pillH)
                val covered = sorted.any { o -> o.id != c.id && o.layer > c.layer && Rect.intersects(o.bounds, pillRect) }

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

            // drop pills of windows that are gone
            val iter = pills.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                if (e.key !in alive) { safeRemove(e.value); iter.remove() }
            }
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
                    if (inCaptionBand) {
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
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6202124"))
                cornerRadius = dp(15).toFloat()
            }
            setPadding(dp(4), 0, dp(4), 0)
        }
        val btnW = dp(34)
        val btnH = dp(30)
        val min = IconButton(ctx, IconButton.MINIMIZE)
        val max = IconButton(ctx, IconButton.MAXIMIZE)
        val close = IconButton(ctx, IconButton.CLOSE)
        row.addView(min, LinearLayout.LayoutParams(btnW, btnH))
        row.addView(max, LinearLayout.LayoutParams(btnW, btnH))
        row.addView(close, LinearLayout.LayoutParams(btnW, btnH))

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

        val pill = Pill(row, lp, max)

        min.setOnClickListener { onMinimize(pill) }
        max.setOnClickListener { onMaximize(pill) }
        close.setOnClickListener { onClose(pill) }
        return pill
    }

    private fun onClose(p: Pill) {
        val taskId = p.taskId
        val pkg = p.pkg
        minimized.remove(taskId)
        homeMinimized.remove(taskId)
        io {
            var closed = taskId != -1 && AppUtils.closeTask(ctx, taskId)
            if (!closed) closed = forceStop(pkg)
            handler.post {
                if (!closed) Toast.makeText(ctx, R.string.close_app_privilege_required, Toast.LENGTH_SHORT).show()
                handler.postDelayed({ scheduleUpdate() }, 350)
            }
        }
    }

    private fun onMaximize(p: Pill) {
        val taskId = p.taskId
        if (taskId == -1) return
        val mode = if (p.isMax) "standard" else "maximized"
        val dh = dockHeight()
        io {
            val resized = AppUtils.resizeTask(ctx, mode, taskId, dh)
            handler.post {
                if (!resized) Toast.makeText(ctx, R.string.window_resize_failed, Toast.LENGTH_SHORT).show()
                handler.postDelayed({ scheduleUpdate() }, 300)
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
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
                handler.postDelayed({ scheduleUpdate() }, 300)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────

    /** Small canvas-drawn icon button — no drawable resources needed. */
    private class IconButton(context: Context, var kind: Int) : View(context) {
        companion object { const val MINIMIZE = 0; const val MAXIMIZE = 1; const val RESTORE = 2; const val CLOSE = 3 }

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
                else -> {
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, paint)
                    canvas.drawLine(cx - s, cy + s, cx + s, cy - s, paint)
                }
            }
        }

        override fun setPressed(pressed: Boolean) { super.setPressed(pressed); invalidate() }
    }
}
