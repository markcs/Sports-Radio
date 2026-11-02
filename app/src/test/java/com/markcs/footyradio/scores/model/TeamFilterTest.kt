package com.markcs.footyradio.scores.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamFilterTest {

    private fun team(id: String) = TeamRef(name = id, shortName = id, logoUrl = null, id = id)

    private fun match(id: String, home: String, away: String) = MatchScore(
        id = id,
        home = team(home),
        away = team(away),
        homeScore = "0",
        awayScore = "0",
        clock = null,
        status = MatchStatus.LIVE,
    )

    private val snapshot = LiveScoreSnapshot(
        sportId = "nba",
        matches = listOf(
            match("g1", "LAL", "BOS"),
            match("g2", "MIA", "NYK"),
            match("g3", "BOS", "DEN"),
        ),
    )

    @Test
    fun empty_selection_keeps_every_match() {
        assertEquals(3, snapshot.filterByTeams(emptySet())!!.matches.size)
    }

    @Test
    fun keeps_matches_where_either_team_is_selected() {
        val result = snapshot.filterByTeams(setOf("BOS"))!!
        assertEquals(listOf("g1", "g3"), result.matches.map { it.id })
    }

    @Test
    fun unions_selected_teams_across_matches() {
        val result = snapshot.filterByTeams(setOf("LAL", "MIA"))!!
        assertEquals(listOf("g1", "g2"), result.matches.map { it.id })
    }

    @Test
    fun returns_null_when_no_match_involves_a_selected_team() {
        assertNull(snapshot.filterByTeams(setOf("PHX")))
    }

    @Test
    fun keeps_the_sport_id() {
        assertEquals("nba", snapshot.filterByTeams(setOf("BOS"))!!.sportId)
    }
}
