package com.goholand.doozle.ui.screens.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goholand.doozle.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * State for the comparison screen.
 */
sealed interface ComparisonState {
    data object Loading : ComparisonState
    data class Ready(
        val left: PairCandidate,
        val right: PairCandidate,
        val totalPhotos: Int,
        val unseenCount: Int
    ) : ComparisonState
    data object NeedMorePhotos : ComparisonState
    data class Error(val message: String) : ComparisonState
}

/**
 * ViewModel for the comparison screen.
 * Orchestrates BStarTree, UnseenManager, and PairSelector.
 */
class ComparisonViewModel(
    private val tree: BStarTree,
    private val unseenManager: UnseenManager,
    private val config: EngineConfig
) : ViewModel() {

    private val _state = MutableStateFlow<ComparisonState>(ComparisonState.Loading)
    val state: StateFlow<ComparisonState> = _state

    private var pairSelector: PairSelector? = null

    fun initialize(fs: FileSystem, unseenRoot: String) {
        viewModelScope.launch {
            try {
                tree.initialize()
                unseenManager.initialize()

                // Scan for new photos
                unseenManager.scanForNewPhotos()

                // Create pair selector
                pairSelector = PairSelector(tree, fs, unseenRoot, config)

                // Load first pair
                loadNextPair()
            } catch (e: Exception) {
                _state.value = ComparisonState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onWinnerSelected(winner: PairCandidate, loser: PairCandidate) {
        viewModelScope.launch {
            try {
                // If unseen photo won/lost, promote it first
                val winnerPos = resolvePosition(winner)
                val loserPos = resolvePosition(loser)

                if (winnerPos < 0 || loserPos < 0) {
                    // Something went wrong with position resolution
                    loadNextPair()
                    return@launch
                }

                // Apply comparison
                tree.applyComparison(
                    winnerPosition = winnerPos,
                    loserPosition = loserPos,
                    winnerIsUnseen = winner.isUnseen,
                    loserIsUnseen = loser.isUnseen
                )

                // Load next pair
                loadNextPair()
            } catch (e: Exception) {
                _state.value = ComparisonState.Error(e.message ?: "Comparison failed")
            }
        }
    }

    /**
     * Resolve a candidate's position in the tree.
     * If unseen, promote it to the tree first (insertAtCenter).
     */
    private fun resolvePosition(candidate: PairCandidate): Int {
        if (!candidate.isUnseen) return candidate.position

        // Promote unseen photo to ranked tree
        val photo = Photo(
            originalName = candidate.path.substringAfterLast('/'),
            path = candidate.path
        )
        val stagingPath = unseenManager.promotePhoto(photo)
        return tree.insertAtCenter(stagingPath)
    }

    private fun loadNextPair() {
        val selector = pairSelector ?: return
        val pair = selector.selectPair()

        if (pair == null) {
            _state.value = ComparisonState.NeedMorePhotos
        } else {
            _state.value = ComparisonState.Ready(
                left = pair.first,
                right = pair.second,
                totalPhotos = tree.totalPhotos(),
                unseenCount = unseenManager.getUnseenPhotos().size
            )
        }
    }
}
