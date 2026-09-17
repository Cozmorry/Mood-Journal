# Mood Journal

A local-first Android journal app. Write free-text entries, tag each one with
a mood and intensity, and watch trends emerge over time — no backend, no
accounts, no network calls. Everything lives on-device.

> **Status: pre-implementation.** The repository currently contains the
> stock Android Studio project scaffold plus a full design spec and
> implementation plan for the MVP (see [Documentation](#documentation)
> below). None of the app code described in this README has been built yet.

## Table of contents

- [Purpose](#purpose)
- [Planned features](#planned-features)
- [Architecture](#architecture)
- [Data model](#data-model)
- [Screens & navigation](#screens--navigation)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [Documentation](#documentation)

## Purpose

Mood Journal is a small, single-developer personal project: a private space
to log how you're feeling alongside a written entry, then look back at the
pattern over time. It is intentionally simple — no sync, no sharing, no
server component. All data is stored locally on the device using Room.

## Planned features

**In scope for v1 (MVP):**

- Create, edit, and delete journal entries (text + mood + intensity)
- Reverse-chronological list of all entries
- A mood-over-time chart summarizing recent entries

**Explicitly out of scope for v1** (each is a candidate fast-follow, to be
designed separately when picked up):

- Photo attachments on entries
- Search/filter by text, mood, or date range
- Daily reminder notifications

## Architecture

Single-module Android app using a standard unidirectional MVVM flow:

```
Compose UI  -->  ViewModel (StateFlow)  -->  Repository  -->  Room DAO  -->  SQLite
```

There is deliberately no multi-module or clean-architecture layering — this
is a single-developer personal app, and the extra indirection wouldn't pay
for itself. The `Repository` exists only as a thin wrapper over the Room DAO
so ViewModels aren't coupled directly to Room, which keeps them testable
against a fake.

## Data model

A single Room entity, `JournalEntry`, is sufficient since the set of moods is
fixed at compile time:

| Field       | Type                                    | Notes                                      |
|-------------|------------------------------------------|---------------------------------------------|
| `id`        | `Long` (PK, autogenerate)                |                                               |
| `createdAt` | Instant / epoch millis                   | set once, on insert                          |
| `updatedAt` | Instant / epoch millis                   | updated on every edit                        |
| `text`      | `String`                                 | must be non-blank to save                    |
| `mood`      | enum: `GREAT, GOOD, OKAY, BAD, AWFUL`    | stored via a Room `TypeConverter`             |
| `intensity` | `Int` (1–5)                              | how strongly the mood is felt                |

## Screens & navigation

Compose Navigation with three destinations:

1. **Entry List** (start destination) — reverse-chronological list of
   entries; each row shows date, mood emoji/color, and a text preview. A FAB
   opens the Entry Editor in "new" mode; tapping a row opens it in "edit"
   mode. A top-bar icon navigates to Mood Trend.
2. **Entry Editor** — multiline text field, a row of five mood options
   (emoji/color + label), and an intensity slider (1–5). Save is disabled
   while the text is blank. Delete is available only in edit mode.
3. **Mood Trend** — a Canvas-drawn line/dot chart plotting mood + intensity
   over the last 30 days. No charting library is used; Compose `Canvas` is
   enough for a single trend line.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3 (not yet added to the project)
- Room, with Kotlin coroutines/Flow for reactive queries
- Navigation-Compose
- ViewModel + StateFlow for UI state
- `minSdk` 24 / `compileSdk` 37 / `targetSdk` 37

See [`gradle/libs.versions.toml`](gradle/libs.versions.toml) for exact
dependency versions once they land.

## Project structure

```
app/
  src/main/java/com/example/test/   # application code (package retained from the original scaffold)
  src/main/AndroidManifest.xml
  src/main/res/                     # resources (drawables, mipmaps, values)
  src/test/                         # local unit tests (JVM)
  src/androidTest/                  # instrumented tests (device/emulator)
docs/superpowers/
  specs/                            # design specs
  plans/                            # step-by-step implementation plans
gradle/                             # version catalog + wrapper
```

> Note: the Gradle root project name is `Test` and the app package is
> `com.example.test`, both inherited from the original Android Studio
> scaffold. They are intentionally left unchanged (see the implementation
> plan's global constraints) even though the app is now called Mood Journal.

## Getting started

**Prerequisites:**

- [Android Studio](https://developer.android.com/studio) (recent stable
  version) with the Android SDK for API 37 installed
- A device or emulator running Android 7.0 (API 24) or higher

**Clone and open:**

```bash
git clone https://github.com/Cozmorry/Mood-Journal.git
cd Mood-Journal
```

Open the folder in Android Studio and let it sync Gradle, or build from the
command line:

```bash
# macOS/Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

Run on a connected device or emulator with the ▶ button in Android Studio,
or:

```bash
./gradlew installDebug
```

## Testing

The design calls for three layers of tests once the app is built:

- **Room DAO tests** — in-memory Room database; verify insert/update/delete
  and query ordering.
- **ViewModel tests** — against a fake repository (no real Room); verify
  validation logic (blank text) and state updates.
- **Compose UI test** — one happy-path test: open the editor, type text,
  pick a mood, save, and confirm it appears in the list.

Run local unit tests:

```bash
./gradlew test
```

Run instrumented tests (requires a connected device or running emulator —
check with `adb devices` first):

```bash
./gradlew connectedAndroidTest
```

## Roadmap

Post-MVP phases, each to get its own design pass when picked up:

- Photo attachments (storage, permissions, display in list/editor)
- Search/filter (query design, possibly full-text search if needed)
- Daily reminder notification (WorkManager/AlarmManager, Android 13+
  notification permission, user-configurable time)

## Documentation

Full design and implementation detail lives under `docs/superpowers/`:

- [Design spec](docs/superpowers/specs/2026-09-15-mood-journal-design.md) —
  purpose, scope, architecture, data model, and error-handling decisions.
- [Implementation plan](docs/superpowers/plans/2026-09-15-mood-journal-mvp.md) —
  the task-by-task build plan derived from the spec.
