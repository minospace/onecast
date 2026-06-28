## Important message: most of this app has been made using claude AI, but multiple test are run for each release and the app never collects or asks any data, so there are no privacy risks.


# Onecast

A simple Android podcast app with a **native Samsung One UI** look, built on the
[OneUIProject/oneui-design](https://github.com/OneUIProject/oneui-design) (SESL) component library.

## Features (functional release)

- **Add podcasts** — search the Apple/iTunes directory by name, or paste an RSS feed URL.
- **Browse** subscriptions in a One UI collapsing-app-bar grid; open a podcast to see its episodes,
  with an expandable show description and pull-to-refresh. Subscribed feeds also auto-refresh
  whenever the app is opened.
- **Stream playback** with a background `MediaSessionService` — media notification, lock-screen
  controls, audio-focus handling, skip −15s / +30s, variable speed, and chapter support (chapter
  list/picker plus the current chapter shown live as it plays).
- **Mini-player** card on every screen that expands into the full now-playing screen with a
  shared-element artwork animation (the player grows up out of the mini-player), an
  artwork-adaptive background gradient, and per-episode artwork. Pressing play again on a
  finished episode replays it from the start.
- **Mark episodes played** (manually, in bulk via *Mark all as played / unplayed*, or automatically
  when an episode finishes); resume positions are saved per episode. An option to hide played
  episodes is available per podcast.
- **Home-screen widget** showing the current/last episode with play/pause and skip-forward,
  staying in sync with playback started from the app, lock screen, or the widget itself.
- Light/dark follows the system.

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
app/src/main/java/be/miro/onecast/
  data/        Room entities (Podcast, Episode, Chapter), DAOs, AppDatabase, PodcastRepository, AppSettings
  feed/        ItunesSearchClient, RssParser, ChaptersClient, FeedFetcher
  playback/    PlaybackService (Media3), PlayerConnection, MediaItems
  widget/      OnecastWidgetProvider, WidgetState — home-screen widget
  ui/          MediaActivity, MainActivity (home), Format, SquareImageView
    search/    SearchActivity (+ adapter)         — add a podcast
    podcast/   PodcastActivity (+ EpisodeAdapter) — detail + episodes
    player/    PlayerActivity, MiniPlayerView, ChapterAdapter — playback UI
    subscriptions/ PodcastGridAdapter
```

## Build

Requires a JDK (17–21) and the Android SDK. The Gradle wrapper is pinned to 8.7.

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
``
