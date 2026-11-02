# Modular Live Scores: Phase 1a Drafts

Drafts for the revised live scoring architecture. Phase 1a adds the structured model, the pure reducer, the snapshot builder, the provider and the fixture tests. It does not change any production consumers.

## File layout

```
scores/
  model/LiveScores.kt            // structured model (sport-agnostic)
  SportsScoreProvider.kt         // provider contract
  SportsScoreManager.kt          // selects the active provider
  format/ScoreTickerFormatter.kt // presentation only
  afl/SquiggleModels.kt          // SSE event types + parser
  afl/SquiggleReducer.kt         // pure state + reduce()
  afl/SquiggleSnapshot.kt        // state -> LiveScoreSnapshot
  afl/SquiggleAflProvider.kt     // scheduling, lifecycle, I/O
test/.../afl/SquiggleReducerTest.kt
```

---

## 1. Model

```kotlin
package com.markcs.footyradio.scores.model

data class LiveScoreSnapshot(
    val sportId: String,
    val matches: List<MatchScore>,        // every relevant match today, pre-sorted by the provider
) {
    /** The match whose logos are used for artwork (the most active one). */
    val primary: MatchScore? get() = matches.firstOrNull()
}

data class MatchScore(
    val id: String,
    val home: TeamRef,
    val away: TeamRef,
    val homeScore: String?,               // String so every sport can use its own format
    val awayScore: String?,
    val clock: String?,                   // "Q4 18:22", "78'", "Q4 2:15"; null when not started
    val status: MatchStatus,
)

data class TeamRef(
    val name: String,                     // full display name
    val shortName: String,                // abbreviation, falls back to name
    val logoUrl: String?,
)

enum class MatchStatus { SCHEDULED, LIVE, PAUSED, FINISHED }
```

`isLive` has been removed. It duplicated `status`, and the old `true` default conflicted with `SCHEDULED`.

---

## 2. Provider contract and manager

```kotlin
package com.markcs.footyradio.scores

import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

interface SportsScoreProvider {
    val sportId: String
    val displayName: String

    /**
     * One shared, hot stream per provider. The provider owns its connection and
     * scheduling and starts them only while there are subscribers
     * (SharingStarted.WhileSubscribed). Because of that, there is no
     * onActiveScreensChanged() and no abbreviation parameter.
     */
    val snapshot: StateFlow<LiveScoreSnapshot?>
}

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
```

The manager is deliberately small. Its only job is routing, so it can't drift from the providers.

---

## 3. Presentation (formatter)

```kotlin
package com.markcs.footyradio.scores.format

import com.markcs.footyradio.scores.model.LiveScoreSnapshot

/**
 * The goal is one formatter per surface. This is the ticker used by NowPlaying,
 * MiniPlayer and the notification. Android Auto and share text get their own.
 */
object ScoreTickerFormatter {

    fun format(snapshot: LiveScoreSnapshot?, abbreviated: Boolean): String? {
        val matches = snapshot?.matches.orEmpty()
        if (matches.isEmpty()) return null
        return matches.joinToString(" | ") { m ->
            val home = if (abbreviated) m.home.shortName else m.home.name
            val away = if (abbreviated) m.away.shortName else m.away.name
            // "?: \"0\"" preserves the legacy display. Revisit once scheduled games are handled.
            val base = "$home (${m.homeScore ?: "0"}) v $away (${m.awayScore ?: "0"})"
            m.clock?.let { "$base $it" } ?: base
        }
    }
}
```

The abbreviation setting now lives entirely here. Flipping it re-renders the ticker without reconnecting anything.

---

## 4. AFL: event model and parser

```kotlin
package com.markcs.footyradio.scores.afl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal sealed interface SquiggleEvent {
    data class Game(
        val id: Int, val hscore: Int?, val ascore: Int?,
        val complete: Int?, val timestr: String?, val winnerTeamId: Int?,
    ) : SquiggleEvent

    data class Score(
        val gameId: Int, val hscore: Int?, val ascore: Int?,
        val complete: Int?, val timestr: String?,
    ) : SquiggleEvent

    data class Winner(val gameId: Int, val winnerTeamId: Int) : SquiggleEvent
    data class TimeStr(val gameId: Int, val timestr: String) : SquiggleEvent
    data class Complete(val gameId: Int, val complete: Int) : SquiggleEvent
}

// Wire DTOs, kept private to the parser. They mirror the existing SSE* classes.
@Serializable private data class WireGame(
    val id: Int, val hscore: Int? = null, val ascore: Int? = null,
    val complete: Int? = null, val timestr: String? = null, val winner: Int? = null,
)
@Serializable private data class WireScore(
    val gameid: Int, val complete: Int? = null, val timestr: String? = null,
    val score: WireScoreDetail? = null,
)
@Serializable private data class WireScoreDetail(val hscore: Int, val ascore: Int)
@Serializable private data class WireWinner(val gameid: Int, val winner: Int)
@Serializable private data class WireTimeStr(val gameid: Int, val timestr: String)
@Serializable private data class WireComplete(val gameid: Int, val complete: Int)

/** Returns null for unknown or malformed events. Callers log those and move on. */
internal fun parseSquiggleEvent(name: String, data: String, json: Json): SquiggleEvent? = try {
    when (name) {
        "game" -> json.decodeFromString<WireGame>(data).let {
            SquiggleEvent.Game(it.id, it.hscore, it.ascore, it.complete, it.timestr, it.winner)
        }
        "score" -> json.decodeFromString<WireScore>(data).let {
            SquiggleEvent.Score(it.gameid, it.score?.hscore, it.score?.ascore, it.complete, it.timestr)
        }
        "winner" -> json.decodeFromString<WireWinner>(data).let {
            SquiggleEvent.Winner(it.gameid, it.winner)
        }
        "timestr" -> json.decodeFromString<WireTimeStr>(data).let {
            SquiggleEvent.TimeStr(it.gameid, it.timestr)
        }
        "complete" -> json.decodeFromString<WireComplete>(data).let {
            SquiggleEvent.Complete(it.gameid, it.complete)
        }
        else -> null
    }
} catch (e: Exception) {
    null
}
```

The current `SSEGameEvent` and related classes can be deleted once this lands.

---

## 5. AFL: pure reducer

```kotlin
package com.markcs.footyradio.scores.afl

import com.markcs.footyradio.data.SquiggleGame

/**
 * Everything the provider knows. Pure data, no clocks, no I/O.
 * winnerSeenAtMs records when we first saw a game finish. The 15-minute linger
 * is measured from that moment, so a duplicate "winner" event can't reset it.
 */
internal data class SquiggleState(
    val games: Map<Int, SquiggleGame> = emptyMap(),
    val winnerSeenAtMs: Map<Int, Long> = emptyMap(),
) {
    fun gamesOn(day: String): List<SquiggleGame> =
        games.values.filter { it.date?.startsWith(day) == true }

    /** True only when there is at least one game today and all of them are final. */
    fun allFinished(day: String): Boolean {
        val today = gamesOn(day)
        return today.isNotEmpty() && today.all { (it.complete ?: 0) >= 100 }
    }
}

internal const val WINNER_LINGER_MS = 15 * 60 * 1000L

internal fun List<SquiggleGame>.indexById(): Map<Int, SquiggleGame> =
    mapNotNull { g -> g.id?.let { it to g } }.toMap()

internal fun SquiggleState.reduce(event: SquiggleEvent, nowMs: Long): SquiggleState = when (event) {
    is SquiggleEvent.Game -> {
        val updated = updateGame(event.id) { g ->
            g.copy(
                hscore = event.hscore ?: g.hscore,
                ascore = event.ascore ?: g.ascore,
                complete = event.complete ?: g.complete,
                timestr = event.timestr ?: g.timestr,
                winner = event.winnerTeamId?.let { winnerName(g, it) } ?: g.winner,
            )
        }
        if (event.complete == 100) updated.withWinnerSeen(event.id, nowMs) else updated
    }

    is SquiggleEvent.Score -> updateGame(event.gameId) { g ->
        g.copy(
            hscore = event.hscore ?: g.hscore,
            ascore = event.ascore ?: g.ascore,
            complete = event.complete ?: g.complete,
            timestr = event.timestr ?: g.timestr,
        )
    }

    is SquiggleEvent.Winner -> updateGame(event.gameId) { g ->
        g.copy(winner = winnerName(g, event.winnerTeamId) ?: g.winner, complete = 100)
    }.withWinnerSeen(event.gameId, nowMs)

    is SquiggleEvent.TimeStr -> updateGame(event.gameId) { it.copy(timestr = event.timestr) }

    is SquiggleEvent.Complete -> {
        val updated = updateGame(event.gameId) { it.copy(complete = event.complete) }
        if (event.complete == 100) updated.withWinnerSeen(event.gameId, nowMs) else updated
    }
}

private fun SquiggleState.updateGame(id: Int, transform: (SquiggleGame) -> SquiggleGame): SquiggleState =
    games[id]?.let { copy(games = games + (id to transform(it))) } ?: this

private fun SquiggleState.withWinnerSeen(gameId: Int, nowMs: Long): SquiggleState =
    if (gameId in winnerSeenAtMs) this
    else copy(winnerSeenAtMs = winnerSeenAtMs + (gameId to nowMs))

private fun winnerName(g: SquiggleGame, winnerTeamId: Int): String? = when (winnerTeamId) {
    g.hteamid -> g.hteam
    g.ateamid -> g.ateam
    else -> null
}
```

---

## 6. AFL: state to snapshot

```kotlin
package com.markcs.footyradio.scores.afl

import com.markcs.footyradio.data.SquiggleGame
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchScore
import com.markcs.footyradio.scores.model.MatchStatus
import com.markcs.footyradio.scores.model.TeamRef
import java.text.SimpleDateFormat
import java.util.Locale

private const val TEAM_LOGO_BASE = "https://markcs.github.io/Sports-Radio/team-logos"

internal fun SquiggleState.toSnapshot(
    day: String,
    nowMs: Long,
    shortNames: Map<String, String>,      // lowercased full name -> abbreviation, from teams.json
): LiveScoreSnapshot {
    val matches = gamesOn(day)
        .filter { isVisible(it, nowMs) }
        .sortedByDescending { it.complete ?: 0 }     // same ordering as the legacy ticker
        .map { it.toMatchScore(shortNames) }
    return LiveScoreSnapshot(sportId = "afl", matches = matches)
}

private fun SquiggleState.isVisible(g: SquiggleGame, nowMs: Long): Boolean {
    val complete = g.complete ?: 0
    return when {
        complete <= 0 -> false                                    // scheduled
        complete < 100 -> true                                    // live
        else -> g.id?.let { winnerSeenAtMs[it] }
            ?.let { nowMs - it < WINNER_LINGER_MS } ?: false     // finished, lingering
    }
}

private fun SquiggleGame.toMatchScore(shortNames: Map<String, String>): MatchScore = MatchScore(
    id = id?.toString().orEmpty(),
    home = teamRef(hteam, shortNames),
    away = teamRef(ateam, shortNames),
    homeScore = hscore?.toString(),
    awayScore = ascore?.toString(),
    clock = timestr?.takeIf { it.isNotBlank() },
    // Squiggle does not expose PAUSED. Treat quarter breaks as LIVE.
    status = if ((complete ?: 0) >= 100) MatchStatus.FINISHED else MatchStatus.LIVE,
)

private fun teamRef(name: String?, shortNames: Map<String, String>): TeamRef {
    val full = name ?: "Unknown"
    return TeamRef(
        name = full,
        shortName = shortNames[full.lowercase()] ?: full,
        logoUrl = name?.let { "$TEAM_LOGO_BASE/${it.replace(" ", "%20")}.png" },
    )
}

/** Squiggle `localtime` is "yyyy-MM-dd HH:mm:ss" in device-local time, as in the legacy code. */
internal fun SquiggleGame.startMs(): Long? =
    localtime?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(it)?.time }
```

---

## 7. AFL: provider

```kotlin
package com.markcs.footyradio.scores.afl

import com.markcs.footyradio.data.SquiggleGame
import com.markcs.footyradio.scores.SportsScoreProvider
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Injectable time source. Tests pin both the instant and the calendar day. */
interface ScoreClock {
    fun nowMs(): Long
    fun dayPrefix(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs()))
    fun year(): Int = Calendar.getInstance().apply { timeInMillis = nowMs() }.get(Calendar.YEAR)
}

/** Network boundary. The real implementation wraps Ktor. Tests supply fixtures. */
interface SquiggleApi {
    suspend fun fetchGames(year: Int): List<SquiggleGame>?
    fun sseEvents(): Flow<SquiggleEvent>
}

class SquiggleAflProvider(
    private val api: SquiggleApi,
    private val clock: ScoreClock,
    private val shortNames: StateFlow<Map<String, String>>,
    scope: CoroutineScope,
) : SportsScoreProvider {

    override val sportId = "afl"
    override val displayName = "AFL"

    override val snapshot: StateFlow<LiveScoreSnapshot?> = flow {
        var state = SquiggleState()
        var fetchedDay: String? = null

        while (currentCoroutineContext().isActive) {
            val day = clock.dayPrefix()

            // Refetch the schedule when the calendar day changes. The legacy code
            // fetched once per app session, so an app left open overnight never
            // learned the next day's fixtures.
            if (day != fetchedDay) {
                state = SquiggleState(games = fetchInitialGames(clock.year()))
                fetchedDay = day
            }

            emit(state.toSnapshot(day, clock.nowMs(), shortNames.value))

            val today = state.gamesOn(day)
            val anyLive = today.any { (it.complete ?: 0) in 1..99 }

            if (!anyLive) {
                // Sleep until SSE_LEAD_MS before the next start, but never longer than
                // IDLE_RECHECK_MS. This avoids the legacy code's effectively infinite sleep
                // when no games are scheduled, and re-evaluates the day regularly.
                val nextStart = today.mapNotNull { it.startMs() }
                    .filter { it > clock.nowMs() }
                    .minOrNull()
                val wakeAt = nextStart?.minus(SSE_LEAD_MS) ?: (clock.nowMs() + IDLE_RECHECK_MS)
                delay((wakeAt - clock.nowMs()).coerceIn(MIN_WAIT_MS, IDLE_RECHECK_MS))
                continue
            }

            try {
                // Each SSE event is reduced into state and emitted as a snapshot.
                // transformWhile stops collection once everything is final. Stopping the
                // collection cancels the underlying sse block, which closes the connection.
                api.sseEvents()
                    .scan(state) { acc, ev -> acc.reduce(ev, clock.nowMs()) }
                    .onEach { state = it }
                    .transformWhile { s ->
                        emit(s.toSnapshot(day, clock.nowMs(), shortNames.value))
                        !s.allFinished(day)
                    }
                    .collect { snap -> emit(snap) }

                if (!state.allFinished(day)) delay(SSE_RETRY_MS)   // server closed early
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(SSE_RETRY_MS)
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private suspend fun fetchInitialGames(year: Int): Map<Int, SquiggleGame> {
        while (true) {
            api.fetchGames(year)?.let { return it.indexById() }
            delay(INITIAL_RETRY_MS)
        }
    }

    private companion object {
        const val SSE_LEAD_MS = 5 * 60 * 1000L
        const val IDLE_RECHECK_MS = 10 * 60 * 1000L
        const val MIN_WAIT_MS = 30_000L
        const val SSE_RETRY_MS = 30_000L
        const val INITIAL_RETRY_MS = 30_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

The Ktor adapter for `SquiggleApi.sseEvents()` is short. Emitting inside `client.sse { }` is legal because it runs in the same coroutine:

```kotlin
override fun sseEvents(): Flow<SquiggleEvent> = flow {
    client.sse("https://sse.squiggle.com.au/events") {
        incoming.collect { ev ->
            parseSquiggleEvent(ev.event.orEmpty(), ev.data.orEmpty(), json)?.let { emit(it) }
        }
    }
}
```

---

## 8. Tests with fixtures

These tests are the Phase 1a baseline. The golden string was derived by hand from the legacy `formatScores` format string, `"$hTeam ($hScore) v $aTeam ($aScore) $timeStr"`, sorted by `complete` descending and joined with `" | "`. Before merging, run the legacy `formatScores` against the same fixture and confirm it produces the same string.

```kotlin
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
        assertEquals(listOf("2", "4"), snap.matches.map { it.id })   // 1 scheduled, 3 unannounced, 5 other day
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
        // Same snapshot, full names: no provider or stream involvement.
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
```

---

## Behaviour changes versus the current code

These are the intended differences. Everything else should match the legacy output exactly.

1. **One SSE connection per app, not two.** The provider is hot and shared, so `AudioService` and `PlayerViewModel` subscribe to the same stream.
2. **The abbreviation toggle no longer restarts the connection.** It only changes the formatter.
3. **Schedule refetch on day change.** Long-running sessions pick up the next day's fixtures.
4. **No effectively infinite sleep when nothing is scheduled.** The legacy `timeToFirstGame - 5min` could be enormous when there were no games. The wait is now capped at 10 minutes and re-evaluated.
5. **Duplicate winner events don't reset the linger timer.** `winnerSeenAtMs` is set only once per game.
6. **Winner name fallback is unified.** The legacy `game` and `winner` handlers differed slightly when the winner ID matched neither team. Both now keep the existing name.

---

## Things to verify before Phase 1b

- **Timezone of Squiggle's `date` field.** The day filter uses `date.startsWith(dayPrefix)` with the device's local day, which is what the legacy code does. Check that `date` is in the same zone as `localtime` before relying on it, especially for late-night games that cross midnight. A user travelling to another timezone would see the wrong day.
- **Squiggle's `complete` semantics.** The code treats 100 as final and 1–99 as live, as the legacy code assumes. Confirm against a real game day, since a stoppage or delayed start may show a different value.
- **Ktor SSE closing.** Confirm that cancelling the `transformWhile` collection closes the HTTP connection and doesn't leave it open until the server times out.

---

## Suggested next step

Start with Phase 1a: add the model, reducer, snapshot builder and the test file, then run the legacy `formatScores` on the same fixture to confirm the golden string. That step touches no production consumers, so it's safe to merge on its own.

After that:
- **1b:** move `PlayerViewModel` and `AudioService` onto `SportsScoreManager.snapshot`.
- **1c:** replace the string matching in `applyMetadata` and `updateDisplayMetadata` with structured extras.
