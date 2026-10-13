package com.markcs.footyradio.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Playback state drawn on top of the station artwork card.
 * Buffering shows a spinner; the current item shows an equalizer that animates
 * while playing and holds still while paused; non-current artwork shows nothing.
 */
@Composable
fun ArtworkPlaybackOverlay(
    isBuffering: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val state = when {
        isCurrent && isBuffering -> OverlayState.BUFFERING
        isCurrent -> OverlayState.ACTIVE
        else -> OverlayState.IDLE
    }

    Crossfade(targetState = state, label = "artworkPlaybackOverlay", modifier = modifier) { current ->
        if (current != OverlayState.IDLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (current == OverlayState.BUFFERING) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    EqualizerAnimation(
                        isAnimating = isPlaying,
                        barColor = Color.White,
                        modifier = Modifier.height(16.dp)
                    )
                }
            }
        }
    }
}

private enum class OverlayState { IDLE, BUFFERING, ACTIVE }
