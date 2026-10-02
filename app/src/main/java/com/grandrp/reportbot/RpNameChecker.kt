package com.grandrp.reportbot

/**
 * Decides whether an in-game name is a valid RP name (Firstname_Lastname)
 * or a Non-RP name (gamer-tags, famous people, numbers, titles...).
 */
object RpNameChecker {

    val FAMOUS_NAMES = listOf(
        "john cena", "elon musk", "donald trump", "salman khan", "shahrukh khan", "shah rukh khan",
        "srk", "akshay kumar", "amir khan", "aamir khan", "hrithik roshan", "virat kohli",
        "ms dhoni", "mahendra singh dhoni", "rohit sharma", "lionel messi", "messi",
        "cristiano ronaldo", "ronaldo", "neymar", "mbappe", "kylie jenner", "kim kardashian",
        "justin bieber", "ariana grande", "eminem", "snoop dogg", "drake", "kanye west",
        "rihanna", "beyonce", "michael jackson", "bruce lee", "jackie chan", "jet li",
        "tony stark", "iron man", "spider man", "spiderman", "batman", "superman", "thor",
        "loki", "hulk", "deadpool", "wolverine", "johnny depp", "brad pitt",
        "leonardo dicaprio", "tom cruise", "will smith", "the rock", "dwayne johnson",
        "conor mcgregor", "khabib", "mike tyson", "muhammad ali", "adolf hitler", "osama",
        "saddam", "putin", "vladimir putin", "narendra modi", "barack obama", "joe biden",
        "james bond", "sherlock holmes", "harry potter", "lord voldemort", "goku", "vegeta",
        "naruto", "sasuke", "luffy", "zoro", "itachi", "madara", "obito", "kakashi",
        "saitama", "tanjiro", "nezuko"
    )

    val NRP_BLOCKLIST = listOf(
        "killer", "killa", "slayer", "shadow", "shady", "sniper", "assassin", "hunter", "boss",
        "king", "queen", "lord", "god", "gamer", "gaming", "pro", "noob", "nub", "hack", "hacker",
        "cheater", "toxic", "savage", "beast", "dragon", "wolf", "snake", "tiger", "demon", "devil",
        "ghost", "phantom", "ninja", "samurai", "warrior", "fighter", "player", "gangster",
        "gangsta", "thug", "mafia", "don", "mobs", "money", "rich", "billionaire", "swag", "dope",
        "legend", "master", "op", "imba", "chad", "sigma", "alpha", "beta", "playboy", "joker",
        "admin", "moderator", "tester", "owner", "grand", "mobile", "roleplay", "samp", "gta",
        "hitler", "osama", "terrorist", "jihad", "bomb", "blast", "tharki", "randi", "pimp",
        "nigga", "fuck", "bitch", "asshole", "dick", "pussy", "penis", "sexy", "hot", "horny",
        "porn", "xxx"
    )

    data class Result(
        val verdict: String, // "RP" or "NRP"
        val reasons: List<String>,
    )

    fun check(name: String): Result {
        val reasons = mutableListOf<String>()
        val clean = name.trim()
        val lower = clean.lowercase()

        if (clean.isEmpty()) return Result("NRP", listOf("Empty name"))

        val parts = clean.split("_").filter { it.isNotEmpty() }

        if (parts.size < 2) {
            reasons.add("Must be in Firstname_Lastname format (missing underscore / surname)")
        } else if (parts.size > 3) {
            reasons.add("Too many parts — max Firstname_Middle_Lastname (3 parts)")
        }

        for (part in parts) {
            if (part.any { it.isDigit() }) reasons.add("\"$part\" contains numbers")
            if (part.any { !it.isLetter() }) reasons.add("\"$part\" contains special characters")
            if (part.isNotEmpty() && part[0].isLowerCase()) reasons.add("\"$part\" must start with a capital letter")
            if (part.length == 1) reasons.add("\"$part\" is too short (min 2 letters)")
            if (part.length >= 4 && part.all { it.isUpperCase() }) reasons.add("\"$part\" is all caps (use John, not JOHN)")
            if (Regex("(.)\\1{2,}", RegexOption.IGNORE_CASE).containsMatchIn(part)) reasons.add("\"$part\" has repeated letters (looks fake)")
            if (Regex("[a-z][A-Z]").containsMatchIn(part)) reasons.add("\"$part\" has mixed case like a gamer-tag (PrO / xX)")
            if (part.lowercase() == "xx" || part.lowercase() == "x") reasons.add("\"$part\" is an xX gamer-tag decoration")
        }

        if (FAMOUS_NAMES.contains(lower)) {
            reasons.add("This is a famous / real celebrity name — not allowed")
        } else {
            val spaced = lower.replace('_', ' ')
            for (famous in FAMOUS_NAMES) {
                if (famous.contains(' ') && spaced.contains(famous)) {
                    reasons.add("Famous name detected: \"$famous\" is not allowed")
                    break
                }
            }
        }

        for (part in parts) {
            if (NRP_BLOCKLIST.contains(part.lowercase())) {
                reasons.add("\"$part\" is a fantasy / gamer / title word, not a real first/last name")
                break
            }
        }

        if (!clean.contains('_') && parts.size == 1 && Regex("^[A-Z][a-z]+$").matches(clean)) {
            reasons.add("Single name only — surname required (Firstname_Lastname)")
        }

        val verdict = if (reasons.isEmpty()) "RP" else "NRP"
        return Result(verdict, reasons.distinct())
    }
}
