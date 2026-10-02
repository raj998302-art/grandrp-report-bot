package com.grandrp.reportbot

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var botEnabledCheck: CheckBox
    private lateinit var commandInput: EditText
    private lateinit var cooldownInput: EditText
    private lateinit var strictCheck: CheckBox
    private lateinit var autoSendCheck: CheckBox
    private lateinit var adminNameInput: EditText
    private lateinit var adminIdInput: EditText
    private lateinit var otherAdminsInput: EditText
    private lateinit var handshakeCheck: CheckBox
    private lateinit var handshakeKeywordsInput: EditText
    private lateinit var handshakeMinInput: EditText
    private lateinit var handshakeMaxInput: EditText
    private lateinit var rulesStatus: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, stroke: Int, radius: Int = dp(12)): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), stroke)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(28))
        }

        // ---------- header ----------
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply {
            text = "GrandRP"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFE7ECE9.toInt())
        })
        header.addView(TextView(this).apply {
            text = " ReportBot"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF10B981.toInt())
        })
        root.addView(header)
        root.addView(hint("Auto-reply bot for Grand RP Mobile admins"))

        // ---------- status card ----------
        val statusCard = card()
        statusCard.addView(sectionLabel("SETUP STATUS"))
        accessibilityStatus = statusLine(statusCard, "① Accessibility service")
        overlayStatus = statusLine(statusCard, "② Overlay permission")

        val accBtn = button("Enable accessibility service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        statusCard.addView(accBtn)

        val overlayBtn = button("Grant overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        statusCard.addView(overlayBtn)

        val showOverlayBtn = button("Show floating panel", primary = true) {
            startOverlayService()
        }
        statusCard.addView(showOverlayBtn)
        root.addView(statusCard)

        // ---------- bot settings ----------
        val botCard = card()
        botCard.addView(sectionLabel("BOT"))

        botEnabledCheck = checkBox("Bot enabled (auto-reply reports)")
        botCard.addView(botEnabledCheck)

        botCard.addView(inputLabel("Reply command  ({id} {name} {message})"))
        commandInput = input(BotPrefs.getCommandTemplate(this), InputType.TYPE_CLASS_TEXT)
        botCard.addView(commandInput)

        botCard.addView(inputLabel("Cooldown per player (seconds)"))
        cooldownInput = input(BotPrefs.getCooldown(this).toString(), InputType.TYPE_CLASS_NUMBER)
        botCard.addView(cooldownInput)

        strictCheck = checkBox("Only reply to real player reports ([Num. of reports])")
        botCard.addView(strictCheck)

        autoSendCheck = checkBox("Type reply automatically (else copy to clipboard)")
        botCard.addView(autoSendCheck)
        root.addView(botCard)

        // ---------- admin identity (safety) ----------
        val safetyCard = card()
        safetyCard.addView(sectionLabel("ADMIN IDENTITY — SAFETY"))
        safetyCard.addView(hint(
            "The bot ONLY answers player reports (red ID + yellow text). " +
                "Your own lines, other admins' lines and <ADM> replies are NEVER answered — " +
                "replying to an admin can get your account warned."
        ))

        val adminIds = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nameCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nameCol.addView(inputLabel("Your admin name (e.g. ZENUS_CARLOS)"))
        adminNameInput = input(BotPrefs.getAdminName(this), InputType.TYPE_CLASS_TEXT)
        nameCol.addView(adminNameInput)
        adminIds.addView(nameCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val idCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        idCol.addView(inputLabel("Your admin ID (e.g. 372)"))
        adminIdInput = input(BotPrefs.getAdminId(this), InputType.TYPE_CLASS_NUMBER)
        idCol.addView(adminIdInput)
        adminIds.addView(idCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        safetyCard.addView(adminIds)

        safetyCard.addView(inputLabel("Other admins — never reply to (comma separated)"))
        otherAdminsInput = input(
            BotPrefs.getOtherAdminNames(this).joinToString(", "),
            InputType.TYPE_CLASS_TEXT,
        )
        otherAdminsInput.hint = "Kratos_Dominus, Raj_Xoxx, …"
        safetyCard.addView(otherAdminsInput)
        root.addView(safetyCard)

        // ---------- handshake ----------
        val hsCard = card()
        hsCard.addView(sectionLabel("ANTI-AFK HANDSHAKE"))
        hsCard.addView(hint("Other admins test you with handshake offers while you sleep. The bot auto-confirms them after a random human-like delay."))

        handshakeCheck = checkBox("Auto-accept handshake offers")
        hsCard.addView(handshakeCheck)

        hsCard.addView(inputLabel("Detection keywords (comma separated)"))
        handshakeKeywordsInput = input(BotPrefs.getHandshakeKeywords(this), InputType.TYPE_CLASS_TEXT)
        hsCard.addView(handshakeKeywordsInput)

        val delays = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val minCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        minCol.addView(inputLabel("Min delay (s)"))
        handshakeMinInput = input(BotPrefs.getHandshakeMinDelay(this).toString(), InputType.TYPE_CLASS_NUMBER)
        minCol.addView(handshakeMinInput)
        delays.addView(minCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val maxCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        maxCol.addView(inputLabel("Max delay (s)"))
        handshakeMaxInput = input(BotPrefs.getHandshakeMaxDelay(this).toString(), InputType.TYPE_CLASS_NUMBER)
        maxCol.addView(handshakeMaxInput)
        delays.addView(maxCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hsCard.addView(delays)
        root.addView(hsCard)

        // ---------- rules ----------
        val rulesCard = card()
        rulesCard.addView(sectionLabel("RULES"))
        rulesStatus = TextView(this).apply {
            text = "17 default rules active"
            textSize = 12f
            setTextColor(0xFF9AA39C.toInt())
        }
        rulesCard.addView(rulesStatus)

        val importBtn = button("Import rules JSON (from dashboard)") {
            showImportDialog()
        }
        rulesCard.addView(importBtn)

        val resetBtn = button("Reset rules to defaults") {
            BotPrefs.resetRules(this)
            refreshRulesStatus()
            Toast.makeText(this, "Rules reset to defaults", Toast.LENGTH_SHORT).show()
        }
        rulesCard.addView(resetBtn)
        root.addView(rulesCard)

        // ---------- save ----------
        val saveBtn = Button(this).apply {
            text = "Save settings"
            isAllCaps = false
            textSize = 15f
            setTextColor(0xFF0C0F0D.toInt())
            background = roundedBg(0xFF10B981.toInt(), 0xFF10B981.toInt(), dp(12))
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { saveSettings() }
        }
        root.addView(saveBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        // ---------- guide ----------
        val guideCard = card()
        guideCard.addView(sectionLabel("QUICK START"))
        guideCard.addView(hint(
            "1. Fill ADMIN IDENTITY — your name/ID so the bot never replies to itself\n" +
                "2. Enable the accessibility service for 'GrandRP ReportBot'\n" +
                "3. Grant overlay permission and show the floating panel\n" +
                "4. Open Grand RP Mobile — the bot reads the chat\n" +
                "5. PLAYER reports are auto-answered — admins & your own lines are never touched\n" +
                "6. Handshake offers are auto-confirmed (anti-AFK)\n\n" +
                "Tip: edit the command template to match your server's report answer command (e.g. /ans or /re)."
        ))
        root.addView(guideCard)

        scroll.addView(root)
        setContentView(scroll)

        loadSettings()

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ------------------------------------------------------------------ UI helpers

    private fun hint(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF9AA39C.toInt())
            setLineSpacing(0f, 1.25f)
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xF2161B17.toInt(), 0xFF263029.toInt(), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }

    private fun sectionLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(0xFF10B981.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }

    private fun inputLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFFC7CCC8.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
                bottomMargin = dp(4)
            }
        }

    private fun input(value: String, type: Int): EditText =
        EditText(this).apply {
            setText(value)
            inputType = type
            textSize = 13f
            setTextColor(0xFFE7ECE9.toInt())
            setHintTextColor(0xFF6B7280.toInt())
            background = roundedBg(0xF21E241F.toInt(), 0xFF263029.toInt(), dp(10))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

    private fun checkBox(label: String): CheckBox =
        CheckBox(this).apply {
            text = label
            textSize = 13f
            setTextColor(0xFFE7ECE9.toInt())
            buttonTintList = android.content.res.ColorStateList.valueOf(0xFF10B981.toInt())
        }

    private fun button(label: String, primary: Boolean = false, onClick: (Button) -> Unit): Button =
        Button(this, null, 0).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(if (primary) 0xFF0C0F0D.toInt() else 0xFFE7ECE9.toInt())
            background = roundedBg(
                if (primary) 0xFF10B981.toInt() else 0xF21E241F.toInt(),
                if (primary) 0xFF10B981.toInt() else 0xFF263029.toInt(),
                dp(10),
            )
            setPadding(0, dp(10), 0, dp(10))
            minimumHeight = dp(40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            setOnClickListener { onClick(this) }
        }

    private fun statusLine(parent: LinearLayout, label: String): TextView {
        val tv = TextView(this).apply {
            text = "$label  —  checking…"
            textSize = 13f
            setTextColor(0xFF9AA39C.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
        }
        parent.addView(tv)
        return tv
    }

    // ------------------------------------------------------------------ logic

    private fun loadSettings() {
        botEnabledCheck.isChecked = BotPrefs.isBotEnabled(this)
        strictCheck.isChecked = BotPrefs.isStrictMarker(this)
        autoSendCheck.isChecked = BotPrefs.isAutoSend(this)
        handshakeCheck.isChecked = BotPrefs.isHandshakeEnabled(this)
        refreshRulesStatus()
    }

    private fun refreshRulesStatus() {
        val count = RuleEngine.loadRules(this).size
        rulesStatus.text = if (BotPrefs.getRulesJson(this) == null) {
            "$count default rules active"
        } else {
            "$count custom rules loaded (imported)"
        }
    }

    private fun refreshStatus() {
        val accEnabled = isAccessibilityServiceEnabled()
        accessibilityStatus.text = "① Accessibility service  —  ${if (accEnabled) "✓ ON" else "✗ OFF"}"
        accessibilityStatus.setTextColor(if (accEnabled) 0xFF10B981.toInt() else 0xFFF05252.toInt())

        val overlayEnabled = Settings.canDrawOverlays(this)
        overlayStatus.text = "② Overlay permission  —  ${if (overlayEnabled) "✓ GRANTED" else "✗ MISSING"}"
        overlayStatus.setTextColor(if (overlayEnabled) 0xFF10B981.toInt() else 0xFFF05252.toInt())
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val setting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            setting.contains(packageName, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Floating panel started — open the game", Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings() {
        BotPrefs.setBotEnabled(this, botEnabledCheck.isChecked)
        BotPrefs.setCommandTemplate(this, commandInput.text.toString().trim().ifEmpty { BotPrefs.DEFAULT_COMMAND_TEMPLATE })
        BotPrefs.setGreetingPrefix(this, BotPrefs.getGreetingPrefix(this))
        BotPrefs.setCooldown(this, cooldownInput.text.toString().toIntOrNull() ?: 45)
        BotPrefs.setStrictMarker(this, strictCheck.isChecked)
        BotPrefs.setAutoSend(this, autoSendCheck.isChecked)
        BotPrefs.setAdminName(this, adminNameInput.text.toString())
        BotPrefs.setAdminId(this, adminIdInput.text.toString())
        BotPrefs.setOtherAdminNames(this, otherAdminsInput.text.toString())
        BotPrefs.setHandshakeEnabled(this, handshakeCheck.isChecked)
        BotPrefs.setHandshakeKeywords(this, handshakeKeywordsInput.text.toString().trim().ifEmpty { "handshake" })
        BotPrefs.setHandshakeMinDelay(this, handshakeMinInput.text.toString().toIntOrNull() ?: 2)
        BotPrefs.setHandshakeMaxDelay(this, handshakeMaxInput.text.toString().toIntOrNull() ?: 6)

        BotState.botEnabled = botEnabledCheck.isChecked
        BotState.update(if (botEnabledCheck.isChecked) "Bot enabled — watching the game" else "Bot paused")
        Toast.makeText(this, "Settings saved ✓", Toast.LENGTH_SHORT).show()
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "Paste rules JSON here (from web dashboard → Rules → Export JSON)"
            setHintTextColor(0xFF6B7280.toInt())
            setTextColor(0xFFE7ECE9.toInt())
            textSize = 12f
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBg(0xF21E241F.toInt(), 0xFF263029.toInt(), dp(10))
        }

        AlertDialog.Builder(this)
            .setTitle("Import rules JSON")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val json = input.text.toString().trim()
                if (json.isEmpty()) {
                    Toast.makeText(this, "Nothing to import", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    val parsed = org.json.JSONObject(json)
                    val rules = parsed.optJSONArray("rules")
                    if (rules == null || rules.length() == 0) {
                        Toast.makeText(this, "No rules found in JSON", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    BotPrefs.setRulesJson(this, json)

                    // also import admin identity/safety settings exported from the dashboard
                    val settings = parsed.optJSONObject("settings")
                    if (settings != null) {
                        val adminName = settings.optString("adminName", "")
                        if (adminName.isNotEmpty()) BotPrefs.setAdminName(this, adminName)
                        val adminId = settings.optString("adminId", "")
                        if (adminId.isNotEmpty()) BotPrefs.setAdminId(this, adminId)
                        val otherAdmins = settings.optJSONArray("adminNames")
                        if (otherAdmins != null && otherAdmins.length() > 0) {
                            BotPrefs.setOtherAdminNames(this, (0 until otherAdmins.length()).map { otherAdmins.optString(it) }.joinToString(","))
                        }
                        adminNameInput.setText(BotPrefs.getAdminName(this))
                        adminIdInput.setText(BotPrefs.getAdminId(this))
                        otherAdminsInput.setText(BotPrefs.getOtherAdminNames(this).joinToString(", "))
                    }

                    refreshRulesStatus()
                    Toast.makeText(this, "Imported ${rules.length()} rules ✓", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid JSON: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
