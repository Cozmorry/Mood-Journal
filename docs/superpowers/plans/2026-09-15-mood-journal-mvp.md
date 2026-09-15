# Mood Journal MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local-first Android journal app where the user writes text entries tagged with a mood + intensity, and can view a mood-over-time chart.

**Architecture:** Single-module app, unidirectional MVVM: Jetpack Compose UI → ViewModel (StateFlow) → Repository → Room DAO → SQLite. No multi-module or clean-architecture layering.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose + Material 3, Room 2.6.1 (KSP), Navigation-Compose 2.8.5, ViewModel + StateFlow, kotlinx-coroutines.

**Spec:** `docs/superpowers/specs/2026-09-15-mood-journal-design.md`

## Global Constraints

- Package/namespace: `com.example.test` (unchanged from current project — do not rename).
- minSdk 24, compileSdk 37, targetSdk 37 (unchanged from current project).
- Kotlin 2.1.0 throughout; UI is 100% Jetpack Compose (no XML layouts, no Views-based Activity).
- Persistence is Room only; no network calls anywhere in this app.
- In scope: create/edit/delete journal entries (text + mood + intensity), entry list, mood-over-time chart.
- Out of scope (do not build): photo attachments, search/filter, reminder notifications. Do not add hooks or stubs for these — they get their own design pass later.
- Connected/instrumented tests (Room DAO tests, the Compose E2E test) require an emulator or physical device. Before running them, confirm one is available with `adb devices` (must list at least one device); start an emulator via Android Studio's Device Manager if none is listed.

---

## Task 1: Project setup — Kotlin, Compose, Room, Navigation

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: Gradle plugins/dependencies available to every later task — `kotlin-android`, `kotlin-compose` (Compose compiler), `ksp`, Compose BOM libs, `androidx.room:room-runtime`/`room-ktx` + KSP compiler, `androidx.navigation:navigation-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`/`lifecycle-runtime-compose`, `kotlinx-coroutines-android`, and test deps `kotlinx-coroutines-test`, `androidx.compose.ui:ui-test-junit4`, `androidx.compose.ui:ui-test-manifest`.

- [ ] **Step 1: Replace the version catalog**

Replace the full contents of `gradle/libs.versions.toml` with:

```toml
[versions]
agp = "9.3.0"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
appcompat = "1.6.1"
material = "1.10.0"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navigationCompose = "2.8.5"
room = "2.6.1"
coroutines = "1.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Note: if Gradle sync reports it cannot resolve the `ksp` plugin/dependency version, check https://github.com/google/ksp/releases for the KSP release matching Kotlin `2.1.0` and update the `ksp` version above — KSP releases are versioned as `<kotlinVersion>-<kspVersion>` and must match the Kotlin version exactly.

- [ ] **Step 2: Register the new plugins in the root build script**

Replace the full contents of `build.gradle.kts` (root) with:

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Apply the plugins and dependencies in the app module**

Replace the full contents of `app/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.test"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.test"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 4: Verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. There's no app code yet, so this only proves the toolchain/dependencies resolve and configure correctly.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build: add Kotlin, Compose, Room, and Navigation to the project"
```

---

## Task 2: Data layer — entity, DAO, database

**Files:**
- Create: `app/src/main/java/com/example/test/data/Mood.kt`
- Create: `app/src/main/java/com/example/test/data/JournalEntry.kt`
- Create: `app/src/main/java/com/example/test/data/Converters.kt`
- Create: `app/src/main/java/com/example/test/data/JournalEntryDao.kt`
- Create: `app/src/main/java/com/example/test/data/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/example/test/data/JournalEntryDaoTest.kt`

**Interfaces:**
- Consumes: Room/KSP from Task 1.
- Produces: `Mood` enum (`GREAT, GOOD, OKAY, BAD, AWFUL`, each with `label: String` and `emoji: String`); `JournalEntry` data class (`id: Long`, `createdAt: Long`, `updatedAt: Long`, `text: String`, `mood: Mood`, `intensity: Int`); `JournalEntryDao` with `getAll(): Flow<List<JournalEntry>>`, `getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>>`, `suspend getById(id: Long): JournalEntry?`, `suspend insert(entry: JournalEntry): Long`, `suspend update(entry: JournalEntry)`, `suspend delete(entry: JournalEntry)`; `AppDatabase.getInstance(context: Context): AppDatabase` exposing `.journalEntryDao()`.

- [ ] **Step 1: Write the DAO test first (it won't compile yet — that's expected)**

Create `app/src/androidTest/java/com/example/test/data/JournalEntryDaoTest.kt`:

```kotlin
package com.example.test.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalEntryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: JournalEntryDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.journalEntryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndGetAll_returnsEntriesNewestFirst() = runBlocking {
        dao.insert(JournalEntry(createdAt = 1L, updatedAt = 1L, text = "First", mood = Mood.OKAY, intensity = 3))
        dao.insert(JournalEntry(createdAt = 2L, updatedAt = 2L, text = "Second", mood = Mood.GOOD, intensity = 4))

        val entries = dao.getAll().first()

        assertEquals(listOf("Second", "First"), entries.map { it.text })
    }

    @Test
    fun update_changesExistingEntry() = runBlocking {
        val id = dao.insert(
            JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Original", mood = Mood.OKAY, intensity = 3),
        )
        val updated = dao.getById(id)!!.copy(text = "Updated", mood = Mood.GREAT, intensity = 5)

        dao.update(updated)

        val result = dao.getById(id)
        assertEquals("Updated", result?.text)
        assertEquals(Mood.GREAT, result?.mood)
    }

    @Test
    fun delete_removesEntry() = runBlocking {
        val entry = JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Temp", mood = Mood.BAD, intensity = 2)
        val id = dao.insert(entry)
        val saved = dao.getById(id)!!

        dao.delete(saved)

        assertNull(dao.getById(id))
    }

    @Test
    fun getSince_excludesOlderEntries() = runBlocking {
        dao.insert(JournalEntry(createdAt = 100L, updatedAt = 100L, text = "Old", mood = Mood.OKAY, intensity = 3))
        dao.insert(JournalEntry(createdAt = 500L, updatedAt = 500L, text = "Recent", mood = Mood.GOOD, intensity = 4))

        val entries = dao.getSince(sinceEpochMillis = 200L).first()

        assertEquals(listOf("Recent"), entries.map { it.text })
    }
}
```

- [ ] **Step 2: Create the Mood enum**

Create `app/src/main/java/com/example/test/data/Mood.kt`:

```kotlin
package com.example.test.data

enum class Mood(val label: String, val emoji: String) {
    GREAT("Great", "😄"),
    GOOD("Good", "🙂"),
    OKAY("Okay", "😐"),
    BAD("Bad", "🙁"),
    AWFUL("Awful", "😢"),
}
```

- [ ] **Step 3: Create the JournalEntry entity**

Create `app/src/main/java/com/example/test/data/JournalEntry.kt`:

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
)
```

- [ ] **Step 4: Create the type converter for Mood**

Create `app/src/main/java/com/example/test/data/Converters.kt`:

```kotlin
package com.example.test.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun moodToString(mood: Mood): String = mood.name

    @TypeConverter
    fun stringToMood(value: String): Mood = Mood.valueOf(value)
}
```

- [ ] **Step 5: Create the DAO**

Create `app/src/main/java/com/example/test/data/JournalEntryDao.kt`:

```kotlin
package com.example.test.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    fun getAll(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE createdAt >= :sinceEpochMillis ORDER BY createdAt ASC")
    fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getById(id: Long): JournalEntry?

    @Insert
    suspend fun insert(entry: JournalEntry): Long

    @Update
    suspend fun update(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)
}
```

- [ ] **Step 6: Create the database**

Create `app/src/main/java/com/example/test/data/AppDatabase.kt`:

```kotlin
package com.example.test.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [JournalEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mood-journal.db",
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 7: Run the DAO tests**

Confirm a device is available: `adb devices` (must list at least one).
Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.test.data.JournalEntryDaoTest"`
Expected: all 4 tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/test/data app/src/androidTest/java/com/example/test/data
git commit -m "feat: add Room data layer for journal entries"
```

---

## Task 3: Repository layer

**Files:**
- Create: `app/src/main/java/com/example/test/repository/JournalRepository.kt`
- Create: `app/src/main/java/com/example/test/repository/RoomJournalRepository.kt`
- Create: `app/src/test/java/com/example/test/repository/FakeJournalRepository.kt`

**Interfaces:**
- Consumes: `JournalEntryDao`, `JournalEntry` from Task 2.
- Produces: `JournalRepository` interface (`getAll(): Flow<List<JournalEntry>>`, `getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>>`, `suspend getById(id: Long): JournalEntry?`, `suspend save(entry: JournalEntry): Long`, `suspend delete(entry: JournalEntry)`); `RoomJournalRepository(dao: JournalEntryDao)` implementing it; `FakeJournalRepository` (test double, same interface, plus a `currentEntries: List<JournalEntry>` property for assertions) that every later ViewModel test uses instead of Room.

This is a thin pass-through layer with no independent branching logic, so it's covered indirectly by the ViewModel tests in later tasks rather than a dedicated test here.

- [ ] **Step 1: Create the repository interface**

Create `app/src/main/java/com/example/test/repository/JournalRepository.kt`:

```kotlin
package com.example.test.repository

import com.example.test.data.JournalEntry
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    fun getAll(): Flow<List<JournalEntry>>
    fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>>
    suspend fun getById(id: Long): JournalEntry?
    suspend fun save(entry: JournalEntry): Long
    suspend fun delete(entry: JournalEntry)
}
```

- [ ] **Step 2: Create the Room-backed implementation**

Create `app/src/main/java/com/example/test/repository/RoomJournalRepository.kt`:

```kotlin
package com.example.test.repository

import com.example.test.data.JournalEntry
import com.example.test.data.JournalEntryDao
import kotlinx.coroutines.flow.Flow

class RoomJournalRepository(private val dao: JournalEntryDao) : JournalRepository {
    override fun getAll(): Flow<List<JournalEntry>> = dao.getAll()

    override fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>> =
        dao.getSince(sinceEpochMillis)

    override suspend fun getById(id: Long): JournalEntry? = dao.getById(id)

    override suspend fun save(entry: JournalEntry): Long {
        return if (entry.id == 0L) {
            dao.insert(entry)
        } else {
            dao.update(entry)
            entry.id
        }
    }

    override suspend fun delete(entry: JournalEntry) = dao.delete(entry)
}
```

- [ ] **Step 3: Create the fake repository for tests**

Create `app/src/test/java/com/example/test/repository/FakeJournalRepository.kt`:

```kotlin
package com.example.test.repository

import com.example.test.data.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeJournalRepository : JournalRepository {
    private val entriesFlow = MutableStateFlow<List<JournalEntry>>(emptyList())
    private var nextId = 1L

    val currentEntries: List<JournalEntry> get() = entriesFlow.value

    override fun getAll(): Flow<List<JournalEntry>> = entriesFlow.asStateFlow()

    override fun getSince(sinceEpochMillis: Long): Flow<List<JournalEntry>> =
        MutableStateFlow(entriesFlow.value.filter { it.createdAt >= sinceEpochMillis }).asStateFlow()

    override suspend fun getById(id: Long): JournalEntry? =
        entriesFlow.value.find { it.id == id }

    override suspend fun save(entry: JournalEntry): Long {
        val id = if (entry.id == 0L) nextId++ else entry.id
        val saved = entry.copy(id = id)
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id } + saved
        return id
    }

    override suspend fun delete(entry: JournalEntry) {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == entry.id }
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/test/repository app/src/test/java/com/example/test/repository
git commit -m "feat: add journal repository and fake for tests"
```

---

## Task 4: App shell + Entry List screen

**Files:**
- Create: `app/src/main/java/com/example/test/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/example/test/MainActivity.kt`
- Create: `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt`
- Create: `app/src/main/java/com/example/test/ui/list/EntryListViewModel.kt`
- Create: `app/src/main/java/com/example/test/ui/list/EntryListScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/example/test/MainDispatcherRule.kt`
- Test: `app/src/test/java/com/example/test/ui/list/EntryListViewModelTest.kt`

**Interfaces:**
- Consumes: `JournalRepository`, `FakeJournalRepository` from Task 3.
- Produces: `MainDispatcherRule` (shared JUnit rule reused by every later ViewModel test); `MoodJournalNavHost(repository: JournalRepository, navController: NavHostController = rememberNavController())` with a `Routes` object (extended in later tasks); `EntryListScreen(repository, onAddEntry: () -> Unit, onEntryClick: (Long) -> Unit, onShowTrend: () -> Unit)`.

- [ ] **Step 1: Add the shared test dispatcher rule**

Create `app/src/test/java/com/example/test/MainDispatcherRule.kt`:

```kotlin
package com.example.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 2: Write the failing ViewModel test**

Create `app/src/test/java/com/example/test/ui/list/EntryListViewModelTest.kt`:

```kotlin
package com.example.test.ui.list

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun entries_reflectsRepositoryData() = runTest {
        val repository = FakeJournalRepository()
        repository.save(
            JournalEntry(createdAt = 1L, updatedAt = 1L, text = "Hi", mood = Mood.GOOD, intensity = 3),
        )
        val viewModel = EntryListViewModel(repository)

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.entries.collect() }

        assertEquals(listOf("Hi"), viewModel.entries.value.map { it.text })

        collectJob.cancel()
    }
}
```

- [ ] **Step 3: Run it to confirm it fails to compile (EntryListViewModel doesn't exist yet)**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL with "unresolved reference: EntryListViewModel"

- [ ] **Step 4: Create the ViewModel**

Create `app/src/main/java/com/example/test/ui/list/EntryListViewModel.kt`:

```kotlin
package com.example.test.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.JournalEntry
import com.example.test.repository.JournalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class EntryListViewModel(repository: JournalRepository) : ViewModel() {
    val entries: StateFlow<List<JournalEntry>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.test.ui.list.EntryListViewModelTest"`
Expected: PASS

- [ ] **Step 6: Create the theme**

Create `app/src/main/java/com/example/test/ui/theme/Theme.kt`:

```kotlin
package com.example.test.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun MoodJournalTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
```

- [ ] **Step 7: Create the Entry List screen**

Create `app/src/main/java/com/example/test/ui/list/EntryListScreen.kt`:

```kotlin
package com.example.test.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.repository.JournalRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    repository: JournalRepository,
    onAddEntry: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onShowTrend: () -> Unit,
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
                        Icon(Icons.Filled.ShowChart, contentDescription = "Mood trend")
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
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.text.take(60)) },
                    leadingContent = { Text(entry.mood.emoji) },
                    modifier = Modifier.clickable { onEntryClick(entry.id) },
                )
            }
        }
    }
}
```

- [ ] **Step 8: Create the navigation host**

Create `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt`:

```kotlin
package com.example.test.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.test.repository.JournalRepository
import com.example.test.ui.list.EntryListScreen

object Routes {
    const val ENTRY_LIST = "entryList"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.ENTRY_LIST) {
        composable(Routes.ENTRY_LIST) {
            EntryListScreen(
                repository = repository,
                onAddEntry = {},
                onEntryClick = {},
                onShowTrend = {},
            )
        }
    }
}
```

- [ ] **Step 9: Create MainActivity**

Create `app/src/main/java/com/example/test/MainActivity.kt`:

```kotlin
package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.test.data.AppDatabase
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
        setContent {
            MoodJournalTheme {
                MoodJournalNavHost(repository = repository)
            }
        }
    }
}
```

- [ ] **Step 10: Register MainActivity in the manifest**

In `app/src/main/AndroidManifest.xml`, add an `<activity>` element inside `<application>` (after the existing attributes, before the closing `/>` — change it to an open/close tag):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

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

    </application>

</manifest>
```

- [ ] **Step 11: Build and install the app to confirm it launches**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, and launching the app on the device shows an empty "Mood Journal" list screen with a FAB (tapping it does nothing yet — that's Task 5).

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/example/test app/src/main/AndroidManifest.xml app/src/test/java/com/example/test
git commit -m "feat: add app shell, navigation, and entry list screen"
```

---

## Task 5: Entry Editor screen

**Files:**
- Create: `app/src/main/java/com/example/test/ui/editor/EntryEditorViewModel.kt`
- Create: `app/src/main/java/com/example/test/ui/editor/EntryEditorScreen.kt`
- Modify: `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt` (replace entire file)
- Test: `app/src/test/java/com/example/test/ui/editor/EntryEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `JournalRepository`, `FakeJournalRepository`, `Mood`, `JournalEntry` from Tasks 2-3; `MainDispatcherRule` from Task 4.
- Produces: `EntryEditorUiState` (`entryId`, `createdAt`, `text`, `mood`, `intensity`, `isExistingEntry`, `isSaved`, computed `isSaveEnabled`); `EntryEditorViewModel(repository: JournalRepository, entryId: Long)` with `onTextChange`, `onMoodChange`, `onIntensityChange`, `save()`, `delete()`; `EntryEditorScreen(repository, entryId: Long, onDone: () -> Unit)`. Extends `Routes` with `ENTRY_EDITOR`, `ENTRY_EDITOR_NEW`, `entryEditorEdit(id)`.

- [ ] **Step 1: Write the failing ViewModel tests**

Create `app/src/test/java/com/example/test/ui/editor/EntryEditorViewModelTest.kt`:

```kotlin
package com.example.test.ui.editor

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EntryEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveDisabled_whenTextBlank() = runTest {
        val viewModel = EntryEditorViewModel(FakeJournalRepository(), entryId = 0L)

        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("   ")
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onTextChange("Feeling good today")
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun save_persistsEntryToRepository() = runTest {
        val repository = FakeJournalRepository()
        val viewModel = EntryEditorViewModel(repository, entryId = 0L)

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
        val viewModel = EntryEditorViewModel(repository, entryId = entryId)

        viewModel.delete()

        assertTrue(repository.currentEntries.isEmpty())
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL with "unresolved reference: EntryEditorViewModel"

- [ ] **Step 3: Create the ViewModel**

Create `app/src/main/java/com/example/test/ui/editor/EntryEditorViewModel.kt`:

```kotlin
package com.example.test.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
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
    val isExistingEntry: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isSaveEnabled: Boolean get() = text.isNotBlank()
}

class EntryEditorViewModel(
    private val repository: JournalRepository,
    entryId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EntryEditorUiState(entryId = entryId))
    val uiState: StateFlow<EntryEditorUiState> = _uiState.asStateFlow()

    init {
        if (entryId != 0L) {
            viewModelScope.launch {
                repository.getById(entryId)?.let { entry ->
                    _uiState.value = EntryEditorUiState(
                        entryId = entry.id,
                        createdAt = entry.createdAt,
                        text = entry.text,
                        mood = entry.mood,
                        intensity = entry.intensity,
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

    fun save() {
        val state = _uiState.value
        if (!state.isSaveEnabled) return
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
                ),
            )
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
                ),
            )
            _uiState.value = state.copy(isSaved = true)
        }
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.test.ui.editor.EntryEditorViewModelTest"`
Expected: all 3 tests PASS

- [ ] **Step 5: Create the Entry Editor screen**

Create `app/src/main/java/com/example/test/ui/editor/EntryEditorScreen.kt`:

```kotlin
package com.example.test.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    repository: JournalRepository,
    entryId: Long,
    onDone: () -> Unit,
    viewModel: EntryEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { EntryEditorViewModel(repository, entryId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isExistingEntry) "Edit entry" else "New entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = uiState.text,
                onValueChange = viewModel::onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What's on your mind?") },
                minLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Mood.entries.forEach { mood ->
                    val style = if (mood == uiState.mood) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    }
                    TextButton(onClick = { viewModel.onMoodChange(mood) }) {
                        Text(text = mood.emoji, style = style)
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

- [ ] **Step 6: Wire the editor into navigation**

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
import com.example.test.ui.editor.EntryEditorScreen
import com.example.test.ui.list.EntryListScreen

object Routes {
    const val ENTRY_LIST = "entryList"
    const val ENTRY_EDITOR = "entryEditor"
    const val ENTRY_EDITOR_ARG_ID = "entryId"
    const val ENTRY_EDITOR_NEW = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=0"
    fun entryEditorEdit(id: Long) = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=$id"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.ENTRY_LIST) {
        composable(Routes.ENTRY_LIST) {
            EntryListScreen(
                repository = repository,
                onAddEntry = { navController.navigate(Routes.ENTRY_EDITOR_NEW) },
                onEntryClick = { id -> navController.navigate(Routes.entryEditorEdit(id)) },
                onShowTrend = {},
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
                entryId = entryId,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 7: Install and manually verify**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`; on the device, tapping the FAB opens the editor, typing text enables Save, saving returns to the list showing the new entry, and tapping that row reopens it for editing with a Delete action available.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/test/ui app/src/test/java/com/example/test/ui/editor
git commit -m "feat: add entry editor screen with create/edit/delete"
```

---

## Task 6: Mood Trend screen

**Files:**
- Create: `app/src/main/java/com/example/test/ui/trend/MoodTrendViewModel.kt`
- Create: `app/src/main/java/com/example/test/ui/trend/MoodTrendScreen.kt`
- Modify: `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt` (replace entire file)
- Test: `app/src/test/java/com/example/test/ui/trend/MoodTrendViewModelTest.kt`

**Interfaces:**
- Consumes: `JournalRepository`, `FakeJournalRepository`, `Mood` from Tasks 2-3; `MainDispatcherRule` from Task 4.
- Produces: `MoodPoint(timestampMillis: Long, score: Float)`; `MoodTrendViewModel(repository: JournalRepository)` exposing `points: StateFlow<List<MoodPoint>>`; `MoodTrendScreen(repository, onBack: () -> Unit)`. Extends `Routes` with `MOOD_TREND`.

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/test/java/com/example/test/ui/trend/MoodTrendViewModelTest.kt`:

```kotlin
package com.example.test.ui.trend

import com.example.test.MainDispatcherRule
import com.example.test.data.JournalEntry
import com.example.test.data.Mood
import com.example.test.repository.FakeJournalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoodTrendViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun points_mapsMoodAndIntensityToScore() = runTest {
        val repository = FakeJournalRepository()
        val now = System.currentTimeMillis()
        repository.save(
            JournalEntry(createdAt = now, updatedAt = now, text = "Great day", mood = Mood.GREAT, intensity = 5),
        )

        val viewModel = MoodTrendViewModel(repository)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.points.collect() }

        val point = viewModel.points.value.single()
        assertEquals(now, point.timestampMillis)
        assertEquals(5f, point.score, 0.01f)

        collectJob.cancel()
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL with "unresolved reference: MoodTrendViewModel"

- [ ] **Step 3: Create the ViewModel**

Create `app/src/main/java/com/example/test/ui/trend/MoodTrendViewModel.kt`:

```kotlin
package com.example.test.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

data class MoodPoint(val timestampMillis: Long, val score: Float)

private const val TREND_WINDOW_DAYS = 30L

class MoodTrendViewModel(repository: JournalRepository) : ViewModel() {
    val points: StateFlow<List<MoodPoint>> = repository
        .getSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TREND_WINDOW_DAYS))
        .map { entries ->
            entries.map { entry ->
                val moodScore = (Mood.entries.size - 1 - entry.mood.ordinal).toFloat()
                MoodPoint(
                    timestampMillis = entry.createdAt,
                    score = moodScore + entry.intensity / 5f,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.test.ui.trend.MoodTrendViewModelTest"`
Expected: PASS

- [ ] **Step 5: Create the trend screen with a hand-rolled Canvas chart**

Create `app/src/main/java/com/example/test/ui/trend/MoodTrendScreen.kt`:

```kotlin
package com.example.test.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.repository.JournalRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodTrendScreen(
    repository: JournalRepository,
    onBack: () -> Unit,
    viewModel: MoodTrendViewModel = viewModel(
        factory = viewModelFactory { initializer { MoodTrendViewModel(repository) } },
    ),
) {
    val points by viewModel.points.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood trend") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (points.size < 2) {
            Text(
                "Add a few more entries to see your trend.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                val minX = points.first().timestampMillis.toFloat()
                val maxX = points.last().timestampMillis.toFloat()
                val spanX = (maxX - minX).coerceAtLeast(1f)
                val maxScore = 5f

                val offsets = points.map { point ->
                    val xFraction = (point.timestampMillis - minX) / spanX
                    val yFraction = point.score / maxScore
                    Offset(
                        x = xFraction * size.width,
                        y = size.height - (yFraction * size.height),
                    )
                }

                for (i in 0 until offsets.size - 1) {
                    drawLine(
                        color = Color(0xFF6750A4),
                        start = offsets[i],
                        end = offsets[i + 1],
                        strokeWidth = 4f,
                    )
                }
                offsets.forEach { offset ->
                    drawCircle(color = Color(0xFF6750A4), radius = 6f, center = offset)
                }
            }
        }
    }
}
```

- [ ] **Step 6: Wire the trend screen into navigation**

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
                entryId = entryId,
                onDone = { navController.popBackStack() },
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

- [ ] **Step 7: Install and manually verify**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`; tapping the trend icon in the list's top bar opens the chart, showing a line/dot trend once there are 2+ entries (and a hint message otherwise).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/test/ui app/src/test/java/com/example/test/ui/trend
git commit -m "feat: add mood trend chart screen"
```

---

## Task 7: End-to-end create-entry test

**Files:**
- Test: `app/src/androidTest/java/com/example/test/EntryCreationFlowTest.kt`

**Interfaces:**
- Consumes: the fully wired app from Tasks 4-6 (`MainActivity`, `EntryListScreen`, `EntryEditorScreen`).
- Produces: nothing consumed by later tasks (this is the final MVP verification).

- [ ] **Step 1: Write the end-to-end test**

Create `app/src/androidTest/java/com/example/test/EntryCreationFlowTest.kt`:

```kotlin
package com.example.test

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class EntryCreationFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun creatingEntry_showsItInList() {
        // Unique text avoids collisions with entries left over from earlier test runs
        // in the app's real on-device database (this test doesn't use an isolated DB).
        val entryText = "Had a great walk today ${System.currentTimeMillis()}"

        composeRule.onNodeWithContentDescription("New entry").performClick()
        composeRule.onNodeWithText("What's on your mind?").performTextInput(entryText)
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText(entryText).assertExists()
    }
}
```

- [ ] **Step 2: Run it**

Confirm a device is available: `adb devices` (must list at least one).
Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.test.EntryCreationFlowTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/example/test/EntryCreationFlowTest.kt
git commit -m "test: add end-to-end entry creation flow test"
```

---

## Done criteria

All 7 tasks complete, all unit tests (`./gradlew :app:testDebugUnitTest`) and connected tests (`./gradlew :app:connectedDebugAndroidTest`) passing, and the app installed and manually confirmed to: create, edit, and delete journal entries with mood + intensity, list them newest-first, and show a mood trend chart.
