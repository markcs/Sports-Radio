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
