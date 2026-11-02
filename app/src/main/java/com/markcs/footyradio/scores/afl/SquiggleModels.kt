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

@Serializable internal data class SquiggleTeam(val id: Int, val name: String)

@Serializable internal data class SquiggleTeamsResponse(val teams: List<SquiggleTeam> = emptyList())
