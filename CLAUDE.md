# Onecast — CLAUDE.md

Android podcast app styled to feel **fully native on Samsung One UI**, built on
[OneUIProject/oneui-design](https://github.com/OneUIProject/oneui-design) (the SESL forks of
AndroidX/Material). Package `be.miro.onecast`, app label "Onecast".

Source lives on GitHub at [minospace/onecast](https://github.com/minospace/onecast),
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
- **Versioning happens only when the user asks to push** (any branch) **or to build an APK**, never
  on an ordinary commit. The two fields move on different triggers:
  - `versionCode` (int, `app/build.gradle.kts`) **always increments by 1 before building an APK and
    on a push**. **Every request to build an APK bumps it, without exception** — even if a push
    just bumped it and nothing has been built at that code yet. Don't skip the bump by reasoning
    about what's already in `~/Desktop/onecast_build/`. It's the system's upgrade
    counter, not a label: the package manager refuses to install over a higher code, and Play
    rejects a repeat, so every build needs its own — including betas and re-cuts of the "same"
    release.
  - `versionName` (`MAJOR.MINOR.PATCH`) **changes only when the user explicitly says to**, and to
    the value they give. Don't infer a bump from the size of the change; a release can keep its
    name across several `versionCode`s (e.g. successive betas of 2.9.0).
  Mirror whatever you changed into `VERSION.txt` (gitignored local quick-reference of
  `versionCode`/`versionName`) — regenerate it if missing. Don't push yourself unless asked — the
  user normally runs the push.
- **Always log user-visible changes in `releasenotes.txt`** (project root, gitignored): whenever a
  change adds or alters something a user would notice, append one short, user-facing bullet
  describing *what changed for them* (not the implementation). Do this automatically as part of
  making the change — don't wait to be asked and don't ask whether to add it; it is not gated on a
  commit or a push. (Version bumps, by contrast, happen *only* on a push or an APK build — see
  above.) **If `releasenotes.txt` is missing, create it** — that's the normal state right after a
  build, not a mistake. Skip purely internal refactors/chores that a user wouldn't see.
- **`./build.sh`** (gitignored, local only) builds the APK into `~/Desktop/onecast_build/` as
  `Onecast-<versionName>-<versionCode>.apk`, and *moves* `releasenotes.txt` there too (as
  `Onecast-<versionName>-<versionCode>-releasenotes.txt`, beside its APK), so entries
  accumulate between builds and each build starts the next set of notes fresh. It builds a signed
  release when `keystore.properties` is present, otherwise a debug APK (suffixed `-debug`). Bump
  `versionCode` first (see above). When the user wants *only* the APK, build it with
  `./gradlew assembleRelease` and move `app-release.apk` into that folder under the same name
  yourself, leaving `releasenotes.txt` where it is.
- No emulator/Studio installed by default. A throwaway headless AVD can be created with
  `avdmanager`/`emulator` for verification — see "Testing" below for the gotchas.

## Architecture

```
app/src/main/java/be/miro/onecast/
  data/        Room entities (Podcast, Episode), DAOs, AppDatabase, PodcastRepository
  feed/        ItunesSearchClient (iTunes Search API), RssParser (XmlPullParser), FeedFetcher
  playback/    PlaybackService (Media3 MediaSessionService), PlayerConnection, MediaItems
  download/    EpisodeDownloader (queue + OkHttp), DownloadService (foreground + live
               notification), DownloadStore (files), DownloadNotifications
  widget/      OnecastWidgetProvider, WidgetState — home-screen widget
  ui/          MediaActivity (base), MainActivity (home grid), Format, SquareImageView
    search/    SearchActivity + SearchResultAdapter   — add a podcast (iTunes search + RSS URL)
    podcast/   PodcastActivity + EpisodeAdapter        — detail screen + episode list
    player/    PlayerActivity, MiniPlayerView          — now-playing UI
    downloads/ DownloadsActivity + DownloadsAdapter    — offline episodes + downloads in flight
    subscriptions/ PodcastGridAdapter
```

- **MVVM-ish, no DI framework**: `OnecastApp.repository` is the single manually-wired singleton
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
- **Design system: "One UI 9 Style"** (the user's own design system). Every screen is a grey
  **page** (`bg`: #F2F2F2 light, pure black dark) with content grouped on rounded **surface** cards
  (`surface`: white / #171717, `radius-lg` 26dp, inset by the 12dp gutter). Floating things (sheets,
  the mini-player) are `surface-raised` (white / #242424). Tokens live in `values/colors.xml` +
  `values-night/colors.xml` as `app_<token>` (`app_surface`, `app_surface_raised`,
  `app_surface_container`, `app_divider`, `app_outline`, `app_danger`, `app_primary`,
  `app_primary_text`), shape/spacing in `values/dimens.xml` (`gutter`, `radius_sm/md/lg/xl`), and
  the type scale + rows/buttons/chips as styles in `values/styles.xml` (`Onecast.Text.*`,
  `Onecast.Row`, `Onecast.IconButton`, `Onecast.Button.Tonal/Filled/Text`, `Onecast.Chip`) — use
  those rather than setting sizes or colours on a view by hand.
  - The page colour comes from overriding SESL's own `sesl_background_color_light/dark` (which
    `oui_background_color` points at), so the whole `ToolbarLayout`, window and system bars follow
    without any view walking; `sesl_round_and_bgcolor_*` / `sesl_action_bar_background_color_*`
    point at it too, so app bar and content are one page. SESL text colours
    (`sesl_*_text_color_*`) and the blue (`sesl_primary_color_*`, `sesl_primary_dark_color_*`) are
    overridden the same way, so preferences, dialogs and menus pick up the design system's values.
  - **List groups**: a `RecyclerView` gets `ui/GroupedListDecoration` with a `groupOf(position)`
    lambda (equal keys next to each other = one group, `null` = not grouped). It paints each row's
    rounded surface + ripple and the inset hairlines at draw time (so corners stay right through
    diffs), and puts 12dp between groups. Rows use `style="@style/Onecast.Row"` (12dp side margins,
    20/16dp padding, 64dp min height; `Onecast.Row.TrailingButton` when it ends in a 48dp icon
    button). A lone grouped row outside a list uses `GroupedListDecoration.applySingleRow(view)`.
    Rows inside a sheet aren't grouped — the sheet is the surface.
  - Touch targets are ≥48dp (`Onecast.IconButton`; chips are 48dp views with a 36dp inset pill).
  - Status is never colour alone: say it in words too (the episode meta line's "Now playing" /
    "Played") or change the shape (outline vs filled check).
  - Confirmation dialogs: the **title is the question** ("Delete this download?"), the message one
    sentence of consequence, the confirm button repeats the verb. Destructive ones use
    `AlertDialog.Builder.showDestructive()` (`ui/Feedback.kt`) for the `danger` button colour.
  - Anything the user can take back (marking played, Up Next changes) confirms with
    `showUndo(...)` — a snackbar with Undo — and restores the exact prior state
    (`PodcastRepository.restorePlayedState`, `queueSlot`/`restoreQueueSlot`).
  - Toolbars carry **at most one action icon plus More options**; everything else goes in the
    overflow menu. List screens use the expanded app bar; editors and search stay collapsed.
  - Settings' `PreferenceFragmentCompat` cards are the same groups: `ui/SettingsActivity` paints
    the list `app_surface` and recolours SESL's private rounded-corner painters to the page colour.
- **AMOLED pure-black mode** (Settings → "Pure black dark mode"): the dark page is already pure
  black, so this option turns the grouped **surfaces** black too, for an all-black look. Code that
  paints a group surface reads `AmoledTheme.surface(context)` (black when active, else
  `app_surface`) — `GroupedListDecoration` does; Settings flattens its cards instead
  (`seslSetRoundedCorner(false)`, `seslSetFillBottomEnabled(false)`, clearing the
  `"preferencecategory"` subheader backgrounds); the Up Next sheet tints its background black.
  Floating surfaces (the mini-player) keep their lift. Activities call `recreate()` from `onResume`
  when the active state changed. Only applies while the system is in dark mode.
- **Material 3 Expressive accent** (Settings → "Material You colours", off by default): swaps the
  One UI blue accent for the wallpaper-derived M3 palette (`@android:color/system_accent1_*` on API
  31+, M3 baseline below). *Accent only* — the page and surfaces stay the design system's neutrals,
  so it composes with AMOLED mode. The accent is reachable via `?attr/colorPrimary` (fills) and
  `?attr/appPrimaryText` (blue *text*, lighter than the fill in dark mode), so
  `ui/ExpressiveTheme.apply()` layers the
  `ThemeOverlay.Onecast.Expressive` overlay onto the activity theme — **call it in `onCreate` before
  `setContentView`**, since the overlay only reaches views inflated after it. `MediaActivity` does
  this for its five subclasses; `SearchActivity`/`SettingsActivity` do it themselves. Back-stack
  screens `recreate()` from `onResume` on change, same as AMOLED.
  **Nothing should reference `@color/app_primary` / `app_primary_text` directly** — use
  `?attr/colorPrimary` / `?attr/appPrimaryText` (or `ExpressiveTheme.accent()` /
  `accentText()` from code), or that element stays blue while the rest of the app moves.
  The home-screen widget is the deliberate exception: it's always Material You on API 31+ and
  ignores this toggle, because a launcher-hosted widget can't read the app's theme or settings at
  inflate time.
- **Bottom sheets** (`ui/queue/QueueSheet`, the Up Next queue): the SESL Material fork ships
  `BottomSheetDialogFragment`, so use it — there's no One UI sheet in oneui-design. Two things to
  copy: the fragment's own theme (`Onecast.BottomSheetDialog`) only strips the modal's background so
  `@drawable/bg_bottom_sheet` (`surface-raised`, `radius-xl` top corners) shows through, and the content is inflated with
  `inflater.cloneInContext(requireActivity())` — a dialog theme is applied *on top of* the
  activity's, so inflating with the dialog's context would override the One UI day/night colours and
  the Expressive accent overlay with Material's defaults. AMOLED is handled by tinting the sheet
  background black in `onViewCreated` (no view walking needed — the sheet paints its own surface).

## Playback gotchas

- Podcast audio URLs are almost always multi-hop tracking redirects (e.g.
  `tracking.swap.fm → byspotify → podtrac → cdn`), frequently crossing http↔https. ExoPlayer's
  `DefaultHttpDataSource` must have `setAllowCrossProtocolRedirects(true)` (wired in
  `PlaybackService.onCreate`) or playback fails with a generic "Source error". Manifest also
  needs `android:usesCleartextTraffic="true"`.
- That same redirect chain is the bulk of the wait before the first sound, and ExoPlayer opens a
  URL several times per episode (header read, then again from a byte offset on every seek/resume,
  and after a network hiccup). `RedirectCachingDataSource` wraps the data source and remembers
  where each URL landed, so only the first open pays the chain; entries are process-scoped and a
  failed shortcut falls back to the original URL, because CDN links are often signed and expire.
- **Video episodes** (`playback/VideoMode`): a video podcast almost always ships *one* enclosure
  (`type="video/mp4"`) carrying both tracks — a separate audio file only appears via Podcasting 2.0
  `<podcast:alternateEnclosure>` or Media RSS. So switching between watching and listening is mostly
  a **track-selection** change (`setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, …)`), and only swaps the
  media item when the two sources are genuinely different files. Both URLs ride along in the media
  item's metadata extras (`MediaItems`), so the player screen can switch without a database read.
  Video is transient, never persisted: every episode starts as audio, and `PlaybackService` clears
  the flag on a media-item transition *to a different episode id* (a source swap keeps the id).
  Settings → "Preload video" keeps the video track enabled while listening so switching is instant;
  turning it off means re-enabling the track mid-episode, which re-buffers. **A video renderer
  failure must never cost the audio** — `VideoMode.recoverFromError` catches the renderer-level
  `ExoPlaybackException`, disables video and re-prepares, because an ExoPlayer error otherwise stops
  the whole episode (seen for real: a device with no free/capable H.264 decoder).
  The player draws into a `TextureView`, not a `SurfaceView`: the player screen is a sheet the user
  drags and fades, which a surface wouldn't follow.
- A `BroadcastReceiver`'s `Context` (including `AppWidgetProvider.onReceive`, even after
  `goAsync()`) is **bind-restricted** — `MediaController.Builder(context, token).buildAsync()`
  throws `ReceiverCallNotAllowedException` if given the receiver's own context. Always pass
  `context.applicationContext` when connecting a `MediaController` from a receiver (see
  `OnecastWidgetProvider`).
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
mini-player + full player with a shared-element artwork transition, mark played (manual/bulk/auto,
with Undo), resume positions, home-screen widget, light/dark following the system, optional
Material You accent and pure-black dark mode, live search while typing, episode downloads/offline
playback, video podcasts (audio first, with a toggle to watch).

**Downloads**: strictly user-initiated — there is no auto-download and none should be added. An
episode with video asks before downloading: a choice of versions when audio and video are separate
files, otherwise a confirmation that the (much larger) video file is what gets saved;
`episodes.downloadHasVideo` records which one landed on disk.
`EpisodeDownloader` runs one download at a time off an in-memory queue (progress is far too chatty
to persist; only the finished file's path/size/timestamp land on the `episodes` row).
`DownloadService` exists solely to hold the foreground-service notification that mirrors that queue
— it stops itself the moment nothing is queued or running. A download writes to a `.part` file that
is renamed into place only on success, so a partial file is never mistaken for a finished one; any
failure (HTTP error, truncated transfer, OkHttp read timeout, or the stall watchdog that fires when
bytes stop arriving for 60s) deletes the partial, notifies the user with the reason, and leaves a
retry row on the Downloads screen. `MainActivity` sweeps orphaned files once per process
(`PodcastRepository.pruneDownloads`), which is what catches leftovers from a process death.
