# Podcast — CLAUDE.md

Android podcast app styled to feel **fully native on Samsung One UI**, built on
[OneUIProject/oneui-design](https://github.com/OneUIProject/oneui-design) (the SESL forks of
AndroidX/Material). Package `be.dimsumfamily.podcast`, app label "Podcast".

Source lives on GitHub at [minospace/one-ui-podcasts](https://github.com/minospace/one-ui-podcasts),
default branch `main`.

## Critical constraint: View/XML only, never Compose

The One UI look comes from Samsung's **SESL** widget forks (`io.github.oneuiproject.sesl:*`) and
`dev.oneuiproject.oneui.layout.ToolbarLayout`. These are View/XML-based — there is no Compose
equivalent that reproduces the real One UI styling. Do not introduce Jetpack Compose here.

## Build

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # JDK 21
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # build + install over adb (phone needs USB debugging on)
```

- Gradle wrapper is pinned to **8.7** (AGP 8.5.2 rejects newer Gradle; the Homebrew system
  `gradle` is 9.6 — always use `./gradlew`, never the global binary).
- Android SDK lives at `/opt/homebrew/share/android-commandlinetools` (installed via
  `brew install --cask android-commandlinetools`); `local.properties` points `sdk.dir` there and
  is gitignored — regenerate it if missing: `echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties`.
- No emulator/Studio installed by default. A throwaway headless AVD can be created with
  `avdmanager`/`emulator` for verification — see "Testing" below for the gotchas.

## Architecture

```
app/src/main/java/be/dimsumfamily/podcast/
  data/        Room entities (Podcast, Episode), DAOs, AppDatabase, PodcastRepository
  feed/        ItunesSearchClient (iTunes Search API), RssParser (XmlPullParser), FeedFetcher
  playback/    PlaybackService (Media3 MediaSessionService), PlayerConnection, MediaItems
  widget/      PodcastWidgetProvider, WidgetState — home-screen widget
  ui/          MediaActivity (base), MainActivity (home grid), Format, SquareImageView
    search/    SearchActivity + SearchResultAdapter   — add a podcast (iTunes search + RSS URL)
    podcast/   PodcastActivity + EpisodeAdapter        — detail screen + episode list
    player/    PlayerActivity, MiniPlayerView          — now-playing UI
    subscriptions/ PodcastGridAdapter
```

- **MVVM-ish, no DI framework**: `PodcastApp.repository` is the single manually-wired singleton
  (`Context.podcastRepository` extension for convenient access). No Hilt/Koin — keep it that way
  unless complexity genuinely demands it.
- **MediaActivity** is the base class for any screen that needs the player: it owns a
  lifecycle-bound `PlayerConnection` (wraps a Media3 `MediaController`) and exposes `repository`.
- **Single source of truth**: UI observes `PodcastRepository` Flows (Room) directly; no separate
  ViewModel layer was introduced — activities hold minimal state and re-render on Flow emissions
  plus `PlayerConnection.onUpdate` ticks.

## SESL / oneui-design gotchas (don't relitigate these)

- **Menus**: `ToolbarLayout` sets its internal `Toolbar` as the support action bar. Use the
  standard `onCreateOptionsMenu` / `onOptionsItemSelected` Activity callbacks — calling
  `binding.toolbarLayout.toolbar.inflateMenu(...)` directly is silently ignored (the menu never
  appears).
- **Theme**: `AppTheme` must inherit `OneUITheme` (see `values/themes.xml`).
- **Stock AndroidX must be excluded**: the `configurations.all { exclude(...) }` block in
  `app/build.gradle.kts` removes the real `androidx.appcompat`/`core`/`recyclerview`/etc. and
  `com.google.android.material` so only the SESL forks remain on the classpath. If you add a new
  dependency that pulls in stock AndroidX transitively, add a matching exclude or you'll get
  duplicate-class errors or the wrong (non-One UI) widget styling.
- **Child placement** inside `ToolbarLayout`/`DrawerLayout` uses `app:layout_location`, an enum:
  `main_content` (0), `appbar_header` (1), `footer` (2), `root` (3), `drawer_header` (4),
  `drawer_panel` (5). The mini-player lives in `footer`.
- **Navigation/back button**: `setNavigationButtonAsBack()` on detail-style screens.

## Playback gotchas

- Podcast audio URLs are almost always multi-hop tracking redirects (e.g.
  `tracking.swap.fm → byspotify → podtrac → cdn`), frequently crossing http↔https. ExoPlayer's
  `DefaultHttpDataSource` must have `setAllowCrossProtocolRedirects(true)` (wired in
  `PlaybackService.onCreate`) or playback fails with a generic "Source error". Manifest also
  needs `android:usesCleartextTraffic="true"`.
- A `BroadcastReceiver`'s `Context` (including `AppWidgetProvider.onReceive`, even after
  `goAsync()`) is **bind-restricted** — `MediaController.Builder(context, token).buildAsync()`
  throws `ReceiverCallNotAllowedException` if given the receiver's own context. Always pass
  `context.applicationContext` when connecting a `MediaController` from a receiver (see
  `PodcastWidgetProvider`).
- `PlaybackService` persists resume position periodically and on play/pause changes, and
  auto-marks an episode played on `Player.STATE_ENDED`. The home-screen widget mirrors playback
  state via `WidgetState` (a SharedPreferences snapshot) updated from the same player listener —
  keep both in sync if you touch the listener.

## Testing

There's no automated test suite (UI-driven smoke testing only, done manually via adb/uiautomator
during development). If you spin up a headless emulator to verify changes:

```bash
avdmanager create avd -n podcast_test -k "system-images;android-34;default;arm64-v8a" -d pixel_6
emulator @podcast_test -no-window -no-audio -no-snapshot -no-boot-anim \
  -gpu swiftshader_indirect -dns-server 8.8.8.8,8.8.4.4
```

- **`-dns-server` is required** — without it the emulator can't resolve podcast tracking/CDN
  hosts and playback fails with `UnknownHostException`, even though RSS/artwork fetches (simpler
  hosts) succeed.
- **Non-exported activities can't be launched via `adb shell am start`** on API 34+ — drive the UI
  with real taps: `adb shell uiautomator dump` the view tree, locate the target element's bounds,
  `adb shell input tap <x> <y>`. Retry the dump in a loop until the element appears before tapping
  — a dump taken mid-transition is a common source of flaky "tap did nothing" results, not an app
  bug.
- The home-screen widget's actual *rendering* on a launcher can't be verified on the AOSP
  test-image emulator (no widget picker) — that needs a check on a real device/launcher
  (the target is the One UI launcher on the user's Samsung phone).

## Scope

**Implemented**: add podcast (iTunes search + RSS URL), subscriptions grid, podcast detail with
swipe-refresh, streaming playback (background service, lock-screen controls, skip ±, speed),
mini-player + full player with a shared-element artwork transition, mark played (manual/bulk/auto),
resume positions, home-screen widget, light/dark following the system.

**Deliberately deferred** (don't add speculatively): episode downloads/offline, sleep timer, play
queue/up-next, WorkManager auto-refresh, OPML import/export, per-podcast settings.
