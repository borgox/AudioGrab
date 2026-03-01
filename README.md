# AudioGrab

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="AudioGrab logo" width="72" height="72" />

[![Release](https://img.shields.io/github/v/release/borgox/AudioGrab)](https://github.com/borgox/AudioGrab/releases/latest)
[![APK](https://img.shields.io/badge/APK-download-blue)](https://github.com/borgox/AudioGrab/releases/latest/download/AudioGrab-v1.0.apk)

AudioGrab turns video links into MP3s with previews, bulk downloads, and a simple on-device workflow.

## Download

- Direct APK: [AudioGrab-v1.0.apk](https://github.com/borgox/AudioGrab/releases/latest/download/AudioGrab-v1.0.apk)
- All releases: https://github.com/borgox/AudioGrab/releases

## Highlights

- Preview the title, channel, and thumbnail before downloading
- MP3 output with quality presets
- Bulk mode for multiple links at once
- Default output to `Music/AudioGrab` or pick a folder

## Supported sources

- Works with any service supported by NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor#supported-services
- Paste a supported video URL; unsupported or blocked sources show an error
- Some sources require cookies; the settings screen includes an optional cookie field

## Tech stack

- Kotlin + Jetpack Compose UI
- NewPipe Extractor for stream info
- FFmpegKit for MP3 conversion
- OkHttp for downloads

## Quick start

1. Paste a link
2. Review the preview
3. Tap Download MP3

## Where files go

- Default: `Music/AudioGrab`
- If you choose a folder, files go there instead

## Limitations

- MP3 output only
- Keep the app open during download and conversion
- DRM/paid/age-restricted content may fail to download

## Build (developers)

```bash
./gradlew assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## License

[MIT](https://github.com/borgox/AudioGrab/blob/main/LICENSE)

## Responsible use

Only download content you have the rights to use.
