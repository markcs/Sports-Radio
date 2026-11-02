package com.markcs.footyradio.scores.espn

import com.markcs.footyradio.scores.model.MatchStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EspnScoreboardTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun parse(body: String) = json.decodeFromString(EspnScoreboard.serializer(), body)

    @Test
    fun `live game shows quarter and clock, scheduled and final have no clock`() {
        val snap = parse(FIXTURE).toSnapshot("nba", EspnClockStyle.PERIOD_AND_CLOCK)

        assertEquals(listOf("401", "402", "403"), snap.matches.map { it.id })

        val live = snap.matches[0]
        assertEquals(MatchStatus.LIVE, live.status)
        assertEquals("BOS", live.home.shortName)
        assertEquals("112", live.homeScore)
        assertEquals("LAL", live.away.shortName)
        assertEquals("108", live.awayScore)
        assertEquals("Q4 2:15", live.clock)
        assertEquals("https://a.espncdn.com/bos.png", live.home.logoUrl)

        val scheduled = snap.matches[1]
        assertEquals(MatchStatus.SCHEDULED, scheduled.status)
        assertNull(scheduled.homeScore)
        assertNull(scheduled.clock)

        val final = snap.matches[2]
        assertEquals(MatchStatus.FINISHED, final.status)
        assertEquals("DEN", final.home.shortName)
        assertNull(final.clock)
        // No logo in the fixture, so it should be null rather than an empty string.
        assertNull(final.home.logoUrl)
    }

    @Test
    fun `clock-only style shows the bare clock for soccer`() {
        val live = parse(FIXTURE).toSnapshot("epl", EspnClockStyle.CLOCK_ONLY).matches.first()
        assertEquals("2:15", live.clock)
    }

    @Test
    fun `halftime maps to paused`() {
        val body = """
            {"events":[{"id":"9","status":{"period":2,"type":{"state":"in","name":"STATUS_HALFTIME"}},
              "competitions":[{"competitors":[
                {"homeAway":"home","score":"50","team":{"displayName":"Home","abbreviation":"HOM"}},
                {"homeAway":"away","score":"48","team":{"displayName":"Away","abbreviation":"AWY"}}]}]}]}
        """.trimIndent()
        val match = parse(body).toSnapshot("nba", EspnClockStyle.PERIOD_AND_CLOCK).matches.single()
        assertEquals(MatchStatus.PAUSED, match.status)
        assertEquals("Half", match.clock)
    }

    @Test
    fun `empty or unexpected payload gives an empty snapshot`() {
        assertEquals(emptyList<Any>(), parse("""{"events":[]}""").toSnapshot("nba", EspnClockStyle.PERIOD_AND_CLOCK).matches)
        assertEquals(emptyList<Any>(), parse("{}").toSnapshot("nba", EspnClockStyle.PERIOD_AND_CLOCK).matches)
    }

    private companion object {
        // Four events. "404" is missing its away team and must be dropped.
        val FIXTURE = """
            {"events":[
              {"id":"401",
               "status":{"displayClock":"2:15","period":4,"type":{"state":"in","name":"STATUS_IN_PROGRESS"}},
               "competitions":[{"competitors":[
                 {"homeAway":"away","score":"108","team":{"displayName":"Los Angeles Lakers","abbreviation":"LAL"}},
                 {"homeAway":"home","score":"112","team":{"displayName":"Boston Celtics","abbreviation":"BOS","logo":"https://a.espncdn.com/bos.png"}}
               ]}]},
              {"id":"402",
               "status":{"displayClock":"7:30","period":0,"type":{"state":"pre","name":"STATUS_SCHEDULED"}},
               "competitions":[{"competitors":[
                 {"homeAway":"home","score":"0","team":{"displayName":"Miami Heat","abbreviation":"MIA"}},
                 {"homeAway":"away","score":"0","team":{"displayName":"New York Knicks","abbreviation":"NY"}}
               ]}]},
              {"id":"403",
               "status":{"displayClock":"0.0","period":4,"type":{"state":"post","name":"STATUS_FINAL"}},
               "competitions":[{"competitors":[
                 {"homeAway":"home","score":"99","team":{"displayName":"Denver Nuggets","abbreviation":"DEN"}},
                 {"homeAway":"away","score":"95","team":{"displayName":"Phoenix Suns","abbreviation":"PHX"}}
               ]}]},
              {"id":"404",
               "status":{"type":{"state":"in","name":"STATUS_IN_PROGRESS"}},
               "competitions":[{"competitors":[{"homeAway":"home","team":{"displayName":"Only Home"}}]}]}
            ]}
        """.trimIndent()
    }
}
