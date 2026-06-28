# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.example.simpleprojectmap.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

Single-module Android app (`app/`) using Jetpack Compose with Material3.

- **Entry point:** `MainActivity.kt` — sets up edge-to-edge display and hosts the Compose content tree.
- **Theme:** `ui/theme/` contains `Theme.kt` (color scheme selection with dynamic color on Android 12+), `Color.kt` (seed colors), and `Type.kt` (typography). The theme wrapper is `SimpleProjectMapTheme`.
- **Package:** `com.example.simpleprojectmap`
- **minSdk:** 24, **targetSdk/compileSdk:** 36
- **AGP:** 9.2.1, **Kotlin:** 2.2.10, **Compose BOM:** 2026.02.01

All dependency versions are centralized in `gradle/libs.versions.toml` (version catalog). Add new dependencies there before referencing them in `app/build.gradle.kts`.
