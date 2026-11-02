package com.markcs.footyradio.scores.afl

import com.markcs.footyradio.data.SquiggleGame
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchScore
import com.markcs.footyradio.scores.model.MatchStatus
import com.markcs.footyradio.scores.model.TeamRef
import java.text.SimpleDateFormat
import java.util.Locale

internal const val TEAM_LOGO_BASE = "https://markcs.github.io/Sports-Radio/team-logos"

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
        complete <= 0 -> true                                     // scheduled; the start window decides
        complete < 100 -> true                                    // live
        else -> g.id?.let { winnerSeenAtMs[it] }
            ?.let { nowMs - it < WINNER_LINGER_MS } ?: false     // finished, lingering
    }
}

private fun SquiggleGame.toMatchScore(shortNames: Map<String, String>): MatchScore = MatchScore(
    id = id?.toString().orEmpty(),
    home = teamRef(hteam, hteamid, shortNames),
    away = teamRef(ateam, ateamid, shortNames),
    homeScore = hscore?.toString(),
    awayScore = ascore?.toString(),
    clock = timestr?.takeIf { it.isNotBlank() },
    // Squiggle does not expose PAUSED. Treat quarter breaks as LIVE.
    status = when {
        (complete ?: 0) >= 100 -> MatchStatus.FINISHED
        (complete ?: 0) <= 0 -> MatchStatus.SCHEDULED
        else -> MatchStatus.LIVE
    },
    startMs = startMs(),
)

private fun teamRef(name: String?, id: Int?, shortNames: Map<String, String>): TeamRef {
    val full = name ?: "Unknown"
    return TeamRef(
        id = id?.toString().orEmpty(),
        name = full,
        shortName = shortNames[full.lowercase()] ?: full,
        logoUrl = name?.let { "$TEAM_LOGO_BASE/${it.replace(" ", "%20")}.png" },
    )
}

/** Squiggle `localtime` is "yyyy-MM-dd HH:mm:ss" in device-local time, as in the legacy code. */
internal fun SquiggleGame.startMs(): Long? =
    localtime?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(it)?.time }
