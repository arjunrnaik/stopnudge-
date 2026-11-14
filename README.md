# StopNudge — Bengaluru Destination Alarm for Android

[![StopNudge Android Build](https://github.com/arjunrnaik/stopnudge-/actions/workflows/android_build.yml/badge.svg)](https://github.com/arjunrnaik/stopnudge-/actions/workflows/android_build.yml)

**StopNudge** is an offline-first proximity alarm app designed specifically for Bengaluru transit commuters across **Namma Metro** and **BMTC bus** networks. It sounds a loud wake-up alarm before reaching your destination so you never miss your stop while resting or reading during your commute.

---

## Key Features

1. **Dual Offline SQLite Databases**:
   - **`transit_stops.db` (Static Bundled Asset)**: 83 Namma Metro stations (Purple, Green, Yellow lines) and 5,311 BMTC bus stops with local Kannada (`name_kn`) and English (`name_en`) tags, verified from OpenStreetMap under ODbL 1.0. Fast local search with zero network usage.
   - **`user_data.db` (Isolated User Data)**: Dedicated Room database for saved places, trip histories, and multi-stage alert configurations. Isolated with non-destructive migrations.

2. **Aggressive Battery Kill Protection**:
   - Explicit Foreground Service (`android:foregroundServiceType="location"`) with ongoing notification tracking remaining distance.
   - Built-in System Health & Battery Readiness diagnostics screen with direct shortcuts to disable battery optimizations and manufacturer-specific guidance (MIUI Autostart, Samsung Never Sleeping Apps, ColorOS background activity).
   - Standby watchdog alarm receiver to maintain location sampling reliability.

3. **Multi-Stage Audio & Vibration Engine**:
   - High-priority alarms routed through `AudioAttributes.USAGE_ALARM` (`STREAM_ALARM`) that pierce through Do Not Disturb (DND) and silent switches.
   - Continuous distinct pulse vibration patterns (`VibrationEffect.createWaveform`).
   - Configurable alert buffer distances (200m, 500m, 1km, 2km).

4. **Destination Search & Google Maps Link Parsing**:
   - Instant search across 5,394 Bengaluru transit hubs.
   - Built-in URL regex engine to parse dropped pins, short links, or coordinates pasted directly from Google Maps.
   - Quick-pick shortcuts for major hubs (Majestic, Indiranagar, Silk Board, Whitefield, Airport).

---

## Tech Stack & Architecture

- **Platform**: Android (Min SDK 29 / Android 10, Target SDK 34 / Android 14)
- **UI Framework**: Modern Jetpack Compose with Material 3 BOM (2024.06.00)
- **Architecture**: MVVM with Kotlin Coroutines and StateFlow
- **Persistence**: Android Room 2.6.1 with Google KSP (`com.google.devtools.ksp`)
- **Location Services**: Google Play Services Location (`FusedLocationProviderClient`)
- **CI/CD**: GitHub Actions remote compilation with Temurin JDK 17, Gradle 8.7, and automated debug APK artifact publishing

---

## Downloading the Pre-Built APK

Each push to `main` automatically compiles, runs unit tests, and publishes a fresh debug APK via GitHub Actions:

1. Navigate to the [GitHub Actions Runs](https://github.com/arjunrnaik/stopnudge-/actions).
2. Click on the latest green workflow run (e.g., Run #9).
3. Scroll down to the **Artifacts** section at the bottom of the summary page.
4. Download **`stopnudge-debug-apk`**, extract the zip file, and install `app-debug.apk` onto your Android phone.

---

## Project Structure

```text
stopnudge/
├── .github/workflows/
│   └── android_build.yml          # Automated CI/CD pipeline (Test + Assemble + Artifact Upload)
├── app/
│   ├── src/main/
│   │   ├── assets/databases/
│   │   │   └── transit_stops.db   # 83 Metro + 5,311 Bus stops pre-compiled SQLite asset
│   │   ├── java/com/stopnudge/app/
│   │   │   ├── alarm/             # AlarmController (STREAM_ALARM + pulse vibrator)
│   │   │   ├── data/local/
│   │   │   │   ├── transit/       # TransitDatabase, BusStopEntity, MetroStationEntity, TransitDao
│   │   │   │   └── user/          # UserDatabase, SavedPlaceEntity, TripEntity, AlertStageEntity, UserDao
│   │   │   ├── service/           # TrackingForegroundService & AlarmWatchdogReceiver
│   │   │   ├── tracking/          # LocationTrackingManager (FusedLocationProviderClient + Haversine)
│   │   │   ├── ui/                # Compose screens: Home, TransitStopPicker, Saved, History, BatteryReadiness
│   │   │   ├── util/              # GoogleMapsUrlParser (extracts coordinates from links)
│   │   │   ├── MainActivity.kt    # Main navigation shell
│   │   │   └── StopNudgeApp.kt    # Application class with Notification Channels
│   │   ├── res/                   # Drawables, mipmaps, strings, and themes
│   │   └── AndroidManifest.xml    # Permissions (Location, Foreground Service, Alarms, Notifications)
│   ├── src/test/java/             # Unit tests (Haversine math, Google Maps URL parser)
│   └── build.gradle.kts           # App build configuration (KSP, Compose, Room, Play Services)
├── gradle/
│   ├── libs.versions.toml         # Gradle Version Catalog
│   └── wrapper/                   # Gradle 8.7 Wrapper
├── src/                           # Python OSM transit data extraction & packaging pipeline
│   ├── cleaning/                  # Tag preservation, null operator validation, deduplication
│   └── writers/packager.py        # transit_stops.db generator
└── build.gradle.kts               # Root build configuration
```

---

## OpenStreetMap Data License & Attribution

Transit stop data is derived from [OpenStreetMap](https://www.openstreetmap.org/) contributors under the **Open Database License (ODbL) 1.0**.
- Untagged bus stops retain explicit `operator = null` to prevent spurious BMTC tagging.
- Metro stations are reconciled against ground-truth BMRCL Phase 1 & 2 corridor alignments.
