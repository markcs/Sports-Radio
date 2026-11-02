package com.markcs.footyradio.scores.afl

import com.markcs.footyradio.data.SquiggleResponse
import com.markcs.footyradio.scores.format.ScoreTickerFormatter
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SquiggleReducerTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val day = "2026-08-29"
    private val t0 = 1_000_000_000_000L
    private val fifteenMin = 15 * 60 * 1000L
    private val shortNames = mapOf("essendon" to "ESS", "hawthorn" to "HAW")

    /**
     * Games:
     *  1  Collingwood v Melbourne   scheduled (complete 0)            -> hidden
     *  2  Essendon v Hawthorn       live, Q4 18:22, 84-78 (complete 88)
     *  3  Carlton v Collingwood     finished (complete 100), no winner event yet -> hidden
     *  4  Geelong v Sydney Swans    live, Q2 03:10, 45-40 (complete 52)
     *  5  Fremantle v Brisbane      yesterday (other day)             -> hidden
     */
    private val fixture = """
        {"games":[
          {"id":1,"year":2026,"hteam":"Collingwood","ateam":"Melbourne","hteamid":1,"ateamid":11,
           "date":"2026-08-29 19:25:00","localtime":"2026-08-29 19:25:00","complete":0},
          {"id":2,"year":2026,"hteam":"Essendon","ateam":"Hawthorn","hteamid":3,"ateamid":12,
           "date":"2026-08-29 13:10:00","localtime":"2026-08-29 13:10:00","complete":88,
           "hscore":84,"ascore":78,"timestr":"Q4 18:22","someFieldWeDoNotModel":true},
          {"id":3,"year":2026,"hteam":"Carlton","ateam":"Collingwood","hteamid":4,"ateamid":1,
           "date":"2026-08-29 13:10:00","localtime":"2026-08-29 13:10:00","complete":100,
           "hscore":90,"ascore":88,"timestr":"Full Time"},
          {"id":4,"year":2026,"hteam":"Geelong","ateam":"Sydney Swans","hteamid":5,"ateamid":16,
           "date":"2026-08-29 16:40:00","localtime":"2026-08-29 16:40:00","complete":52,
           "hscore":45,"ascore":40,"timestr":"Q2 03:10"},
          {"id":5,"year":2026,"hteam":"Fremantle","ateam":"Brisbane","hteamid":6,"ateamid":2,
           "date":"2026-08-28 19:40:00","localtime":"2026-08-28 19:40:00","complete":50,
           "hscore":10,"ascore":12,"timestr":"Q2"}
        ]}
    """.trimIndent()

    private fun initialState(): SquiggleState {
        val response = json.decodeFromString<SquiggleResponse>(fixture)
        return SquiggleState(games = response.games.indexById())
    }

    @Test
    fun parses_rest_games_ignoring_unknown_fields() {
        val s = initialState()
        assertEquals(5, s.games.size)
        assertEquals("Essendon", s.games.getValue(2).hteam)
        assertEquals(84, s.games.getValue(2).hscore)
    }

    @Test
    fun snapshot_excludes_scheduled_other_day_and_unannounced_finished_games() {
        val snap = initialState().toSnapshot(day, t0, shortNames)
        assertEquals(listOf("2", "4"), snap.matches.map { it.id })
    }

    @Test
    fun snapshot_sorts_by_progress_descending() {
        val snap = initialState().toSnapshot(day, t0, shortNames)
        assertEquals("2", snap.matches.first().id)                   // complete 88 before 52
    }

    @Test
    fun legacy_ticker_string_is_preserved_byte_for_byte() {
        val text = ScoreTickerFormatter.format(
            initialState().toSnapshot(day, t0, shortNames),
            abbreviated = false,
        )
        assertEquals(
            "Essendon (84) v Hawthorn (78) Q4 18:22 | Geelong (45) v Sydney Swans (40) Q2 03:10",
            text,
        )
    }

    @Test
    fun abbreviation_is_a_display_choice_not_a_data_change() {
        val snap = initialState().toSnapshot(day, t0, shortNames)
        assertEquals(
            "ESS (84) v HAW (78) Q4 18:22 | Geelong (45) v Sydney Swans (40) Q2 03:10",
            ScoreTickerFormatter.format(snap, abbreviated = true),
        )
        assertEquals("Essendon", snap.matches.first().home.name)
    }

    @Test
    fun finished_game_lingers_for_15_minutes_after_winner_event() {
        val s = initialState().reduce(SquiggleEvent.Winner(gameId = 3, winnerTeamId = 1), nowMs = t0)
        assertEquals("Collingwood", s.games.getValue(3).winner)
        assertTrue(s.toSnapshot(day, t0 + fifteenMin - 1, shortNames).matches.any { it.id == "3" })
        assertFalse(s.toSnapshot(day, t0 + fifteenMin, shortNames).matches.any { it.id == "3" })
    }

    @Test
    fun duplicate_winner_event_does_not_reset_linger_timer() {
        val first = initialState().reduce(SquiggleEvent.Winner(3, 1), nowMs = t0)
        val second = first.reduce(SquiggleEvent.Winner(3, 1), nowMs = t0 + 10 * 60_000L)
        assertFalse(second.toSnapshot(day, t0 + fifteenMin, shortNames).matches.any { it.id == "3" })
    }

    @Test
    fun score_event_updates_scores_without_starting_linger_timer() {
        val s = initialState().reduce(
            SquiggleEvent.Score(gameId = 2, hscore = 86, ascore = 78, complete = 90, timestr = "Q4 15:00"),
            nowMs = t0,
        )
        val m = s.toSnapshot(day, t0, shortNames).matches.first()
        assertEquals("86", m.homeScore)
        assertEquals("Q4 15:00", m.clock)
        assertNull(s.winnerSeenAtMs[2])
    }

    @Test
    fun unknown_or_malformed_sse_events_parse_to_null() {
        assertNull(parseSquiggleEvent("mystery", "{}", json))
        assertNull(parseSquiggleEvent("score", "not json", json))
    }

    @Test
    fun logo_urls_encode_spaces() {
        val m = initialState().toSnapshot(day, t0, shortNames).matches.last()
        assertEquals(
            "https://markcs.github.io/Sports-Radio/team-logos/Sydney%20Swans.png",
            m.away.logoUrl,
        )
    }
}
