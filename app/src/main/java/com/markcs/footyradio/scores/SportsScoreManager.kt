package com.markcs.footyradio.scores

import android.content.SharedPreferences
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchStatus
import com.markcs.footyradio.scores.model.TeamRef
import com.markcs.footyradio.scores.model.filterByTeams
import com.markcs.footyradio.scores.model.visibleAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the selected sports, the teams to follow per sport and the start window, and
 * merges the selected sports into one snapshot. Every selection is persisted. An empty
 * sport selection turns live scores off. An empty team selection means all teams.
 */
class SportsScoreManager(
    private val providers: Map<String, SportsScoreProvider>,
    private val prefs: SharedPreferences,
    scope: CoroutineScope,
) {
    private val _selected = MutableStateFlow(loadSelection())
    private val _teamFilters = MutableStateFlow(loadTeamFilters())
    private val _windowMinutes = MutableStateFlow(loadWindowMinutes())
    private val teamCache = HashMap<String, List<TeamRef>>()

    val selectedSportIds: StateFlow<Set<String>> = _selected.asStateFlow()

    /** Epoch ms, refreshed every second while subscribed. Drives when scheduled games appear. */
    private val clockMs: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }

    /**
     * Stage 1: the selected providers' matches, narrowed by each sport's team filter and
     * merged. It re-subscribes only when sports or teams change, not every second.
     */
    private val teamFilteredSnapshot: Flow<LiveScoreSnapshot?> = combine(_selected, _teamFilters) { ids, filters ->
        ids to filters
    }
        .flatMapLatest { (ids, filters) ->
            val flows = providers.filterKeys { it in ids }.map { (id, provider) ->
                provider.snapshot.map { it?.filterByTeams(filters[id].orEmpty()) }
            }
            if (flows.isEmpty()) {
                flowOf(null)
            } else {
                combine(flows) { snaps -> merge(snaps.filterNotNull()) }
            }
        }

    /**
     * Stage 2: applies the start window against the current time. Scheduled games appear
     * once they are within the window. Everything else is shown as the provider reports it.
     */
    val snapshot: StateFlow<LiveScoreSnapshot?> = combine(teamFilteredSnapshot, clockMs, _windowMinutes) { snap, now, minutes ->
        snap?.visibleAt(now, minutes * MINUTE_MS)
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

    /** The team ids followed for [sportId]. Empty means every team. */
    fun teamFilter(sportId: String): Set<String> = _teamFilters.value[sportId].orEmpty()

    /** Replaces the followed teams for [sportId]. An empty set restores "all teams". */
    fun setTeamFilter(sportId: String, teamIds: Set<String>) {
        if (sportId !in providers) return
        val cleaned = teamIds.toSet()
        val editor = prefs.edit()
        if (cleaned.isEmpty()) {
            editor.remove(teamKey(sportId))
        } else {
            editor.putStringSet(teamKey(sportId), HashSet(cleaned))
        }
        editor.apply()
        _teamFilters.value = if (cleaned.isEmpty()) {
            _teamFilters.value - sportId
        } else {
            _teamFilters.value + (sportId to cleaned)
        }
    }

    /** How long before its start a scheduled game appears on the ticker. 0 means at start. */
    fun scheduledWindowMinutes(): Int = _windowMinutes.value

    fun setScheduledWindowMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_SCHEDULED_WINDOW, minutes).apply()
        _windowMinutes.value = minutes
    }

    /** The full team list for [sportId], cached once loaded. Failures are not cached, so a retry works. */
    suspend fun teamsFor(sportId: String): List<TeamRef> {
        teamCache[sportId]?.let { return it }
        val teams = providers[sportId]?.teams().orEmpty()
        if (teams.isNotEmpty()) teamCache[sportId] = teams
        return teams
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

    private fun loadTeamFilters(): Map<String, Set<String>> = providers.keys.mapNotNull { id ->
        prefs.getStringSet(teamKey(id), null)
            ?.takeIf { it.isNotEmpty() }
            ?.let { id to it.toSet() }
    }.toMap()

    private fun loadWindowMinutes(): Int = prefs.getInt(KEY_SCHEDULED_WINDOW, DEFAULT_WINDOW_MINUTES)

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
        private const val KEY_SCHEDULED_WINDOW = "scheduled_window_minutes"
        private const val MULTI_SPORT_ID = "multi"
        private const val MINUTE_MS = 60_000L
        private const val CLOCK_TICK_MS = 1_000L

        /** The start-window choices offered in Settings. */
        val SCHEDULED_WINDOW_OPTIONS = listOf(0, 5, 10, 15, 30, 60)
        const val DEFAULT_WINDOW_MINUTES = 15

        private fun teamKey(sportId: String) = "team_filter_$sportId"
    }
}
