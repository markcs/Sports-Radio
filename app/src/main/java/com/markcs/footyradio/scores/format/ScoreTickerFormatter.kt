package com.markcs.footyradio.scores.format

import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            if (m.status == MatchStatus.SCHEDULED) {
                // Not started yet: show the start time rather than an empty "0 v 0".
                listOfNotNull("$home v $away", m.startMs?.let { startTime(it) }).joinToString(" ")
            } else {
                // "?: \"0\"" preserves the legacy display for matches with no score yet.
                val base = "$home (${m.homeScore ?: "0"}) v $away (${m.awayScore ?: "0"})"
                m.clock?.let { "$base $it" } ?: base
            }
        }
    }

    private fun startTime(ms: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
}
