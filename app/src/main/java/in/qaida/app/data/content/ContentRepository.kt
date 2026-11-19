package `in`.qaida.app.data.content

import `in`.qaida.app.data.db.BossLineEntity
import `in`.qaida.app.data.db.ConfusionSetMemberEntity
import `in`.qaida.app.data.db.ContentDao
import `in`.qaida.app.data.db.ContentMetaEntity
import `in`.qaida.app.data.db.LetterAudioEntity
import `in`.qaida.app.data.db.LetterConfusableEntity
import `in`.qaida.app.data.db.LetterEntity
import `in`.qaida.app.data.db.MakhrajEntity
import `in`.qaida.app.domain.AudioClip
import `in`.qaida.app.domain.CatalogLetter
import `in`.qaida.app.domain.ContentPack
import `in`.qaida.app.domain.LetterForms
import `in`.qaida.app.domain.MakhrajInfo
import `in`.qaida.app.domain.BossLine
import java.security.MessageDigest

/**
 * Imports the bundled content pack into Room. Runs on every launch but only writes when the pack's
 * hash differs from the one last imported, so first launch imports and later launches are a single query.
 */
class ContentRepository(
    private val source: ContentSource,
    private val dao: ContentDao,
) {
    /** Returns the number of letters imported, or null if the stored content was already current. */
    suspend fun importIfChanged(): Int? {
        val hash = sha256(*listOfNotNull(ContentPaths.LETTERS, ContentPaths.CONFUSION_SETS, ContentPaths.MAKHRAJ,
            ContentPaths.BOSS.takeIf(source::exists)).toTypedArray())
        if (dao.meta(META_HASH) == hash) return null
        val pack = source.loadPack()
        write(pack, hash)
        return pack.letters.size
    }

    /** Every letter with its confusables, confusion sets and isolated clips, in teaching order. */
    suspend fun catalog(): List<CatalogLetter> {
        val audio = dao.allAudio().groupBy { it.letterId }
        val confusables = dao.allConfusables().groupBy({ it.letterId }, { it.otherId })
        val sets = dao.allSetMembers().groupBy({ it.letterId }, { it.setIndex })
        val makhraj = dao.allMakhraj().associateBy { it.id }
        return dao.letters().map { l ->
            CatalogLetter(
                id = l.id,
                glyph = l.glyph,
                unlockTier = l.unlockTier,
                ordinal = l.ordinal,
                confusableWith = confusables[l.id].orEmpty(),
                confusionSets = sets[l.id].orEmpty().toSet(),
                isolatedClips = clips(audio[l.id], AssetValidator.ISOLATED),
                forms = LetterForms(l.formIsolated, l.formInitial, l.formMedial, l.formFinal),
                harakatClips = AssetValidator.HARAKAT.associateWith { clips(audio[l.id], it) }.filterValues { it.isNotEmpty() },
                makhraj = makhraj[l.makhrajId]?.let { m -> MakhrajInfo(m.diagram, m.labelEn, m.labelMl, l.makhrajNoteEn, l.makhrajNoteMl) },
                nameEn = l.nameEn,
                nameMl = l.nameMl,
            )
        }
    }

    suspend fun bossLines(): List<BossLine> = dao.bossLines().map { BossLine(it.tier, it.text, it.audio) }

    private fun clips(rows: List<LetterAudioEntity>?, context: String) =
        rows.orEmpty().filter { it.context == context }.sortedBy { it.file }.map { AudioClip(it.file, it.voiceId) }

    private suspend fun write(pack: ContentPack, hash: String) {
        dao.replaceAll(
            letters = pack.letters.mapIndexed { i, l ->
                LetterEntity(
                    id = l.id, ordinal = i, glyph = l.glyph,
                    formIsolated = l.forms.isolated, formInitial = l.forms.initial,
                    formMedial = l.forms.medial, formFinal = l.forms.final,
                    nameEn = l.nameEn, nameMl = l.nameMl, makhrajId = l.makhrajId,
                    makhrajNoteEn = l.makhrajNoteEn, makhrajNoteMl = l.makhrajNoteMl,
                    unlockTier = l.unlockTier,
                )
            },
            audio = pack.letters.flatMap { l ->
                l.audio.flatMap { (context, files) ->
                    files.map { f -> LetterAudioEntity(l.id, context, AssetValidator.voiceOf(l.id, context, f) ?: f, f) }
                }
            },
            confusables = pack.letters.flatMap { l ->
                l.confusableWith.distinct().mapIndexed { rank, other -> LetterConfusableEntity(l.id, other, rank) }
            },
            setMembers = pack.confusionSets.flatMapIndexed { i, set ->
                set.letterIds.distinct().map { ConfusionSetMemberEntity(i, it) }
            },
            makhraj = pack.makhraj.map { MakhrajEntity(it.id, it.diagram, it.labelEn, it.labelMl) },
            bossLines = pack.bossLines.map { BossLineEntity(it.tier, it.text, it.audio) },
            meta = listOf(ContentMetaEntity(META_HASH, hash)),
        )
    }

    private fun sha256(vararg paths: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in paths) md.update(source.readText(p).toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val META_HASH = "content_sha256"
    }
}
