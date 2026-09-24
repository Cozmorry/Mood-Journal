# Photo Attachments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user attach one photo (from the gallery or the camera) to a journal entry, see a thumbnail in the list and editor, and view it full-screen.

**Architecture:** Extends the existing unidirectional MVVM flow with one new parallel subsystem: `Compose UI -> ViewModel (StateFlow) -> PhotoStorage -> app-private files dir`, alongside the existing `-> Repository -> Room DAO -> SQLite` path. `PhotoStorage` follows the same interface + real-impl + fake-for-tests shape as `JournalRepository`.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Room (KSP), Navigation-Compose, ViewModel + StateFlow, `androidx.activity` Photo Picker + camera contracts, `androidx.core` `FileProvider`, Coil (`coil-compose`) for image loading.

**Spec:** `docs/superpowers/specs/2026-09-24-photo-attachments-design.md`

## Global Constraints

- Package/namespace: `com.example.test` (unchanged — do not rename).
- minSdk 24, compileSdk 37, targetSdk 37 (unchanged).
- One photo per entry, stored as a bare filename (no subdirectories) in a new nullable `JournalEntry.photoPath: String?` column.
- Photo files live under `context.filesDir/photos/`, copied there on pick/capture — never reference a `content://` URI directly in the database.
- Room migration `1 -> 2` must be additive (`ALTER TABLE ... ADD COLUMN`); `fallbackToDestructiveMigration()` stays unused.
- Gallery picking uses the system Photo Picker (`ActivityResultContracts.PickVisualMedia`) — no runtime storage permission requested.
- Camera capture requests `android.permission.CAMERA` at runtime; denial must not crash and must leave the gallery option usable.
- No zoom/pan in the full-screen viewer; no multi-photo support; out of scope per the spec.
- Orphaned photo files from an abandoned/replaced edit are an accepted limitation for this phase — do not build cleanup machinery for it.
- Connected/instrumented tests require an emulator or physical device; confirm one is available with `adb devices` before running them.

---

## Task 1: Coil dependency + PhotoStorage subsystem

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/test/repository/PhotoStorage.kt`
- Create: `app/src/main/java/com/example/test/repository/FilePhotoStorage.kt`
- Create: `app/src/test/java/com/example/test/repository/FakePhotoStorage.kt`
- Test: `app/src/androidTest/java/com/example/test/repository/FilePhotoStorageTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `data class CameraCapture(val uri: Uri, val filename: String)`; `PhotoStorage` interface (`suspend fun copyFrom(uri: Uri): String`, `fun newCameraCapture(): CameraCapture`, `fun delete(photoPath: String)`, `fun resolve(photoPath: String): File`); `FilePhotoStorage(context: Context)` implementing it against `context.filesDir/photos/`; `FakePhotoStorage` (in-memory, tracks `copiedUris`/`deletedPaths` for assertions) for later ViewModel tests.

- [ ] **Step 1: Add the Coil dependency**

In `gradle/libs.versions.toml`, add to `[versions]` (after `coroutines`):

```toml
coil = "2.7.0"
```

Add to `[libraries]` (after `kotlinx-coroutines-test`):

```toml
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

In `app/build.gradle.kts`, add inside the `dependencies { ... }` block, after `implementation(libs.androidx.navigation.compose)`:

```kotlin
    implementation(libs.coil.compose)
```

- [ ] **Step 2: Verify the dependency resolves**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Write the failing instrumented test for FilePhotoStorage**

Create `app/src/androidTest/java/com/example/test/repository/FilePhotoStorageTest.kt`:

```kotlin
package com.example.test.repository

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilePhotoStorageTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storage = FilePhotoStorage(context)

    @Test
    fun copyFrom_copiesSourceBytesIntoAStoredFile() = runBlocking {
        val source = File.createTempFile("source", ".jpg", context.cacheDir)
        source.writeBytes(byteArrayOf(1, 2, 3, 4))

        val filename = storage.copyFrom(Uri.fromFile(source))

        val stored = storage.resolve(filename)
        assertTrue(stored.exists())
        assertEquals(listOf<Byte>(1, 2, 3, 4), stored.readBytes().toList())
    }

    @Test
    fun newCameraCapture_returnsAWritableUriAndMatchingFilename() {
        val capture = storage.newCameraCapture()

        val resolved = storage.resolve(capture.filename)
        assertTrue(resolved.exists())
        context.contentResolver.openOutputStream(capture.uri)?.use { it.write(byteArrayOf(9)) }
        assertEquals(listOf<Byte>(9), resolved.readBytes().toList())
    }

    @Test
    fun delete_removesTheStoredFile() = runBlocking {
        val source = File.createTempFile("source", ".jpg", context.cacheDir)
        source.writeBytes(byteArrayOf(1))
        val filename = storage.copyFrom(Uri.fromFile(source))

        storage.delete(filename)

        assertFalse(storage.resolve(filename).exists())
    }
}
```

- [ ] **Step 4: Run it to confirm it fails to compile**

Confirm a device is available: `adb devices` (must list at least one).
Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.test.repository.FilePhotoStorageTest"`
Expected: FAIL with "unresolved reference: FilePhotoStorage"

- [ ] **Step 5: Create the PhotoStorage interface**

Create `app/src/main/java/com/example/test/repository/PhotoStorage.kt`:

```kotlin
package com.example.test.repository

import android.net.Uri
import java.io.File

data class CameraCapture(val uri: Uri, val filename: String)

interface PhotoStorage {
    suspend fun copyFrom(uri: Uri): String
    fun newCameraCapture(): CameraCapture
    fun delete(photoPath: String)
    fun resolve(photoPath: String): File
}
```

- [ ] **Step 6: Create the FilePhotoStorage implementation**

Create `app/src/main/java/com/example/test/repository/FilePhotoStorage.kt`:

```kotlin
package com.example.test.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class FilePhotoStorage(private val context: Context) : PhotoStorage {
    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    private fun newFilename() = "photo_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"

    override suspend fun copyFrom(uri: Uri): String {
        val filename = newFilename()
        val destination = File(photosDir, filename)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open input stream for $uri")
        input.use { stream -> destination.outputStream().use { stream.copyTo(it) } }
        return filename
    }

    override fun newCameraCapture(): CameraCapture {
        val filename = newFilename()
        val destination = File(photosDir, filename)
        destination.createNewFile()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destination,
        )
        return CameraCapture(uri = uri, filename = filename)
    }

    override fun delete(photoPath: String) {
        File(photosDir, photoPath).delete()
    }

    override fun resolve(photoPath: String): File = File(photosDir, photoPath)
}
```

- [ ] **Step 7: Run the test to confirm it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.test.repository.FilePhotoStorageTest"`
Expected: all 3 tests PASS

- [ ] **Step 8: Create the fake for later ViewModel tests**

Create `app/src/test/java/com/example/test/repository/FakePhotoStorage.kt`:

```kotlin
package com.example.test.repository

import android.net.Uri
import java.io.File

class FakePhotoStorage : PhotoStorage {
    val copiedUris = mutableListOf<Uri>()
    val deletedPaths = mutableListOf<String>()
    private var nextId = 1

    override suspend fun copyFrom(uri: Uri): String {
        copiedUris += uri
        return "fake_photo_${nextId++}.jpg"
    }

    override fun newCameraCapture(): CameraCapture {
        val filename = "fake_photo_${nextId++}.jpg"
        return CameraCapture(uri = Uri.parse("content://fake/$filename"), filename = filename)
    }

    override fun delete(photoPath: String) {
        deletedPaths += photoPath
    }

    override fun resolve(photoPath: String): File = File("/fake/$photoPath")
}
```

- [ ] **Step 9: Verify the fake compiles**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/test/repository/PhotoStorage.kt app/src/main/java/com/example/test/repository/FilePhotoStorage.kt app/src/test/java/com/example/test/repository/FakePhotoStorage.kt app/src/androidTest/java/com/example/test/repository/FilePhotoStorageTest.kt
git commit -m "feat: add PhotoStorage subsystem and Coil dependency"
```

---

## Task 2: Room migration — JournalEntry.photoPath

**Files:**
- Modify: `app/src/main/java/com/example/test/data/JournalEntry.kt`
- Modify: `app/src/main/java/com/example/test/data/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/example/test/data/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: nothing new (extends the existing `JournalEntry`/`AppDatabase` from the MVP).
- Produces: `JournalEntry.photoPath: String? = null` (new field, source-compatible with every existing call site); `AppDatabase.MIGRATION_1_2` (public, `androidx.room.migration.Migration`); `AppDatabase` bumped to version 2.

No production code depends on this yet — this task only proves the schema evolves safely.

- [ ] **Step 1: Write the failing migration test**

Create `app/src/androidTest/java/com/example/test/data/AppDatabaseMigrationTest.kt`:

```kotlin
package com.example.test.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"
    private var database: AppDatabase? = null

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_preservesExistingRowsAndAddsNullablePhotoPath() = runBlocking {
        context.deleteDatabase(dbName)
        val v1Database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        v1Database.execSQL(
            "CREATE TABLE journal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, text TEXT NOT NULL, " +
                "mood TEXT NOT NULL, intensity INTEGER NOT NULL)",
        )
        v1Database.execSQL(
            "INSERT INTO journal_entries (createdAt, updatedAt, text, mood, intensity) " +
                "VALUES (1, 1, 'Pre-migration entry', 'OKAY', 3)",
        )
        v1Database.version = 1
        v1Database.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        database = migrated

        val entries = migrated.journalEntryDao().getAll().first()

        assertEquals(1, entries.size)
        assertEquals("Pre-migration entry", entries[0].text)
        assertEquals(Mood.OKAY, entries[0].mood)
        assertNull(entries[0].photoPath)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `JournalEntry` has no `photoPath` property and `AppDatabase.MIGRATION_1_2` doesn't exist.

- [ ] **Step 3: Add photoPath to the entity**

In `app/src/main/java/com/example/test/data/JournalEntry.kt`, replace the full contents:

```kotlin
package com.example.test.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val text: String,
    val mood: Mood,
    val intensity: Int,
    val photoPath: String? = null,
)
```

- [ ] **Step 4: Add the migration and bump the database version**

In `app/src/main/java/com/example/test/data/AppDatabase.kt`, replace the full contents:

```kotlin
package com.example.test.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JournalEntry::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN photoPath TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mood-journal.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 5: Run the migration test and the existing DAO tests**

Confirm a device is available: `adb devices` (must list at least one).
Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.test.data.AppDatabaseMigrationTest" --tests "com.example.test.data.JournalEntryDaoTest"`
Expected: all tests PASS (the DAO tests still pass unchanged — `photoPath` defaults to `null`, so every existing `JournalEntry(...)` call site still compiles).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/test/data/JournalEntry.kt app/src/main/java/com/example/test/data/AppDatabase.kt app/src/androidTest/java/com/example/test/data/AppDatabaseMigrationTest.kt
git commit -m "feat: add photoPath column via additive Room migration"
```

---

## Task 3: FileProvider + EntryEditorViewModel photo state + EntryEditorScreen UI

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/java/com/example/test/ui/editor/EntryEditorViewModel.kt` (replace entire file)
- Modify: `app/src/test/java/com/example/test/ui/editor/EntryEditorViewModelTest.kt` (replace entire file)
- Modify: `app/src/main/java/com/example/test/ui/editor/EntryEditorScreen.kt` (replace entire file)
- Modify: `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt` (replace entire file)
- Modify: `app/src/main/java/com/example/test/MainActivity.kt` (replace entire file)

**Interfaces:**
- Consumes: `PhotoStorage`, `FilePhotoStorage`, `FakePhotoStorage`, `CameraCapture` from Task 1; `JournalEntry.photoPath` from Task 2.
- Produces: `EntryEditorUiState.photoPath: String?`; `EntryEditorViewModel(repository, photoStorage, entryId)` with new `onPhotoPicked(path: String)`/`onPhotoRemoved()`; `EntryEditorScreen(repository, photoStorage, entryId, onDone, onViewPhoto: (String) -> Unit)`. `MoodJournalNavHost` gains a `photoStorage: PhotoStorage` parameter. `onViewPhoto` is wired to a no-op `{}` here — Task 4 replaces it with real navigation once the viewer screen exists.

- [ ] **Step 1: Write the failing ViewModel tests**

Replace the full contents of `app/src/test/java/com/example/test/ui/editor/EntryEditorViewModelTest.kt`:

```kotlin
package com.example.test.ui.editor

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import com.example.test.repository.FakePhotoStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EntryEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveDisabled_whenTextBlank() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)

        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("   ")
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("Feeling good today")
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun save_persistsEntryToRepository() = runTest {
        val repository = FakeJournalRepository()
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = 0L)

        viewModel.onTextChange("Feeling good today")
        viewModel.onMoodChange(Mood.GREAT)
        viewModel.onIntensityChange(5)
        viewModel.save()

        val entries = repository.currentEntries
        assertEquals(1, entries.size)
        assertEquals("Feeling good today", entries[0].text)
        assertEquals(Mood.GREAT, entries[0].mood)
        assertEquals(5, entries[0].intensity)
    }

    @Test
    fun delete_removesExistingEntry() = runTest {
        val repository = FakeJournalRepository()
        val entryId = repository.save(
            JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Old", mood = Mood.OKAY, intensity = 2),
        )
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = entryId)

        viewModel.delete()

        assertTrue(repository.currentEntries.isEmpty())
    }

    @Test
    fun onPhotoPicked_updatesPhotoPathInState() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)

        viewModel.onPhotoPicked("photo_1.jpg")

        assertEquals("photo_1.jpg", viewModel.uiState.value.photoPath)
    }

    @Test
    fun onPhotoRemoved_clearsPhotoPath() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), FakePhotoStorage(), entryId = 0L)
        viewModel.onPhotoPicked("photo_1.jpg")

        viewModel.onPhotoRemoved()

        assertNull(viewModel.uiState.value.photoPath)
    }

    @Test
    fun save_persistsPhotoPathToRepository() = runTest {
        val repository = FakeJournalRepository()
        val viewModel = EntryEditorViewModel(repository, FakePhotoStorage(), entryId = 0L)

        viewModel.onTextChange("Beach day")
        viewModel.onPhotoPicked("photo_1.jpg")
        viewModel.save()

        assertEquals("photo_1.jpg", repository.currentEntries[0].photoPath)
    }

    @Test
    fun save_deletesOldPhotoFileWhenReplaced() = runTest {
        val repository = FakeJournalRepository()
        val entryId = repository.save(
            JournalEntry(
                createdAt = 1L,
                updatedAt = 1L,
                text = "Beach day",
                mood = Mood.GOOD,
                intensity = 3,
                photoPath = "old_photo.jpg",
            ),
        )
        val photoStorage = FakePhotoStorage()
        val viewModel = EntryEditorViewModel(repository, photoStorage, entryId = entryId)

        viewModel.onPhotoPicked("new_photo.jpg")
        viewModel.save()

        assertEquals(listOf("old_photo.jpg"), photoStorage.deletedPaths)
        assertEquals("new_photo.jpg", repository.currentEntries[0].photoPath)
    }

    @Test
    fun delete_deletesPhotoFileForExistingEntry() = runTest {
        val repository = FakeJournalRepository()
        val entryId = repository.save(
            JournalEntry(
                createdAt = 1L,
                updatedAt = 1L,
                text = "Beach day",
                mood = Mood.GOOD,
                intensity = 3,
                photoPath = "photo_1.jpg",
            ),
        )
        val photoStorage = FakePhotoStorage()
        val viewModel = EntryEditorViewModel(repository, photoStorage, entryId = entryId)

        viewModel.delete()

        assertEquals(listOf("photo_1.jpg"), photoStorage.deletedPaths)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `EntryEditorViewModel` doesn't take a `FakePhotoStorage` argument yet, and `onPhotoPicked`/`onPhotoRemoved` don't exist.

- [ ] **Step 3: Update the ViewModel**

Replace the full contents of `app/src/main/java/com/example/test/ui/editor/EntryEditorViewModel.kt`:

```kotlin
package com.example.test.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EntryEditorUiState(
    val entryId: Long = 0L,
    val createdAt: Long = 0L,
    val text: String = "",
    val mood: Mood = Mood.OKAY,
    val intensity: Int = 3,
    val photoPath: String? = null,
    val isExistingEntry: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isSaveEnabled: Boolean get() = text.isNotBlank()
}

class EntryEditorViewModel(
    private val repository: JournalRepository,
    private val photoStorage: PhotoStorage,
    entryId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EntryEditorUiState(entryId = entryId))
    val uiState: StateFlow<EntryEditorUiState> = _uiState.asStateFlow()

    private var saveInFlight = false
    private var persistedPhotoPath: String? = null

    init {
        if (entryId != 0L) {
            viewModelScope.launch {
                repository.getById(entryId)?.let { entry ->
                    persistedPhotoPath = entry.photoPath
                    _uiState.value = EntryEditorUiState(
                        entryId = entry.id,
                        createdAt = entry.createdAt,
                        text = entry.text,
                        mood = entry.mood,
                        intensity = entry.intensity,
                        photoPath = entry.photoPath,
                        isExistingEntry = true,
                    )
                }
            }
        }
    }

    fun onTextChange(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun onMoodChange(mood: Mood) {
        _uiState.value = _uiState.value.copy(mood = mood)
    }

    fun onIntensityChange(intensity: Int) {
        _uiState.value = _uiState.value.copy(intensity = intensity)
    }

    fun onPhotoPicked(path: String) {
        _uiState.value = _uiState.value.copy(photoPath = path)
    }

    fun onPhotoRemoved() {
        _uiState.value = _uiState.value.copy(photoPath = null)
    }

    fun save() {
        val state = _uiState.value
        if (!state.isSaveEnabled || saveInFlight) return
        saveInFlight = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.save(
                JournalEntry(
                    id = state.entryId,
                    createdAt = if (state.isExistingEntry) state.createdAt else now,
                    updatedAt = now,
                    text = state.text,
                    mood = state.mood,
                    intensity = state.intensity,
                    photoPath = state.photoPath,
                ),
            )
            val previousPhotoPath = persistedPhotoPath
            if (previousPhotoPath != null && previousPhotoPath != state.photoPath) {
                photoStorage.delete(previousPhotoPath)
            }
            persistedPhotoPath = state.photoPath
            _uiState.value = state.copy(isSaved = true)
        }
    }

    fun delete() {
        val state = _uiState.value
        if (!state.isExistingEntry) return
        viewModelScope.launch {
            repository.delete(
                JournalEntry(
                    id = state.entryId,
                    createdAt = state.createdAt,
                    updatedAt = state.createdAt,
                    text = state.text,
                    mood = state.mood,
                    intensity = state.intensity,
                    photoPath = state.photoPath,
                ),
            )
            persistedPhotoPath?.let { photoStorage.delete(it) }
            _uiState.value = state.copy(isSaved = true)
        }
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.test.ui.editor.EntryEditorViewModelTest"`
Expected: all 8 tests PASS

- [ ] **Step 5: Add the FileProvider**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="photos" path="photos/" />
</paths>
```

In `app/src/main/AndroidManifest.xml`, add a `<uses-permission>` for the camera before `<application>`, and a `<provider>` inside `<application>`, after the closing `</activity>` tag:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Test">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>

</manifest>
```

Note: `FilePhotoStorage` (Task 1) builds its `FileProvider` authority as `"${context.packageName}.fileprovider"` — `context.packageName` resolves to the same value the manifest's `${applicationId}` placeholder expands to at build time, so these match.

- [ ] **Step 6: Update the Entry Editor screen**

Replace the full contents of `app/src/main/java/com/example/test/ui/editor/EntryEditorScreen.kt`:

```kotlin
package com.example.test.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    entryId: Long,
    onDone: () -> Unit,
    onViewPhoto: (String) -> Unit,
    viewModel: EntryEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { EntryEditorViewModel(repository, photoStorage, entryId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var pendingCameraFilename by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    viewModel.onPhotoPicked(photoStorage.copyFrom(uri))
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Couldn't add that photo")
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            pendingCameraFilename?.let { viewModel.onPhotoPicked(it) }
        }
        pendingCameraFilename = null
    }

    fun launchCameraCapture() {
        coroutineScope.launch {
            try {
                val capture = photoStorage.newCameraCapture()
                pendingCameraFilename = capture.filename
                cameraLauncher.launch(capture.uri)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Couldn't open the camera")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            launchCameraCapture()
        } else {
            cameraPermissionDenied = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isExistingEntry) "Edit entry" else "New entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isExistingEntry) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.text,
                onValueChange = viewModel::onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What's on your mind?") },
                minLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val photoPath = uiState.photoPath
                if (photoPath != null) {
                    Box {
                        AsyncImage(
                            model = photoStorage.resolve(photoPath),
                            contentDescription = "Attached photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(
                                    selected = false,
                                    onClick = { onViewPhoto(photoPath) },
                                ),
                        )
                        IconButton(
                            onClick = viewModel::onPhotoRemoved,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo")
                        }
                    }
                }
                IconButton(onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "Add from gallery")
                }
                IconButton(onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        launchCameraCapture()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Take photo")
                }
            }
            if (cameraPermissionDenied) {
                Text(
                    "Camera permission needed to take a photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Mood.entries.forEach { mood ->
                    val isSelected = mood == uiState.mood
                    val emojiStyle = if (isSelected) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    }
                    val backgroundColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Column(
                        modifier = Modifier
                            .clip(CircleShape)
                            .selectable(
                                selected = isSelected,
                                onClick = { viewModel.onMoodChange(mood) },
                                role = Role.RadioButton,
                            )
                            .background(backgroundColor)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = mood.emoji, style = emojiStyle, color = contentColor)
                        Text(text = mood.label, style = MaterialTheme.typography.labelSmall, color = contentColor)
                    }
                }
            }
            Text("Intensity: ${uiState.intensity}", modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = uiState.intensity.toFloat(),
                onValueChange = { viewModel.onIntensityChange(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
            )
            Button(
                onClick = viewModel::save,
                enabled = uiState.isSaveEnabled,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Save")
            }
        }
    }
}
```

- [ ] **Step 7: Thread PhotoStorage through the nav host**

Replace the full contents of `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt`:

```kotlin
package com.example.test.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import com.example.test.ui.editor.EntryEditorScreen
import com.example.test.ui.list.EntryListScreen
import com.example.test.ui.trend.MoodTrendScreen

object Routes {
    const val ENTRY_LIST = "entryList"
    const val ENTRY_EDITOR = "entryEditor"
    const val ENTRY_EDITOR_ARG_ID = "entryId"
    const val ENTRY_EDITOR_NEW = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=0"
    const val MOOD_TREND = "moodTrend"
    fun entryEditorEdit(id: Long) = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=$id"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.ENTRY_LIST) {
        composable(Routes.ENTRY_LIST) {
            EntryListScreen(
                repository = repository,
                onAddEntry = { navController.navigate(Routes.ENTRY_EDITOR_NEW) },
                onEntryClick = { id -> navController.navigate(Routes.entryEditorEdit(id)) },
                onShowTrend = { navController.navigate(Routes.MOOD_TREND) },
            )
        }
        composable(
            route = "${Routes.ENTRY_EDITOR}?${Routes.ENTRY_EDITOR_ARG_ID}={${Routes.ENTRY_EDITOR_ARG_ID}}",
            arguments = listOf(
                navArgument(Routes.ENTRY_EDITOR_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong(Routes.ENTRY_EDITOR_ARG_ID) ?: 0L
            EntryEditorScreen(
                repository = repository,
                photoStorage = photoStorage,
                entryId = entryId,
                onDone = { navController.popBackStack(Routes.ENTRY_LIST, inclusive = false) },
                onViewPhoto = {}, // wired to the real Photo Viewer route in Task 4
            )
        }
        composable(Routes.MOOD_TREND) {
            MoodTrendScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 8: Construct FilePhotoStorage in MainActivity**

Replace the full contents of `app/src/main/java/com/example/test/MainActivity.kt`:

```kotlin
package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.test.data.AppDatabase
import com.example.test.repository.FilePhotoStorage
import com.example.test.repository.RoomJournalRepository
import com.example.test.ui.MoodJournalNavHost
import com.example.test.ui.theme.MoodJournalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = RoomJournalRepository(
            AppDatabase.getInstance(applicationContext).journalEntryDao(),
        )
        val photoStorage = FilePhotoStorage(applicationContext)
        setContent {
            MoodJournalTheme {
                MoodJournalNavHost(repository = repository, photoStorage = photoStorage)
            }
        }
    }
}
```

- [ ] **Step 9: Install and manually verify**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`. On the device: open an entry, tap "Add from gallery," pick an image, confirm it shows as a thumbnail with a remove (✕) button; tap "Take photo," confirm the camera permission prompt appears the first time, grant it, take a photo, confirm it replaces the thumbnail; tap the remove button, confirm the thumbnail disappears; save, reopen the entry, confirm the photo persisted. Tapping the thumbnail does nothing yet (`onViewPhoto` is a no-op until Task 4).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/main/java/com/example/test/ui/editor app/src/test/java/com/example/test/ui/editor app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt app/src/main/java/com/example/test/MainActivity.kt
git commit -m "feat: add photo attach/replace/remove to the entry editor"
```

---

## Task 4: Photo Viewer screen + Entry List thumbnail

**Files:**
- Create: `app/src/main/java/com/example/test/ui/viewer/PhotoViewerScreen.kt`
- Modify: `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt` (replace entire file)
- Modify: `app/src/main/java/com/example/test/ui/list/EntryListScreen.kt` (replace entire file)

**Interfaces:**
- Consumes: `PhotoStorage` from Task 1; the editor's `onViewPhoto` hook from Task 3.
- Produces: `PhotoViewerScreen(photoStorage, photoPath: String, onBack: () -> Unit)`; `Routes.PHOTO_VIEWER` + `Routes.photoViewer(photoPath: String): String`. `EntryListScreen` gains `photoStorage: PhotoStorage` and `onPhotoClick: (String) -> Unit` parameters.

- [ ] **Step 1: Create the Photo Viewer screen**

Create `app/src/main/java/com/example/test/ui/viewer/PhotoViewerScreen.kt`:

```kotlin
package com.example.test.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.test.repository.PhotoStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photoStorage: PhotoStorage,
    photoPath: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = photoStorage.resolve(photoPath),
                contentDescription = "Full-screen photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
```

- [ ] **Step 2: Add the thumbnail to the Entry List screen**

Replace the full contents of `app/src/main/java/com/example/test/ui/list/EntryListScreen.kt`:

```kotlin
package com.example.test.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    onAddEntry: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onShowTrend: () -> Unit,
    onPhotoClick: (String) -> Unit,
    viewModel: EntryListViewModel = viewModel(
        factory = viewModelFactory { initializer { EntryListViewModel(repository) } },
    ),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood Journal") },
                actions = {
                    IconButton(onClick = onShowTrend) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Mood trend")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = "New entry")
            }
        },
    ) { padding ->
        val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.text.take(60)) },
                    supportingContent = { Text(dateFormat.format(Date(entry.createdAt))) },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val photoPath = entry.photoPath
                            if (photoPath != null) {
                                AsyncImage(
                                    model = photoStorage.resolve(photoPath),
                                    contentDescription = "Entry photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPhotoClick(photoPath) },
                                )
                            }
                            Text(entry.mood.emoji)
                        }
                    },
                    modifier = Modifier.clickable { onEntryClick(entry.id) },
                )
            }
        }
    }
}
```

- [ ] **Step 3: Wire the viewer route and the new callbacks into the nav host**

Replace the full contents of `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt`:

```kotlin
package com.example.test.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import com.example.test.ui.editor.EntryEditorScreen
import com.example.test.ui.list.EntryListScreen
import com.example.test.ui.trend.MoodTrendScreen
import com.example.test.ui.viewer.PhotoViewerScreen

object Routes {
    const val ENTRY_LIST = "entryList"
    const val ENTRY_EDITOR = "entryEditor"
    const val ENTRY_EDITOR_ARG_ID = "entryId"
    const val ENTRY_EDITOR_NEW = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=0"
    const val MOOD_TREND = "moodTrend"
    const val PHOTO_VIEWER = "photoViewer"
    const val PHOTO_VIEWER_ARG_PATH = "photoPath"
    fun entryEditorEdit(id: Long) = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=$id"
    fun photoViewer(photoPath: String) = "$PHOTO_VIEWER/$photoPath"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.ENTRY_LIST) {
        composable(Routes.ENTRY_LIST) {
            EntryListScreen(
                repository = repository,
                photoStorage = photoStorage,
                onAddEntry = { navController.navigate(Routes.ENTRY_EDITOR_NEW) },
                onEntryClick = { id -> navController.navigate(Routes.entryEditorEdit(id)) },
                onShowTrend = { navController.navigate(Routes.MOOD_TREND) },
                onPhotoClick = { path -> navController.navigate(Routes.photoViewer(path)) },
            )
        }
        composable(
            route = "${Routes.ENTRY_EDITOR}?${Routes.ENTRY_EDITOR_ARG_ID}={${Routes.ENTRY_EDITOR_ARG_ID}}",
            arguments = listOf(
                navArgument(Routes.ENTRY_EDITOR_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong(Routes.ENTRY_EDITOR_ARG_ID) ?: 0L
            EntryEditorScreen(
                repository = repository,
                photoStorage = photoStorage,
                entryId = entryId,
                onDone = { navController.popBackStack(Routes.ENTRY_LIST, inclusive = false) },
                onViewPhoto = { path -> navController.navigate(Routes.photoViewer(path)) },
            )
        }
        composable(Routes.MOOD_TREND) {
            MoodTrendScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.PHOTO_VIEWER}/{${Routes.PHOTO_VIEWER_ARG_PATH}}",
            arguments = listOf(navArgument(Routes.PHOTO_VIEWER_ARG_PATH) { type = NavType.StringType }),
        ) { backStackEntry ->
            val photoPath = backStackEntry.arguments?.getString(Routes.PHOTO_VIEWER_ARG_PATH).orEmpty()
            PhotoViewerScreen(
                photoStorage = photoStorage,
                photoPath = photoPath,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 4: Install and manually verify the full flow**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`. On the device: an entry with a photo shows a thumbnail in the list; tapping the thumbnail opens the full-screen viewer (back arrow returns to the list); tapping elsewhere on that same row still opens the editor; inside the editor, tapping the photo preview also opens the full-screen viewer.

- [ ] **Step 5: Run the full automated test suite**

Confirm a device is available: `adb devices`.
Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
Expected: all unit and instrumented tests PASS, including the pre-existing `EntryCreationFlowTest`, `JournalEntryDaoTest`, and `MoodTrendViewModelTest`, which are unaffected by this feature.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/test/ui
git commit -m "feat: add photo viewer screen and entry list thumbnail"
```

---

## Task 5: Update docs and run the full verification pass

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: the completed feature from Tasks 1-4.
- Produces: nothing consumed by later tasks — this is the closing task.

- [ ] **Step 1: Update README's Planned features section**

In `README.md`, replace:

```markdown
**Explicitly out of scope for v1** (each is a candidate fast-follow, to be
designed separately when picked up):

- Photo attachments on entries
- Search/filter by text, mood, or date range
- Daily reminder notification
```

with:

```markdown
**Shipped as a fast-follow:**

- Photo attachments — one photo per entry, from the gallery or camera, with
  a full-screen viewer

**Explicitly out of scope for now** (each is a candidate fast-follow, to be
designed separately when picked up):

- Search/filter by text, mood, or date range
- Daily reminder notification
```

- [ ] **Step 2: Update the data model table**

In `README.md`, replace:

```markdown
| `intensity` | `Int` (1–5)                              | how strongly the mood is felt                |
```

with:

```markdown
| `intensity` | `Int` (1–5)                              | how strongly the mood is felt                |
| `photoPath` | `String?`                                | bare filename under the app's photos dir; `null` = no photo |
```

- [ ] **Step 3: Update the Screens & navigation section**

In `README.md`, replace:

```markdown
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
```

with:

```markdown
1. **Entry List** (start destination) — reverse-chronological list of
   entries; each row shows date, mood emoji/color, a text preview, and a
   photo thumbnail when the entry has one. A FAB opens the Entry Editor in
   "new" mode; tapping a row opens it in "edit" mode; tapping a thumbnail
   opens the Photo Viewer directly. A top-bar icon navigates to Mood Trend.
2. **Entry Editor** — multiline text field, a row of five mood options
   (emoji/color + label), an intensity slider (1–5), and an optional photo
   (add from the gallery or camera, replace, or remove). Save is disabled
   while the text is blank. Delete is available only in edit mode.
3. **Mood Trend** — a Canvas-drawn line/dot chart plotting mood + intensity
   over the last 30 days. No charting library is used; Compose `Canvas` is
   enough for a single trend line.
4. **Photo Viewer** — a full-screen view of an entry's photo, reachable from
   either the list thumbnail or the editor's photo preview.
```

- [ ] **Step 4: Add Coil to the tech stack list**

In `README.md`, replace:

```markdown
- Navigation-Compose
- ViewModel + StateFlow for UI state
```

with:

```markdown
- Navigation-Compose
- Coil, for loading photo attachments
- ViewModel + StateFlow for UI state
```

- [ ] **Step 5: Update the Roadmap section**

In `README.md`, replace:

```markdown
## Roadmap

Post-MVP phases, each to get its own design pass when picked up:

- Photo attachments (storage, permissions, display in list/editor)
- Search/filter (query design, possibly full-text search if needed)
- Daily reminder notification (WorkManager/AlarmManager, Android 13+
  notification permission, user-configurable time)
```

with:

```markdown
## Roadmap

Photo attachments shipped — see
[Photo attachments design](docs/superpowers/specs/2026-09-24-photo-attachments-design.md).

Remaining post-MVP phases, each to get its own design pass when picked up:

- Search/filter (query design, possibly full-text search if needed)
- Daily reminder notification (WorkManager/AlarmManager, Android 13+
  notification permission, user-configurable time)
```

- [ ] **Step 6: Add the new spec/plan to the Documentation section**

In `README.md`, replace:

```markdown
- [Implementation plan](docs/superpowers/plans/2026-09-15-mood-journal-mvp.md) —
  the task-by-task build plan derived from the spec.
```

with:

```markdown
- [Implementation plan](docs/superpowers/plans/2026-09-15-mood-journal-mvp.md) —
  the task-by-task build plan derived from the spec.
- [Photo attachments design](docs/superpowers/specs/2026-09-24-photo-attachments-design.md)
  and [implementation plan](docs/superpowers/plans/2026-09-24-photo-attachments.md) —
  the first post-MVP fast-follow phase.
```

- [ ] **Step 7: Update CLAUDE.md's architecture notes**

In `CLAUDE.md`, replace:

```markdown
- **Package/project name**: the Gradle root project name (`Test`) and application package (`com.example.test`) are intentionally left over from the original Android Studio scaffold and are *not* meant to be renamed to match "Mood Journal" — a deliberate constraint from the implementation plan, not an oversight.
```

with:

```markdown
- **Package/project name**: the Gradle root project name (`Test`) and application package (`com.example.test`) are intentionally left over from the original Android Studio scaffold and are *not* meant to be renamed to match "Mood Journal" — a deliberate constraint from the implementation plan, not an oversight.
- **Photos** go through a `PhotoStorage` interface (`FilePhotoStorage` in production, `FakePhotoStorage` in tests), the same interface/real/fake shape as `JournalRepository`. Files live under `context.filesDir/photos/`; `JournalEntry.photoPath` stores only the bare filename, never a `content://` URI — see `docs/superpowers/specs/2026-09-24-photo-attachments-design.md`. There's a 4th nav destination, the Photo Viewer (`Routes.photoViewer(path)`), reachable from both the list thumbnail and the editor's preview.
```

- [ ] **Step 8: Update CLAUDE.md's scope discipline note**

In `CLAUDE.md`, replace:

```markdown
This is a deliberately narrow MVP — see `docs/superpowers/specs/2026-09-15-mood-journal-design.md` and the paired implementation plan in the same `docs/superpowers/` tree for the full rationale. Photo attachments, search/filter, and reminder notifications are explicitly out of scope for v1; each is a future phase needing its own design pass. Don't add them, new architectural layers, or new dependencies without confirming first.
```

with:

```markdown
This is a deliberately narrow MVP — see `docs/superpowers/specs/2026-09-15-mood-journal-design.md` and the paired implementation plan in the same `docs/superpowers/` tree for the full rationale. Photo attachments shipped as the first fast-follow phase (`docs/superpowers/specs/2026-09-24-photo-attachments-design.md`); search/filter and reminder notifications remain explicitly out of scope, each needing its own design pass before implementation. Don't add them, new architectural layers, or new dependencies without confirming first.
```

- [ ] **Step 9: Run the full verification pass**

Confirm a device is available: `adb devices`.
Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lint`
Expected: all unit tests, all instrumented tests (including `FilePhotoStorageTest` and `AppDatabaseMigrationTest`), and lint all PASS with no new warnings introduced by this feature.

- [ ] **Step 10: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: update README and CLAUDE.md for photo attachments"
```

---

## Done criteria

All 5 tasks complete; `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest` both fully green (including the new `FilePhotoStorageTest`, `AppDatabaseMigrationTest`, and the extended `EntryEditorViewModelTest`); and, on-device, a user can: attach a photo to an entry from the gallery or the camera, see it as a thumbnail in the list and a preview in the editor, replace or remove it, view it full-screen from either the list or the editor, and have it (and its file) cleanly deleted when the entry is deleted.

