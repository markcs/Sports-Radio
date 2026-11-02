package com.markcs.footyradio.scores.espn

import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchScore
import com.markcs.footyradio.scores.model.MatchStatus
import com.markcs.footyradio.scores.model.TeamRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/*
 * DTOs for the ESPN public scoreboard (site.api.espn.com/apis/site/v2/sports/{league}/scoreboard).
 * The API is undocumented. Every field is optional so an odd payload degrades to
 * "no game" rather than a crash.
 */

@Serializable
internal data class EspnScoreboard(val events: List<EspnEvent> = emptyList())

@Serializable
internal data class EspnEvent(
    val id: String = "",
    val competitions: List<EspnCompetition> = emptyList(),
    val status: EspnStatus = EspnStatus(),
)

@Serializable
internal data class EspnCompetition(val competitors: List<EspnCompetitor> = emptyList())

@Serializable
internal data class EspnCompetitor(
    val homeAway: String = "",
    val score: JsonElement? = null,       // a string such as "108"; null before the game
    val team: EspnTeam = EspnTeam(),
)

@Serializable
internal data class EspnTeam(
    val displayName: String = "",
    val abbreviation: String? = null,
    val logo: String? = null,
)

@Serializable
internal data class EspnStatus(
    val displayClock: String? = null,     // "2:15" (NBA) or "78'" (soccer)
    val period: Int = 0,
    val type: EspnStatusType = EspnStatusType(),
)

@Serializable
internal data class EspnStatusType(
    val state: String = "pre",            // "pre" | "in" | "post"
    val name: String = "",                // e.g. "STATUS_HALFTIME"
)

/** How the period is shown next to the clock. */
internal enum class EspnClockStyle { PERIOD_AND_CLOCK, CLOCK_ONLY }

/** Maps a scoreboard to the sport-agnostic snapshot. Events without both teams are dropped. */
internal fun EspnScoreboard.toSnapshot(sportId: String, style: EspnClockStyle): LiveScoreSnapshot {
    val matches = events
        .mapNotNull { it.toMatch(style) }
        .sortedBy { it.status.sortRank() }
    return LiveScoreSnapshot(sportId = sportId, matches = matches)
}

private fun EspnEvent.toMatch(style: EspnClockStyle): MatchScore? {
    val competitors = competitions.firstOrNull()?.competitors.orEmpty()
    val home = competitors.firstOrNull { it.homeAway == "home" } ?: return null
    val away = competitors.firstOrNull { it.homeAway == "away" } ?: return null
    val matchStatus = status.toMatchStatus()

    return MatchScore(
        id = id,
        home = home.team.toRef(),
        away = away.team.toRef(),
        homeScore = if (matchStatus == MatchStatus.SCHEDULED) null else home.score.scoreText(),
        awayScore = if (matchStatus == MatchStatus.SCHEDULED) null else away.score.scoreText(),
        clock = clockText(matchStatus, style),
        status = matchStatus,
    )
}

private fun EspnStatus.toMatchStatus(): MatchStatus = when (type.state) {
    "in" -> if (type.name == "STATUS_HALFTIME") MatchStatus.PAUSED else MatchStatus.LIVE
    "post" -> MatchStatus.FINISHED
    else -> MatchStatus.SCHEDULED
}

private fun EspnEvent.clockText(status: MatchStatus, style: EspnClockStyle): String? {
    val clock = this.status.displayClock?.takeIf { it.isNotBlank() }
    val period = this.status.period.takeIf { it > 0 }
    return when (status) {
        MatchStatus.LIVE -> when (style) {
            EspnClockStyle.PERIOD_AND_CLOCK -> listOfNotNull(period?.let { "Q$it" }, clock)
                .joinToString(" ")
                .ifBlank { null }
            EspnClockStyle.CLOCK_ONLY -> clock
        }
        MatchStatus.PAUSED -> if (style == EspnClockStyle.CLOCK_ONLY) "HT" else "Half"
        MatchStatus.SCHEDULED, MatchStatus.FINISHED -> null
    }
}

private fun EspnTeam.toRef(): TeamRef = TeamRef(
    name = displayName,
    shortName = abbreviation?.takeIf { it.isNotBlank() } ?: displayName,
    logoUrl = logo?.takeIf { it.isNotBlank() },
)

private fun JsonElement?.scoreText(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun MatchStatus.sortRank(): Int = when (this) {
    MatchStatus.LIVE -> 0
    MatchStatus.PAUSED -> 1
    MatchStatus.SCHEDULED -> 2
    MatchStatus.FINISHED -> 3
}
