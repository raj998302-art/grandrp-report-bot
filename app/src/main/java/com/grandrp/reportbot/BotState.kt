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
    @Volatile var lastEvent: String = "Bot idle"
    @Volatile var lastCommand: String = ""

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
        lastEvent = "Counters reset"
        lastCommand = ""
        notifyChanged()
    }
}
