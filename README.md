# Podcast

A simple Android podcast app with a **native Samsung One UI** look, built on the
[OneUIProject/oneui-design](https://github.com/OneUIProject/oneui-design) (SESL) component library.

## Features (v1)

- **Add podcasts** — search the Apple/iTunes directory by name, or paste an RSS feed URL.
- **Browse** subscriptions in a One UI collapsing-app-bar grid; open a podcast to see its episodes.
- **Stream playback** with a background `MediaSessionService` — media notification, lock-screen
  controls, audio-focus handling, skip −15s / +30s, and variable speed.
- **Mini-player** card on every screen that expands into the full now-playing screen with a
  shared-element artwork animation (the player grows up out of the mini-player).
- **Mark episodes played** (manually, in bulk via *Mark all as played / unplayed*, or automatically
  when an episode finishes); resume positions are saved per episode.
- **Home-screen widget** showing the current/last episode with play/pause and skip-forward,
  staying in sync with playback started from the app, lock screen, or the widget itself.
- Pull-to-refresh a feed; light/dark follows the system.

## Tech stack

- **Kotlin**, View/XML UI (required for the authentic One UI look — the SESL libraries are not
  Jetpack Compose).
- **oneui-design + SESL** forks of AndroidX/Material (`io.github.oneuiproject*`) — see the stock
  AndroidX `exclude` block and `OneUITheme` in [app/build.gradle.kts](app/build.gradle.kts) /
  [themes.xml](app/src/main/res/values/themes.xml).
- **Media3 (ExoPlayer + MediaSession)** for playback, **Room** for persistence,
  **OkHttp** + `XmlPullParser` for feeds/search, **Glide** for artwork.

## Project layout

```
app/src/main/java/be/dimsumfamily/podcast/
  data/        Room entities (Podcast, Episode), DAOs, AppDatabase, PodcastRepository
  feed/        ItunesSearchClient, RssParser, FeedFetcher
  playback/    PlaybackService (Media3), PlayerConnection, MediaItems
  widget/      PodcastWidgetProvider, WidgetState — home-screen widget
  ui/          MediaActivity, MainActivity (home), Format, SquareImageView
    search/    SearchActivity (+ adapter)         — add a podcast
    podcast/   PodcastActivity (+ EpisodeAdapter) — detail + episodes
    player/    PlayerActivity, MiniPlayerView     — playback UI
    subscriptions/ PodcastGridAdapter
```

## Build

Requires a JDK (17–21) and the Android SDK. The Gradle wrapper is pinned to 8.7.

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Install on a Samsung phone

1. On the phone: **Settings → About phone → Software information**, tap **Build number** 7×
   to unlock Developer options, then **Settings → Developer options → USB debugging → On**.
2. Connect via USB and accept the "Allow USB debugging" prompt.
3. Install:
   ```bash
   ./gradlew installDebug          # or:
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

The project also opens directly in Android Studio.

## Deferred (not in v1)

Episode downloads/offline, sleep timer, play queue / up-next, WorkManager auto-refresh,
OPML import/export, per-podcast settings.
