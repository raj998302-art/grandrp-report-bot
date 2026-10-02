package com.grandrp.reportbot

/**
 * Parses Grand RP Mobile chat lines into structured reports.
 *
 * Player report line:
 *   19:53 Ryan_Itachi[440]: help [Num. of reports: 1]
 *   19:50 Muhammad_Ghufran: change my skin
 */
object ReportParser {

    data class ParsedReport(
        val playerName: String,
        val playerId: String?,
        val message: String,
        val raw: String,
    )

    private val REPORT_RE = Regex(
        "^(?:\\d{1,2}:\\d{2}\\s*)?([A-Za-z][A-Za-z0-9_]{1,23})(?:\\[(\\d{1,5})\\])?:\\s*(.+?)(?:\\s*\\[Num\\.?\\s*of\\s*reports:?\\s*\\d+\\])?\\s*$"
    )

    private val SKIP_PREFIXES = listOf("<ADM>", "[A]", "((", "Welcome to", "Connecting", "Logging in")

    fun parseLine(line: String): ParsedReport? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        for (p in SKIP_PREFIXES) {
            if (raw.startsWith(p)) return null
        }
        val m = REPORT_RE.find(raw) ?: return null
        val message = m.groupValues[3].trim()
        if (message.isEmpty()) return null
        return ParsedReport(
            playerName = m.groupValues[1],
            playerId = m.groupValues[2].ifEmpty { null },
            message = message,
            raw = raw,
        )
    }

    fun hasReportMarker(line: String): Boolean = line.contains("[Num. of reports", ignoreCase = true)
}
