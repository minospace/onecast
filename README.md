# Onecast

A simple, free Android podcast app that looks and feels like it belongs on a Samsung phone —
built with Samsung's own One UI design language instead of generic Material Design.

> **Privacy:** Onecast collects no data and asks for no permissions beyond internet access to
> fetch podcasts. Most of the app was built with the help of Claude AI, but every release is
> tested by hand before publishing.

## What it does

- **Add podcasts** by searching Apple's podcast directory, or by pasting an RSS feed URL directly.
- **Browse your subscriptions** in a clean grid; tap a show to see its episodes, description,
  and pull down to refresh. Shows you're subscribed to also refresh automatically whenever you
  open the app.
- **Play episodes** with full background playback — notification and lock-screen controls,
  skip back 15s / forward 30s, adjustable playback speed, and chapter support with a live
  "now playing" chapter indicator for podcasts that include chapters.
- **Mini-player** on every screen that expands into a full now-playing screen with a smooth
  artwork animation and a background that adapts to each episode's artwork.
- **Track what you've listened to** — episodes are marked played automatically when they finish,
  or you can mark them manually or in bulk. Playback position is remembered per episode, and you
  can hide already-played episodes per podcast.
- **Home-screen widget (WIP)** showing the current or last-played episode with play/pause and skip
  controls, always in sync with whatever started playback (app, lock screen, or the widget).
- Follows your phone's **light/dark** setting automatically.

## Install

Download the latest APK from the [Releases page](https://github.com/minospace/onecast/releases)
and install it on your phone:

1. On your phone, open the downloaded `.apk` file and allow installation from this source if
   prompted.
2. That's it — Onecast doesn't need any special permissions or accounts to use.

Android may warn that the app is from an "unknown developer" since Onecast isn't on the Play
Store. This is expected for a small open-source app — it just means there's no review process,
not that anything is wrong with the app.

## For developers

Onecast is built with Kotlin, View/XML UI (no Jetpack Compose), and the
[OneUIProject/oneui-design](https://github.com/OneUIProject/oneui-design) (SESL) widget forks of
AndroidX/Material to get the authentic One UI look. Playback uses Media3/ExoPlayer, persistence
uses Room, and feeds/search use OkHttp + XmlPullParser.

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug   # build + install over adb
```

Requires a JDK (17–21) and the Android SDK; the Gradle wrapper is pinned to 8.7. The project
also opens directly in Android Studio. See [CLAUDE.md](CLAUDE.md) for architecture notes and
build/testing gotchas.
