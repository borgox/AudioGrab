# AudioGrab

AudioGrab is an Android app that turns video links into MP3 files with previews, bulk downloads, and a clean on-device workflow. It uses NewPipeExtractor for metadata and stream discovery, and FFmpeg for audio conversion.

## Features

- Single and bulk link downloads
- Metadata previews (title, channel, thumbnail)
- MP3 conversion with quality presets
- Optional custom save folder (SAF)
- Public Music/AudioGrab default output

## Build

```bash
./gradlew assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- For some hosts, downloads may require additional headers or cookies.
- Output defaults to `Music/AudioGrab` unless a custom folder is selected.

## License

MIT
