package `in`.qaida.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.qaida.app.AppContainer
import `in`.qaida.app.domain.CardFactory
import `in`.qaida.app.domain.CardType
import `in`.qaida.app.domain.CatalogLetter
import `in`.qaida.app.domain.HearPickCard
import `in`.qaida.app.domain.ItemKey
import `in`.qaida.app.domain.Mastery
import `in`.qaida.app.domain.PlannedCard
import `in`.qaida.app.domain.ReviewState
import `in`.qaida.app.domain.Scheduler
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

    data class Card(
        val index: Int,
        val card: HearPickCard,
        val progress: Float,
        val feedback: Feedback?,
    ) : SessionUiState

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
    val chosenId: String

    data class Correct(override val chosenId: String) : Feedback

    /** The incorrect answer waits for a tap to continue: the replay of the right letter is the lesson. */
    data class Incorrect(override val chosenId: String, val correctId: String) : Feedback
}

data class LetterMove(val letter: CatalogLetter, val from: Mastery, val to: Mastery)

class SessionViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private lateinit var catalog: Map<String, CatalogLetter>
    private lateinit var planned: List<PlannedCard>
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
        planned = SessionComposer.compose(unlocked, states, CARD_TYPES, now, c.random, config.maxCards)
        if (planned.isEmpty()) {
            _state.value = SessionUiState.NothingDue
            return
        }

        val cards = planned.map { buildCard(it) }
        c.playback.preload(cards.map { it.clip.file })
        sessionId = c.progressRepository.startSession(now)
        startedAt = now
        builtCards = cards
        show(0)
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                _state.update { s -> if (s is SessionUiState.Card) s.copy(progress = progressAt(s.index)) else s }
            }
        }
    }

    private var builtCards: List<HearPickCard> = emptyList()

    private fun buildCard(p: PlannedCard): HearPickCard {
        val target = catalog.getValue(p.item.letterId)
        val card = CardFactory.hearPick(target, catalog, p.isIntro, lastVoice[target.id], c.random)
        lastVoice[target.id] = card.clip.voiceId
        return card
    }

    private fun show(index: Int) {
        val card = builtCards[index]
        cardShownAt = c.clock()
        _state.value = SessionUiState.Card(index, card, progressAt(index), feedback = null)
        c.playback.play(card.clip.file)
    }

    private fun progressAt(index: Int): Float {
        val byCards = index.toFloat() / builtCards.size
        val byTime = (c.clock() - startedAt).toFloat() / config.durationMs
        return maxOf(byCards, byTime).coerceIn(0f, 1f)
    }

    fun replay() {
        val s = _state.value as? SessionUiState.Card ?: return
        c.playback.play(s.card.clip.file)
    }

    fun answer(letterId: String) {
        val s = _state.value as? SessionUiState.Card ?: return
        if (s.feedback != null) return
        val now = c.clock()
        val item = planned[s.index].item
        val correct = letterId == s.card.target.id

        val next = Scheduler.onAnswer(states[item] ?: ReviewState(), correct, now)
        states[item] = next
        answered++
        if (correct) correctCount++
        viewModelScope.launch {
            c.progressRepository.recordGraded(sessionId, item, next, correct, now - cardShownAt, now)
        }

        if (correct) {
            _state.value = s.copy(feedback = Feedback.Correct(letterId))
            c.uiSounds.correct()
            viewModelScope.launch {
                delay(CORRECT_ADVANCE_MS)
                advance()
            }
        } else {
            _state.value = s.copy(feedback = Feedback.Incorrect(letterId, s.card.target.id))
            c.playback.play(s.card.clip.file)
        }
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
        if (next >= builtCards.size || outOfTime) finish() else show(next)
    }

    private fun finish() {
        ticker?.cancel()
        c.playback.stop()
        val now = c.clock()
        viewModelScope.launch { c.progressRepository.endSession(sessionId, now) }

        val seen = planned.take(answered).map { it.item }.distinct()
        val moved = seen.mapNotNull { item ->
            val from = Scheduler.mastery(startStates[item] ?: ReviewState(), now)
            val to = Scheduler.mastery(states.getValue(item), now)
            if (from != to) LetterMove(catalog.getValue(item.letterId), from, to) else null
        }
        val watch = seen.minByOrNull { Scheduler.recall(states.getValue(it), now) }?.let { catalog.getValue(it.letterId) }
        _state.value = SessionUiState.Summary(correctCount, answered, moved, watch, newTier = false)
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
        val CARD_TYPES = listOf(CardType.HEAR_PICK)
        const val CORRECT_ADVANCE_MS = 400L
        const val SUMMARY_MS = 4_000L
        private const val TICK_MS = 250L
    }
}
