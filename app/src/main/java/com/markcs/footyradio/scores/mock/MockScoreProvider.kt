package com.markcs.footyradio.scores.mock

import com.markcs.footyradio.scores.SportsScoreProvider
import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import com.markcs.footyradio.scores.model.MatchScore
import com.markcs.footyradio.scores.model.MatchStatus
import com.markcs.footyradio.scores.model.TeamRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Debug-only provider with synthetic AFL games. It lets the ticker, notification and
 * Android Auto metadata be exercised outside the football season. Release builds never
 * use it.
 */
internal class MockScoreProvider(scope: CoroutineScope) : SportsScoreProvider {

    override val sportId = MOCK_SPORT_ID
    override val displayName = "AFL (debug mock)"

    override val snapshot: StateFlow<LiveScoreSnapshot?> = flow {
        var tick = 0
        while (true) {
            emit(mockSnapshotAt(tick++))
            delay(TICK_MS)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}

private const val TICK_MS = 5_000L
private const val MOCK_SPORT_ID = "afl_mock"
private const val LOGO_BASE = "https://markcs.github.io/Sports-Radio/team-logos"
private const val QUARTER_SECS = 900
private const val SECS_PER_TICK = 5

internal fun mockSnapshotAt(tick: Int): LiveScoreSnapshot = LiveScoreSnapshot(
    sportId = MOCK_SPORT_ID,
    matches = listOf(
        mockMatch("mock-1", team("Essendon", "ESS"), team("Hawthorn", "HAW"), tick, base = 10, offset = 0),
        mockMatch("mock-2", team("Carlton", "CAR"), team("Collingwood", "COLL"), tick, base = 5, offset = 120),
    ),
)

private fun team(name: String, short: String) = TeamRef(
    name = name,
    shortName = short,
    logoUrl = "$LOGO_BASE/${name.replace(" ", "%20")}.png",
)

private fun mockMatch(
    id: String,
    home: TeamRef,
    away: TeamRef,
    tick: Int,
    base: Int,
    offset: Int,
): MatchScore {
    val t = tick + offset
    val elapsed = (t * SECS_PER_TICK) % (4 * QUARTER_SECS)
    val quarter = elapsed / QUARTER_SECS + 1
    val remaining = QUARTER_SECS - elapsed % QUARTER_SECS
    return MatchScore(
        id = id,
        home = home,
        away = away,
        homeScore = (base + t / 3).toString(),
        awayScore = (base + t / 4).toString(),
        clock = "Q$quarter %d:%02d".format(remaining / 60, remaining % 60),
        status = MatchStatus.LIVE,
    )
}
