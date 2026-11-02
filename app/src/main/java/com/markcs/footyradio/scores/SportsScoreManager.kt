package com.markcs.footyradio.scores

import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class SportsScoreManager(
    private val providers: Map<String, SportsScoreProvider>,
    selectedSportId: StateFlow<String>,
    scope: CoroutineScope,
) {
    /** Switching sports swaps the upstream. Toggling display settings never touches this. */
    val snapshot: StateFlow<LiveScoreSnapshot?> = selectedSportId
        .flatMapLatest { id -> providers[id]?.snapshot ?: flowOf(null) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun availableSports(): List<Pair<String, String>> =
        providers.values.map { it.sportId to it.displayName }
}
