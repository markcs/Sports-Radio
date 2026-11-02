package com.markcs.footyradio.scores.format

import com.markcs.footyradio.scores.model.LiveScoreSnapshot

/**
 * One formatter per surface. This is the ticker used by NowPlaying,
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
