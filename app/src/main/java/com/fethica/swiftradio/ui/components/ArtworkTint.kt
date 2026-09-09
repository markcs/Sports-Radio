package com.fethica.swiftradio.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The dominant colour of the artwork at [artworkUrl], for tinting the now playing bar's glass.
 *
 * Unspecified until the first artwork has been read; after that the previous colour stays while the
 * next one loads, so a station change never flashes the glass back to untinted. The artwork comes out
 * of Coil's cache at thumbnail size, so this costs one small decode per artwork, not a second download.
 */
@Composable
fun rememberArtworkTint(artworkUrl: String?): Color {
    val context = LocalContext.current
    var tint by remember { mutableStateOf(Color.Unspecified) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) return@LaunchedEffect
        val next = withContext(Dispatchers.Default) { dominantColor(context, artworkUrl) }
        if (next != null) tint = next
    }
    return tint
}

private suspend fun dominantColor(context: Context, url: String): Color? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(96)
        .allowHardware(false)
        .build()
    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
    val palette = Palette.from(bitmap).generate()
    // Vibrant first: a logo on a white or black field has a dull dominant swatch and a telling vibrant one.
    return (palette.vibrantSwatch ?: palette.dominantSwatch)?.let { Color(it.rgb) }
}
