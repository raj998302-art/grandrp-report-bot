package com.grandrp.reportbot

/**
 * Parses Grand RP Mobile chat lines into structured reports.
 *
 * PLAYER report line (red ID + yellow report text):
 *   19:53 Ryan_Itachi[440]: help [Num. of reports: 1]
 *
 * Admin lines — NEVER answered, their IDs are NEVER used (that would earn a warning):
 *   19:53 <ADM> Kratos_Dominus[348] has replied to Ryan_Itachi[440]: Greetings, Help is coming
 *   19:54 <ADM> ZENUS_CARLOS[372] has replied to Flexi_Jen[385]: ...
 */
object ReportParser {

    data class ParsedReport(
        val playerName: String,
        val playerId: String?,
        val message: String,
        val raw: String,
    )

    /** Why a line was skipped — surfaced in the overlay so the admin can see the bot behaving safely. */
    enum class SkipReason { ADMIN, SELF, SYSTEM, COMMAND, NO_MARKER, UNPARSED }

    sealed class LineResult {
        data class Report(val report: ParsedReport) : LineResult()
        data class Skipped(val reason: SkipReason, val detail: String) : LineResult()
    }

    data class ParseOptions(
        val adminName: String = "",
        val adminId: String = "",
        val otherAdminNames: List<String> = emptyList(),
        val requireMarker: Boolean = true,
    )

    private val REPORT_RE = Regex(
        "^(?:\\d{1,2}:\\d{2}\\s*)?([A-Za-z][A-Za-z0-9_]{1,23})(?:\\[(\\d{1,5})\\])?:\\s*(.+?)(?:\\s*\\[Num\\.?\\s*of\\s*reports:?\\s*\\d+\\])?\\s*$"
    )

    /** Player-report signature: `[Num. of reports: N]` (the red ID + yellow text line) */
    private val MARKER_RE = Regex("\\[\\s*Num\\.?\\s*of\\s*reports", RegexOption.IGNORE_CASE)

    /** Admin badge anywhere in the line: <ADM>, [ADM], (ADM) */
    private val ADMIN_BADGE_RE = Regex("(?:<\\s*adm\\s*>|\\[\\s*adm\\s*]|\\(\\s*adm\\s*\\))", RegexOption.IGNORE_CASE)

    /** Admin reply structure: `Kratos_Dominus[348] has replied to Ryan_Itachi[440]: …` */
    private val ADMIN_REPLY_RE =
        Regex("^[A-Za-z][A-Za-z0-9_]{1,23}\\[\\d{1,5}]\\s+has\\s+replied\\s+to\\b", RegexOption.IGNORE_CASE)

    private val TIMESTAMP_RE = Regex("^\\d{1,2}:\\d{2}\\s*")

    private val SYSTEM_PREFIXES = listOf("[A]", "((", "welcome to", "connecting", "logging in", "===", "***")

    fun hasReportMarker(line: String): Boolean = MARKER_RE.containsMatchIn(line)

    /**
     * Classify one chat line. Only genuine PLAYER reports pass — admin replies,
     * the owner's own messages, system lines and command echoes are always skipped,
     * so the bot can never reply to an admin or use an admin ID.
     */
    fun analyzeLine(line: String, opts: ParseOptions): LineResult {
        val raw = line.trim()
        if (raw.isEmpty()) return LineResult.Skipped(SkipReason.UNPARSED, "empty")

        // typed command echo (e.g. the /ans line the bot itself sent)
        if (raw.startsWith("/")) return LineResult.Skipped(SkipReason.COMMAND, "command echo")

        val body = TIMESTAMP_RE.replace(raw, "")

        // admin badge or admin-reply structure — hard block
        if (ADMIN_BADGE_RE.containsMatchIn(body)) {
            return LineResult.Skipped(SkipReason.ADMIN, "admin message (<ADM>)")
        }
        if (ADMIN_REPLY_RE.containsMatchIn(body)) {
            return LineResult.Skipped(SkipReason.ADMIN, "admin reply line")
        }

        val lower = body.lowercase()
        for (p in SYSTEM_PREFIXES) {
            if (lower.startsWith(p)) return LineResult.Skipped(SkipReason.SYSTEM, "system message")
        }

        val m = REPORT_RE.find(body) ?: return LineResult.Skipped(SkipReason.UNPARSED, "not a report line")
        val message = m.groupValues[3].trim()
        if (message.isEmpty()) return LineResult.Skipped(SkipReason.UNPARSED, "empty message")

        val playerName = m.groupValues[1]
        val playerId = m.groupValues[2].ifEmpty { null }

        // own admin account — never reply to yourself, never use your ID
        val adminName = opts.adminName.trim().lowercase()
        val adminId = opts.adminId.trim()
        if (adminName.isNotEmpty() && playerName.lowercase() == adminName) {
            return LineResult.Skipped(SkipReason.SELF, "own admin name")
        }
        if (adminId.isNotEmpty() && playerId == adminId) {
            return LineResult.Skipped(SkipReason.SELF, "own admin ID")
        }

        // other known admins — never reply to them
        if (opts.otherAdminNames.map { it.trim().lowercase() }.contains(playerName.lowercase())) {
            return LineResult.Skipped(SkipReason.ADMIN, "known admin $playerName")
        }

        // strict mode: only real player reports carry the marker
        if (opts.requireMarker && !hasReportMarker(raw)) {
            return LineResult.Skipped(SkipReason.NO_MARKER, "no report marker — normal chat")
        }

        return LineResult.Report(ParsedReport(playerName, playerId, message, raw))
    }

    /** Legacy helper — plain parse without admin filters (used only for pure text parsing). */
    fun parseLine(line: String): ParsedReport? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        val m = REPORT_RE.find(TIMESTAMP_RE.replace(raw, "")) ?: return null
        val message = m.groupValues[3].trim()
        if (message.isEmpty()) return null
        return ParsedReport(
            playerName = m.groupValues[1],
            playerId = m.groupValues[2].ifEmpty { null },
            message = message,
            raw = raw,
        )
    }
}
