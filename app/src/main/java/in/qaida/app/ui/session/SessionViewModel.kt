package `in`.qaida.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.qaida.app.AppContainer
import `in`.qaida.app.domain.AudioClip
import `in`.qaida.app.domain.CardType
import `in`.qaida.app.data.content.ContentPaths
import `in`.qaida.app.domain.BossLine
import `in`.qaida.app.domain.SelfRating
import `in`.qaida.app.dsp.Confidence
import `in`.qaida.app.ui.produce.ProduceController
import `in`.qaida.app.domain.CardFactory
import `in`.qaida.app.domain.CatalogLetter
import `in`.qaida.app.domain.ItemKey
import `in`.qaida.app.domain.Levels
import `in`.qaida.app.domain.Mastery
import `in`.qaida.app.domain.PlannedCard
import `in`.qaida.app.domain.Progression
import `in`.qaida.app.domain.Prompt
import `in`.qaida.app.domain.ReviewState
import `in`.qaida.app.domain.Scheduler
import `in`.qaida.app.domain.SessionCard
import `in`.qaida.app.domain.SessionComposer
import `in`.qaida.app.domain.SessionConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface SessionUiState {
    data object Loading : SessionUiState

    /** Nothing to practise: everything unlocked is reviewed and not yet due. */
    data object NothingDue : SessionUiState

    /** A completed tier's mushaf line, offered before the cards. Read aloud; never graded. */
    data class Boss(val line: BossLine, val produce: ProduceController, val hasReference: Boolean) : SessionUiState

    data class Card(
        val index: Int,
        val card: SessionCard,
        val progress: Float,
        val feedback: Feedback?,
        /** Plays of the prompt still allowed before answering; null is unlimited. */
        val playsLeft: Int?,
        /** SEE_PICK: the clip option last played, which the confirm button submits. */
        val selectedKey: String?,
        /** SEE_PICK at level 4: options already played once. */
        val spentOptions: Set<String>,
        /** PRODUCE cards only. */
        val produce: ProduceController? = null,
    ) : SessionUiState {
        val canPlayPrompt get() = feedback == null && (playsLeft == null || playsLeft > 0)
    }

    data class Summary(
        val correct: Int,
        val answered: Int,
        val moved: List<LetterMove>,
        val watch: CatalogLetter?,
        /** A new tier of letters opened as a result of this session. */
        val newTier: Boolean,
    ) : SessionUiState
}

sealed interface Feedback {
    val chosenKey: String

    data class Correct(override val chosenKey: String) : Feedback

    /** The incorrect answer waits for a tap to continue: the replay of the right answer is the lesson. */
    data class Incorrect(override val chosenKey: String, val correctKey: String) : Feedback
}

data class LetterMove(val letter: CatalogLetter, val from: Mastery, val to: Mastery)

class SessionViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private lateinit var catalog: Map<String, CatalogLetter>
    private lateinit var planned: List<PlannedCard>
    private var cards: List<SessionCard> = emptyList()
    private val states = HashMap<ItemKey, ReviewState>()
    private lateinit var startStates: Map<ItemKey, ReviewState>
    private val lastVoice = HashMap<String, String>()
    private var sessionId = 0L
    private var startedAt = 0L
    private var cardShownAt = 0L
    private var correctCount = 0
    private var answered = 0
    private var ticker: Job? = null
    private var unlockedTiers: Set<Int> = emptySet()
    private var config = SessionConfig()
    private var graded = 0
    private var reached = -1
    private var pendingBoss: BossLine? = null
    private var instructionLanguage = "en"
    private val instructed = HashSet<CardType>()

    init {
        viewModelScope.launch { begin() }
    }

    private suspend fun begin() {
        c.contentReady.await()
        val letters = c.contentRepository.catalog()
        catalog = letters.associateBy { it.id }
        states.putAll(c.progressRepository.reviewStates())
        startStates = states.toMap()

        val now = c.clock()
        config = c.sessionConfig()
        unlockedTiers = c.progressRepository.refreshUnlockedTiers(letters, states, now)
        val unlocked = letters.filter { it.unlockTier in unlockedTiers }
        val settings = c.settingsRepository.current()
        val produceAllowed = !settings.micDeclined
        instructionLanguage = settings.instructionLanguage.tag
        planned = SessionComposer.compose(unlocked, states, now, c.random, config.maxCards, produceAllowed)
        val attempted = c.progressRepository.bossAttemptedTiers()
        pendingBoss = c.contentRepository.bossLines().firstOrNull { line ->
            line.tier !in attempted && Progression.tierComplete(line.tier, letters, states, unlockedTiers, now)
        }
        if (planned.isEmpty() && pendingBoss == null) {
            _state.value = SessionUiState.NothingDue
            return
        }

        cards = planned.map { p ->
            val target = catalog.getValue(p.item.letterId)
            CardFactory.build(p.item.cardType, target, catalog, p.isIntro, Levels.of(states[p.item], now), lastVoice[target.id], c.random)
                .also { card -> card.revealClips.firstOrNull()?.let { lastVoice[target.id] = it.voiceId } }
        }
        val instructions = cards.map { it.item.cardType }.distinct().map { ContentPaths.instruction(it.name, instructionLanguage) }
            .filter { c.contentSource.exists(ContentPaths.audio(it)) }
        c.playback.preload(cards.flatMap { card -> card.clips.map { it.file } } + listOfNotNull(pendingBoss?.audio) + instructions)
        sessionId = c.progressRepository.startSession(now)
        startedAt = now
        val boss = pendingBoss
        if (boss != null) {
            val ref = boss.audio?.let { AudioClip(it, "boss") }
            _state.value = SessionUiState.Boss(boss, ProduceController(c, viewModelScope, ref), ref != null)
        } else {
            show(0)
        }
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                _state.update { s -> if (s is SessionUiState.Card) s.copy(progress = progressAt(s.index)) else s }
            }
        }
    }

    private fun show(index: Int) {
        val card = cards[index]
        reached = index
        cardShownAt = c.clock()
        val speak = card.prompt as? Prompt.Speak
        _state.value = SessionUiState.Card(
            index, card, progressAt(index), feedback = null,
            playsLeft = Levels.maxPlays(card.level), selectedKey = null, spentOptions = emptySet(),
            produce = speak?.let { ProduceController(c, viewModelScope, it.reference) },
        )
        // Level 1 plays the prompt (or, for a reading card, the letter's sound) without being asked, preceded
        // the first time a card type appears by its spoken instruction, if the pack has one.
        val instruction = instructionFor(card)
        when {
            instruction != null -> {
                val prompt = (card.prompt as? Prompt.Listen)?.clips?.map { it.file }
                    ?: listOfNotNull((card.prompt as? Prompt.Speak)?.reference?.file ?: card.hintClip?.file)
                c.playback.playSequence(listOf(instruction) + prompt)
            }
            speak != null -> c.playback.play(speak.reference.file)
            Levels.autoplay(card.level) -> playPrompt()
        }
    }

    /**
     * The spoken instruction for a level 1 card's type, once per type per session. Instructions honour the
     * silent switch; letter audio does not, because the learner asked for it.
     */
    private fun instructionFor(card: SessionCard): String? {
        if (card.level != 1 || !instructed.add(card.item.cardType) || !c.uiSounds.ringerOn()) return null
        return ContentPaths.instruction(card.item.cardType.name, instructionLanguage)
            .takeIf { c.contentSource.exists(ContentPaths.audio(it)) }
    }

    private fun progressAt(index: Int): Float {
        val byCards = index.toFloat() / cards.size
        val byTime = (c.clock() - startedAt).toFloat() / config.durationMs
        return maxOf(byCards, byTime).coerceIn(0f, 1f)
    }

    /** The replay button. At level 4 the prompt can be heard only once. */
    fun playPrompt() {
        val s = _state.value as? SessionUiState.Card ?: return
        if (!s.canPlayPrompt) return
        val card = s.card
        when (val p = card.prompt) {
            is Prompt.Listen -> c.playback.playSequence(p.clips.map { it.file }, volume = Levels.volume(card.level))
            is Prompt.Read -> card.hintClip?.let { c.playback.play(it.file, Levels.volume(card.level)) } ?: return
            is Prompt.Speak -> c.playback.play(p.reference.file)
        }
        _state.value = s.copy(playsLeft = s.playsLeft?.minus(1))
    }

    /** SEE_PICK: hearing an option also selects it; the confirm button then submits. */
    fun playOption(key: String) {
        val s = _state.value as? SessionUiState.Card ?: return
        if (s.feedback != null) return
        val clip = s.card.options.firstOrNull { it.key == key }?.clip ?: return
        val onceOnly = Levels.maxPlays(s.card.level) != null
        if (onceOnly && key in s.spentOptions) {
            _state.value = s.copy(selectedKey = key)
            return
        }
        c.playback.play(clip.file, Levels.volume(s.card.level))
        _state.value = s.copy(selectedKey = key, spentOptions = if (onceOnly) s.spentOptions + key else s.spentOptions)
    }

    fun confirmSelection() {
        val s = _state.value as? SessionUiState.Card ?: return
        s.selectedKey?.let(::answer)
    }

    fun answer(key: String) {
        val s = _state.value as? SessionUiState.Card ?: return
        if (s.feedback != null) return
        val now = c.clock()
        val item = planned[s.index].item
        val correct = key == s.card.correctKey

        val next = Scheduler.onAnswer(states[item] ?: ReviewState(), correct, now)
        states[item] = next
        answered++
        graded++
        if (correct) correctCount++
        viewModelScope.launch {
            c.progressRepository.recordGraded(sessionId, item, next, correct, now - cardShownAt, now)
        }

        if (correct) {
            _state.value = s.copy(feedback = Feedback.Correct(key))
            c.uiSounds.correct()
            viewModelScope.launch {
                delay(CORRECT_ADVANCE_MS)
                advance()
            }
        } else {
            _state.value = s.copy(feedback = Feedback.Incorrect(key, s.card.correctKey))
            reveal(s.card.revealClips)
        }
    }

    // The reveal ignores play limits and volume scaffolds: it is the teaching moment.
    private fun reveal(clips: List<AudioClip>) = c.playback.playSequence(clips.map { it.file })

    /** PRODUCE: the learner's own rating feeds the scheduler; the app never grades the recording. */
    fun rateProduce(rating: SelfRating) {
        val s = _state.value as? SessionUiState.Card ?: return
        val produce = s.produce ?: return
        val now = c.clock()
        val item = planned[s.index].item
        val next = Scheduler.onSelfRating(states[item] ?: ReviewState(), rating, now)
        states[item] = next
        answered++
        val distance = produce.state.value.analysis?.takeIf { it.confidence == Confidence.OK }?.alignmentDistance
        viewModelScope.launch {
            c.progressRepository.recordSelfRated(sessionId, item, next, rating, distance, now - cardShownAt, now)
        }
        advance()
    }

    /** Leaves a PRODUCE card unscheduled and moves on. */
    fun skipProduce() {
        if ((_state.value as? SessionUiState.Card)?.produce != null) advance()
    }

    /** The learner declined the microphone: no speaking cards until they allow it. */
    fun micDeclined() {
        viewModelScope.launch { c.settingsRepository.update { it.copy(micDeclined = true) } }
        skipProduce()
    }

    /** Boss line: marking it read turns the tier gold for good. */
    fun bossDone(attempted: Boolean) {
        val s = _state.value as? SessionUiState.Boss ?: return
        if (attempted) viewModelScope.launch { c.progressRepository.markBossAttempted(s.line.tier, c.clock()) }
        c.playback.stop()
        if (cards.isEmpty()) finish() else show(0)
    }

    /** Tap to continue after an incorrect answer. */
    fun continueAfterIncorrect() {
        val s = _state.value as? SessionUiState.Card ?: return
        if (s.feedback is Feedback.Incorrect) advance()
    }

    private fun advance() {
        val s = _state.value as? SessionUiState.Card ?: return
        val next = s.index + 1
        val outOfTime = c.clock() - startedAt >= config.durationMs
        if (next >= cards.size || outOfTime) finish() else show(next)
    }

    private fun finish() {
        ticker?.cancel()
        c.playback.stop()
        val now = c.clock()
        viewModelScope.launch {
            c.progressRepository.endSession(sessionId, now)
            c.recorder.prune()
        }

        val letters = planned.take(reached + 1).map { it.item.letterId }.distinct()
        val moved = letters.mapNotNull { id ->
            val from = Progression.weakest(id, startStates, now)?.let { Scheduler.mastery(it, now) } ?: Mastery.NEW
            val to = Progression.weakest(id, states, now)?.let { Scheduler.mastery(it, now) } ?: Mastery.NEW
            if (from != to) LetterMove(catalog.getValue(id), from, to) else null
        }
        val watch = letters.minByOrNull { id -> Progression.weakest(id, states, now)?.let { Scheduler.recall(it, now) } ?: 0.0 }
            ?.let { catalog.getValue(it) }
        _state.value = SessionUiState.Summary(correctCount, graded, moved, watch, newTier = false)
        viewModelScope.launch {
            val nowOpen = c.progressRepository.refreshUnlockedTiers(catalog.values.toList(), states, now)
            if ((nowOpen - unlockedTiers).isNotEmpty()) {
                _state.update { s -> if (s is SessionUiState.Summary) s.copy(newTier = true) else s }
            }
        }
    }

    override fun onCleared() {
        c.playback.stop()
    }

    companion object {
        const val CORRECT_ADVANCE_MS = 400L
        const val SUMMARY_MS = 4_000L
        private const val TICK_MS = 250L
    }
}
