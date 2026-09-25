# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Mood Journal — a local-first Android journaling app (Kotlin + Jetpack Compose + Room). Single-developer personal project: no backend, no accounts, no network calls, everything on-device. The MVP (entry list, entry editor, mood trend chart) is fully implemented, despite the README's "Status: pre-implementation" line, which is stale — don't take it at face value.

## Commands

Build:
- `./gradlew assembleDebug` — build debug APK (Windows: `gradlew.bat assembleDebug`)
- `./gradlew installDebug` — build and install on a connected device/emulator

Test:
- `./gradlew test` — JVM unit tests (ViewModel tests run against `FakeJournalRepository`, not mocks)
- `./gradlew test --tests "com.example.test.ui.editor.EntryEditorViewModelTest"` — single test class
- `./gradlew connectedAndroidTest` — instrumented tests (Room DAO test + Compose UI e2e test); requires a connected device/emulator — check with `adb devices` first

Lint:
- `./gradlew lint`

If `gradlew` fails with `JAVA_HOME is not set and no 'java' command could be found`, the shell's PATH has no JDK on it — point `JAVA_HOME` at one (e.g. Android Studio's bundled `jbr`) before retrying.

## Architecture

- **Layering**: Compose UI → ViewModel (`StateFlow`) → `JournalRepository` (interface) → Room DAO → SQLite. Deliberately no clean-architecture/multi-module split — per the design spec, the extra indirection wouldn't pay for itself in a single-developer app.
- **No DI framework** (no Hilt/Koin). `MainActivity` builds the one `RoomJournalRepository` (wrapping `AppDatabase.getInstance()`) and passes it down as a plain constructor argument through `MoodJournalNavHost` to each screen. Each screen composable wires its own `ViewModel` with a manual `viewModel(factory = viewModelFactory { initializer { ... } })` block (see `EntryEditorScreen.kt`, `EntryListScreen.kt`, `MoodTrendScreen.kt`) rather than a shared factory or container.
- **`JournalRepository` has two implementations**: `RoomJournalRepository` (production) and `FakeJournalRepository` (`app/src/test/.../repository/FakeJournalRepository.kt`, backed by an in-memory `StateFlow`). Every ViewModel unit test runs against the fake instead of mocking Room.
- **Navigation**: all routes are defined once, in the `Routes` object at the top of `MoodJournalNavHost.kt`. The entry editor route takes an optional `entryId` nav argument (default `0L` = create-new, non-zero = edit-existing) instead of separate create/edit routes.
- **Data model**: a single Room entity, `JournalEntry` (`data/JournalEntry.kt`), with `Mood` (`GREAT/GOOD/OKAY/BAD/AWFUL`) persisted through a `Converters` `TypeConverter`. No second entity is planned for v1.
- **Mood Trend chart** is hand-drawn with Compose `Canvas` (`ui/trend/MoodTrendScreen.kt`) — there is no charting library dependency.
- **Package/project name**: the Gradle root project name (`Test`) and application package (`com.example.test`) are intentionally left over from the original Android Studio scaffold and are *not* meant to be renamed to match "Mood Journal" — a deliberate constraint from the implementation plan, not an oversight.
- **Photos** go through a `PhotoStorage` interface (`FilePhotoStorage` in production, `FakePhotoStorage` in tests), the same interface/real/fake shape as `JournalRepository`. Files live under `context.filesDir/photos/`; `JournalEntry.photoPath` stores only the bare filename, never a `content://` URI — see `docs/superpowers/specs/2026-09-24-photo-attachments-design.md`. There's a 4th nav destination, the Photo Viewer (`Routes.photoViewer(path)`), reachable from both the list thumbnail and the editor's preview.
- **The daily reminder** lives in its own `com.example.test.reminder` package: `ReminderPreferences`/`SharedPreferencesReminderPreferences` (plain `SharedPreferences`, not DataStore) and `ReminderScheduler`/`WorkManagerReminderScheduler` follow the same interface/real/fake shape as everything else here. `ReminderWorker` deliberately does **not** take an injectable repository — like `MainActivity`, it builds `RoomJournalRepository` directly from `AppDatabase.getInstance()`, so there's still no custom `WorkerFactory` or `Application` subclass anywhere in this app. See `docs/superpowers/specs/2026-09-25-daily-reminder-design.md`. There's a 5th nav destination, Settings (`Routes.SETTINGS`), reachable via a gear icon in the Entry List's top bar.

## Scope discipline

This is a deliberately narrow MVP — see `docs/superpowers/specs/2026-09-15-mood-journal-design.md` and the paired implementation plan in the same `docs/superpowers/` tree for the full rationale. Photo attachments and the daily reminder notification shipped as the first two fast-follow phases (`docs/superpowers/specs/2026-09-24-photo-attachments-design.md`, `docs/superpowers/specs/2026-09-25-daily-reminder-design.md`); search/filter remains explicitly out of scope, needing its own design pass before implementation. Don't add it, new architectural layers, or new dependencies without confirming first.
