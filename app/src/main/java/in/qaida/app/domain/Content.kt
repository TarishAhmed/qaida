package `in`.qaida.app.domain

/** One teaching unit, as defined in assets/content/letters.json. */
data class Letter(
    val id: String,
    val glyph: String,
    val forms: LetterForms,
    val nameEn: String,
    val nameMl: String,
    /** Empty when the pack has no articulation data for this letter. */
    val makhrajId: String,
    val makhrajNoteEn: String,
    val makhrajNoteMl: String,
    val confusableWith: List<String>,
    /** Audio file names keyed by context ("isolated", later the harakat). */
    val audio: Map<String, List<String>>,
    val unlockTier: Int,
)

data class LetterForms(
    val isolated: String,
    val initial: String,
    val medial: String,
    val final: String,
)

/** A minimal-pair set of letter ids that drives listening cards. */
data class ConfusionSet(val letterIds: List<String>)

/** An articulation-point diagram, referenced by [Letter.makhrajId]. */
data class Makhraj(
    val id: String,
    /** Asset path of the diagram, relative to the assets root. */
    val diagram: String,
    val labelEn: String,
    val labelMl: String,
)

/** A real line of Indo-Pak mushaf text using only letters up to [tier]: the payoff for completing it. */
data class BossLine(
    val tier: Int,
    val text: String,
    /** Optional recitation of the line, in the audio folder. */
    val audio: String?,
)

data class ContentPack(
    val letters: List<Letter>,
    val confusionSets: List<ConfusionSet>,
    val makhraj: List<Makhraj>,
    /** From the optional boss.json; empty when the pack has none. */
    val bossLines: List<BossLine> = emptyList(),
)

enum class CardType { HEAR_PICK, SEE_PICK, AB_SAME_DIFF, FORM_PICK, HARAKAH_PICK, PRODUCE }
