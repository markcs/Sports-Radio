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
    val startMs: Long? = null,            // scheduled start in epoch ms, when the provider knows it
)

data class TeamRef(
    val name: String,                     // full display name
    val shortName: String,                // abbreviation, falls back to name
    val logoUrl: String?,
    val id: String = "",                  // provider's stable team id, used by the team filter
)

enum class MatchStatus { SCHEDULED, LIVE, PAUSED, FINISHED }

/**
 * Keeps only matches where at least one team is in [selectedTeamIds]. An empty selection
 * means "all teams". Returns null when nothing is left, so the ticker is hidden.
 */
fun LiveScoreSnapshot.filterByTeams(selectedTeamIds: Set<String>): LiveScoreSnapshot? {
    if (selectedTeamIds.isEmpty()) return this
    val kept = matches.filter { it.home.id in selectedTeamIds || it.away.id in selectedTeamIds }
    return if (kept.isEmpty()) null else copy(matches = kept)
}

/**
 * Keeps live, paused and finished matches, and scheduled matches whose start is within
 * [windowMs] of [nowMs]. A window of 0 shows a scheduled match from its start time exactly.
 * A scheduled match with no known start time is hidden. Returns null when nothing is left.
 */
fun LiveScoreSnapshot.visibleAt(nowMs: Long, windowMs: Long): LiveScoreSnapshot? {
    val kept = matches.filter { m ->
        m.status != MatchStatus.SCHEDULED ||
            (m.startMs != null && m.startMs - nowMs <= windowMs)
    }
    return if (kept.isEmpty()) null else copy(matches = kept)
}
