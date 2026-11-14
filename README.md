# LiquidTube

A personal, native Android music player with an Apple Music–style design (iOS 26
"Liquid Glass"), using YouTube Music as its library. Kotlin, Jetpack Compose and Media3.

> A personal project, not affiliated with Apple or Google. Sign in with your own
> YouTube Music account; Premium features use your own Premium subscription.

## Highlights

- **Now Playing:** full-bleed artwork with a progressive blur, word-by-word synced
  lyrics with optional translation, lyric cards to share, queue with history and undo.
- **Landscape layout:** a side tab rail and a two-column player.
- **Your library:** YouTube Music playlists, albums and artists; your own playlists
  with search-to-add and suggestions that follow what you add; smart playlists
  (On Repeat, Most Played, Forgotten Favorites, time-of-day mixes).
- **Offline:** downloads, a smart auto-downloaded Offline Mix and a listening
  cache. The app notices when the connection drops and re-plans the queue
  without stopping the song.
- **Audio:** 256 kbps Premium streams, crossfade, equalizer, Sound Check and smart shuffle.
- **Everywhere:** home screen widgets (Now Playing, live Lyrics), lyrics on the
  lock screen, a screen saver while charging, Android Auto, and auto-resume when
  headphones or the car connect.
- **Yours:** Replay stats and a shareable monthly story, plays synced to your
  YouTube Music history, and daily backup to Google Drive.

## Build

- Android Studio (Ladybug or newer), JDK 17
- minSdk 31 (Android 12; needed for the RenderEffect glass blur)
- Build the **release** variant for smooth performance:
  `./gradlew :app:assembleRelease`

## Project layout

- `innertube/`: YouTube Music (InnerTube) API client
- `app/`: the Android app
  - `ui/`: Compose screens and the Liquid Glass components
  - `playback/`: Media3 service, queue logic, Android Auto
  - `premium/`: 256 kbps Premium stream support
  - `download/`: downloads, Offline Mix, listening cache
  - `data/`: settings, library, lyrics, backup, network monitor
