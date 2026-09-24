# Photo Attachments — Design

## Purpose

Let the user attach one photo to a journal entry, from either the gallery or
the camera, and see it alongside the entry in the list and editor. This is
the first of the three fast-follow phases called out in the [MVP
design](2026-09-15-mood-journal-design.md#future-phases-not-designed-yet).

## Scope

**In scope:**
- One photo per entry (add, replace, remove)
- Add via the system Photo Picker (gallery) or the device camera
- Thumbnail in the Entry List row; preview in the Entry Editor
- Full-screen photo viewer, reachable from either thumbnail

**Explicitly out of scope for this phase:**
- Multiple photos per entry
- Pinch-to-zoom/pan in the full-screen viewer
- Cropping/editing the photo before attaching
- Cleanup of orphaned photo files when a photo is picked but the entry is
  never saved (see [Error handling](#error-handling))

## Architecture

Extends the existing unidirectional flow with one new subsystem:

```
Compose UI  -->  ViewModel (StateFlow)  -->  Repository  -->  Room DAO  -->  SQLite
                              \-->  PhotoStorage  -->  app-private files dir
```

`PhotoStorage` is a new interface, following the same shape as
`JournalRepository`: a real implementation backed by the filesystem, and a
fake backed by in-memory state for ViewModel tests. ViewModels depend on the
interface, never the concrete implementation.

```kotlin
interface PhotoStorage {
    suspend fun copyFrom(uri: Uri): String   // gallery pick -> stored filename
    fun newCameraCaptureUri(): Uri           // FileProvider URI for camera to write into
    fun delete(photoPath: String)
    fun resolve(photoPath: String): File     // stored filename -> loadable File
}
```

- `FilePhotoStorage(context)` — production implementation. Stores files under
  `context.filesDir/photos/`, named `photo_<epochMillis>_<uuid>.jpg`.
  `copyFrom` opens an `InputStream` on the picked `content://` URI and copies
  bytes into a new file in that directory. `newCameraCaptureUri` creates an
  empty file in the same directory and returns a `FileProvider` URI for it
  (camera writes directly there — no separate copy step).
- `FakePhotoStorage` (`app/src/test/.../repository/FakePhotoStorage.kt`) — no
  real disk I/O; tracks calls and returns deterministic fake filenames, for
  ViewModel unit tests.
- Stored `photoPath` values are **bare filenames only**, no subdirectories —
  this keeps them safe to pass as a Nav Compose string route argument later
  without URI-encoding concerns.
- No new architectural layer beyond this one interface; everything else
  keeps using the existing MVVM shape.

## Data model

One new nullable column on the existing `JournalEntry` entity (table name is
`journal_entries`):

| Field       | Type      | Notes                                          |
|-------------|-----------|--------------------------------------------------|
| `photoPath` | `String?` | bare filename under `filesDir/photos/`; `null` = no photo |

Room migration `1 → 2`, additive only:

```sql
ALTER TABLE journal_entries ADD COLUMN photoPath TEXT
```

Existing rows keep all their data; `photoPath` defaults to `NULL`. The
migration is registered explicitly on `AppDatabase`'s `Room.databaseBuilder`
— `fallbackToDestructiveMigration()` stays unused, consistent with the MVP
design's error-handling stance of failing loudly on an unhandled schema
change rather than silently wiping the user's entries.

No backup-rules changes needed: `app/src/main/res/xml/data_extraction_rules.xml`
currently excludes nothing, so `filesDir/photos/` is included in Android's
Auto Backup by default, same as the Room database — that's the desired
behavior (photos travel with the rest of the journal).

## Screens & navigation

Extends the existing 3-destination Compose Navigation graph with a 4th:

1. **Entry List** — each row gains a small square thumbnail (leading edge,
   before the date/mood/text preview) when the entry has a `photoPath`.
   Tapping the thumbnail navigates to the Photo Viewer; tapping anywhere else
   on the row opens the Entry Editor, unchanged from today.
2. **Entry Editor** — a new photo section between the text field and the
   mood row:
   - No photo: two icon buttons, "Add from gallery" and "Take photo."
   - Photo present: a preview thumbnail (tap → Photo Viewer) plus a remove
     (✕) affordance. Tapping either add button again replaces the current
     photo.
   - Gallery button launches `ActivityResultContracts.PickVisualMedia()` —
     the system Photo Picker. No runtime permission is requested or needed;
     the picker falls back gracefully to a documents-style picker on API
     levels below where the native picker exists, with the same no-permission
     behavior, all the way down to `minSdk` 24.
   - Camera button requests `android.permission.CAMERA` at runtime via
     `ActivityResultContracts.RequestPermission()` first. On grant, launches
     `ActivityResultContracts.TakePicture()` targeting the URI returned by
     `PhotoStorage.newCameraCaptureUri()`. On denial, an inline message
     appears near the button ("Camera permission needed to take a photo");
     the gallery option remains available regardless.
   - Requires a new `<provider>` entry (`androidx.core.content.FileProvider`,
     authority `com.example.test.fileprovider`) and a matching
     `res/xml/file_paths.xml` exposing the `photos/` subdirectory of
     `filesDir` — there is no `FileProvider` declared in the manifest today.
3. **Mood Trend** — unchanged.
4. **Photo Viewer** *(new)* — full-screen `Image` of the resolved photo file,
   plus a close/back button. No zoom/pan, no other chrome. New route
   `Routes.PHOTO_VIEWER`, taking the photo's filename as a string nav
   argument. Reachable from the Entry List thumbnail and the Entry Editor
   thumbnail.

## Image loading

`coil-compose` is added as a new dependency for every place a photo is
rendered (list thumbnail, editor preview, full-screen viewer). It handles
downsampling, memory/disk caching, and lifecycle-aware loading, avoiding
hand-rolled `BitmapFactory` sampling logic — camera-captured photos can be
several megabytes, and decoding them at full resolution for every list row
would risk memory pressure on a long entry list.

## Editor state changes

`EntryEditorUiState` gains `photoPath: String?`. `EntryEditorViewModel` gains:

- `onPhotoPicked(path: String)` — sets `photoPath`, replacing any previous
  value held in editor state. The file behind a replaced or removed value is
  never deleted here, only at `save()` time — see
  [Error handling](#error-handling).
- `onPhotoRemoved()` — clears `photoPath` to `null`.

`save()` and `delete()` are unaffected in shape; `save()` persists whatever
`photoPath` is currently in state, and `delete()` is extended to also call
`PhotoStorage.delete(photoPath)` when the deleted entry had one, so removing
an entry doesn't leak its photo file.

## Error handling

- **Camera permission denied** — inline message near the camera button, no
  crash; gallery attach still works.
- **Copy/write I/O failure** (low storage, unreadable URI, etc.) — caught,
  surfaced as a snackbar, prior photo state in the editor is left untouched.
- **Orphaned files (accepted limitation)** — a copied/captured file becomes
  orphaned on disk, uncleaned, in two cases: (1) the user attaches or
  replaces a photo and then leaves the editor without saving (back press,
  process death), or (2) the user picks more than one photo within the same
  editing session before saving — only the final pick's file ends up
  referenced by the saved entry; earlier picks in that session are
  discarded but not deleted. Proper cleanup would need process-death-safe
  tracking (WorkManager-tier complexity) for a case that, on a personal
  single-device journal app, costs at most a few stray image files.
  Accepted as a documented trade-off for this phase rather than built now.
- **Replacing or removing a saved photo** — file deletion for the
  *previously-persisted* `photoPath` happens only at `save()` time, comparing
  the entry's old persisted `photoPath` to the new one being saved: if they
  differ (replaced with a new photo, or cleared to `null` via
  `onPhotoRemoved`), the old file is deleted after the new state is
  successfully persisted. Backing out of the editor without saving always
  leaves the previously-saved photo intact.

## Testing

- **Room migration test** (new, instrumented, alongside `JournalEntryDaoTest`)
  — runs the 1→2 migration against a pre-populated v1 database, asserts
  existing rows are unchanged and `photoPath` reads back as `NULL`.
- **`FakePhotoStorage`** — new fake, same role as `FakeJournalRepository`,
  used by `EntryEditorViewModelTest`.
- **`EntryEditorViewModelTest`** — extended with cases for `onPhotoPicked`,
  `onPhotoRemoved`, and save-with-photo state transitions, all against the
  fakes (no real Room, no real file I/O).
- **Known gap**: the existing Compose e2e test (`EntryCreationFlowTest`) is
  *not* extended to drive the real system Photo Picker or camera — those
  launch external activities/processes that instrumented UI tests can't
  reliably automate. Photo-related UI (button presence, thumbnail rendering
  given a pre-set fake `photoPath`) is covered at the ViewModel/fake level
  only; there is no automated coverage of the actual picker/camera
  round-trip. This is a known, accepted coverage gap for this phase.
