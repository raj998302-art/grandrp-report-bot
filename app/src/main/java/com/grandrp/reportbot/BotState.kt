package com.grandrp.reportbot

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared in-memory state between the accessibility service, the overlay and the activity.
 */
object BotState {

    @Volatile var accessibilityConnected: Boolean = false
    @Volatile var overlayRunning: Boolean = false
    @Volatile var botEnabled: Boolean = true
    @Volatile var reportsHandled: Int = 0
    @Volatile var handshakesAccepted: Int = 0
    @Volatile var adminLinesSkipped: Int = 0
    @Volatile var lastEvent: String = "Bot idle"
    @Volatile var lastCommand: String = ""

    /** Ring buffer of the last raw screen texts — "what the bot sees".
     *  Copied to clipboard by the overlay Debug button so the admin can paste
     *  it into the dashboard simulator to verify detection. */
    private val seenLinesBuf = ArrayDeque<String>(48)

    fun recordSeenLines(texts: List<String>) {
        synchronized(seenLinesBuf) {
            for (t in texts) {
                val s = t.trim()
                if (s.isEmpty()) continue
                seenLinesBuf.addLast(s)
                if (seenLinesBuf.size > 40) seenLinesBuf.removeFirst()
            }
        }
    }

    fun dumpSeenLines(): String = synchronized(seenLinesBuf) { seenLinesBuf.joinToString("\n") }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(event: String? = null, command: String? = null) {
        event?.let { lastEvent = it }
        command?.let { lastCommand = it }
        notifyChanged()
    }

    fun notifyChanged() {
        for (l in listeners) {
            try {
                l()
            } catch (_: Exception) {
            }
        }
    }

    fun resetCounters() {
        reportsHandled = 0
        handshakesAccepted = 0
        adminLinesSkipped = 0
        lastEvent = "Counters reset"
        lastCommand = ""
        synchronized(seenLinesBuf) { seenLinesBuf.clear() }
        notifyChanged()
    }
}
