package com.grandrp.reportbot

import android.content.Context

/**
 * Holds all bot settings in SharedPreferences, including the imported rules JSON.
 */
object BotPrefs {

    private const val PREFS = "grandrp_bot_prefs"

    private const val KEY_BOT_ENABLED = "botEnabled"
    private const val KEY_COMMAND_TEMPLATE = "commandTemplate"
    private const val KEY_GREETING_PREFIX = "greetingPrefix"
    private const val KEY_COOLDOWN = "replyCooldown"
    private const val KEY_STRICT_MARKER = "strictReportMarker"
    private const val KEY_AUTO_SEND = "autoSend"
    private const val KEY_HANDSHAKE_ENABLED = "handshakeEnabled"
    private const val KEY_HANDSHAKE_KEYWORDS = "handshakeKeywords"
    private const val KEY_HANDSHAKE_MIN = "handshakeMinDelay"
    private const val KEY_HANDSHAKE_MAX = "handshakeMaxDelay"
    private const val KEY_RULES_JSON = "rulesJson"
    private const val KEY_SCAN_INTERVAL = "scanInterval"

    const val DEFAULT_COMMAND_TEMPLATE = "/ans {id} {message}"
    const val DEFAULT_GREETING = "Greetings, "

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBotEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_BOT_ENABLED, true)
    fun setBotEnabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_BOT_ENABLED, value).apply()

    fun getCommandTemplate(ctx: Context): String =
        prefs(ctx).getString(KEY_COMMAND_TEMPLATE, DEFAULT_COMMAND_TEMPLATE) ?: DEFAULT_COMMAND_TEMPLATE

    fun setCommandTemplate(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_COMMAND_TEMPLATE, v).apply()

    fun getGreetingPrefix(ctx: Context): String =
        prefs(ctx).getString(KEY_GREETING_PREFIX, DEFAULT_GREETING) ?: DEFAULT_GREETING

    fun setGreetingPrefix(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_GREETING_PREFIX, v).apply()

    fun getCooldown(ctx: Context): Int = prefs(ctx).getInt(KEY_COOLDOWN, 45)
    fun setCooldown(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_COOLDOWN, v.coerceIn(5, 3600)).apply()

    /** When true, only lines containing "[Num. of reports" are treated as reports (avoids replying to normal chat) */
    fun isStrictMarker(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_STRICT_MARKER, true)
    fun setStrictMarker(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_STRICT_MARKER, v).apply()

    /** When true the bot types into the game input by itself; otherwise it copies the command for you */
    fun isAutoSend(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO_SEND, true)
    fun setAutoSend(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_AUTO_SEND, v).apply()

    fun isHandshakeEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HANDSHAKE_ENABLED, true)
    fun setHandshakeEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_HANDSHAKE_ENABLED, v).apply()

    fun getHandshakeKeywords(ctx: Context): String =
        prefs(ctx).getString(KEY_HANDSHAKE_KEYWORDS, "handshake,hand shake,hs offer,handshake offer") ?: "handshake"

    fun setHandshakeKeywords(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_HANDSHAKE_KEYWORDS, v).apply()

    fun getHandshakeMinDelay(ctx: Context): Int = prefs(ctx).getInt(KEY_HANDSHAKE_MIN, 2)
    fun setHandshakeMinDelay(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_HANDSHAKE_MIN, v.coerceIn(0, 60)).apply()

    fun getHandshakeMaxDelay(ctx: Context): Int = prefs(ctx).getInt(KEY_HANDSHAKE_MAX, 6)
    fun setHandshakeMaxDelay(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_HANDSHAKE_MAX, v.coerceIn(1, 120)).apply()

    /** Minimum ms between two screen scans (performance guard) */
    fun getScanInterval(ctx: Context): Int = prefs(ctx).getInt(KEY_SCAN_INTERVAL, 900)
    fun setScanInterval(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_SCAN_INTERVAL, v.coerceIn(300, 5000)).apply()

    fun getRulesJson(ctx: Context): String? = prefs(ctx).getString(KEY_RULES_JSON, null)
    fun setRulesJson(ctx: Context, json: String) = prefs(ctx).edit().putString(KEY_RULES_JSON, json).apply()

    fun resetRules(ctx: Context) = prefs(ctx).edit().remove(KEY_RULES_JSON).apply()
}
