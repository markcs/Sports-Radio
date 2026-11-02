package com.markcs.footyradio.scores.espn

import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchScore
import com.markcs.footyradio.scores.model.MatchStatus
import com.markcs.footyradio.scores.model.TeamRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

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
    val date: String? = null,             // scheduled start, e.g. "2026-11-02T09:30Z"
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
    val id: String = "",
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

/** How long a finished game stays on the ticker. */
internal const val FINISHED_LINGER_MS = 15 * 60 * 1000L

/**
 * Maps a scoreboard to the sport-agnostic snapshot. Events without both teams are dropped,
 * and finished games are dropped once they are more than [FINISHED_LINGER_MS] past their
 * finish. Live and scheduled games are never dropped here.
 */
internal fun EspnScoreboard.toSnapshot(
    sportId: String,
    style: EspnClockStyle,
    tracker: FinishedGameTracker,
    typicalDurationMs: Long,
    nowMs: Long,
): LiveScoreSnapshot {
    val matches = events
        .mapNotNull { event -> event.toMatch(style)?.let { match -> match to event } }
        .filter { (match, event) ->
            tracker.isVisible(match.id, match.status, event.estimatedEndMs(typicalDurationMs), nowMs)
        }
        .map { (match, _) -> match }
        .sortedBy { it.status.sortRank() }
    tracker.retain(events.mapTo(HashSet()) { it.id })
    return LiveScoreSnapshot(sportId = sportId, matches = matches)
}

/**
 * Works out when each finished game ended, so the linger window can be enforced.
 *
 * If the app saw the game turn final, the finish time is the moment it was seen.
 * If the game was already final on first sight, the finish time is estimated as
 * start + typical duration. The estimate is capped at now, so a game that ended
 * early is not kept alive by its estimate.
 */
internal class FinishedGameTracker {
    private val lastStatus = HashMap<String, MatchStatus>()
    private val finishedAtMs = HashMap<String, Long>()

    fun isVisible(id: String, status: MatchStatus, estimatedEndMs: Long?, nowMs: Long): Boolean {
        val previous = lastStatus.put(id, status)
        if (status != MatchStatus.FINISHED) {
            finishedAtMs.remove(id)
            return true
        }
        val finishedAt = finishedAtMs.getOrPut(id) {
            if (previous != null && previous != MatchStatus.FINISHED) {
                nowMs
            } else {
                minOf(estimatedEndMs ?: nowMs, nowMs)
            }
        }
        return nowMs - finishedAt <= FINISHED_LINGER_MS
    }

    /** Forgets games ESPN no longer returns, so the maps do not grow all day. */
    fun retain(ids: Set<String>) {
        lastStatus.keys.retainAll(ids)
        finishedAtMs.keys.retainAll(ids)
    }
}

private fun EspnEvent.estimatedEndMs(typicalDurationMs: Long): Long? =
    parseEspnDateMs(date)?.let { it + typicalDurationMs }

/** Parses ESPN's UTC timestamps, with or without seconds, e.g. "2026-11-02T09:30Z". */
internal fun parseEspnDateMs(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
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
        startMs = parseEspnDateMs(date),
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
    id = id,
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

/*
 * DTOs for the ESPN teams endpoint (site.api.espn.com/apis/site/v2/sports/{league}/teams),
 * used by the team filter. Every field is optional, as above.
 */

@Serializable
internal data class EspnTeamsResponse(val sports: List<EspnTeamsSport> = emptyList())

@Serializable
internal data class EspnTeamsSport(val leagues: List<EspnTeamsLeague> = emptyList())

@Serializable
internal data class EspnTeamsLeague(val teams: List<EspnTeamEntry> = emptyList())

@Serializable
internal data class EspnTeamEntry(val team: EspnTeamDetail = EspnTeamDetail())

@Serializable
internal data class EspnTeamDetail(
    val id: String = "",
    val displayName: String = "",
    val abbreviation: String? = null,
    val logos: List<EspnLogo> = emptyList(),
)

@Serializable
internal data class EspnLogo(val href: String = "")

internal fun EspnTeamsResponse.toTeamRefs(): List<TeamRef> = sports
    .flatMap { it.leagues }
    .flatMap { it.teams }
    .map { it.team }
    .filter { it.id.isNotBlank() }
    .map { team ->
        TeamRef(
            id = team.id,
            name = team.displayName,
            shortName = team.abbreviation?.takeIf { it.isNotBlank() } ?: team.displayName,
            logoUrl = team.logos.firstOrNull()?.href?.takeIf { it.isNotBlank() },
        )
    }
