# Swift Radio Android

[![Android build](https://github.com/fethica/Swift-Radio-Android/actions/workflows/android.yml/badge.svg)](https://github.com/fethica/Swift-Radio-Android/actions/workflows/android.yml)

An open-source starting point for building an Android radio app with Kotlin, Jetpack Compose, and Media3. Load stations from a local or hosted JSON file, using the same format as [Swift Radio on iOS](https://github.com/analogcode/Swift-Radio-Pro).

<p align="center">
    <img alt="Station list with the now playing bar at the bottom, a pane of dark glass tinted by the current artwork" src="screenshots/stations.png" width="300" hspace="16">
    <img alt="The player expanded from the bar on the same glass, the station list showing through as blurred colour" src="screenshots/player.png" width="300" hspace="16">
</p>

## Features

- Live and on-demand audio with background playback and controls on the lock screen and in notifications
- Now playing bar that swipes up into the full player, built on [compose-popupbar](https://github.com/fethica/compose-popupbar): a pane of glass tinted by the artwork by default, with the bar style, progress strip and background as config switches
- HLS (.m3u8) streams alongside the usual MP3 and AAC endpoints
- Android Auto integration with browse tree, playback controls, and artwork
- Album art and track metadata from streams and the iTunes API
- Multiple stations from a local or remote JSON file
- Search and filter stations by name or description
- Material You dynamic color support on Android 12+
- App text in string resources for translation

## Download

Download the latest APK from the [Releases page](https://github.com/fethica/Swift-Radio-Android/releases/latest). The app runs on Android 7.0+ (API 24).

Forks can publish their own signed releases: add the four signing secrets described in [release.yml](.github/workflows/release.yml), then push a version tag like `v1.0.1`.

## Build Requirements

- Android Studio
- Android SDK Platform 36.1
- JDK 17, the version used by [CI](.github/workflows/android.yml)

Use the included Gradle wrapper to build the project.

## Getting Started

1. Fork or clone this repo and open it in [Android Studio](https://developer.android.com/studio).
2. Choose a station source using the table below.
3. Set your contact details and feature flags in [Config.kt](app/src/main/java/com/fethica/swiftradio/Config.kt).
4. Add any bundled station images to `app/src/main/assets/`, as described under [Station Format](#station-format).
5. Run the app on an Android 7.0+ device or emulator.

| Station source | Settings in `Config.kt` | Where to edit the stations |
| --- | --- | --- |
| Bundled JSON (default) | `useLocalStations = true` | [app/src/main/assets/stations.json](app/src/main/assets/stations.json) |
| Hosted JSON | `useLocalStations = false`; set `stationsURL` to your JSON URL | The hosted file, using the same format shown below |

Changing `stationsURL` has no effect while `useLocalStations` is `true`.

To build a debug APK from the repo root:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Station Format

Each station in `stations.json` supports the following fields:

```json
{
  "station": [
    {
      "name": "Station Name",
      "streamURL": "https://example.com/stream",
      "imageURL": "station-image",
      "desc": "Short description",
      "longDesc": "Detailed description shown in the station info sheet.",
      "website": "https://example.com"
    }
  ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Station name displayed in the list and player |
| `streamURL` | Yes | Direct stream URL, including MP3, AAC, or an HLS `.m3u8` playlist |
| `imageURL` | Yes | Image filename in `app/src/main/assets/` without its extension, or a full URL |
| `desc` | Yes | Short subtitle shown below the station name |
| `longDesc` | No | Longer description shown in the station info sheet |
| `website` | No | Station website URL shown in the info sheet |

For a bundled image, `"imageURL": "station-logo"` loads `app/src/main/assets/station-logo.png`. PNG, JPG, and JPEG files are supported; leave the extension out of the JSON value. When reusing an iOS station file, copy its bundled images into the Android project too.

For a remote image, use the full URL, such as `"imageURL": "https://example.com/station-logo.png"`.

### Configuration

Common settings in [Config.kt](app/src/main/java/com/fethica/swiftradio/Config.kt):

```kotlin
object Config {
    val gradientColor: Color = Color.White       // diagonal gradient overlay color
    const val useLocalStations = true             // false = fetch from stationsURL
    const val stationsURL = "https://..."         // remote stations JSON URL
    const val hideNextPreviousButtons = false      // hide skip controls on the player
    const val enableSearch = true                  // show/hide search bar on station list
    val popupBarStyle = PopupBarStyle.Floating     // Floating, FloatingCompact, Prominent, Compact
    val popupProgressStyle = PopupProgressStyle.Top // progress strip on the bar: None, Top, Bottom
    val popupBackground = PopupBackgroundOption.TranslucentCoverTint
        // Opaque, Translucent, TranslucentAppTint, TranslucentCoverTint (glass tinted by the artwork), Artwork
    const val email = "contact@example.com"
    const val feedbackURL = "https://..."
    const val shareText = "Check out Swift Radio!"
}
```

### Customizing Text and Translation

App text lives in [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml). To add a new language:

1. Create a new directory: `app/src/main/res/values-XX/` (e.g., `values-fr` for French)
2. Copy `strings.xml` into the new directory
3. Translate the string values

Android will automatically use the correct language based on the user's device settings.

## Dependencies

| Library | Purpose |
|---------|---------|
| [AndroidX Media3](https://developer.android.com/media/media3) | Audio playback, media session, Android Auto |
| [Jetpack Compose](https://developer.android.com/compose) | UI framework with Material 3 |
| [compose-popupbar](https://github.com/fethica/compose-popupbar) | Now playing bar and its swipe-up player |
| [Coil](https://github.com/coil-kt/coil) | Image loading and caching |
| [AndroidX Palette](https://developer.android.com/jetpack/androidx/releases/palette) | Dominant colour of the artwork, for the glass tint |
| [Ktor](https://github.com/ktorio/ktor) | HTTP client for remote station loading |
| [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) | JSON parsing |

## Single Station Version

The single-station version skips the station list and launches straight into the player. One purchase includes the iOS and Android projects.

[![Buy on Payhip](https://img.shields.io/badge/Buy-Single%20Station%20iOS%20%2B%20Android-blue)](https://payhip.com/b/x15QB)

If you've built an app with Swift Radio or need custom work, [email me](mailto:contact@fethica.com).

## Credits

- [Fethi](https://fethica.com)
- Based on [Swift Radio Pro](https://github.com/analogcode/Swift-Radio-Pro) for iOS

## License

Swift Radio is open source and available under the [MIT License](LICENSE).
