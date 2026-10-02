package com.grandrp.reportbot

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating control panel: shows live bot status and quick actions on top of the game.
 */
class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "grandrp_bot_overlay"
        private const val NOTIFICATION_ID = 1101
    }

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private lateinit var statusText: TextView
    private lateinit var counterText: TextView
    private lateinit var eventText: TextView
    private lateinit var commandText: TextView

    private val listener = {
        refreshUi()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        addOverlay()
        BotState.overlayRunning = true
        BotState.addListener(listener)
        refreshUi()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        BotState.removeListener(listener)
        BotState.overlayRunning = false
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bot control overlay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Keeps the GrandRP ReportBot control panel running" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun startInForeground() {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        builder
            .setContentTitle("GrandRP ReportBot")
            .setContentText("Bot is watching reports · tap to open settings")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pi)
            .setOngoing(true)

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val dp = { v: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), Resources.getSystem().displayMetrics).toInt()
        }

        fun roundedBg(color: Int, stroke: Int, radius: Int = dp(14)): GradientDrawable =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius.toFloat()
                setStroke(dp(1), stroke)
            }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBg(0xF2161B17.toInt(), 0xFF263029.toInt(), dp(16))
        }

        // header (drag handle + status)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        statusText = TextView(this).apply {
            text = "● BOT RUNNING"
            setTextColor(0xFF10B981.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF9AA39C.toInt())
            textSize = 15f
            setPadding(dp(8), 0, dp(2), 0)
            setOnClickListener { stopSelf() }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(header)

        counterText = TextView(this).apply {
            text = "0 reports · 0 handshakes"
            setTextColor(0xFFE7ECE9.toInt())
            textSize = 12f
        }
        root.addView(counterText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        eventText = TextView(this).apply {
            text = "Bot idle"
            setTextColor(0xFF9AA39C.toInt())
            textSize = 11f
            maxLines = 2
        }
        root.addView(eventText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })

        commandText = TextView(this).apply {
            text = ""
            setTextColor(0xFF10B981.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            maxLines = 3
            movementMethod = ScrollingMovementMethod()
            background = roundedBg(0xF20C0F0D.toInt(), 0xFF263029.toInt(), dp(10))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            visibility = View.GONE
        }
        root.addView(commandText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        // action buttons
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            topMargin = dp(8)
        }
        fun btn(label: String, primary: Boolean, onClick: (Button) -> Unit): Button {
            val b = Button(this, null, 0).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                setTextColor(if (primary) 0xFF0C0F0D.toInt() else 0xFFE7ECE9.toInt())
                background = roundedBg(
                    if (primary) 0xFF10B981.toInt() else 0xFF1E241F.toInt(),
                    if (primary) 0xFF10B981.toInt() else 0xFF263029.toInt(),
                    dp(10),
                )
                setPadding(dp(10), 0, dp(10), 0)
                minimumHeight = dp(34)
                setOnClickListener { onClick(this) }
            }
            return b
        }

        val pauseBtn = btn(if (BotState.botEnabled) "Pause" else "Resume", false) { b ->
            val newVal = !BotState.botEnabled
            BotState.botEnabled = newVal
            BotPrefs.setBotEnabled(this@OverlayService, newVal)
            b.text = if (newVal) "Pause" else "Resume"
            refreshUi()
        }
        buttons.addView(pauseBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })

        val copyBtn = btn("Copy last", true) { _ ->
            if (BotState.lastCommand.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("bot command", BotState.lastCommand))
            }
        }
        buttons.addView(copyBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        overlayView = root

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }

        // drag on the whole panel except buttons
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = e.rawX
                    initialTouchY = e.rawY
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - initialTouchX
                    val dy = e.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) moved = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(root, params)
                    } catch (_: Exception) {
                    }
                    moved
                }
                MotionEvent.ACTION_UP -> moved
                else -> false
            }
        }

        try {
            windowManager?.addView(root, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun refreshUi() {
        val running = BotState.botEnabled
        statusText.text = if (running) "● BOT RUNNING" else "● BOT PAUSED"
        statusText.setTextColor(if (running) 0xFF10B981.toInt() else Color.parseColor("#6B7280"))
        counterText.text = "${BotState.reportsHandled} reports · ${BotState.handshakesAccepted} handshakes"
        eventText.text = BotState.lastEvent
        if (BotState.lastCommand.isNotEmpty()) {
            commandText.visibility = View.VISIBLE
            commandText.text = BotState.lastCommand
        }
    }
}
