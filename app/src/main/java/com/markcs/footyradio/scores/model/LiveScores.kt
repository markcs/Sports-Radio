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
