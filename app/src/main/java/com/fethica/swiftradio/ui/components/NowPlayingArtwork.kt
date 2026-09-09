package com.fethica.swiftradio.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.fethica.popupbar.PopupBarDefaults
import com.fethica.swiftradio.Config
import kotlin.math.PI
import kotlin.math.cos

/** The full player's artwork corner. Also the shape its popup image slot reports. */
val NowPlayingArtworkCorner = 16.dp

private val BarArtworkCorner = PopupBarDefaults.imageCornerRadius(Config.popupBarStyle)

/**
 * The single artwork instance the popup host carries between the bar's thumbnail
 * and the full player's square.
 *
 * The decorations live here rather than in `NowPlayingScreen` because the host
 * draws this composable ABOVE the popup content: a buffering scrim left behind
 * in the player would be painted over by the travelling image. Both are gated
 * on [expansion] so they belong to the full player and never to the thumbnail.
 *
 * [expansion] is read inside `graphicsLayer` blocks, a draw-phase read, so a
 * drag moves the artwork without recomposing anything.
 */
@Composable
fun NowPlayingArtwork(
    artworkUrl: String?,
    isBuffering: Boolean,
    isPlaying: Boolean,
    expansion: () -> Float,
    modifier: Modifier = Modifier
) {
    // The player's artwork breathes with playback. The bar's thumbnail must
    // not, so the scale is interpolated back to 1 as the popup collapses.
    val playingScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "artworkScale"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = expansion().coerceIn(0f, 1f)
                val scale = 1f + (playingScale - 1f) * t
                scaleX = scale
                scaleY = scale
                // The host clips the travelling image to its own interpolated
                // radius, but that clip does not scale with this layer: a
                // shrunk artwork would show square corners inside it. Clipping
                // here on the same curve the host uses keeps the two edges
                // coincident at rest and correct while the artwork is scaled.
                clip = true
                shape = RoundedCornerShape(
                    lerp(BarArtworkCorner, NowPlayingArtworkCorner, easeInOutSine(t))
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artworkUrl)
                .crossfade(true)
                // The host composes this once and scales it from the bar
                // thumbnail up to the full-width expanded slot. Coil sizes a
                // request from the first measurement it sees, which is the
                // thumbnail; without this, expanding would stretch a tiny
                // bitmap. Station logos are small, so the original costs little.
                .size(Size.ORIGINAL)
                .build(),
            // Decorative: the popup bar already carries the station name as
            // its own accessibility node.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Crossfade(targetState = isBuffering, label = "bufferingOverlay") { buffering ->
            if (buffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expansion().coerceIn(0f, 1f) }
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

/**
 * The library's own easing for the travelling image's corner radius, copied
 * because it is internal there and the clip above has to follow the same curve.
 */
private fun easeInOutSine(t: Float): Float =
    (-(cos(PI * t.coerceIn(0f, 1f)) - 1.0) / 2.0).toFloat()
