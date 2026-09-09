package com.fethica.swiftradio

import androidx.compose.ui.graphics.Color
import com.fethica.popupbar.PopupBarStyle
import com.fethica.popupbar.PopupProgressStyle

object Config {
    val gradientColor: Color = Color.White

    const val useLocalStations = true
    const val stationsURL = "https://fethica.com/assets/swift-radio/stations.json"

    const val hideNextPreviousButtons = false
    const val enableSearch = true // Toggle to show/hide search bar on the Stations Screen

    // Now playing bar. Floating, FloatingCompact, Prominent or Compact.
    val popupBarStyle: PopupBarStyle = PopupBarStyle.Floating
    // Progress strip on the bar for on-demand streams: None, Top or Bottom. Live streams never show one.
    val popupProgressStyle: PopupProgressStyle = PopupProgressStyle.Top
    // What the bar and the player are made of. Opaque is the plain card; the Translucent options are one
    // pane of glass over the station list, untinted, tinted with the theme's primary colour, or tinted with
    // the dominant colour of the current artwork; Artwork blurs the artwork itself behind bar and player.
    val popupBackground: PopupBackgroundOption = PopupBackgroundOption.TranslucentCoverTint

    enum class PopupBackgroundOption { Opaque, Translucent, TranslucentAppTint, TranslucentCoverTint, Artwork }

    const val email = "contact@fethica.com"
    const val feedbackURL = "https://fethica.com/#contact"
    const val licenseURL = "https://raw.githubusercontent.com/fethica/Swift-Radio-Android/refs/heads/main/LICENSE"
    const val shareText = "Check out Swift Radio!"

    data class LibraryItem(val owner: String, val repo: String)

    val libraries = listOf(
        LibraryItem("fethica", "Swift-Radio-Android"),
        LibraryItem("fethica", "compose-popupbar"),
        LibraryItem("coil-kt", "coil"),
        LibraryItem("ktorio", "ktor"),
    )
}
