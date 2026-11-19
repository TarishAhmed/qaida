package `in`.qaida.app.data.content

import `in`.qaida.app.domain.ContentPack

data class ValidationReport(val errors: List<String>, val warnings: List<String>) {
    val ok get() = errors.isEmpty()
}

/**
 * Semantic checks on a parsed [ContentPack] against the files actually present in [ContentSource].
 * Runs as a unit test over the bundled pack; any error fails the build.
 */
object AssetValidator {
    private val ID = Regex("[a-z][a-z0-9_]*")
    private val VOICE = Regex("[a-z0-9]+")
    const val ISOLATED = "isolated"
    /** Vowelled contexts used by HARAKAH_PICK. Further harakat are out of scope for v1. */
    val HARAKAT = listOf("fatha", "kasra", "damma")
    val CONTEXTS = listOf(ISOLATED) + HARAKAT
    const val MIN_VOICES = 4

    fun validate(pack: ContentPack, source: ContentSource): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val letterIds = pack.letters.map { it.id }.toSet()
        val makhrajIds = pack.makhraj.map { it.id }.toSet()

        if (pack.letters.isEmpty()) errors += "letters.json: no letters"
        duplicates(pack.letters.map { it.id }).forEach { errors += "letters.json: duplicate id '$it'" }
        duplicates(pack.makhraj.map { it.id }).forEach { errors += "makhraj.json: duplicate id '$it'" }

        for (l in pack.letters) {
            val at = "letter '${l.id}'"
            if (!ID.matches(l.id)) errors += "$at: id must match ${ID.pattern}"
            listOf(l.glyph, l.forms.isolated, l.forms.initial, l.forms.medial, l.forms.final, l.nameEn, l.nameMl)
                .forEach { if (it.isBlank()) errors += "$at: glyph, forms and names must not be blank" }
            if (l.makhrajId !in makhrajIds) errors += "$at: makhrajId '${l.makhrajId}' is not defined in makhraj.json"
            if (l.unlockTier < 1) errors += "$at: unlockTier must be >= 1"

            if (l.confusableWith.isEmpty()) errors += "$at: confusableWith is empty"
            for (other in l.confusableWith) {
                if (other == l.id) errors += "$at: confusableWith contains itself"
                else if (other !in letterIds) errors += "$at: confusableWith '$other' is not a letter"
            }
            if (l.confusableWith.distinct().size < 3) {
                warnings += "$at: ${l.confusableWith.distinct().size} confusable(s), so HEAR_PICK shows fewer than 4 options"
            }

            val isolated = l.audio[ISOLATED].orEmpty()
            if (isolated.isEmpty()) errors += "$at: no '$ISOLATED' audio"
            else if (isolated.size < MIN_VOICES) warnings += "$at: ${isolated.size} isolated voice(s), fewer than $MIN_VOICES"
            for (context in l.audio.keys - CONTEXTS.toSet()) errors += "$at: unknown audio context '$context' (allowed: $CONTEXTS)"
            val harakat = HARAKAT.filter { !l.audio[it].isNullOrEmpty() }
            if (harakat.isNotEmpty() && harakat.size < HARAKAT.size) {
                warnings += "$at: only ${harakat} harakat audio, so HARAKAH_PICK offers fewer vowel choices"
            }
            for ((context, files) in l.audio) {
                duplicates(files).forEach { errors += "$at: audio file '$it' listed twice" }
                for (file in files) {
                    if (voiceOf(l.id, context, file) == null) {
                        errors += "$at: audio '$file' must be named ${l.id}_${context}_<voiceId>.opus"
                    }
                    if (!source.exists(ContentPaths.audio(file))) errors += "$at: audio file missing: ${ContentPaths.audio(file)}"
                }
            }
        }

        val tiers = pack.letters.map { it.unlockTier }.toSortedSet()
        if (tiers.isNotEmpty() && tiers.toList() != (1..tiers.last()).toList()) {
            errors += "letters.json: unlock tiers must run 1..N without gaps, found $tiers"
        }

        pack.confusionSets.forEachIndexed { i, set ->
            val at = "confusion_sets.json[$i]"
            if (set.letterIds.size < 2) errors += "$at: a confusion set needs at least 2 letters"
            duplicates(set.letterIds).forEach { errors += "$at: '$it' listed twice" }
            set.letterIds.filter { it !in letterIds }.forEach { errors += "$at: '$it' is not a letter" }
        }

        val tierSet = pack.letters.map { it.unlockTier }.toSet()
        duplicates(pack.bossLines.map { it.tier.toString() }).forEach { errors += "boss.json: tier $it has more than one line" }
        for (b in pack.bossLines) {
            val at = "boss.json tier ${b.tier}"
            if (b.tier !in tierSet) errors += "$at: no such tier"
            if (b.text.isBlank()) errors += "$at: text is blank"
            b.audio?.let { if (!source.exists(ContentPaths.audio(it))) errors += "$at: audio file missing: ${ContentPaths.audio(it)}" }
            // Every base letter in the line must already be taught by this tier. Marks and spaces are ignored;
            // Indo-Pak text may use code points the glyph table does not, so this is a warning for review.
            val taught = pack.letters.filter { it.unlockTier <= b.tier }.flatMap { l -> listOf(l.glyph) + listOf(l.forms.isolated) }
                .flatMap { it.toList() }.toSet()
            val untaught = b.text.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() && !it.isWhitespace() && it != '\u0640' }
                .toSet() - taught
            if (untaught.isNotEmpty()) warnings += "$at: characters not taught by this tier: ${untaught.joinToString(" ") { "U+%04X".format(it.code) }}"
        }

        // Spoken instructions are optional, but a pack that has some should have both languages for each.
        for (type in listOf("hear_pick", "see_pick", "ab_same_diff", "form_pick", "harakah_pick", "produce")) {
            val present = listOf("en", "ml").filter { source.exists(ContentPaths.audio(ContentPaths.instruction(type, it))) }
            if (present.size == 1) warnings += "instructions: $type has only ${present.single()} audio"
        }

        for (m in pack.makhraj) {
            if (!source.exists(m.diagram)) errors += "makhraj '${m.id}': diagram missing: ${m.diagram}"
        }

        return ValidationReport(errors, warnings)
    }

    /** The voice id encoded in `<letterId>_<context>_<voiceId>.opus`, or null if the name does not follow it. */
    fun voiceOf(letterId: String, context: String, file: String): String? {
        val prefix = "${letterId}_${context}_"
        if (!file.startsWith(prefix) || !file.endsWith(".opus")) return null
        return file.removePrefix(prefix).removeSuffix(".opus").takeIf { VOICE.matches(it) }
    }

    private fun duplicates(xs: List<String>) = xs.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
