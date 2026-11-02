package com.markcs.footyradio.scores

import com.markcs.footyradio.scores.model.LiveScoreSnapshot
import kotlinx.coroutines.flow.StateFlow

interface SportsScoreProvider {
    val sportId: String
    val displayName: String

    /**
     * One shared, hot stream per provider. The provider owns its connection and
     * scheduling and starts them only while there are subscribers
     * (SharingStarted.WhileSubscribed). Because of that, there is no
     * onActiveScreensChanged() and no abbreviation parameter.
     */
    val snapshot: StateFlow<LiveScoreSnapshot?>
}
