package com.markcs.footyradio

import androidx.compose.ui.graphics.Color

object Config {
    val gradientColor: Color = Color.White

    const val stationsURL = "https://markcs.github.io/Sports-Radio/stations/stations.json"
    const val teamsURL = "https://markcs.github.io/Sports-Radio/stations/teams.json"

    const val enableTeamAbbreviations = false
    const val hideNextPreviousButtons = false
    const val enableSearch = true // Toggle to show/hide search bar on the Stations Screen

    const val userAgent = "SportsRadio - contact@campbellsmith.me"
    const val fallbackUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"

    const val email = "contact@campbellsmith.me"
    const val feedbackURL = "https://github.com/markcs/Sports-Radio/issues"
    const val licenseURL = "https://github.com/markcs/Sports-Radio/blob/main/LICENSE"
    const val shareText = "Check out Sports Radio!"

    data class LibraryItem(val owner: String, val repo: String)

    val libraries = listOf(
        LibraryItem("fethica", "Swift-Radio-Android"),
        LibraryItem("coil-kt", "coil"),
        LibraryItem("ktorio", "ktor"),
    )
}
