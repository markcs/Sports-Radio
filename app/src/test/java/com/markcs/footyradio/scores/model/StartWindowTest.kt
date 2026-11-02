package com.markcs.footyradio.scores.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StartWindowTest {

    private val start = 1_000_000_000_000L
    private val minute = 60_000L

    private fun team(id: String) = TeamRef(name = id, shortName = id, logoUrl = null, id = id)

    private fun match(status: MatchStatus, startMs: Long?) = MatchScore(
        id = "m",
        home = team("HOM"),
        away = team("AWY"),
        homeScore = null,
        awayScore = null,
        clock = null,
        status = status,
        startMs = startMs,
    )

    private fun snapshot(match: MatchScore) = LiveScoreSnapshot(sportId = "nba", matches = listOf(match))

    @Test
    fun scheduled_game_is_hidden_until_the_window_opens() {
        assertNull(snapshot(match(MatchStatus.SCHEDULED, start)).visibleAt(start - 16 * minute, 15 * minute))
    }

    @Test
    fun scheduled_game_is_shown_once_inside_the_window() {
        assertNotNull(snapshot(match(MatchStatus.SCHEDULED, start)).visibleAt(start - 15 * minute, 15 * minute))
    }

    @Test
    fun zero_window_shows_a_scheduled_game_only_from_its_start_time() {
        val scheduled = snapshot(match(MatchStatus.SCHEDULED, start))
        assertNull(scheduled.visibleAt(start - 1, 0L))
        assertNotNull(scheduled.visibleAt(start, 0L))
    }

    @Test
    fun live_and_finished_games_are_never_hidden_by_the_window() {
        assertNotNull(snapshot(match(MatchStatus.LIVE, start)).visibleAt(start - 60 * minute, 0L))
        assertNotNull(snapshot(match(MatchStatus.FINISHED, start)).visibleAt(start - 60 * minute, 0L))
    }

    @Test
    fun scheduled_game_without_a_start_time_is_hidden() {
        assertNull(snapshot(match(MatchStatus.SCHEDULED, null)).visibleAt(start, 60 * minute))
    }
}
