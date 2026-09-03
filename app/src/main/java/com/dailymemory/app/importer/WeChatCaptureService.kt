package com.dailymemory.app.importer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.dailymemory.app.R
import java.time.LocalDate

class WeChatCaptureService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LinearLayout? = null
    private var label: TextView? = null
    private var action: Button? = null
    private var pages = 0
    private var unchanged = 0
    private var previousPage = ""
    private var startedAt = 0L
    private var captureGeneration = -1
    private var scheduled = false
    private val tick = Runnable { scheduled = false; capturePage() }
    private val visibilityWatch = object : Runnable {
        override fun run() {
            if (ImportSession.stage !in setOf(ImportSession.Stage.READY, ImportSession.Stage.CAPTURING)) {
                stopTick(); removeOverlay(); return
            }
            val root = rootInActiveWindow
            val inWeChat = root?.packageName?.toString() == WECHAT
            root?.recycle()
            if (!inWeChat) {
                stopTick(); removeOverlay()
                ImportSession.pause("已暂停；返回目标群后可继续提取")
            } else showOverlay()
            handler.postDelayed(this, 650)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(visibilityWatch)
        handler.postDelayed(visibilityWatch, 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (ImportSession.stage !in setOf(ImportSession.Stage.READY, ImportSession.Stage.CAPTURING)) {
            stopTick(); removeOverlay(); return
        }
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != WECHAT) {
            // Never inspect another app. Returning to WeChat requires another explicit Start tap.
            stopTick(); removeOverlay()
            if (ImportSession.stage == ImportSession.Stage.CAPTURING) ImportSession.pause("已暂停；返回目标群后可继续提取")
            root?.recycle(); return
        }
        root.recycle()
        showOverlay()
        handler.removeCallbacks(visibilityWatch)
        handler.postDelayed(visibilityWatch, 650)
        if (ImportSession.stage == ImportSession.Stage.CAPTURING) schedule()
    }

    override fun onInterrupt() { ImportSession.pause("屏幕读取已中断，可返回软件继续审核"); stopTick(); removeOverlay() }
    override fun onDestroy() {
        handler.removeCallbacks(visibilityWatch)
        ImportSession.pause("屏幕读取服务已关闭，请返回软件审核已提取内容")
        stopTick(); removeOverlay(); super.onDestroy()
    }

    private fun schedule() {
        if (!scheduled) { scheduled = true; handler.postDelayed(tick, 950) }
    }
    private fun stopTick() { handler.removeCallbacks(tick); scheduled = false }

    private fun begin() {
        val root = rootInActiveWindow ?: return
        try {
            if (!isTargetChat(root)) { ImportSession.status = "请进入群“${ImportSession.group}”的聊天页面；不要停留在搜索结果或群设置"; render(); return }
            if (captureGeneration != ImportSession.generation) {
                pages = 0; captureGeneration = ImportSession.generation
            }
            unchanged = 0; previousPage = ""; startedAt = System.currentTimeMillis()
            ImportSession.startCapture(); render(); schedule()
        } finally { root.recycle() }
    }

    private fun capturePage() {
        if (ImportSession.stage != ImportSession.Stage.CAPTURING) { removeOverlay(); return }
        val range = ImportSession.range ?: return
        val root = rootInActiveWindow
        if (root == null) { ImportSession.pause("暂时无法读取微信屏幕，请重新开始"); render(); return }
        try {
            if (!isTargetChat(root)) {
                ImportSession.pause("已暂停：请回到所选群聊，点击继续提取")
                if (root.packageName?.toString() != WECHAT) removeOverlay() else render()
                return
            }
            val lists = findNodes(root) { it.className?.contains("ListView") == true || it.className?.contains("RecyclerView") == true }
            val list = lists.maxByOrNull { bounds(it).height() }
            if (list == null) { ImportSession.pause("当前微信版本未提供可读取的聊天列表，请使用文本导入"); render(); return }
            try {
                val rows = (0 until list.childCount).mapNotNull { index ->
                    val row = list.getChild(index) ?: return@mapNotNull null
                    try { ChatRow(texts(row)) } finally { row.recycle() }
                }
                val pageKey = fingerprint(*rows.flatMap { it.labels }.map { it.text + "|" + it.description }.toTypedArray())
                unchanged = if (pageKey == previousPage) unchanged + 1 else 0
                previousPage = pageKey
                val parsed = ChatPageParser.parse(ImportSession.group, rows, LocalDate.now(), range)
                ImportSession.add(parsed.candidates)
                pages++
                ImportSession.status = "已读取 $pages 屏，找到 ${ImportSession.pending.size} 条候选"
                render()
                val reason = when {
                    ImportSession.pending.size >= 500 -> "已达到本次 500 条候选上限，请分时间段继续提取"
                    pages >= 200 || System.currentTimeMillis() - startedAt > 6 * 60_000 -> "已达到本次提取上限，请分时间段继续提取"
                    unchanged >= 3 -> "页面已不再变化，已结束本次提取；请核对是否已覆盖所选时间范围"
                    parsed.latestObservedTime?.isBefore(range.start) == true -> "已翻到开始时间之前，提取结束"
                    else -> null
                }
                if (reason != null) { finish(reason); return }
                if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                    finish("微信列表无法继续向前翻页，已返回本次读取结果；这不代表完整聊天记录")
                } else schedule()
            } finally { lists.forEach { it.recycle() } }
        } finally { root.recycle() }
    }

    private fun isTargetChat(root: AccessibilityNodeInfo): Boolean {
        if (root.packageName?.toString() != WECHAT) return false
        val height = resources.displayMetrics.heightPixels
        val nodes = findNodes(root) { true }
        return try {
            val titleMatches = nodes.any {
                bounds(it).bottom < height / 5 && normalizeGroup(it.text?.toString().orEmpty()) == normalizeGroup(ImportSession.group)
            }
            val inputPresent = nodes.any { it.isEditable || it.contentDescription?.toString() in setOf("切换到键盘", "切换到按住说话") }
            titleMatches && inputPresent
        } finally { nodes.forEach { it.recycle() } }
    }

    private fun texts(root: AccessibilityNodeInfo): List<ChatText> {
        val nodes = findNodes(root) { it.isVisibleToUser && (!it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank()) }
        return try { nodes.map { ChatText(it.text?.toString().orEmpty(), it.contentDescription?.toString().orEmpty(), bounds(it).top, bounds(it).left) }.distinct() }
        finally { nodes.forEach { it.recycle() } }
    }

    private fun findNodes(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (++visited > 4000 || depth > 40) return
            if (predicate(node)) result.add(AccessibilityNodeInfo.obtain(node))
            repeat(node.childCount) { i -> node.getChild(i)?.let { child -> try { visit(child, depth + 1) } finally { child.recycle() } } }
        }
        visit(root, 0); return result
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }
    private fun normalizeGroup(value: String) = value.trim().replace(Regex("[（(]\\d+[）)]$"), "").trim()

    private fun showOverlay() {
        if (overlay != null) { render(); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 9, 16, 9)
            setBackgroundColor(Color.rgb(255, 244, 245))
        }
        label = TextView(this).apply { setTextColor(Color.rgb(118, 39, 19)); textSize = 13f }
        box.addView(label)
        val buttons = LinearLayout(this)
        action = Button(this).apply {
            textSize = 12f
            setOnClickListener {
                if (ImportSession.stage == ImportSession.Stage.CAPTURING) finish("已手动结束提取，请审核本次读取结果") else begin()
            }
        }
        buttons.addView(action, LinearLayout.LayoutParams(0, 45.dp, 1f))
        buttons.addView(Button(this).apply {
            text = "退出"; textSize = 12f
            setOnClickListener {
                stopTick(); ImportSession.pause("已暂停")
                ImportSession.exitRequested = true
                removeOverlay(); returnToApp()
            }
        }, LinearLayout.LayoutParams(65.dp, 45.dp))
        box.addView(buttons)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(box, params); overlay = box; render() }
        catch (_: Exception) { ImportSession.pause("无法显示提取控制条，请返回软件重试") }
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private fun render() {
        label?.text = getString(R.string.chat_capture_overlay_status, ImportSession.group, ImportSession.status)
        action?.text = if (ImportSession.stage == ImportSession.Stage.CAPTURING) "停止并审核（${ImportSession.pending.size}）" else "开始 / 继续提取"
    }
    private fun removeOverlay() {
        overlay?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        overlay = null; label = null; action = null
    }
    private fun finish(message: String) {
        stopTick(); ImportSession.review(message); removeOverlay(); returnToApp()
    }
    private fun returnToApp() {
        startActivity(Intent(this, ChatImportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    companion object { const val WECHAT = "com.tencent.mm" }
}
