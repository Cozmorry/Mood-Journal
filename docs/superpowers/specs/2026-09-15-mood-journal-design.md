# Mood Journal — v1 (MVP) Design

## Purpose

A local-first Android journal app. The user writes free-text journal entries and
tags each one with a mood and intensity. Over time, the app visualizes mood
trends. No backend, no accounts — everything lives on-device.

## Scope

**In scope (v1 / MVP):**
- Create, edit, delete journal entries (text + mood + intensity)
- List entries, reverse-chronological
- Mood-over-time chart

**Explicitly out of scope for v1** (planned as fast-follow phases, each will get
its own design/plan when picked up):
- Photo attachments on entries
- Search/filter entries by text, mood, or date range
- Daily reminder notification

## Architecture

Single-module Android app. Standard unidirectional MVVM:

```
Compose UI  -->  ViewModel (StateFlow)  -->  Repository  -->  Room DAO  -->  SQLite
```

No multi-module or clean-architecture layering — this is a single-developer
personal app; the extra indirection wouldn't pay for itself. Repository exists
as a thin wrapper over the DAO so ViewModels aren't coupled directly to Room,
keeping ViewModels testable against a fake.

## Data model

One Room entity, `JournalEntry`:

| Field       | Type      | Notes                                   |
|-------------|-----------|------------------------------------------|
| id          | Long (PK, autogenerate) |                            |
| createdAt   | Instant / epoch millis | set once, on insert         |
| updatedAt   | Instant / epoch millis | updated on every edit        |
| text        | String    | must be non-blank to save                |
| mood        | enum: `GREAT, GOOD, OKAY, BAD, AWFUL` | stored as String or Int via TypeConverter |
| intensity   | Int (1–5) | how strongly the mood is felt            |

A single table is sufficient — the mood set is fixed at compile time, so no
separate moods table is needed.

## Screens & navigation

Compose Navigation with 3 destinations:

1. **Entry List** (start destination) — reverse-chronological list of entries;
   each row shows date, mood emoji/color, and a text preview. FAB opens the
   Entry Editor in "new" mode. Tapping a row opens it in "edit" mode. An icon
   in the top bar navigates to Mood Trend.
2. **Entry Editor** — multiline text field, a row of 5 mood options
   (emoji/color + label), an intensity slider (1–5). Save button disabled
   while text is blank. Delete action available only in edit mode.
3. **Mood Trend** — Canvas-drawn line/dot chart plotting mood+intensity over
   the last 30 days. No charting library; Compose `Canvas` is enough for a
   single trend line.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3 (not yet in the project — needs to be added)
- Room (with Kotlin coroutines/Flow for reactive queries)
- Navigation-Compose
- ViewModel + StateFlow for UI state
- minSdk 24 / compileSdk 37 (unchanged from the current template)

## Error handling

All operations are local, so there's no network error surface to handle.
Concrete cases:
- **Blank entry text** — validated inline in the Entry Editor; Save is
  disabled rather than allowing an invalid save.
- **Schema migrations** — not a v1 concern (first schema), but the Room
  database should be set up with `fallbackToDestructiveMigration()` disabled
  by default so a future schema change fails loudly in dev rather than
  silently wiping data.

## Testing

- **Room DAO tests** — in-memory Room database, verify insert/update/delete
  and ordering of queries.
- **ViewModel tests** — against a fake repository (no real Room), verify
  validation logic (blank text) and state updates.
- **Compose UI test** — one happy-path test: open editor, type text, pick a
  mood, save, see it appear in the list.

## Future phases (not designed yet)

Each of these gets its own brainstorming pass when picked up:
- Photo attachments (storage, permissions, display in list/editor)
- Search/filter (query design, possibly FTS if text search gets slow)
- Daily reminder notification (WorkManager or AlarmManager, notification
  permission on Android 13+, user-configurable time)
