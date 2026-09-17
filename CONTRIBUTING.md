# Contributing to Mood Journal

Thanks for your interest in this project. It's primarily a personal, single-
developer app, but contributions, bug reports, and suggestions are welcome.

## Before you start

For anything beyond a trivial fix (typos, small bugs), please open an issue
first to discuss the change. This project deliberately keeps a narrow MVP
scope — see the [design spec](docs/superpowers/specs/2026-09-15-mood-journal-design.md)
for what's in scope and what's an explicit future phase — so it's worth
confirming a feature fits before investing time in a PR.

## Development setup

1. Fork and clone the repo.
2. Open the project in [Android Studio](https://developer.android.com/studio)
   and let it sync Gradle, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
3. See the [README](README.md#getting-started) for prerequisites and how to
   run the app on a device or emulator.

## Making changes

- Create a branch off `main` with a descriptive name
  (e.g. `fix/entry-editor-crash`, `feature/mood-trend-chart`).
- Keep pull requests focused — one logical change per PR.
- Match the existing code style (Kotlin, Jetpack Compose, MVVM as described
  in the design spec). Don't introduce new architectural layers or
  dependencies without discussing them first.
- Add or update tests for any behavior change:
  ```bash
  ./gradlew test                  # unit tests
  ./gradlew connectedAndroidTest  # instrumented tests (needs a device/emulator)
  ```

## Commit messages

Write clear, descriptive commit messages that explain *why* a change was
made, not just what changed.

## Submitting a pull request

1. Make sure `./gradlew test` passes locally.
2. Push your branch and open a PR against `main`.
3. Describe what the change does and why, and link any related issue.
4. Be responsive to review feedback — this keeps things moving for everyone.

## Reporting bugs

Open an issue with:
- Steps to reproduce
- Expected vs. actual behavior
- Device/emulator and Android version, if relevant

## Code of conduct

This project follows a [Code of Conduct](CODE_OF_CONDUCT.md). Participation
implies agreement to abide by it.
