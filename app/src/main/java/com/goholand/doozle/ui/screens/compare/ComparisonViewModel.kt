package com.goholand.doozle.ui.screens.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.goholand.doozle.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun initialize(fs: FileSystem, projectRoot: String) {
        viewModelScope.launch {
            try {
                Log.d("ComparisonVM", "initialize: starting")
                withContext(Dispatchers.IO) {
                    tree.initialize()
                    unseenManager.initialize()
                }

                // Create pair selector — scans projectRoot once for unseen, skipping _ranked/
                val selector = withContext(Dispatchers.IO) {
                    PairSelector(tree, fs, projectRoot, config).also {
                        it.listUnseen() // trigger cache fill on IO thread
                    }
                }
                pairSelector = selector

                Log.d("ComparisonVM", "initialize: ${selector.unseenCount()} unseen, ${tree.totalPhotos()} ranked")

                // Load first pair
                loadNextPair()
            } catch (e: Exception) {
                Log.e("ComparisonVM", "initialize error", e)
                _state.value = ComparisonState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onWinnerSelected(winner: PairCandidate, loser: PairCandidate) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // If unseen photo won/lost, promote it first
                    val winnerPos = resolvePosition(winner)
                    val loserPos = resolvePosition(loser)

                    if (winnerPos < 0 || loserPos < 0) {
                        // Something went wrong with position resolution
                        return@withContext
                    }

                    // Apply comparison
                    tree.applyComparison(
                        winnerPosition = winnerPos,
                        loserPosition = loserPos,
                        winnerIsUnseen = winner.isUnseen,
                        loserIsUnseen = loser.isUnseen
                    )
                }

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
        pairSelector?.removeUnseen(candidate.path)
        return tree.insertAtCenter(stagingPath)
    }

    private suspend fun loadNextPair() {
        val selector = pairSelector ?: return
        val pair = withContext(Dispatchers.IO) {
            selector.selectPair()
        }

        if (pair == null) {
            _state.value = ComparisonState.NeedMorePhotos
        } else {
            _state.value = ComparisonState.Ready(
                left = pair.first,
                right = pair.second,
                totalPhotos = tree.totalPhotos(),
                unseenCount = selector.unseenCount()
            )
        }
    }
}
