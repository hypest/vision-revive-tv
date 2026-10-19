# Vision Revive TV (`vision-revive-tv`)

A lightweight Android TV application to revive over-the-air (DVB-T / DVB-T2) live broadcast television on the **Technicolor DSI724HN** (*Pearl*) set-top box (widely distributed as the *Wind Vision* box in Greece).

![Channel list and playback banner](screenshot-live.png)

## Why This Exists

The Technicolor DSI724HN box has capable hardware (Amlogic S905D, 2 GB RAM, built-in DVB-T/T2 tuner). However, on Android 9 on this box, Android's shared TV provider (`TvProvider`) enforces strict caller isolation: only system apps or the original scanner package (`com.technicolor.android.dtvinput`) can query the channel database.

As a result, standard TV apps like Google Live Channels and Mochi Live TV report *"No channels available"*, even after a successful antenna scan:

![Technicolor OTA channel scan](screenshot-scan.png)

While third-party apps cannot query the channel list table, they can still instruct the system input service to tune to channels directly via `TvView.tune()`.

**Vision Revive TV** provides a dedicated, remote-friendly UI that tunes channels directly through the hardware service and resolves station names using Greek DVB stream triplets (`ONID.TSID.SID`) and system broadcast metadata.

## Features

- **Side Panel Guide**: Press `OK` or `LEFT` on the remote to browse the channel list.
- **Fast Zapping**: Use `UP` / `DOWN` or `CH+` / `CH-` to cycle channels.
- **Direct Number Input**: Enter channel numbers directly (e.g. `0` `4`).
- **OSD Info Banner**: Shows channel number, station name, video resolution (1080p, 576i), and active audio stream.
- **Audio & Subtitles**: Quick toggles on the side panel for multi-language audio tracks and DVB subtitles.
- **Scanner Integration**: Launch the native Technicolor antenna scanner and auto-detect new channel ranges directly from the app menu.

## Quick Install (Pre-built APK)

From a computer on the same local network:

```bash
# 1. Connect via ADB (enable Network Debugging in Developer Options first)
adb connect <box-ip>:5555

# 2. Install the application
adb install -r vision-revive-tv.apk

# 3. Grant log read permission (required for automatic channel name resolution)
adb shell pm grant org.hypest.visionrevivetv android.permission.READ_LOGS
```

### First-Time Setup

1. Open **Vision Revive TV** from your Android TV launcher, or run:
   ```bash
   adb shell am start -n org.hypest.visionrevivetv/.MainActivity
   ```
2. Press `OK` to open the side panel, select **OTA Scan**, and run the Technicolor antenna setup wizard.
3. When the scan finishes, press `BACK` to return to Vision Revive TV.
4. Select **Auto-Detect Active Channels** from the scan menu to detect the active channel ID range.

## Building from Source

This project uses the standard Android Gradle build system. You can open it directly in Android Studio or build it from the command line using the included Gradle wrapper.

### Build Commands

```bash
# Build the signed release APK
./gradlew assembleRelease

# Or build the debug APK
./gradlew assembleDebug
```

The output APK will be generated at:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```text
vision-revive-tv/
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
├── BLOG_POST.md
├── screenshot-scan.png
├── screenshot-live.png
├── vision-revive-tv.apk
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/org/hypest/visionrevivetv/
        │   └── MainActivity.java
        └── res/
            ├── drawable/
            │   ├── banner.png
            │   └── ic_launcher.png
            └── values/
                └── strings.xml
```

## Disclaimer

*Wind, Wind Vision, Nova, and Technicolor are trademarks of their respective owners. This project is an independent open-source effort for hardware interoperability and is not affiliated with, endorsed by, or sponsored by Wind Hellas, Nova, Technicolor, or their affiliates.*
