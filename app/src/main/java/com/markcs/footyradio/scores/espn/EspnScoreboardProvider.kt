package com.markcs.footyradio.scores.espn

import com.markcs.footyradio.scores.SportsScoreProvider
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Polls an ESPN league scoreboard. It polls every [LIVE_POLL_MS] while a game is in
 * progress, every [IDLE_POLL_MS] otherwise, and retries after [ERROR_RETRY_MS] on failure.
 * The loop runs only while the snapshot has subscribers.
 */
internal class EspnScoreboardProvider(
    override val sportId: String,
    override val displayName: String,
    private val leaguePath: String,
    private val clockStyle: EspnClockStyle,
    private val fetch: suspend (leaguePath: String) -> EspnScoreboard?,
    scope: CoroutineScope,
) : SportsScoreProvider {

    override val snapshot: StateFlow<LiveScoreSnapshot?> = flow {
        while (true) {
            val board = fetch(leaguePath)
            if (board == null) {
                delay(ERROR_RETRY_MS)
                continue
            }
            val snap = board.toSnapshot(sportId, clockStyle)
            emit(snap)
            delay(if (snap.hasLiveGame()) LIVE_POLL_MS else IDLE_POLL_MS)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private fun LiveScoreSnapshot.hasLiveGame(): Boolean =
        matches.any { it.status == MatchStatus.LIVE || it.status == MatchStatus.PAUSED }

    private companion object {
        const val LIVE_POLL_MS = 30_000L
        const val IDLE_POLL_MS = 10 * 60_000L
        const val ERROR_RETRY_MS = 60_000L
    }
}
