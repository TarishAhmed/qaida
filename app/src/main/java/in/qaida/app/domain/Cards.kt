package `in`.qaida.app.domain

import kotlin.math.pow
import kotlin.random.Random

enum class FormPosition { INITIAL, MEDIAL, FINAL }

/** What the learner is shown or played before answering. */
sealed interface Prompt {
    /** One clip (HEAR_PICK, HARAKAH_PICK) or two in sequence (AB_SAME_DIFF). */
    data class Listen(val clips: List<AudioClip>) : Prompt

    /** Text to read: a glyph (SEE_PICK) or a positional form (FORM_PICK). */
    data class Read(val text: String) : Prompt

    /** PRODUCE: say [text] after hearing [reference]; self-rated, never graded. */
    data class Speak(val text: String, val reference: AudioClip) : Prompt
}

/** One answer tile. Exactly one of [text] / [clip] is set, or neither for SAME / DIFFERENT. */
data class AnswerOption(val key: String, val kind: Kind, val text: String? = null, val clip: AudioClip? = null) {
    enum class Kind { GLYPH, CLIP, SAME, DIFFERENT }
}

data class SessionCard(
    val item: ItemKey,
    val target: CatalogLetter,
    val level: Int,
    val isIntro: Boolean,
    val prompt: Prompt,
    val options: List<AnswerOption>,
    val correctKey: String,
    /** Level 1 support: the target glyph shown with a listening prompt, or the target's sound with a reading one. */
    val hintGlyph: String?,
    val hintClip: AudioClip?,
    /** Played automatically after an incorrect answer: the sound of the right answer is the lesson. */
    val revealClips: List<AudioClip>,
) {
    /** Every clip this card can play, for preloading. */
    val clips: List<AudioClip>
        get() = listOfNotNull(hintClip) + options.mapNotNull { it.clip } + revealClips +
            ((prompt as? Prompt.Listen)?.clips.orEmpty())
}

/**
 * Fading scaffolds. Support is stripped as mastery rises; the learner never chooses the level.
 *
 *  1. audio plays automatically, glyph visible, 4 options
 *  2. audio plays on tap, 4 options
 *  3. audio at 70% volume, 4 options
 *  4. audio once only, no replay, 4 options
 *
 * The level is derived from recall, measured one day ahead: p1d = 2^(-24 h / s). Recall at the moment of a
 * due card is always about 0.9, so it cannot tell a shaky item from a solid one; recall a day out can.
 * A lapsed item (current p below 0.5) drops back to level 1.
 */
object Levels {
    const val VOLUME_AT_LEVEL_3 = 0.7f

    fun of(state: ReviewState?, now: Long): Int {
        if (state?.lastReviewedAt == null) return 1
        if (Scheduler.recall(state, now) < 0.5) return 1
        val dayAhead = 2.0.pow(-24.0 / state.stabilityHours)
        return when {
            dayAhead < 0.5 -> 1
            dayAhead < 0.8 -> 2
            dayAhead < 0.95 -> 3
            else -> 4
        }
    }

    fun autoplay(level: Int) = level == 1
    fun volume(level: Int) = if (level >= 3) VOLUME_AT_LEVEL_3 else 1f
    /** Plays allowed before answering; null means unlimited. */
    fun maxPlays(level: Int): Int? = if (level >= 4) 1 else null
}

object CardTypes {
    /**
     * A letter's HEAR_PICK has to hold for this long before its other card types join sessions:
     * perception first, and it keeps HEAR_PICK the backbone of every session.
     */
    const val OTHER_TYPES_AFTER_HOURS = 8.0

    /**
     * Card types this letter can be drilled with now. PRODUCE joins the graded types only when speaking
     * practice is allowed (the learner has not declined the microphone).
     */
    fun available(letter: CatalogLetter, states: Map<ItemKey, ReviewState>, produceAllowed: Boolean = false): List<CardType> {
        val hear = states[ItemKey(letter.id, CardType.HEAR_PICK)]
        val established = hear?.lastReviewedAt != null && hear.stabilityHours >= OTHER_TYPES_AFTER_HOURS
        if (!established || letter.confusableWith.isEmpty()) return listOf(CardType.HEAR_PICK)
        return buildList {
            add(CardType.HEAR_PICK)
            add(CardType.SEE_PICK)
            add(CardType.AB_SAME_DIFF)
            add(CardType.FORM_PICK)
            if (letter.harakatClips.isNotEmpty()) add(CardType.HARAKAH_PICK)
            if (produceAllowed && letter.isolatedClips.isNotEmpty()) add(CardType.PRODUCE)
        }
    }
}

object CardFactory {
    const val OPTIONS = 4
    const val SAME = "same"
    const val DIFFERENT = "different"
    private const val FATHA = "َ"
    private const val KASRA = "ِ"
    private const val DAMMA = "ُ"
    val HARAKAH_MARKS = mapOf("fatha" to FATHA, "kasra" to KASRA, "damma" to DAMMA)

    /**
     * Builds a card. Distractors come only from the target's confusableWith list, never random letters,
     * so a letter with few confusables gets fewer options. The voice differs from [lastVoice] whenever
     * the letter has more than one.
     */
    fun build(
        type: CardType,
        target: CatalogLetter,
        catalog: Map<String, CatalogLetter>,
        isIntro: Boolean,
        level: Int,
        lastVoice: String?,
        rng: Random,
    ): SessionCard {
        val item = ItemKey(target.id, type)
        val distractors = target.confusableWith.distinct().mapNotNull { catalog[it] }.shuffled(rng).take(OPTIONS - 1)
        val voice = pickVoice(target.isolatedClips, lastVoice, rng)
        fun card(
            prompt: Prompt, options: List<AnswerOption>, correct: String,
            hintGlyph: String?, hintClip: AudioClip?, reveal: List<AudioClip> = listOf(voice),
        ) = SessionCard(item, target, level, isIntro, prompt, options, correct,
            hintGlyph.takeIf { level == 1 }, hintClip.takeIf { level == 1 }, reveal)

        return when (type) {
            CardType.HEAR_PICK -> card(
                Prompt.Listen(listOf(voice)),
                (distractors + target).shuffled(rng).map { glyph(it) },
                target.id, hintGlyph = target.glyph, hintClip = null,
            )
            CardType.SEE_PICK -> card(
                Prompt.Read(target.glyph),
                (distractors + target).shuffled(rng).map { l ->
                    AnswerOption(l.id, AnswerOption.Kind.CLIP, clip = if (l == target) voice else pickVoice(l.isolatedClips, null, rng))
                },
                target.id, hintGlyph = null, hintClip = null,
            )
            CardType.AB_SAME_DIFF -> {
                val other = distractors.firstOrNull()
                val same = other == null || rng.nextBoolean()
                val second = if (same) pickVoice(target.isolatedClips, voice.voiceId, rng) else pickVoice(other!!.isolatedClips, null, rng)
                val pair = listOf(voice, second).shuffled(rng)
                card(
                    Prompt.Listen(pair),
                    listOf(AnswerOption(SAME, AnswerOption.Kind.SAME), AnswerOption(DIFFERENT, AnswerOption.Kind.DIFFERENT)),
                    if (same) SAME else DIFFERENT, hintGlyph = null, hintClip = null, reveal = pair,
                )
            }
            CardType.FORM_PICK -> {
                val form = when (FormPosition.entries.random(rng)) {
                    FormPosition.INITIAL -> target.forms.initial
                    FormPosition.MEDIAL -> target.forms.medial
                    FormPosition.FINAL -> target.forms.final
                }
                card(
                    Prompt.Read(form),
                    (distractors + target).shuffled(rng).map { glyph(it) },
                    target.id, hintGlyph = null, hintClip = voice,
                )
            }
            CardType.HARAKAH_PICK -> {
                val haraka = target.harakatClips.keys.sorted().random(rng)
                val clip = pickVoice(target.harakatClips.getValue(haraka), null, rng)
                // All three vowel choices are offered; only the one being heard needs a recording.
                val vowelled = HARAKAH_MARKS.keys.map { h -> vowelled(target, h) }
                val otherLetter = distractors.firstOrNull()?.let { vowelled(it, haraka) }
                card(
                    Prompt.Listen(listOf(clip)),
                    (vowelled + listOfNotNull(otherLetter)).shuffled(rng),
                    key(target.id, haraka), hintGlyph = target.glyph + HARAKAH_MARKS.getValue(haraka), hintClip = null,
                    reveal = listOf(clip),
                )
            }
            CardType.PRODUCE -> SessionCard(
                item, target, level, isIntro, Prompt.Speak(target.glyph, voice), options = emptyList(), correctKey = "",
                hintGlyph = null, hintClip = null, revealClips = listOf(voice),
            )
        }
    }

    private fun glyph(l: CatalogLetter) = AnswerOption(l.id, AnswerOption.Kind.GLYPH, text = l.glyph)

    /** The letter's glyph with a haraka mark attached: mechanical composition, not authored text. */
    private fun vowelled(l: CatalogLetter, haraka: String) =
        AnswerOption(key(l.id, haraka), AnswerOption.Kind.GLYPH, text = l.glyph + HARAKAH_MARKS.getValue(haraka))

    private fun key(letterId: String, haraka: String) = "$letterId|$haraka"

    private fun pickVoice(clips: List<AudioClip>, avoid: String?, rng: Random): AudioClip =
        clips.filter { it.voiceId != avoid }.ifEmpty { clips }.random(rng)
}
