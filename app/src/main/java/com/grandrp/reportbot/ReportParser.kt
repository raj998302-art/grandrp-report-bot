package com.grandrp.reportbot

/**
 * Parses Grand RP Mobile chat lines into structured reports.
 *
 * PLAYER report line (red ID + yellow report text):
 *   19:53 Ryan_Itachi[440]: help [Num. of reports: 1]
 *
 * IMPORTANT: the game renders this line with MULTIPLE colors (yellow name,
 * red ID, white message, red counter), so the Android accessibility tree
 * often delivers it as separate fragments, e.g.:
 *   "Ryan_Itachi"  |  "[440]"  |  ": help"  |  "[Num. of reports: 1]"
 * [analyzeTexts] reassembles such fragments — the `[Num. of reports]` marker
 * is the player-report signature that triggers the backward reassembly.
 *
 * Admin lines — NEVER answered, their IDs are NEVER used (that would earn a warning):
 *   19:53 <ADM> Kratos_Dominus[348] has replied to Ryan_Itachi[440]: Greetings, Help is coming
 *   23:02 <ADM> (5) Romesa_Sen[163]: tambov is sleeping
 */
object ReportParser {

    data class ParsedReport(
        val playerName: String,
        val playerId: String?,
        val message: String,
        val raw: String,
    )

    /** Why a line was skipped — surfaced in the overlay so the admin can see the bot behaving safely. */
    enum class SkipReason { ADMIN, SELF, SYSTEM, COMMAND, NO_MARKER, NO_ID, UNPARSED }

    sealed class LineResult {
        data class Report(val report: ParsedReport) : LineResult()
        data class Skipped(val line: String, val reason: SkipReason, val detail: String) : LineResult()
    }

    data class ParseOptions(
        val adminName: String = "",
        val adminId: String = "",
        val otherAdminNames: List<String> = emptyList(),
        val requireMarker: Boolean = true,
    )

    /** Player-report signature: `[Num. of reports: N]` (the red ID + yellow text line) */
    private val MARKER_RE = Regex("\\[\\s*Num\\.?\\s*of\\s*reports", RegexOption.IGNORE_CASE)

    /** Marker tail at the end of a line, e.g. ` [Num. of reports: 2]` */
    private val MARKER_TAIL_RE = Regex("\\s*\\[\\s*Num\\.?\\s*of\\s*reports:?\\s*\\d+\\s*]\\s*$", RegexOption.IGNORE_CASE)

    /**
     * Report structure when the marker is present (strict mode). The marker itself
     * proves this is a player report, so spacing between tokens is forgiven —
     * that is what makes fragment reassembly work. The player ID is REQUIRED:
     * the reply command may only ever contain the reporting USER's ID.
     */
    private val RELAXED_RE = Regex(
        "^([A-Za-z][A-Za-z0-9_]{1,23})\\s*\\[\\s*(\\d{1,5})\\s*]\\s*:?\\s*(.+)$"
    )

    /** Report structure without a marker (loose mode): `Name[ID]: message` — ID required. */
    private val STRICT_RE = Regex(
        "^([A-Za-z][A-Za-z0-9_]{1,23})\\s*\\[\\s*(\\d{1,5})\\s*]\\s*:\\s*(.+)$"
    )

    /** Reports embedded inside one huge accessibility text blob (no newlines). */
    private val EMBEDDED_RE = Regex(
        "([A-Za-z][A-Za-z0-9_]{1,23})\\[(\\d{1,5})]:\\s*([^\\[]{1,120}?)\\s*\\[\\s*Num\\.?\\s*of\\s*reports:?\\s*\\d+",
        RegexOption.IGNORE_CASE,
    )

    /** Admin badge anywhere in the line: <ADM>, [ADM], (ADM) */
    private val ADMIN_BADGE_RE = Regex("(?:<\\s*adm\\s*>|\\[\\s*adm\\s*]|\\(\\s*adm\\s*\\))", RegexOption.IGNORE_CASE)

    /** Admin reply structure: `Kratos_Dominus[348] has replied to Ryan_Itachi[440]: …` */
    private val ADMIN_REPLY_RE =
        Regex("^[A-Za-z][A-Za-z0-9_]{1,23}\\[\\d{1,5}]\\s+has\\s+replied\\s+to\\b", RegexOption.IGNORE_CASE)

    private val TIMESTAMP_RE = Regex("^\\d{1,2}:\\d{2}\\s*")
    private val TIMESTAMP_ONLY_RE = Regex("^\\d{1,2}:\\d{2}$")

    /** `Name:` prefix without an [ID] — a marker line that can never be replied safely. */
    private val NO_ID_SHAPE_RE = Regex("^[A-Za-z][A-Za-z0-9_]{1,23}\\s*:")

    private val SYSTEM_PREFIXES = listOf("[A]", "((", "welcome to", "connecting", "logging in", "===", "***")

    /** A single accessibility node longer than this is treated as a whole-chat blob. */
    private const val MEGA_TEXT = 300

    /** How many preceding fragments may be joined onto a marker fragment. */
    private const val REJOIN_WINDOW = 6

    fun hasReportMarker(line: String): Boolean = MARKER_RE.containsMatchIn(line)

    /**
     * Classify one chat line. Only genuine PLAYER reports pass — admin replies,
     * the owner's own messages, system lines and command echoes are always skipped,
     * so the bot can never reply to an admin or use an admin ID.
     */
    fun analyzeLine(line: String, opts: ParseOptions): LineResult {
        val raw = line.trim()
        if (raw.isEmpty()) return LineResult.Skipped(raw, SkipReason.UNPARSED, "empty")

        // typed command echo (e.g. the /ans line the bot itself sent)
        if (raw.startsWith("/")) return LineResult.Skipped(raw, SkipReason.COMMAND, "command echo")

        val body = TIMESTAMP_RE.replace(raw, "")

        // admin badge or admin-reply structure — hard block
        if (ADMIN_BADGE_RE.containsMatchIn(body)) {
            return LineResult.Skipped(raw, SkipReason.ADMIN, "admin message (<ADM>)")
        }
        if (ADMIN_REPLY_RE.containsMatchIn(body)) {
            return LineResult.Skipped(raw, SkipReason.ADMIN, "admin reply line")
        }

        val lower = body.lowercase()
        for (p in SYSTEM_PREFIXES) {
            if (lower.startsWith(p)) return LineResult.Skipped(raw, SkipReason.SYSTEM, "system message")
        }

        val hasMarker = hasReportMarker(body)
        if (opts.requireMarker && !hasMarker) {
            return LineResult.Skipped(raw, SkipReason.NO_MARKER, "no report marker — normal chat")
        }

        // detach the trailing [Num. of reports: N] marker before parsing the body
        val bodyNoMarker = if (hasMarker) {
            val stripped = MARKER_TAIL_RE.replace(body, "")
            if (stripped.isBlank()) body else stripped
        } else body

        val m = (if (hasMarker) RELAXED_RE else STRICT_RE).find(bodyNoMarker)
            ?: run {
                // marker line with `Name: message` but no [ID] — can't ever be replied safely
                if (hasMarker && NO_ID_SHAPE_RE.containsMatchIn(bodyNoMarker)) {
                    return LineResult.Skipped(raw, SkipReason.NO_ID, "player ID missing — cannot reply safely")
                }
                return LineResult.Skipped(
                    raw,
                    SkipReason.UNPARSED,
                    if (hasMarker) "report fragments — needs reassembly" else "not a report line",
                )
            }

        val message = m.groupValues[3].trim()
        if (message.isEmpty()) return LineResult.Skipped(raw, SkipReason.UNPARSED, "empty message")

        val playerName = m.groupValues[1]
        val playerId = m.groupValues[2].ifEmpty { null }

        // never reply without the reporting player's ID — the command must
        // only ever contain a USER id, never a name guess
        if (playerId == null) {
            return LineResult.Skipped(raw, SkipReason.NO_ID, "player ID missing — cannot reply safely")
        }

        // own admin account — never reply to yourself, never use your ID
        val adminName = opts.adminName.trim().lowercase()
        val adminId = opts.adminId.trim()
        if (adminName.isNotEmpty() && playerName.lowercase() == adminName) {
            return LineResult.Skipped(raw, SkipReason.SELF, "own admin name")
        }
        if (adminId.isNotEmpty() && playerId == adminId) {
            return LineResult.Skipped(raw, SkipReason.SELF, "own admin ID")
        }

        // other known admins — never reply to them
        if (opts.otherAdminNames.map { it.trim().lowercase() }.contains(playerName.lowercase())) {
            return LineResult.Skipped(raw, SkipReason.ADMIN, "known admin $playerName")
        }

        return LineResult.Report(ParsedReport(playerName, playerId, message, raw))
    }

    /**
     * Analyze one full accessibility scan: every text node the bot can see, in
     * document order. Handles:
     *  - multi-line text nodes (split on newline)
     *  - one huge chat blob (embedded report search)
     *  - fragmented report lines (multi-colored spans) via marker-triggered reassembly
     */
    fun analyzeTexts(texts: List<String>, opts: ParseOptions): List<LineResult> {
        val out = ArrayList<LineResult>()
        val lines = ArrayList<String>(texts.size * 2)

        for (t in texts) {
            val trimmed = t.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.length > MEGA_TEXT) {
                val parts = trimmed.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    lines.addAll(parts)
                } else {
                    lines.add(trimmed)
                }
            } else {
                lines.add(trimmed)
            }
        }

        val seenRaw = HashSet<String>()
        for (i in lines.indices) {
            val line = lines[i]

            // huge single line (whole chat blob in one node) — extract embedded reports
            if (line.length > 120 && hasReportMarker(line)) {
                var found = false
                for (r in findEmbeddedReports(line, opts)) {
                    if (r is LineResult.Report && seenRaw.add(r.report.raw)) {
                        out.add(r)
                        found = true
                    }
                }
                // classify the blob itself for skip transparency (e.g. an admin blob)
                val blobResult = analyzeLine(line, opts)
                if (blobResult is LineResult.Skipped) {
                    if (seenRaw.add("skip|" + blobResult.line)) out.add(blobResult)
                    continue // blob handled — no fragment logic needed
                }
                if (found) continue // one long report line — already extracted
                // else fall through to normal handling
            }

            val direct = analyzeLine(line, opts)
            if (direct is LineResult.Report) {
                if (seenRaw.add(direct.report.raw)) out.add(direct)
                continue
            }

            // fragmented report line: the marker fragment triggers a backward reassembly
            if (hasReportMarker(line)) {
                val joined = reassemble(lines, i)
                if (joined != null) {
                    val combined = analyzeLine(joined, opts)
                    if (combined is LineResult.Report) {
                        if (seenRaw.add(combined.report.raw)) out.add(combined)
                        continue
                    }
                    if (combined is LineResult.Skipped &&
                        (combined.reason == SkipReason.ADMIN || combined.reason == SkipReason.SELF)
                    ) {
                        if (seenRaw.add("skip|" + combined.line)) out.add(combined)
                        continue
                    }
                }
            }

            if (direct is LineResult.Skipped && seenRaw.add("skip|" + direct.line)) {
                out.add(direct)
            }
        }
        return out
    }

    /**
     * Join the marker fragment with the preceding fragments of the same visual
     * line (the game splits colored report lines into several accessibility
     * nodes). Stops at the previous report's marker or at an admin line —
     * admin fragments are never part of a player report, they just sit above
     * it in the chat.
     */
    private fun reassemble(lines: List<String>, markerIdx: Int): String? {
        val parts = ArrayList<String>(REJOIN_WINDOW + 1)
        parts.add(lines[markerIdx])
        var j = markerIdx - 1
        var steps = 0
        while (j >= 0 && steps < REJOIN_WINDOW) {
            val frag = lines[j].trim()
            if (frag.isNotEmpty() && !TIMESTAMP_ONLY_RE.matches(frag)) {
                if (hasReportMarker(frag)) break // previous report line
                if (ADMIN_BADGE_RE.containsMatchIn(frag) || ADMIN_REPLY_RE.containsMatchIn(frag)) break // admin line — stop
                parts.add(0, frag)
            }
            j--
            steps++
        }
        if (parts.size < 2) return null
        return parts.joinToString(" ")
    }

    /** Reports hidden inside one huge chat blob (no newlines). */
    private fun findEmbeddedReports(text: String, opts: ParseOptions): List<LineResult> {
        val out = ArrayList<LineResult>()
        val adminName = opts.adminName.trim().lowercase()
        val adminId = opts.adminId.trim()
        val admins = opts.otherAdminNames.map { it.trim().lowercase() }
        for (m in EMBEDDED_RE.findAll(text)) {
            val name = m.groupValues[1]
            val id = m.groupValues[2]
            val msg = m.groupValues[3].trim()
            if (msg.isEmpty()) continue
            // same identity safety filters as single lines
            if (adminName.isNotEmpty() && name.lowercase() == adminName) continue
            if (adminId.isNotEmpty() && id == adminId) continue
            if (admins.contains(name.lowercase())) continue
            out.add(LineResult.Report(ParsedReport(name, id, msg, "${name}[${id}]: ${msg}")))
        }
        return out
    }

    /** Legacy helper — plain parse without admin filters (used only for pure text parsing). */
    fun parseLine(line: String): ParsedReport? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        val body = MARKER_TAIL_RE.replace(TIMESTAMP_RE.replace(raw, ""), "")
        val m = STRICT_RE.find(body) ?: return null
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
