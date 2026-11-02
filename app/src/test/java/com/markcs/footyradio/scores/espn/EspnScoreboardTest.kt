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

    /** Snapshot with a fresh tracker, as on a first poll. */
    private fun EspnScoreboard.snap(style: EspnClockStyle, nowMs: Long = START + 1_000L) =
        toSnapshot("nba", style, FinishedGameTracker(), DURATION_MS, nowMs)

    @Test
    fun `live game shows quarter and clock, scheduled and final have no clock`() {
        val snap = parse(FIXTURE).snap(EspnClockStyle.PERIOD_AND_CLOCK)

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
        val live = parse(FIXTURE).snap(EspnClockStyle.CLOCK_ONLY).matches.first()
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
        val match = parse(body).snap(EspnClockStyle.PERIOD_AND_CLOCK).matches.single()
        assertEquals(MatchStatus.PAUSED, match.status)
        assertEquals("Half", match.clock)
    }

    @Test
    fun `empty or unexpected payload gives an empty snapshot`() {
        assertEquals(emptyList<Any>(), parse("""{"events":[]}""").snap(EspnClockStyle.PERIOD_AND_CLOCK).matches)
        assertEquals(emptyList<Any>(), parse("{}").snap(EspnClockStyle.PERIOD_AND_CLOCK).matches)
    }

    @Test
    fun `game drops off 15 minutes after the app saw it finish`() {
        val tracker = FinishedGameTracker()
        fun at(nowMs: Long, state: String, name: String) = board(event("7", state, name))
            .toSnapshot("nba", EspnClockStyle.PERIOD_AND_CLOCK, tracker, DURATION_MS, nowMs)
            .matches.map { it.id }

        assertEquals(listOf("7"), at(START + 1 * HOUR, "in", "STATUS_IN_PROGRESS"))
        val finish = START + 3 * HOUR
        assertEquals(listOf("7"), at(finish, "post", "STATUS_FINAL"))
        assertEquals(listOf("7"), at(finish + 14 * MINUTE, "post", "STATUS_FINAL"))
        assertEquals(emptyList<String>(), at(finish + 16 * MINUTE, "post", "STATUS_FINAL"))
    }

    @Test
    fun `game already final on first sight is dropped once its estimated end plus 15 minutes has passed`() {
        // Started 4 hours ago. The estimated end is 1 hour ago, so it should be hidden.
        val stale = board(event("8", "post", "STATUS_FINAL", date = iso(START - 4 * HOUR)))
            .snap(EspnClockStyle.PERIOD_AND_CLOCK, nowMs = START)
        assertEquals(emptyList<String>(), stale.matches.map { it.id })
    }

    @Test
    fun `estimated finish is capped at now so an early finish is not kept alive`() {
        // Started 2 hours before now. The estimated end is 1 hour in the future, so it is
        // capped at now: visible at once, hidden 15 minutes later.
        // One tracker across both polls, as the provider keeps one for its lifetime.
        val tracker = FinishedGameTracker()
        val scoreboard = board(event("9", "post", "STATUS_FINAL", date = iso(START - 2 * HOUR)))
        fun at(nowMs: Long) = scoreboard
            .toSnapshot("nba", EspnClockStyle.PERIOD_AND_CLOCK, tracker, DURATION_MS, nowMs)
            .matches.map { it.id }

        assertEquals(listOf("9"), at(START))
        assertEquals(emptyList<String>(), at(START + 16 * MINUTE))
    }

    @Test
    fun `live games are never dropped by the linger rule`() {
        val snap = board(event("5", "in", "STATUS_IN_PROGRESS", date = iso(START - 10 * HOUR)))
            .snap(EspnClockStyle.PERIOD_AND_CLOCK, nowMs = START + 10 * HOUR)
        assertEquals(listOf("5"), snap.matches.map { it.id })
    }

    @Test
    fun `parses ESPN timestamps with and without seconds`() {
        assertEquals(START, parseEspnDateMs("2026-11-02T10:00Z"))
        assertEquals(START, parseEspnDateMs("2026-11-02T10:00:00Z"))
        assertNull(parseEspnDateMs("not a date"))
        assertNull(parseEspnDateMs(null))
    }

    private fun board(vararg events: String) = parse("""{"events":[${events.joinToString(",")}]}""")

    private fun event(
        id: String,
        state: String,
        name: String,
        date: String = iso(START),
    ): String = """
        {"id":"$id","date":"$date",
         "status":{"period":4,"type":{"state":"$state","name":"$name"}},
         "competitions":[{"competitors":[
           {"homeAway":"home","score":"100","team":{"displayName":"Home Team","abbreviation":"HOM"}},
           {"homeAway":"away","score":"90","team":{"displayName":"Away Team","abbreviation":"AWY"}}
         ]}]}
    """.trimIndent()

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DURATION_MS = 3 * HOUR
        val START = parseEspnDateMs("2026-11-02T10:00Z")!!

        fun iso(ms: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(ms))

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
