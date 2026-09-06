package com.fethica.swiftradio

import android.Manifest
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fethica.popupbar.PopupBar
import com.fethica.popupbar.PopupHost
import com.fethica.popupbar.PopupProgressStyle
import com.fethica.popupbar.rememberPopupState
import com.fethica.swiftradio.ui.AboutScreen
import com.fethica.swiftradio.ui.NowPlayingScreen
import com.fethica.swiftradio.ui.StationsScreen
import com.fethica.swiftradio.ui.components.NowPlayingArtwork
import com.fethica.swiftradio.ui.components.NowPlayingArtworkCorner
import com.fethica.swiftradio.ui.theme.SwiftRadioTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* Handle permission grant/deny result here if needed */ }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)
            val state = vm.uiState
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                vm.connectController()
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> vm.connectController()
                        Lifecycle.Event.ON_DESTROY -> vm.disconnectController()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    vm.disconnectController()
                }
            }

            SwiftRadioTheme {
                var showAbout by remember { mutableStateOf(false) }
                val currentStation = state.currentStation

                // The bar is the app's "something is playing" indicator: it
                // arrives with the first station and leaves when playback is
                // cleared. Nothing else drives its presentation. Remembered
                // above the About switch so opening About does not reset it.
                val popupState = rememberPopupState()
                LaunchedEffect(currentStation != null) {
                    if (currentStation != null) popupState.present() else popupState.hide()
                }

                if (showAbout) {
                    AboutScreen(onBack = { showAbout = false })
                } else {
                    // One host owns the now playing bar and the full player it
                    // morphs into. The station list only learns how much room
                    // to leave at the bottom, through the padding it is handed.
                    PopupHost(
                        state = popupState,
                        modifier = Modifier.fillMaxSize(),
                        // A step lighter than the station cards, so the bar reads
                        // as a player docked over the list and not as one more
                        // row. The expanded player paints its own background.
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        popupBar = {
                            val labels = nowPlayingLabels(
                                stationName = currentStation?.name.orEmpty(),
                                stationDesc = currentStation?.desc.orEmpty(),
                                trackTitle = state.trackTitle,
                                artistName = state.artistName
                            )
                            // On-demand streams only: a live stream has no
                            // position to draw and nowhere to seek to. The
                            // lambdas are remembered because the strip keys its
                            // gesture detector on them; fresh ones every
                            // recomposition would restart a seek under the finger.
                            val durationMs = state.durationMs
                            val seekable = !state.isLive && durationMs > 0
                            val progress = remember(seekable, durationMs) {
                                if (seekable) {
                                    { vm.currentPositionMs.toFloat() / durationMs.toFloat() }
                                } else {
                                    null
                                }
                            }
                            val onSeek = remember(seekable, durationMs) {
                                if (seekable) {
                                    { fraction: Float -> vm.seekTo((fraction * durationMs).toLong()) }
                                } else {
                                    null
                                }
                            }
                            PopupBar(
                                title = labels.title,
                                subtitle = labels.subtitle.takeIf { it.isNotBlank() },
                                progress = progress,
                                onSeek = onSeek,
                                progressStyle = PopupProgressStyle.Top,
                                actions = {
                                    IconButton(onClick = { vm.togglePlayPause() }) {
                                        if (state.isPlaying && state.isLive) {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = stringResource(R.string.cd_stop)
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(
                                                    if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                                ),
                                                contentDescription = stringResource(
                                                    if (state.isPlaying) R.string.cd_pause else R.string.cd_play
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        },
                        popupImage = {
                            NowPlayingArtwork(
                                artworkUrl = vm.resolvedArtwork,
                                isBuffering = state.isBuffering,
                                isPlaying = state.isPlaying,
                                expansion = { popupState.progress }
                            )
                        },
                        popupContent = {
                            if (currentStation != null) {
                                NowPlayingScreen(
                                    stationName = currentStation.name,
                                    stationDesc = currentStation.desc,
                                    stationLongDesc = currentStation.longDesc,
                                    stationWebsite = currentStation.website,
                                    trackTitle = state.trackTitle,
                                    artistName = state.artistName,
                                    artworkUrl = vm.resolvedArtwork,
                                    isPlaying = state.isPlaying,
                                    isBuffering = state.isBuffering,
                                    isLive = state.isLive,
                                    currentPositionProvider = { vm.currentPositionMs },
                                    durationMs = state.durationMs,
                                    onPlayPauseClick = { vm.togglePlayPause() },
                                    onNextClick = { vm.nextStation() },
                                    onPreviousClick = { vm.previousStation() },
                                    onSeek = { vm.seekTo(it) },
                                    hideNextPrevious = Config.hideNextPreviousButtons,
                                    // Where the host lands the artwork it is
                                    // already carrying in the bar.
                                    imageSlot = { modifier ->
                                        PopupImageSlot(
                                            modifier,
                                            RoundedCornerShape(NowPlayingArtworkCorner)
                                        )
                                    }
                                )
                            }
                        }
                    ) { padding ->
                        StationsScreen(
                            stations = state.stations,
                            currentStation = currentStation,
                            isPlaying = state.isPlaying,
                            isBuffering = state.isBuffering,
                            isError = state.isError,
                            isRefreshing = state.isRefreshing,
                            listBottomPadding = padding.calculateBottomPadding(),
                            onStationClick = { station -> vm.playStation(station) },
                            onRefresh = { vm.refreshStations() },
                            onAboutClick = { showAbout = true }
                        )
                    }
                }
            }
        }
    }
}

private data class NowPlayingLabels(val title: String, val subtitle: String)

/**
 * What the bar shows: the track when the stream carries metadata, otherwise the
 * station; the station name or its description underneath. The same rule the
 * full player applies to its own title and subtitle.
 */
private fun nowPlayingLabels(
    stationName: String,
    stationDesc: String,
    trackTitle: String,
    artistName: String
): NowPlayingLabels {
    val normalizedTitle = trackTitle.trim()
    val normalizedArtist = artistName.trim()
    val hasTrackMetadata = normalizedTitle.isNotBlank() &&
        !normalizedTitle.equals(stationName, ignoreCase = true)
    val title = when {
        !hasTrackMetadata -> stationName
        normalizedArtist.isNotBlank() -> "$normalizedTitle — $normalizedArtist"
        else -> normalizedTitle
    }
    val subtitle = if (hasTrackMetadata) stationName else stationDesc
    return NowPlayingLabels(title, subtitle)
}
