package com.markcs.footyradio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Artwork for the player. Song artwork wins, then the two team logos when a live score is
 * showing, then the station image. Logo URLs come from the score provider, so this works for
 * any sport.
 */
@Composable
fun TeamLogoImage(
    artworkUrl: String?,          // song artwork only
    stationArtworkUrl: String?,   // station image fallback
    homeLogoUrl: String?,
    awayLogoUrl: String?,
    showLogos: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    when {
        // Priority 1: song artwork
        artworkUrl != null -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        // Priority 2: team logos when live scores active
        showLogos && homeLogoUrl != null && awayLogoUrl != null -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(homeLogoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(awayLogoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
        // Priority 3: station artwork
        else -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(stationArtworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
    }
}
