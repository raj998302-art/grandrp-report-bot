package com.grandrp.reportbot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ThreadLocalRandom

/**
 * Keyword -> reply rule engine. Reads the same JSON format exported by the web dashboard.
 */
object RuleEngine {

    data class BotRule(
        val name: String,
        val category: String,
        val keywords: List<String>,
        val reply: String,
        val priority: Int,
        val enabled: Boolean,
    )

    data class ReplyResult(
        val reply: String,
        val intent: String,
        val ruleName: String?,
    )

    private val NAME_CHECK_KEYWORDS = listOf(
        "rp or nrp", "is this rp", "valid name", "name rp", "name nrp", "rp name", "nrp name",
        "is this name", "name valid", "is my name", "change name", "rp or non rp", "nrp?",
        "rp?", "non rp name", "is this nickname", "nickname", "nick rp", "name check"
    )

    private val DEFAULT_RULES_JSON = """
    {
      "version": 1,
      "rules": [
        {"name":"RP / NRP Name Check","category":"namecheck","keywords":["rp or nrp","is this rp","name rp","name nrp","rp name","nrp name","valid name","is this name","name valid","is my name","non rp name","nickname","rp or non rp","name check"],"reply":"{namecheck}","priority":10,"enabled":true},
        {"name":"Car Stuck / Flipped / In Water","category":"vehicle","keywords":["car stuck","car flip","flipped","flip","upside down","car in water","dropped in water","drowned","car drowned","vehicle stuck","stuck in car","my car is stuck","car fell","car underwater","car sinking","boat stuck","bike flip","bike stuck"],"reply":"Use towtruck option.","priority":20,"enabled":true},
        {"name":"Fuel / Petrol Empty","category":"service","keywords":["petrol","gas","fuel","out of fuel","fuel empty","gas empty","petrol empty","ran out of fuel","no petrol","no fuel","no gas","fuel finish","tank empty"],"reply":"We don't provide that such services.","priority":30,"enabled":true},
        {"name":"Skin Change Request","category":"service","keywords":["change my skin","skin change","change skin","change my clothes","skin request"],"reply":"We don't provide that such services.","priority":35,"enabled":true},
        {"name":"Money Lost / Scam","category":"moderation","keywords":["scam","scammed","money lost","lost money","money bug","money stolen","got scammed","he scammed","she scammed"],"reply":"Make a report on the forum with proof.","priority":40,"enabled":true},
        {"name":"DM / RK / Kill Reports","category":"moderation","keywords":["dm","deathmatch","killed me","random kill","rk","revive kill","killed for no reason","shooting me","shooting at me","shooting on me","killing me","he killed","she killed","random dm"],"reply":"We are watching, send proof on the forum.","priority":45,"enabled":true},
        {"name":"Admin Abuse Report","category":"moderation","keywords":["admin abuse","abusing admin","admin abuser","admin is abusing","abuse of admin","admin abusing"],"reply":"Report the admin on the forum with evidence.","priority":46,"enabled":true},
        {"name":"Ban Appeal","category":"account","keywords":["unban","ban appeal","banned for no reason","wrongly banned","unban me","unban request","my ban"],"reply":"Make an appeal on the forum.","priority":50,"enabled":true},
        {"name":"Account Recovery","category":"account","keywords":["account hacked","password","forgot password","lost account","account problem","account lost","recover account","recover my account","account stolen","my account"],"reply":"Contact support on the forum with proof of ownership.","priority":55,"enabled":true},
        {"name":"VIP / Donator Issues","category":"account","keywords":["vip not working","vip lost","lost vip","vip problem","donator","donator perk","vip missing","my vip"],"reply":"Reconnect and check again, if it persists contact support.","priority":60,"enabled":true},
        {"name":"Bug / Glitch Report","category":"general","keywords":["bug","glitch","game bug","glitched","bugged","error","not working","broken"],"reply":"Make a bug report on the forum with a video.","priority":65,"enabled":true},
        {"name":"Vehicle Missing","category":"vehicle","keywords":["car gone","car disappeared","vehicle gone","car lost","spawn my car","my car is gone","where is my car","car vanished","car despawn"],"reply":"Your vehicle will respawn shortly, help is coming.","priority":70,"enabled":true},
        {"name":"Teleport / Unstuck Request","category":"general","keywords":["teleport me","tp me","teleport","stuck somewhere","i am stuck","unstuck","i stuck","get unstuck","trapped","fall through map","fell through the map","under the map"],"reply":"Help is coming, stay where you are.","priority":75,"enabled":true},
        {"name":"Salary / Job Issue","category":"general","keywords":["salary","job problem","no salary","payment","salary not received","no payment","paycheck"],"reply":"Help is coming.","priority":80,"enabled":true},
        {"name":"Greeting Only","category":"greeting","keywords":["hi","hello","hey","hi admin","hello admin","hey admin","good morning","good evening","good afternoon","good night","hy","hlo"],"reply":"How can I help you?","priority":90,"enabled":true},
        {"name":"Thanks / Appreciation","category":"greeting","keywords":["thank","thanks","thx","tq","thank you","thanku","tnx","ty","good bot","nice admin"],"reply":"You have been helped. Enjoy the game!","priority":85,"enabled":true},
        {"name":"Generic Help","category":"general","keywords":["help","admin","need help","help me","please help","any admin","support","moderator","assistance","help plz","help pls"],"reply":"Help is coming.","priority":100,"enabled":true}
      ],
      "settings": {
        "greetingPrefix": "Greetings, ",
        "replyVariants": ["Help is coming","Help is on the way","Help must be coming","We are watching","We will monitor"]
      }
    }
    """

    fun defaultRulesJson(): String = DEFAULT_RULES_JSON

    fun loadRules(ctx: Context): List<BotRule> {
        val json = BotPrefs.getRulesJson(ctx) ?: DEFAULT_RULES_JSON
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("rules") ?: return emptyList()
            val out = mutableListOf<BotRule>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val kws = mutableListOf<String>()
                val ka = o.optJSONArray("keywords")
                if (ka != null) {
                    for (j in 0 until ka.length()) kws.add(ka.optString(j).lowercase().trim())
                }
                out.add(
                    BotRule(
                        name = o.optString("name"),
                        category = o.optString("category", "general"),
                        keywords = kws.filter { it.isNotEmpty() },
                        reply = o.optString("reply"),
                        priority = o.optInt("priority", 100),
                        enabled = o.optBoolean("enabled", true),
                    )
                )
            }
            out.sortedBy { it.priority }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getReplyVariants(ctx: Context): List<String> {
        val json = BotPrefs.getRulesJson(ctx) ?: DEFAULT_RULES_JSON
        return try {
            val arr = JSONObject(json).optJSONObject("settings")?.optJSONArray("replyVariants")
                ?: JSONArray("[\"Help is coming\"]")
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            listOf("Help is coming")
        }
    }

    fun getNameCheckKeywords(ctx: Context): List<String> {
        val json = BotPrefs.getRulesJson(ctx) ?: DEFAULT_RULES_JSON
        val fromJson = try {
            val arr = JSONObject(json).optJSONObject("settings")?.optJSONArray("nameCheckKeywords")
            if (arr != null) (0 until arr.length()).map { arr.optString(it).lowercase() } else null
        } catch (e: Exception) {
            null
        }
        return (fromJson ?: NAME_CHECK_KEYWORDS).filter { it.isNotBlank() }
    }

    fun getBlocklist(ctx: Context): List<String> {
        val json = BotPrefs.getRulesJson(ctx) ?: DEFAULT_RULES_JSON
        return try {
            val arr = JSONObject(json).optJSONObject("settings")?.optJSONArray("blocklist")
            if (arr != null) (0 until arr.length()).map { arr.optString(it).lowercase() } else RpNameChecker.NRP_BLOCKLIST
        } catch (e: Exception) {
            RpNameChecker.NRP_BLOCKLIST
        }
    }

    private fun normalize(text: String): String {
        val cleaned = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return " $cleaned "
    }

    fun matchRule(message: String, rules: List<BotRule>): BotRule? {
        val norm = normalize(message)
        var best: BotRule? = null
        var bestScore = 0
        for (rule in rules) {
            if (!rule.enabled) continue
            var score = 0
            for (kw in rule.keywords) {
                if (kw.isEmpty()) continue
                if (norm.contains(" $kw ") || norm.contains(" $kw") || norm.contains("$kw ")) {
                    score += kw.length
                }
            }
            if (score > 0 && (score > bestScore || (score == bestScore && best != null && rule.priority < best.priority))) {
                best = rule
                bestScore = score
            }
        }
        return best
    }

    fun isNameCheckQuestion(message: String, nameCheckKeywords: List<String>): Boolean {
        val norm = normalize(message)
        for (k in nameCheckKeywords) {
            if (norm.contains(" $k ") || norm.contains(" $k? ")) return true
        }
        return Regex("\\brp\\s*(or|\\?)|nrp\\s*(\\?|name|check)", RegexOption.IGNORE_CASE).containsMatchIn(message)
    }

    fun extractNameFromQuestion(message: String): String? {
        val patterns = listOf(
            Regex("([A-Za-z][A-Za-z0-9_]{1,23})\\s*(?:is\\s+)?(?:rp|nrp|non\\s*rp)\\b", RegexOption.IGNORE_CASE),
            Regex("(?:rp|nrp)\\s*(?:check|name)?\\s*[:\\-]?\\s*([A-Za-z][A-Za-z0-9_]{1,23})", RegexOption.IGNORE_CASE),
            Regex("(?:is|check|verify)\\s+([A-Za-z][A-Za-z0-9_]{1,23})\\s*(?:rp|nrp|name)", RegexOption.IGNORE_CASE),
            Regex("(?:name(?:\\s+of)?\\s*[:\\-]?)\\s*([A-Za-z][A-Za-z0-9_]{1,23})", RegexOption.IGNORE_CASE),
        )
        val skip = Regex("^(is|this|the|my|check|verify|rp|nrp|name|or|and|non|he|she|his|her)$", RegexOption.IGNORE_CASE)
        for (p in patterns) {
            val m = p.find(message)
            val candidate = m?.groupValues?.get(1)
            if (!candidate.isNullOrEmpty() && !skip.matches(candidate.trim())) {
                return candidate.trim()
            }
        }
        return null
    }

    fun pickVariant(variants: List<String>): String {
        if (variants.isEmpty()) return "Help is coming"
        return variants[ThreadLocalRandom.current().nextInt(variants.size)].trim()
    }

    /**
     * Full reply pipeline: rule match -> name check -> generic variant.
     */
    fun generateReply(
        ctx: Context,
        report: ReportParser.ParsedReport,
        rules: List<BotRule>,
    ): ReplyResult {
        val prefix = BotPrefs.getGreetingPrefix(ctx)
        val matched = matchRule(report.message, rules)

        if (matched != null) {
            if (matched.reply.contains("{namecheck}") || matched.category == "namecheck") {
                return nameCheckReply(ctx, report, prefix)
            }
            return ReplyResult(
                reply = "$prefix${matched.reply}".replace(Regex("\\s{2,}"), " ").trim(),
                intent = matched.name,
                ruleName = matched.name,
            )
        }

        if (isNameCheckQuestion(report.message, getNameCheckKeywords(ctx))) {
            return nameCheckReply(ctx, report, prefix)
        }

        return ReplyResult(
            reply = "$prefix${pickVariant(getReplyVariants(ctx))}",
            intent = "Generic help",
            ruleName = null,
        )
    }

    private fun nameCheckReply(
        ctx: Context,
        report: ReportParser.ParsedReport,
        prefix: String,
    ): ReplyResult {
        val name = extractNameFromQuestion(report.message) ?: report.playerName
        val verdict = RpNameChecker.check(name)
        val reply = if (verdict.verdict == "RP") {
            "$prefix$name is a valid RP name, no action needed."
        } else {
            "$prefix$name is a Non-RP name, he must change it."
        }
        return ReplyResult(reply, "RP/NRP check: $name → ${verdict.verdict}", "Name check")
    }

    fun buildCommand(ctx: Context, report: ReportParser.ParsedReport, reply: String): String {
        return BotPrefs.getCommandTemplate(ctx)
            .replace("{id}", report.playerId ?: report.playerName)
            .replace("{name}", report.playerName)
            .replace("{message}", reply)
    }
}
