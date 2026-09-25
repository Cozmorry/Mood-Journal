# Tags/Categories on Entries — Design

## Purpose

Let the user attach freeform tags to a journal entry (e.g. `work`,
`stressed`) for lightweight organization, without adding a management
screen or any filtering UI. This is the third of the fast-follow phases
picked up after [photo attachments](2026-09-24-photo-attachments-design.md)
and the [daily reminder](2026-09-25-daily-reminder-design.md).

## Scope

**In scope:**
- Multiple freeform tags per entry, created on the fly while writing/editing
- Tag chips shown in the Entry Editor (add/remove) and the Entry List (display)
- Autocomplete suggestions in the editor, drawn from tags used on other entries

**Explicitly out of scope for this phase:**
- Any tag-management screen (rename, delete, merge tags globally) — tags
  only exist as freeform text on individual entries
- Filtering or searching the Entry List by tag — that belongs to the
  separate search/filter phase already on the roadmap; this phase only
  attaches and displays tags
- Case-insensitive tag normalization/deduplication across entries — `work`
  and `Work` are stored and shown as distinct tags; there is no registry to
  reconcile casing against without a management screen

## Architecture

No new Room entity or join table. Tags are a denormalized column on the
existing `JournalEntry` entity, following the same "no second entity"
precedent this codebase already uses for `Mood` (a `TypeConverter`) and
`photoPath` (a plain additive column):

```
Compose UI  -->  ViewModel (StateFlow)  -->  Repository  -->  Room DAO  -->  SQLite
```

A join-table (`Tag` + `EntryTagCrossRef`) approach was considered and
rejected: it would be this app's first relational join table, for a
personal-scale dataset that doesn't need efficient cross-entry tag queries
at the SQL level. The denormalized column also sidesteps orphaned-row
cleanup entirely — deleting an entry needs no reconciliation step, since
its tags live on the entry row itself and disappear with it.

## Data model & persistence

`JournalEntry` gains one new field:

```kotlin
data class JournalEntry(
    // ...existing fields unchanged...
    val tags: List<String> = emptyList(),
)
```

Backed by a new column, `tags: TEXT NOT NULL DEFAULT ''`, via one additive
Room migration (`ALTER TABLE ... ADD COLUMN tags TEXT NOT NULL DEFAULT ''`)
— the same shape as the existing `photoPath` migration.

`Converters.kt` gains a `List<String> <-> String` pair, joining/splitting on
a comma delimiter:

```kotlin
@TypeConverter
fun fromTagList(tags: List<String>): String = tags.joinToString(",")

@TypeConverter
fun toTagList(raw: String): List<String> = splitTags(raw)

internal fun splitTags(raw: String): List<String> =
    if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
```

`splitTags` is `internal`, not `private`, so both the `TypeConverter` and
the tag-aggregation query described below reuse the same parsing logic
rather than duplicating it.

No `JournalRepository` interface change is needed for basic CRUD — tags
travel on the `JournalEntry` object through insert/update exactly like
`text`, `mood`, and `photoPath` already do.

## Tag input & storage rules

- A comma or pressing Enter/Done in the tag-input field always commits the
  current text as a tag and clears the input for the next one. This means
  a comma can never end up stored inside a tag's own text — no separate
  validation is needed to protect the delimiter.
- Blank/whitespace-only input is ignored on commit (not added as a chip).
- A tag that exactly duplicates an existing tag already on the same entry
  is ignored (no duplicate chips on one entry). Comparison is exact-match,
  case-sensitive, consistent with the no-normalization decision above.
- Tags are stored exactly as typed (trimmed of leading/trailing whitespace,
  otherwise unmodified) — no lowercasing, no cross-entry deduplication.

## Autocomplete suggestions

To reduce accidental near-duplicate tags (e.g. `work` vs `Work` typed on
different days), the editor suggests previously-used tags as the user
types:

- `JournalEntryDao` gains `@Query("SELECT tags FROM entries") fun getAllTagsRaw(): Flow<List<String>>`
  — returns each entry's raw stored `tags` column value.
- `JournalRepository` gains `getAllTags(): Flow<List<String>>` — collects
  the raw values, applies `splitTags` to each, flattens, dedupes, and sorts
  the result into one flat list of every distinct tag used across all
  entries.
- `RoomJournalRepository` implements this via the DAO query above.
  `FakeJournalRepository` implements it by deriving the same result
  in-memory from its existing `StateFlow` of entries, matching the shape
  of its other methods.
- `EntryEditorViewModel` exposes `existingTagSuggestions: StateFlow<List<String>>`
  by observing `getAllTags()`. The editor UI filters this list by a
  case-insensitive "contains" match against the tag-input field's
  in-progress text, capped to the first 5 matches, shown as tappable
  suggestion chips beneath the input. Tapping a suggestion commits it as a
  tag on the current entry exactly as pressing Enter would (subject to the
  same blank/duplicate rules above).
- No pagination or size limiting is applied to the underlying suggestion
  set beyond the 5-match display cap — personal-scale journal data doesn't
  need it (YAGNI).

## Screens & navigation

No new nav destination. Two existing screens change:

- **Entry Editor**: a chip-input row added below the mood/intensity
  section. Existing chips show a small "×" to remove them. Below the
  input, up to 5 filtered suggestion chips appear as described above.
- **Entry List**: each row gains a small row of tag chips, reusing the
  existing dense-row style alongside the mood emoji/photo thumbnail/text
  preview. Capped at showing the first 3 tags per entry (an entry's 4th+
  tag is simply not shown in the list row), with no "+N more" affordance —
  keeps row height bounded without adding UI this app-scale doesn't need.

## Error handling

- Migration default (`''` for all pre-existing rows) must round-trip as an
  empty list, not a list containing one empty string — `splitTags` guards
  on `raw.isBlank()` before splitting.
- Deleting an entry needs no tag cleanup step — tags live on the entry row
  itself, so there is no orphaned join-table state to reconcile (a direct
  consequence of the denormalized-column choice in Architecture).
- No new permission, network, or background-work surface is introduced —
  this phase is pure local read/write, same trust boundary as the rest of
  the app.

## Testing

- A converter round-trip test (empty list ↔ `''`, single tag, multiple
  tags, trimming, blank-string guard) — added alongside wherever
  `Converters` is currently exercised.
- `AppDatabaseMigrationTest` gains a case verifying the new migration
  applies cleanly and the default-empty `tags` column round-trips,
  following the existing `photoPath` migration test's pattern.
- `EntryEditorViewModelTest` gains cases: add a tag chip, remove a chip,
  duplicate-tag-on-same-entry ignored, blank input ignored, save persists
  tags via `FakeJournalRepository`, and suggestion filtering narrows
  correctly against tags from other entries preloaded into the fake.
- The existing Compose UI e2e happy-path test gets one small addition: add
  a tag while creating an entry, confirm it renders as a chip in the list
  row — kept as a minimal extension rather than a new parallel UI test,
  consistent with this project's existing "one happy-path e2e test"
  convention.
