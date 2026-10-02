package com.grandrp.reportbot

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ThreadLocalRandom

/**
 * Core bot service: reads the game screen, detects player reports,
 * types the reply command, and auto-accepts admin handshake offers.
 */
class BotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GRBot"
        private const val MAX_NODES = 500

        private val ACCEPT_WORDS = listOf(
            "accept", "confirm", "agree", "yes", "ok", "allow", "sure", "handshake",
            "принять", "подтвердить"
        )
        private val SEND_WORDS = listOf("send", "paper plane", "submit", "reply", ">", "➤")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastScanAt = 0L
    private val repliedAt = HashMap<String, Long>()

    @Volatile private var handshakeBusy = false
    @Volatile private var replying = false

    private val uiThread = Runnable {
        try {
            scanScreen()
        } catch (e: Exception) {
            Log.e(TAG, "scan failed", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        BotState.accessibilityConnected = true
        BotState.botEnabled = BotPrefs.isBotEnabled(this)
        BotState.update("Bot connected — watching the game")
    }

    override fun onDestroy() {
        BotState.accessibilityConnected = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() {
        BotState.update("Bot interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!BotState.botEnabled) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val now = System.currentTimeMillis()
                val interval = BotPrefs.getScanInterval(this).toLong()
                if (now - lastScanAt >= interval) {
                    lastScanAt = now
                    handler.removeCallbacks(uiThread)
                    handler.postDelayed(uiThread, 250)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Screen scanning
    // ------------------------------------------------------------------

    private fun scanScreen() {
        val root = rootInActiveWindow ?: return
        val texts = collectTexts(root)
        if (texts.isEmpty()) return

        // 1) handshake protection first (anti-AFK test)
        if (BotPrefs.isHandshakeEnabled(this) && !handshakeBusy) {
            checkHandshake(texts, root)
        }

        // 2) player reports
        if (replying) return
        handleReports(texts, root)
    }

    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>(64)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            try {
                node.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
                node.contentDescription?.let {
                    val s = it.toString()
                    if (s.isNotBlank() && s.length < 400) out.add(s)
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            } catch (e: Exception) {
                // node might be stale; keep scanning
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    private fun handleReports(texts: List<String>, root: AccessibilityNodeInfo) {
        val strict = BotPrefs.isStrictMarker(this)
        val cooldownMs = BotPrefs.getCooldown(this) * 1000L
        val now = System.currentTimeMillis()

        // clear stale cooldowns
        if (repliedAt.size > 200) repliedAt.clear()

        for (line in texts) {
            if (line.length > 300) continue
            if (strict && !ReportParser.hasReportMarker(line)) continue
            val report = ReportParser.parseLine(line) ?: continue

            val key = report.playerId ?: report.playerName
            val last = repliedAt[key] ?: 0
            if (now - last < cooldownMs) continue

            val rules = RuleEngine.loadRules(this)
            val result = RuleEngine.generateReply(this, report, rules)
            val command = RuleEngine.buildCommand(this, report, result.reply)

            repliedAt[key] = now
            BotState.reportsHandled++
            BotState.update(
                "Replied to ${report.playerName}${report.playerId?.let { "[$it]" } ?: ""}: ${result.intent}",
                command,
            )
            Log.i(TAG, "reply -> $command")

            if (BotPrefs.isAutoSend(this)) {
                replying = true
                sendReply(root, command, result.reply)
                handler.postDelayed({ replying = false }, 1500)
            } else {
                copyToClipboard(command)
            }
            return // one reply per scan, keep it human
        }
    }

    /** Strategy 1: type the command into the game's chat input, then press send. */
    private fun sendReply(root: AccessibilityNodeInfo, command: String, reply: String) {
        val editable = findEditable(root)
        if (editable != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, command)
            val ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                // give the game a moment to register the text, then click send
                handler.postDelayed({
                    val freshRoot = rootInActiveWindow ?: return@postDelayed
                    val clicked = clickSendButton(freshRoot)
                    if (!clicked) {
                        // try pressing the focused editable's IME action
                        editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        BotState.update("Typed reply for ${reply.take(24)}… (press send manually)")
                    } else {
                        BotState.update("Reply sent: ${reply.take(40)}")
                    }
                }, 400)
                return
            }
        }

        // Strategy 2: clipboard assist — copy and surface the command in the overlay
        copyToClipboard(command)
        BotState.update("No chat input found — command copied, paste it in game")
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            try {
                if (n.isEditable) return n
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { queue.add(it) }
                }
            } catch (e: Exception) {
                // stale node, continue
            }
        }
        return null
    }

    private fun clickSendButton(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            try {
                val label = buildString {
                    n.text?.let { append(it.toString().lowercase()); append(' ') }
                    n.contentDescription?.let { append(it.toString().lowercase()) }
                    n.viewIdResourceName?.let { append(it.lowercase()) }
                }
                if (label.isNotBlank()) {
                    val hit = SEND_WORDS.any { label.contains(it) } ||
                        label.contains("btn_send") || label.contains("sendbtn") ||
                        label.contains("edit_send") || label.contains("chat_send")
                    if (hit && (n.isClickable || n.parent?.isClickable == true)) {
                        return performClick(n)
                    }
                }
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { queue.add(it) }
                }
            } catch (e: Exception) {
                // stale node, continue
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // Handshake (anti-AFK test from other admins)
    // ------------------------------------------------------------------

    private fun checkHandshake(texts: List<String>, root: AccessibilityNodeInfo) {
        val keywords = BotPrefs.getHandshakeKeywords(this)
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return

        val lowered = texts.map { it.lowercase() }
        val hit = lowered.any { line ->
            keywords.any { kw -> line.contains(kw) }
        }
        if (!hit) return

        handshakeBusy = true
        val min = BotPrefs.getHandshakeMinDelay(this) * 1000L
        val max = (BotPrefs.getHandshakeMaxDelay(this) * 1000L).coerceAtLeast(min + 500)
        val delay = ThreadLocalRandom.current().nextLong(min, max)

        BotState.update("Handshake detected — confirming in ${"%.1f".format(delay / 1000f)}s")
        Log.i(TAG, "handshake detected, accepting in ${delay}ms")

        handler.postDelayed({
            try {
                val freshRoot = rootInActiveWindow
                val clicked = freshRoot != null && clickAcceptButton(freshRoot)
                if (clicked) {
                    BotState.handshakesAccepted++
                    BotState.update("Handshake accepted — AFK check passed ✓")
                } else {
                    // fallback: try global back / click the handshake node itself
                    val fallback = freshRoot != null && clickNodeWithText(freshRoot, keywords)
                    if (fallback) {
                        BotState.handshakesAccepted++
                        BotState.update("Handshake confirmed — AFK check passed ✓")
                    } else {
                        BotState.update("Handshake seen but no accept button found — check manually!")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "handshake click failed", e)
            } finally {
                handler.postDelayed({ handshakeBusy = false }, 5000)
            }
        }, delay)
    }

    private fun clickAcceptButton(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            try {
                val label = buildString {
                    n.text?.let { append(it.toString().lowercase()); append(' ') }
                    n.contentDescription?.let { append(it.toString().lowercase()) }
                }
                if (label.isNotBlank() && n.isClickable) {
                    if (ACCEPT_WORDS.any { label.trim().startsWith(it) || label.contains(" $it ") || label.trim() == it }) {
                        if (performClick(n)) return true
                    }
                }
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { queue.add(it) }
                }
            } catch (e: Exception) {
                // stale node, continue
            }
        }
        return false
    }

    private fun clickNodeWithText(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            try {
                val label = buildString {
                    n.text?.let { append(it.toString().lowercase()); append(' ') }
                    n.contentDescription?.let { append(it.toString().lowercase()) }
                }
                if (label.isNotBlank() && keywords.any { label.contains(it) }) {
                    var target: AccessibilityNodeInfo? = n
                    var hops = 0
                    while (target != null && !target.isClickable && hops < 4) {
                        target = target.parent
                        hops++
                    }
                    if (target?.isClickable == true) {
                        if (performClick(target)) return true
                    }
                }
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { queue.add(it) }
                }
            } catch (e: Exception) {
                // stale node, continue
            }
        }
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        val parent = node.parent
        if (parent != null && parent.isClickable) {
            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    private fun copyToClipboard(command: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("GrandRP bot reply", command))
        } catch (e: Exception) {
            Log.e(TAG, "clipboard failed", e)
        }
    }
}
