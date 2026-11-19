package `in`.qaida.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.qaida.app.AppContainer
import `in`.qaida.app.domain.LetterStatus
import `in`.qaida.app.domain.Progression
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TierRow(val tier: Int, val unlocked: Boolean, val bossAttempted: Boolean, val letters: List<LetterStatus>)

data class HomeUiState(
    val loading: Boolean = true,
    val tiers: List<TierRow> = emptyList(),
    val lettersNeedingReview: Int = 0,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Called whenever Home is shown: recall decays with time, so the grid and count are recomputed. */
    fun refresh() {
        viewModelScope.launch {
            c.contentReady.await()
            val now = c.clock()
            val catalog = c.contentRepository.catalog()
            val states = c.progressRepository.reviewStates()
            val unlocked = c.progressRepository.refreshUnlockedTiers(catalog, states, now)
            val bosses = c.progressRepository.bossAttemptedTiers()
            val statuses = Progression.status(catalog, states, unlocked, now)
            _state.value = HomeUiState(
                loading = false,
                tiers = statuses.groupBy { it.letter.unlockTier }.toSortedMap().map { (tier, letters) ->
                    TierRow(tier, tier in unlocked, tier in bosses, letters)
                },
                lettersNeedingReview = Progression.lettersNeedingReview(statuses),
            )
        }
    }
}
