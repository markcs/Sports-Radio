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
internal interface SquiggleApi {
    suspend fun fetchGames(year: Int): List<SquiggleGame>?
    fun sseEvents(): Flow<SquiggleEvent>
}

internal class SquiggleAflProvider(
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
