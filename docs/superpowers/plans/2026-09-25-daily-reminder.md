# Daily Reminder Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user turn on a daily reminder, at a time of their choosing, that nudges them to write a journal entry — skipped automatically on days they've already written one, tapping it opens a new entry directly.

**Architecture:** Extends the existing unidirectional flow with two new parallel subsystems (`ReminderPreferences` over `SharedPreferences`, `ReminderScheduler` over `WorkManager`), both following the interface + real-impl + fake-for-tests shape `JournalRepository`/`PhotoStorage` already use. A `CoroutineWorker` builds its own repository directly (no DI framework), same pattern `MainActivity` already uses.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3 (including `TimePicker`), `androidx.work:work-runtime-ktx` + `work-testing` (new), `SharedPreferences` (no new dependency), `NotificationCompat`/`NotificationManagerCompat` (already available via `core-ktx`).

**Spec:** `docs/superpowers/specs/2026-09-25-daily-reminder-design.md`

## Global Constraints

- Package/namespace: `com.example.test` (unchanged).
- minSdk 24, compileSdk 37, targetSdk 37 (unchanged).
- Approximate `WorkManager` timing, not exact-time `AlarmManager` — see spec's [Scope](../specs/2026-09-25-daily-reminder-design.md#scope).
- No custom `WorkerFactory`, no custom `Application` subclass — `ReminderWorker` builds `RoomJournalRepository` directly from `AppDatabase.getInstance()`, same pattern `MainActivity` uses.
- No DataStore Preferences — plain `SharedPreferences` for the two small reminder values.
- DST drift, exact-time delivery, multiple reminders/day, and reacting to an out-of-app permission grant are explicitly out of scope — do not build them.
- Connected/instrumented tests require an emulator or physical device; confirm one is available with `adb devices` before running them. `connectedDebugAndroidTest` does not support Gradle's `--tests` filter in this AGP version — use `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` instead (comma-separate multiple classes).

---

## Task 1: ReminderPreferences

**Files:**
- Create: `app/src/main/java/com/example/test/reminder/ReminderPreferences.kt`
- Create: `app/src/main/java/com/example/test/reminder/SharedPreferencesReminderPreferences.kt`
- Create: `app/src/test/java/com/example/test/reminder/FakeReminderPreferences.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `ReminderPreferences` (`isEnabled(): Boolean`, `getTime(): Pair<Int, Int>`, `setReminder(enabled: Boolean, hour: Int, minute: Int)`) with `SharedPreferencesReminderPreferences(context)` and `FakeReminderPreferences`.

This is a thin, direct wrapper over `SharedPreferences` with no independent branching logic worth a dedicated test — the same reasoning that kept `RoomJournalRepository` test-free in the MVP plan. It's covered indirectly by `SettingsViewModelTest` (Task 3, against the fake) and exercised for real in that task's manual verification. No new dependency — plain `SharedPreferences` is already available via `Context`.

- [ ] **Step 1: Create the ReminderPreferences interface**

Create `app/src/main/java/com/example/test/reminder/ReminderPreferences.kt`:

```kotlin
package com.example.test.reminder

interface ReminderPreferences {
    fun isEnabled(): Boolean
    fun getTime(): Pair<Int, Int> // hour (0-23), minute (0-59)
    fun setReminder(enabled: Boolean, hour: Int, minute: Int)
}
```

- [ ] **Step 2: Create the SharedPreferences-backed implementation**

Create `app/src/main/java/com/example/test/reminder/SharedPreferencesReminderPreferences.kt`:

```kotlin
package com.example.test.reminder

import android.content.Context

private const val PREFS_NAME = "reminder_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_HOUR = "hour"
private const val KEY_MINUTE = "minute"
private const val DEFAULT_HOUR = 20
private const val DEFAULT_MINUTE = 0

class SharedPreferencesReminderPreferences(context: Context) : ReminderPreferences {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun getTime(): Pair<Int, Int> =
        prefs.getInt(KEY_HOUR, DEFAULT_HOUR) to prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)

    override fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
    }
}
```

- [ ] **Step 3: Create the fake for later ViewModel tests**

Create `app/src/test/java/com/example/test/reminder/FakeReminderPreferences.kt`:

```kotlin
package com.example.test.reminder

class FakeReminderPreferences(
    private var enabled: Boolean = false,
    private var hour: Int = 20,
    private var minute: Int = 0,
) : ReminderPreferences {
    override fun isEnabled(): Boolean = enabled

    override fun getTime(): Pair<Int, Int> = hour to minute

    override fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        this.enabled = enabled
        this.hour = hour
        this.minute = minute
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/test/reminder app/src/test/java/com/example/test/reminder
git commit -m "feat: add ReminderPreferences backed by SharedPreferences"
```

---

## Task 2: ReminderWorker + ReminderScheduler

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/example/test/reminder/ReminderWorker.kt`
- Create: `app/src/main/java/com/example/test/reminder/ReminderScheduler.kt`
- Create: `app/src/main/java/com/example/test/reminder/WorkManagerReminderScheduler.kt`
- Create: `app/src/test/java/com/example/test/reminder/FakeReminderScheduler.kt`
- Test: `app/src/androidTest/java/com/example/test/reminder/ReminderWorkerTest.kt`

**Interfaces:**
- Consumes: `RoomJournalRepository`, `AppDatabase`, `JournalEntry`, `Mood` from the MVP; `MainActivity` (for the tap `PendingIntent`).
- Produces: `EXTRA_OPEN_NEW_ENTRY` (top-level `const val`, the intent extra key Task 4 reads); `ReminderWorker` (a `CoroutineWorker`); `ReminderScheduler` (`schedule(hour: Int, minute: Int)`, `cancel()`) with `WorkManagerReminderScheduler(context)` and `FakeReminderScheduler` (exposes `scheduledTime: Pair<Int, Int>?` and `cancelCallCount: Int`).

`ReminderScheduler`/`WorkManagerReminderScheduler` are created *after* `ReminderWorker` in this task (not in their own task) because `WorkManagerReminderScheduler` references `ReminderWorker` as a type parameter — creating them first would leave an intermediate commit that doesn't compile.

- [ ] **Step 1: Add the WorkManager dependencies**

In `gradle/libs.versions.toml`, add to `[versions]` (after `coil`):

```toml
work = "2.10.0"
```

Add to `[libraries]` (after `coil-compose`):

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
```

In `app/build.gradle.kts`, add inside `dependencies { ... }`, after `implementation(libs.coil.compose)`:

```kotlin
    implementation(libs.androidx.work.runtime.ktx)
```

And after `androidTestImplementation(libs.androidx.compose.ui.test.junit4)`:

```kotlin
    androidTestImplementation(libs.androidx.work.testing)
```

- [ ] **Step 2: Add the POST_NOTIFICATIONS permission**

In `app/src/main/AndroidManifest.xml`, add a `<uses-permission>` after the existing camera lines:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

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

`POST_NOTIFICATIONS` is a no-op below API 33, where notifications don't require a runtime permission.

- [ ] **Step 3: Write the failing instrumented test for ReminderWorker**

Create `app/src/androidTest/java/com/example/test/reminder/ReminderWorkerTest.kt`:

```kotlin
package com.example.test.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.test.data.AppDatabase
import com.example.test.data.JournalEntry
import com.example.test.data.JournalEntryDao
import com.example.test.data.Mood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao: JournalEntryDao = AppDatabase.getInstance(context).journalEntryDao()

    private fun startOfTodayMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private suspend fun deleteTodaysEntries() {
        dao.getSince(startOfTodayMillis()).first().forEach { dao.delete(it) }
    }

    @Before
    fun clearTodaysEntriesBefore() = runBlocking { deleteTodaysEntries() }

    @After
    fun clearTodaysEntriesAfter() = runBlocking { deleteTodaysEntries() }

    @Test
    fun doWork_postsNotification_whenNoEntryToday() = runBlocking {
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val notifications = NotificationManagerCompat.from(context).activeNotifications
        assertTrue(notifications.any { it.id == NOTIFICATION_ID })
    }

    @Test
    fun doWork_skipsNotification_whenEntryExistsToday() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insert(
            JournalEntry(createdAt = now, updatedAt = now, text = "Already journaled", mood = Mood.OKAY, intensity = 3),
        )

        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val notifications = NotificationManagerCompat.from(context).activeNotifications
        assertTrue(notifications.none { it.id == NOTIFICATION_ID })
    }
}
```

- [ ] **Step 4: Run it to confirm it fails to compile**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `ReminderWorker` and `NOTIFICATION_ID` don't exist yet.

- [ ] **Step 5: Create ReminderWorker**

Create `app/src/main/java/com/example/test/reminder/ReminderWorker.kt`:

```kotlin
package com.example.test.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.data.AppDatabase
import com.example.test.repository.RoomJournalRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar

const val EXTRA_OPEN_NEW_ENTRY = "open_new_entry"

internal const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "daily_reminder"

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = RoomJournalRepository(
                AppDatabase.getInstance(applicationContext).journalEntryDao(),
            )
            val hasEntryToday = repository.getSince(startOfTodayMillis()).first().isNotEmpty()
            if (!hasEntryToday) {
                postNotification(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }

    private fun startOfTodayMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun postNotification(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NEW_ENTRY, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Mood Journal")
            .setContentText("How are you feeling today?")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
```

- [ ] **Step 6: Run the test to confirm it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.test.reminder.ReminderWorkerTest`
Expected: both tests PASS.

- [ ] **Step 7: Create the ReminderScheduler interface**

Create `app/src/main/java/com/example/test/reminder/ReminderScheduler.kt`:

```kotlin
package com.example.test.reminder

interface ReminderScheduler {
    fun schedule(hour: Int, minute: Int)
    fun cancel()
}
```

- [ ] **Step 8: Create the WorkManager-backed implementation**

Create `app/src/main/java/com/example/test/reminder/WorkManagerReminderScheduler.kt`:

```kotlin
package com.example.test.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val REMINDER_WORK_NAME = "daily_reminder_work"

class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun schedule(hour: Int, minute: Int) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(computeInitialDelayMillis(hour, minute), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
    }

    private fun computeInitialDelayMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
```

- [ ] **Step 9: Create the fake for later ViewModel tests**

Create `app/src/test/java/com/example/test/reminder/FakeReminderScheduler.kt`:

```kotlin
package com.example.test.reminder

class FakeReminderScheduler : ReminderScheduler {
    var scheduledTime: Pair<Int, Int>? = null
        private set
    var cancelCallCount = 0
        private set

    override fun schedule(hour: Int, minute: Int) {
        scheduledTime = hour to minute
    }

    override fun cancel() {
        cancelCallCount++
        scheduledTime = null
    }
}
```

- [ ] **Step 10: Verify everything compiles together**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/example/test/reminder app/src/test/java/com/example/test/reminder app/src/androidTest/java/com/example/test/reminder
git commit -m "feat: add ReminderWorker and ReminderScheduler"
```

---

## Task 3: SettingsViewModel + SettingsScreen

**Files:**
- Create: `app/src/main/java/com/example/test/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/example/test/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/example/test/ui/settings/SettingsViewModelTest.kt`
- Modify: `app/src/main/java/com/example/test/ui/list/EntryListScreen.kt` (replace entire file)
- Modify: `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt` (replace entire file)
- Modify: `app/src/main/java/com/example/test/MainActivity.kt` (replace entire file)

**Interfaces:**
- Consumes: `ReminderPreferences`, `FakeReminderPreferences`, `ReminderScheduler`, `FakeReminderScheduler` from Tasks 1-2.
- Produces: `SettingsUiState` (`reminderEnabled`, `reminderHour`, `reminderMinute`, `permissionDenied`); `SettingsViewModel(reminderPreferences, reminderScheduler)` with `onPermissionGranted()`, `onPermissionDenied()`, `onReminderDisabled()`, `onTimeChanged(hour, minute)`; `SettingsScreen(reminderPreferences, reminderScheduler, onBack)`. `EntryListScreen` gains `onShowSettings: () -> Unit`. `MoodJournalNavHost` gains `reminderPreferences: ReminderPreferences` and `reminderScheduler: ReminderScheduler` parameters and a `Routes.SETTINGS` destination. Notification-tap routing (`startAtNewEntry`) is deliberately **not** added here — Task 4 adds it as its own change on top of this one, to keep this task's file changes focused on Settings alone.

- [ ] **Step 1: Write the failing ViewModel tests**

Create `app/src/test/java/com/example/test/ui/settings/SettingsViewModelTest.kt`:

```kotlin
package com.example.test.ui.settings

import com.example.test.reminder.FakeReminderPreferences
import com.example.test.reminder.FakeReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun initialState_reflectsStoredPreferences() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 7, minute = 30)
        val viewModel = SettingsViewModel(preferences, FakeReminderScheduler())

        assertTrue(viewModel.uiState.value.reminderEnabled)
        assertEquals(7, viewModel.uiState.value.reminderHour)
        assertEquals(30, viewModel.uiState.value.reminderMinute)
    }

    @Test
    fun onPermissionGranted_enablesAndSchedules() {
        val preferences = FakeReminderPreferences()
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onPermissionGranted()

        assertTrue(viewModel.uiState.value.reminderEnabled)
        assertTrue(preferences.isEnabled())
        assertEquals(20 to 0, scheduler.scheduledTime)
    }

    @Test
    fun onPermissionDenied_leavesDisabledAndShowsMessage() {
        val preferences = FakeReminderPreferences()
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onPermissionDenied()

        assertFalse(viewModel.uiState.value.reminderEnabled)
        assertTrue(viewModel.uiState.value.permissionDenied)
        assertNull(scheduler.scheduledTime)
        assertFalse(preferences.isEnabled())
    }

    @Test
    fun onReminderDisabled_persistsAndCancels() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 20, minute = 0)
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onReminderDisabled()

        assertFalse(viewModel.uiState.value.reminderEnabled)
        assertFalse(preferences.isEnabled())
        assertEquals(1, scheduler.cancelCallCount)
    }

    @Test
    fun onTimeChanged_whileEnabled_reschedules() {
        val preferences = FakeReminderPreferences(enabled = true, hour = 20, minute = 0)
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onTimeChanged(7, 45)

        assertEquals(7, viewModel.uiState.value.reminderHour)
        assertEquals(45, viewModel.uiState.value.reminderMinute)
        assertEquals(7 to 45, scheduler.scheduledTime)
        assertEquals(7 to 45, preferences.getTime())
    }

    @Test
    fun onTimeChanged_whileDisabled_persistsButDoesNotSchedule() {
        val preferences = FakeReminderPreferences(enabled = false, hour = 20, minute = 0)
        val scheduler = FakeReminderScheduler()
        val viewModel = SettingsViewModel(preferences, scheduler)

        viewModel.onTimeChanged(7, 45)

        assertEquals(7 to 45, preferences.getTime())
        assertNull(scheduler.scheduledTime)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `SettingsViewModel` doesn't exist yet.

- [ ] **Step 3: Create the ViewModel**

Create `app/src/main/java/com/example/test/ui/settings/SettingsViewModel.kt`:

```kotlin
package com.example.test.ui.settings

import androidx.lifecycle.ViewModel
import com.example.test.reminder.ReminderPreferences
import com.example.test.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val permissionDenied: Boolean = false,
)

class SettingsViewModel(
    private val reminderPreferences: ReminderPreferences,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        run {
            val (hour, minute) = reminderPreferences.getTime()
            SettingsUiState(
                reminderEnabled = reminderPreferences.isEnabled(),
                reminderHour = hour,
                reminderMinute = minute,
            )
        },
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onPermissionGranted() {
        val state = _uiState.value
        _uiState.value = state.copy(reminderEnabled = true, permissionDenied = false)
        reminderPreferences.setReminder(true, state.reminderHour, state.reminderMinute)
        reminderScheduler.schedule(state.reminderHour, state.reminderMinute)
    }

    fun onPermissionDenied() {
        _uiState.value = _uiState.value.copy(reminderEnabled = false, permissionDenied = true)
    }

    fun onReminderDisabled() {
        val state = _uiState.value
        _uiState.value = state.copy(reminderEnabled = false)
        reminderPreferences.setReminder(false, state.reminderHour, state.reminderMinute)
        reminderScheduler.cancel()
    }

    fun onTimeChanged(hour: Int, minute: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(reminderHour = hour, reminderMinute = minute)
        reminderPreferences.setReminder(state.reminderEnabled, hour, minute)
        if (state.reminderEnabled) {
            reminderScheduler.schedule(hour, minute)
        }
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.test.ui.settings.SettingsViewModelTest"`
Expected: all 6 tests PASS.

- [ ] **Step 5: Create the Settings screen**

Create `app/src/main/java/com/example/test/ui/settings/SettingsScreen.kt`:

```kotlin
package com.example.test.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.reminder.ReminderPreferences
import com.example.test.reminder.ReminderScheduler
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    reminderPreferences: ReminderPreferences,
    reminderScheduler: ReminderScheduler,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(reminderPreferences, reminderScheduler) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Daily reminder")
                Switch(
                    checked = uiState.reminderEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            if (needsPermission) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.onPermissionGranted()
                            }
                        } else {
                            viewModel.onReminderDisabled()
                        }
                    },
                )
            }
            if (uiState.permissionDenied) {
                Text(
                    "Notification permission needed for reminders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (uiState.reminderEnabled) {
                val timeLabel = String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    uiState.reminderHour,
                    uiState.reminderMinute,
                )
                Text(
                    "Reminder time: $timeLabel",
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable { showTimePicker = true },
                )
            }
        }

        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = uiState.reminderHour,
                initialMinute = uiState.reminderMinute,
            )
            Dialog(onDismissRequest = { showTimePicker = false }) {
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TimePicker(state = timePickerState)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                            TextButton(onClick = {
                                viewModel.onTimeChanged(timePickerState.hour, timePickerState.minute)
                                showTimePicker = false
                            }) { Text("OK") }
                        }
                    }
                }
            }
        }
    }
}
```

Note: this hand-rolls the picker dialog with `Dialog` + `Surface` rather than a built-in `TimePickerDialog` composable — the project's Compose Material3 BOM (`2024.12.01`) predates Material3 shipping that convenience wrapper, so this is the standard workaround, not a stopgap.

- [ ] **Step 6: Add the Settings entry point to the Entry List screen**

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
import androidx.compose.material.icons.filled.Settings
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
    onShowSettings: () -> Unit,
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
                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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

- [ ] **Step 7: Wire Settings into the nav host**

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
import com.example.test.reminder.ReminderPreferences
import com.example.test.reminder.ReminderScheduler
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import com.example.test.ui.editor.EntryEditorScreen
import com.example.test.ui.list.EntryListScreen
import com.example.test.ui.settings.SettingsScreen
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
    const val SETTINGS = "settings"
    fun entryEditorEdit(id: Long) = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=$id"
    fun photoViewer(photoPath: String) = "$PHOTO_VIEWER/$photoPath"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    reminderPreferences: ReminderPreferences,
    reminderScheduler: ReminderScheduler,
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
                onShowSettings = { navController.navigate(Routes.SETTINGS) },
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
        composable(Routes.SETTINGS) {
            SettingsScreen(
                reminderPreferences = reminderPreferences,
                reminderScheduler = reminderScheduler,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 8: Construct the reminder subsystems in MainActivity**

Replace the full contents of `app/src/main/java/com/example/test/MainActivity.kt`:

```kotlin
package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.test.data.AppDatabase
import com.example.test.reminder.SharedPreferencesReminderPreferences
import com.example.test.reminder.WorkManagerReminderScheduler
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
        val reminderPreferences = SharedPreferencesReminderPreferences(applicationContext)
        val reminderScheduler = WorkManagerReminderScheduler(applicationContext)
        setContent {
            MoodJournalTheme {
                MoodJournalNavHost(
                    repository = repository,
                    photoStorage = photoStorage,
                    reminderPreferences = reminderPreferences,
                    reminderScheduler = reminderScheduler,
                )
            }
        }
    }
}
```

- [ ] **Step 9: Install and manually verify**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`. On the device: tap the new gear icon in the Entry List's top bar; toggle "Daily reminder" on (grant the notification permission prompt on API 33+); confirm the time row appears, tap it, change the time in the picker, tap OK, confirm the row updates; toggle it off; confirm the row disappears. Then check a job was actually scheduled/cancelled:

```bash
adb shell dumpsys jobscheduler | grep -A 3 com.example.test
```

Expected: a job listed while the reminder is on, gone (or absent) after toggling off.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/test/ui/settings app/src/test/java/com/example/test/ui/settings app/src/main/java/com/example/test/ui/list/EntryListScreen.kt app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt app/src/main/java/com/example/test/MainActivity.kt
git commit -m "feat: add Settings screen with daily reminder toggle and time picker"
```

---

## Task 4: Notification tap opens a new entry directly

**Files:**
- Modify: `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt` (replace entire file)
- Modify: `app/src/main/java/com/example/test/MainActivity.kt` (replace entire file)

**Interfaces:**
- Consumes: `EXTRA_OPEN_NEW_ENTRY` from Task 2.
- Produces: `MoodJournalNavHost` gains a `startAtNewEntry: Boolean = false` parameter.

- [ ] **Step 1: Read the intent extra in MainActivity and pass it through**

Replace the full contents of `app/src/main/java/com/example/test/MainActivity.kt`:

```kotlin
package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.test.data.AppDatabase
import com.example.test.reminder.EXTRA_OPEN_NEW_ENTRY
import com.example.test.reminder.SharedPreferencesReminderPreferences
import com.example.test.reminder.WorkManagerReminderScheduler
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
        val reminderPreferences = SharedPreferencesReminderPreferences(applicationContext)
        val reminderScheduler = WorkManagerReminderScheduler(applicationContext)
        val startAtNewEntry = intent?.getBooleanExtra(EXTRA_OPEN_NEW_ENTRY, false) ?: false
        setContent {
            MoodJournalTheme {
                MoodJournalNavHost(
                    repository = repository,
                    photoStorage = photoStorage,
                    reminderPreferences = reminderPreferences,
                    reminderScheduler = reminderScheduler,
                    startAtNewEntry = startAtNewEntry,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Add the one-shot navigation effect to the nav host**

Replace the full contents of `app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt`:

```kotlin
package com.example.test.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.test.reminder.ReminderPreferences
import com.example.test.reminder.ReminderScheduler
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import com.example.test.ui.editor.EntryEditorScreen
import com.example.test.ui.list.EntryListScreen
import com.example.test.ui.settings.SettingsScreen
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
    const val SETTINGS = "settings"
    fun entryEditorEdit(id: Long) = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=$id"
    fun photoViewer(photoPath: String) = "$PHOTO_VIEWER/$photoPath"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    reminderPreferences: ReminderPreferences,
    reminderScheduler: ReminderScheduler,
    startAtNewEntry: Boolean = false,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.ENTRY_LIST) {
        composable(Routes.ENTRY_LIST) {
            var hasHandledStartAtNewEntry by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (startAtNewEntry && !hasHandledStartAtNewEntry) {
                    hasHandledStartAtNewEntry = true
                    navController.navigate(Routes.ENTRY_EDITOR_NEW)
                }
            }
            EntryListScreen(
                repository = repository,
                photoStorage = photoStorage,
                onAddEntry = { navController.navigate(Routes.ENTRY_EDITOR_NEW) },
                onEntryClick = { id -> navController.navigate(Routes.entryEditorEdit(id)) },
                onShowTrend = { navController.navigate(Routes.MOOD_TREND) },
                onShowSettings = { navController.navigate(Routes.SETTINGS) },
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
        composable(Routes.SETTINGS) {
            SettingsScreen(
                reminderPreferences = reminderPreferences,
                reminderScheduler = reminderScheduler,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

Why the `rememberSaveable`-backed guard, not just a bare `LaunchedEffect(Unit)`: Nav Compose normally keeps an already-visited destination's composition alive across a `popBackStack` (so re-entering `Routes.ENTRY_LIST` this way wouldn't re-run `LaunchedEffect(Unit)` anyway) — but `startAtNewEntry` never changes for the lifetime of this `NavHost` instance, so relying purely on that composition-retention behavior would be fragile against any future change to how this destination gets entered. The explicit flag makes "only once" true by construction instead of by accident.

- [ ] **Step 3: Install and manually verify the tap-to-new-entry route**

Run: `./gradlew :app:installDebug`

Waiting for a real `WorkManager`-scheduled notification isn't practical for manual QA (the delay can be up to 24 hours), so verify the routing logic directly by sending the same intent extra the notification's `PendingIntent` sends:

```bash
adb shell am start -n com.example.test/.MainActivity --ez open_new_entry true
```

Expected: the app opens directly into "New entry" (not the list). Type some text, tap Save, confirm you land on the Entry List afterward (not stuck, not re-opening a new entry again). Then launch normally (`adb shell am start -n com.example.test/.MainActivity`, no extra) and confirm it opens on the Entry List as before.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/test/MainActivity.kt app/src/main/java/com/example/test/ui/MoodJournalNavHost.kt
git commit -m "feat: open a new entry directly when the reminder notification is tapped"
```

---

## Task 5: Update docs and run the full verification pass

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: the completed feature from Tasks 1-4.
- Produces: nothing consumed by later tasks — this is the closing task.

- [ ] **Step 1: Move the reminder out of README's out-of-scope list**

In `README.md`, replace:

```markdown
**Shipped as a fast-follow:**

- Photo attachments — one photo per entry, from the gallery or camera, with
  a full-screen viewer

**Explicitly out of scope for now** (each is a candidate fast-follow, to be
designed separately when picked up):

- Search/filter by text, mood, or date range
- Daily reminder notifications
```

with:

```markdown
**Shipped as a fast-follow:**

- Photo attachments — one photo per entry, from the gallery or camera, with
  a full-screen viewer
- Daily reminder notification — a user-configurable time, skipped on days
  already journaled, tapping it opens a new entry directly

**Explicitly out of scope for now** (each is a candidate fast-follow, to be
designed separately when picked up):

- Search/filter by text, mood, or date range
```

- [ ] **Step 2: Add the Settings screen to Screens & navigation**

In `README.md`, replace:

```markdown
4. **Photo Viewer** — a full-screen view of an entry's photo, reachable from
   either the list thumbnail or the editor's photo preview.
```

with:

```markdown
4. **Photo Viewer** — a full-screen view of an entry's photo, reachable from
   either the list thumbnail or the editor's photo preview.
5. **Settings** — a "Daily reminder" toggle and time picker, reachable via a
   gear icon in the Entry List's top bar. Turning it on schedules a daily
   notification (skipped on days already journaled) at the chosen time;
   tapping the notification opens a new entry directly.
```

- [ ] **Step 3: Add WorkManager to the tech stack list**

In `README.md`, replace:

```markdown
- Coil, for loading photo attachments
- ViewModel + StateFlow for UI state
```

with:

```markdown
- Coil, for loading photo attachments
- WorkManager, for the daily reminder notification
- ViewModel + StateFlow for UI state
```

- [ ] **Step 4: Update the Roadmap section**

In `README.md`, replace:

```markdown
## Roadmap

Photo attachments shipped — see
[Photo attachments design](docs/superpowers/specs/2026-09-24-photo-attachments-design.md).

Remaining post-MVP phases, each to get its own design pass when picked up:

- Search/filter (query design, possibly full-text search if needed)
- Daily reminder notification (WorkManager/AlarmManager, Android 13+
  notification permission, user-configurable time)
```

with:

```markdown
## Roadmap

Photo attachments and the daily reminder notification have shipped — see
[Photo attachments design](docs/superpowers/specs/2026-09-24-photo-attachments-design.md)
and [Daily reminder design](docs/superpowers/specs/2026-09-25-daily-reminder-design.md).

Remaining post-MVP phases, each to get its own design pass when picked up:

- Search/filter (query design, possibly full-text search if needed)
```

- [ ] **Step 5: Add the new spec/plan to the Documentation section**

In `README.md`, replace:

```markdown
- [Photo attachments design](docs/superpowers/specs/2026-09-24-photo-attachments-design.md)
  and [implementation plan](docs/superpowers/plans/2026-09-24-photo-attachments.md) —
  the first post-MVP fast-follow phase.
```

with:

```markdown
- [Photo attachments design](docs/superpowers/specs/2026-09-24-photo-attachments-design.md)
  and [implementation plan](docs/superpowers/plans/2026-09-24-photo-attachments.md) —
  the first post-MVP fast-follow phase.
- [Daily reminder design](docs/superpowers/specs/2026-09-25-daily-reminder-design.md)
  and [implementation plan](docs/superpowers/plans/2026-09-25-daily-reminder.md) —
  the second post-MVP fast-follow phase.
```

- [ ] **Step 6: Update CLAUDE.md's architecture notes**

In `CLAUDE.md`, replace:

```markdown
- **Photos** go through a `PhotoStorage` interface (`FilePhotoStorage` in production, `FakePhotoStorage` in tests), the same interface/real/fake shape as `JournalRepository`. Files live under `context.filesDir/photos/`; `JournalEntry.photoPath` stores only the bare filename, never a `content://` URI — see `docs/superpowers/specs/2026-09-24-photo-attachments-design.md`. There's a 4th nav destination, the Photo Viewer (`Routes.photoViewer(path)`), reachable from both the list thumbnail and the editor's preview.
```

with:

```markdown
- **Photos** go through a `PhotoStorage` interface (`FilePhotoStorage` in production, `FakePhotoStorage` in tests), the same interface/real/fake shape as `JournalRepository`. Files live under `context.filesDir/photos/`; `JournalEntry.photoPath` stores only the bare filename, never a `content://` URI — see `docs/superpowers/specs/2026-09-24-photo-attachments-design.md`. There's a 4th nav destination, the Photo Viewer (`Routes.photoViewer(path)`), reachable from both the list thumbnail and the editor's preview.
- **The daily reminder** lives in its own `com.example.test.reminder` package: `ReminderPreferences`/`SharedPreferencesReminderPreferences` (plain `SharedPreferences`, not DataStore) and `ReminderScheduler`/`WorkManagerReminderScheduler` follow the same interface/real/fake shape as everything else here. `ReminderWorker` deliberately does **not** take an injectable repository — like `MainActivity`, it builds `RoomJournalRepository` directly from `AppDatabase.getInstance()`, so there's still no custom `WorkerFactory` or `Application` subclass anywhere in this app. See `docs/superpowers/specs/2026-09-25-daily-reminder-design.md`. There's a 5th nav destination, Settings (`Routes.SETTINGS`), reachable via a gear icon in the Entry List's top bar.
```

- [ ] **Step 7: Update CLAUDE.md's scope discipline note**

In `CLAUDE.md`, replace:

```markdown
This is a deliberately narrow MVP — see `docs/superpowers/specs/2026-09-15-mood-journal-design.md` and the paired implementation plan in the same `docs/superpowers/` tree for the full rationale. Photo attachments shipped as the first fast-follow phase (`docs/superpowers/specs/2026-09-24-photo-attachments-design.md`); search/filter and reminder notifications remain explicitly out of scope, each needing its own design pass before implementation. Don't add them, new architectural layers, or new dependencies without confirming first.
```

with:

```markdown
This is a deliberately narrow MVP — see `docs/superpowers/specs/2026-09-15-mood-journal-design.md` and the paired implementation plan in the same `docs/superpowers/` tree for the full rationale. Photo attachments and the daily reminder notification shipped as the first two fast-follow phases (`docs/superpowers/specs/2026-09-24-photo-attachments-design.md`, `docs/superpowers/specs/2026-09-25-daily-reminder-design.md`); search/filter remains explicitly out of scope, needing its own design pass before implementation. Don't add it, new architectural layers, or new dependencies without confirming first.
```

- [ ] **Step 8: Run the full verification pass**

Confirm a device is available: `adb devices`.
Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lint`
Expected: all unit tests, all instrumented tests (including the two new `ReminderWorkerTest` cases), and lint all PASS with no new warnings introduced by this feature.

- [ ] **Step 9: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: update README and CLAUDE.md for the daily reminder"
```

---

## Done criteria

All 5 tasks complete; `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest` both fully green (including `SettingsViewModelTest` and `ReminderWorkerTest`); and, on-device, a user can: turn on a daily reminder at a chosen time from Settings, have it skip notifying on days they've already journaled, receive the notification at approximately the chosen time, and tap it to land directly in a new entry.

