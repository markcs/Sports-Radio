package com.markcs.footyradio.scores

import android.content.SharedPreferences
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Routes the selected sport to its provider and persists the choice. [NONE] turns live
 * scores off.
 */
class SportsScoreManager(
    private val providers: Map<String, SportsScoreProvider>,
    private val prefs: SharedPreferences,
    scope: CoroutineScope,
) {
    private val _selectedSportId = MutableStateFlow(
        prefs.getString(KEY_SELECTED_SPORT, null)
            ?.takeIf { it == NONE || providers.containsKey(it) }
            ?: providers.keys.firstOrNull()
            ?: NONE
    )

    val selectedSportId: StateFlow<String> = _selectedSportId.asStateFlow()

    /** Switching sports swaps the upstream. Toggling display settings never touches this. */
    val snapshot: StateFlow<LiveScoreSnapshot?> = _selectedSportId
        .flatMapLatest { id -> providers[id]?.snapshot ?: flowOf(null) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun availableSports(): List<Pair<String, String>> =
        providers.values.map { it.sportId to it.displayName }

    /** Ignores ids that are neither [NONE] nor a registered provider. */
    fun setSport(sportId: String) {
        if (sportId != NONE && !providers.containsKey(sportId)) return
        prefs.edit().putString(KEY_SELECTED_SPORT, sportId).apply()
        _selectedSportId.value = sportId
    }

    companion object {
        const val NONE = "none"
        const val KEY_SELECTED_SPORT = "selected_sport_id"
    }
}
