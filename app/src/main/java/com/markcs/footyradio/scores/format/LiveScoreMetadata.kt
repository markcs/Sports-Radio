package com.markcs.footyradio.scores.format

import android.os.Bundle
import androidx.media3.common.MediaMetadata

/**
 * Marks media metadata whose title is the live score ticker and whose artist is the
 * song info. Set by AudioService and read by PlayerViewModel, so neither side has to
 * infer it by comparing strings.
 */
object LiveScoreMetadata {

    const val OVERLAY_EXTRA = "footyradio.live_score_overlay"

    fun overlayExtras(): Bundle = Bundle().apply { putBoolean(OVERLAY_EXTRA, true) }

    fun isOverlay(metadata: MediaMetadata): Boolean =
        metadata.extras?.getBoolean(OVERLAY_EXTRA, false) == true
}
