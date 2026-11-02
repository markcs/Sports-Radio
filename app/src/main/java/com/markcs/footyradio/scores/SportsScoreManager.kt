package com.markcs.footyradio.scores

import android.content.SharedPreferences
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the set of selected sports, merges their live scores into one snapshot and
 * persists the selection. An empty selection turns live scores off.
 */
class SportsScoreManager(
    private val providers: Map<String, SportsScoreProvider>,
    private val prefs: SharedPreferences,
    scope: CoroutineScope,
) {
    private val _selected = MutableStateFlow(loadSelection())

    val selectedSportIds: StateFlow<Set<String>> = _selected.asStateFlow()

    /**
     * Subscribes only to the selected providers, so unselected sports cost no network
     * traffic. Matches from all selected sports are merged, live games first.
     */
    val snapshot: StateFlow<LiveScoreSnapshot?> = _selected
        .flatMapLatest { ids ->
            val flows = providers.keys
                .filter { it in ids }
                .mapNotNull { providers[it]?.snapshot }
            if (flows.isEmpty()) {
                flowOf(null)
            } else {
                combine(flows) { snaps -> merge(snaps.filterNotNull()) }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun availableSports(): List<Pair<String, String>> =
        providers.values.map { it.sportId to it.displayName }

    /** Ignores ids that are not registered providers. */
    fun setSportSelected(sportId: String, selected: Boolean) {
        if (sportId !in providers) return
        val updated = if (selected) _selected.value + sportId else _selected.value - sportId
        save(updated)
        _selected.value = updated
    }

    private fun loadSelection(): Set<String> {
        prefs.getStringSet(KEY_SELECTED_SPORTS, null)?.let { saved ->
            return saved.filterTo(mutableSetOf()) { it in providers }
        }
        // Migrate the earlier single-sport key. Its value "none" means Off.
        prefs.getString(KEY_LEGACY_SELECTED_SPORT, null)?.let { legacy ->
            return if (legacy in providers) setOf(legacy) else emptySet()
        }
        // First run: AFL (the first provider) is on by default.
        return providers.keys.firstOrNull()?.let { setOf(it) } ?: emptySet()
    }

    private fun save(ids: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_SELECTED_SPORTS, HashSet(ids))
            .remove(KEY_LEGACY_SELECTED_SPORT)
            .apply()
    }

    private fun merge(snaps: List<LiveScoreSnapshot>): LiveScoreSnapshot? {
        val matches = snaps.flatMap { it.matches }.sortedBy { it.status.rank() }
        if (matches.isEmpty()) return null
        val sportId = if (snaps.size == 1) snaps.first().sportId else MULTI_SPORT_ID
        return LiveScoreSnapshot(sportId = sportId, matches = matches)
    }

    private fun MatchStatus.rank(): Int = when (this) {
        MatchStatus.LIVE -> 0
        MatchStatus.PAUSED -> 1
        MatchStatus.SCHEDULED -> 2
        MatchStatus.FINISHED -> 3
    }

    companion object {
        const val KEY_SELECTED_SPORTS = "selected_sport_ids"
        private const val KEY_LEGACY_SELECTED_SPORT = "selected_sport_id"
        private const val MULTI_SPORT_ID = "multi"
    }
}
