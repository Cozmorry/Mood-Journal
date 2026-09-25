# Daily Reminder Notification — Design

## Purpose

Let the user turn on a daily reminder, at a time of their choosing, that
nudges them to write a journal entry — skipped automatically on days they've
already written one. This is the second of the three fast-follow phases
called out in the [MVP design](2026-09-15-mood-journal-design.md#future-phases-not-designed-yet),
following [photo attachments](2026-09-24-photo-attachments-design.md).

## Scope

**In scope:**
- A toggle + time picker on a new Settings screen
- A daily notification, fired approximately (not to-the-minute) at the
  chosen time, skipped if the user already has an entry for that day
- Android 13+ `POST_NOTIFICATIONS` permission handling
- Tapping the notification opens the Entry Editor directly in "new" mode

**Explicitly out of scope for this phase:**
- Exact-time delivery (`AlarmManager` / `SCHEDULE_EXACT_ALARM`) — approximate
  `WorkManager` timing is enough for a journaling nudge
- Multiple reminders per day, or per-day-of-week scheduling
- Correcting for DST drift (see [Error handling](#error-handling))
- Reacting to a notification-permission grant made outside the app (via
  system Settings) — the user re-opens the app's Settings screen and
  re-toggles

## Architecture

Extends the existing unidirectional flow with two new parallel subsystems,
both following the same interface + real-impl + fake-for-tests shape as
`JournalRepository` and `PhotoStorage`:

```
Compose UI  -->  ViewModel (StateFlow)  -->  Repository  -->  Room DAO  -->  SQLite
                              \-->  ReminderPreferences  -->  SharedPreferences
                              \-->  ReminderScheduler     -->  WorkManager
```

`ReminderWorker` (a `CoroutineWorker`, run by WorkManager on its own
schedule, not driven by any ViewModel) builds its own `RoomJournalRepository`
directly from `AppDatabase.getInstance(applicationContext)` inside
`doWork()` — the same manual-construction pattern `MainActivity` already
uses for the repository and photo storage. No DI framework, no custom
`WorkerFactory`, no custom `Application` subclass.

## Data model & persistence

No new Room entity or migration — the reminder's own state (whether it's on,
and the chosen hour/minute) is two small values that don't warrant a
database table, `Room` migration, or a new dependency like DataStore
Preferences. Plain `SharedPreferences` is sufficient:

```kotlin
interface ReminderPreferences {
    fun isEnabled(): Boolean
    fun getTime(): Pair<Int, Int> // hour (0-23), minute (0-59)
    fun setReminder(enabled: Boolean, hour: Int, minute: Int)
}
```

- `SharedPreferencesReminderPreferences(context)` — production implementation,
  backed by a dedicated `SharedPreferences` file (`"reminder_prefs"`), keys
  `enabled: Boolean`, `hour: Int`, `minute: Int`. Default time when never
  set: 20:00 (8:00 PM).
- `FakeReminderPreferences` — in-memory, for `SettingsViewModel` unit tests.

`getSince(sinceEpochMillis)` on the existing `JournalRepository` (unchanged)
is what the worker uses to check "does today already have an entry" — no
new repository method needed.

## Scheduling

```kotlin
interface ReminderScheduler {
    fun schedule(hour: Int, minute: Int)
    fun cancel()
}
```

- `WorkManagerReminderScheduler(context)` — production implementation.
  `schedule()` computes the initial delay to the next occurrence of
  `hour:minute` (today if that time hasn't passed yet today, otherwise
  tomorrow) via `java.util.Calendar` (matching the existing
  `java.util.Date`-based date handling in `EntryListScreen.kt`, rather than
  introducing `java.time`), then calls:

  ```kotlin
  WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      REMINDER_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
          .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
          .build(),
  )
  ```

  `ExistingPeriodicWorkPolicy.UPDATE` means changing the time while the
  reminder is already on just re-enqueues with a new initial delay — no
  separate cancel step needed.
- `cancel()` calls `WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)`.
- WorkManager persists enqueued periodic work across device reboots on its
  own; no `BOOT_COMPLETED` receiver needed.
- `FakeReminderScheduler` — records `schedule`/`cancel` calls, for
  `SettingsViewModel` unit tests. No real `WorkManager` instance is touched
  in unit tests.

## Screens & navigation

- New 5th screen, `SettingsScreen` (route `Routes.SETTINGS`), reachable via
  a new gear icon in the Entry List's top bar, alongside the existing Mood
  Trend icon.
- UI: a `Switch` ("Daily reminder") and, only when it's on, a tappable time
  row below it showing the current time; tapping it opens a Material3
  `TimePicker` dialog (already available via the existing Compose Material3
  dependency — no new library).
- Turning the toggle on:
  1. On API 33+, requests `POST_NOTIFICATIONS` via the same
     `rememberLauncherForActivityResult(RequestPermission())` pattern
     already used for the camera in `EntryEditorScreen.kt`. On API < 33, no
     runtime prompt is needed — proceed directly to step 2.
  2. On grant (or on API < 33): `SettingsViewModel` calls
     `reminderPreferences.setReminder(true, hour, minute)` then
     `reminderScheduler.schedule(hour, minute)`.
  3. On denial: the toggle switches back off, and an inline message appears
     ("Notification permission needed for reminders") — mirrors the
     existing camera-denial UX exactly.
- Turning the toggle off: `setReminder(false, hour, minute)` then
  `reminderScheduler.cancel()`.
- Changing the time while on: `setReminder(true, newHour, newMinute)` then
  `reminderScheduler.schedule(newHour, newMinute)`.
- `MainActivity` reads a `"open_new_entry"` boolean intent extra (set by the
  notification's tap `PendingIntent`) once at launch and passes it to
  `MoodJournalNavHost` as a `startAtNewEntry: Boolean` parameter. The start
  destination stays `Routes.ENTRY_LIST` either way — `startAtNewEntry` only
  triggers a one-shot `LaunchedEffect(Unit)` (guarded so it never re-fires
  on recomposition or configuration change) that calls
  `navController.navigate(Routes.ENTRY_EDITOR_NEW)` immediately after the
  graph starts. Keeping `Routes.ENTRY_LIST` underneath in the back stack
  this way means the editor's existing `onDone` callback
  (`popBackStack(Routes.ENTRY_LIST, inclusive = false)`) needs no changes
  and keeps working exactly as it does today — the user lands on the list
  after saving or cancelling, they just skip seeing it first. (Making
  `ENTRY_EDITOR_NEW` itself the start destination was the first approach
  considered, but it breaks that same `onDone` call: `Routes.ENTRY_LIST`
  would never have been pushed, so popping back to it would silently do
  nothing.)

## Notification content & permission

- `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
  added to the manifest (a no-op below API 33, where notifications don't
  require a runtime permission).
- `ReminderWorker` creates a `NotificationChannel`
  (`NotificationManager.IMPORTANCE_DEFAULT`) before posting — the call is
  idempotent, so creating it on every run is safe and needs no separate
  one-time-setup step.
- Notification: title "Mood Journal", text "How are you feeling today?",
  tapping it launches `MainActivity` with the `"open_new_entry"` extra set,
  via a `PendingIntent` with `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.

## Error handling

- **Notification permission denied**: toggle reverts to off, inline message
  shown (see [Screens & navigation](#screens--navigation)). No crash.
- **DST/timezone shifts**: a `PeriodicWorkRequest`'s 24-hour interval isn't
  recomputed on each fire, so the reminder can drift by up to an hour
  across a DST transition. Accepted as a known limitation for this phase —
  correcting for it would mean rescheduling on every fire, which adds
  complexity for a twice-a-year, hour-scale drift on a best-effort
  reminder.
- **Worker failure** (e.g. a Room read error): caught inside `doWork()`,
  the worker returns `Result.success()` regardless of whether the catch
  fired — periodic work isn't retried early on failure either way, so
  `Result.failure()` would only make the failure visible in WorkManager's
  own status tooling without changing what happens next. A silently-missed
  reminder is preferable to a confusing "failed but nothing retries"
  signal for a database problem the worker can't fix itself.

## Testing

- `FakeReminderPreferences` + `FakeReminderScheduler` → `SettingsViewModelTest`
  (unit): toggling on persists state and calls `schedule`; toggling off
  calls `cancel`; changing the time while on calls `schedule` again with
  the new time; the permission-denied path leaves the toggle off and
  doesn't call `schedule`.
- `ReminderWorkerTest` (instrumented, `app/src/androidTest`, alongside
  `FilePhotoStorageTest`/`AppDatabaseMigrationTest`): using
  `androidx.work:work-testing`'s `TestListenableWorkerBuilder`.
  `work-testing` needs a real Android `Context`, which is why this is
  instrumented rather than a plain JVM unit test (no Robolectric in this
  project). Because `ReminderWorker` deliberately hardcodes
  `AppDatabase.getInstance(applicationContext)` rather than taking an
  injectable repository (see [Architecture](#architecture) — no custom
  `WorkerFactory`), the test runs against the real on-device database, not
  an isolated in-memory one. Unlike `EntryCreationFlowTest` (which only
  needs unique text to avoid *collisions* and never cleans up), the "no
  entry exists today" case is a real precondition, not just a
  non-collision concern, so this test needs actual isolation: a `@Before`
  step deletes any existing entries with `createdAt` on or after today's
  start-of-day (via `JournalEntryDao`, same DAO `JournalEntryDaoTest`
  already uses) to establish a known-clean baseline, and an `@After` step
  does the same to avoid leaking test data into later runs or real device
  usage. Two cases: insert one entry for today, run the worker, assert no
  notification posted (via
  `NotificationManagerCompat.from(context).activeNotifications`); with no
  entry for today, run the worker, assert exactly one notification
  posted.
- **Known gap**: no automated test exercises the real `POST_NOTIFICATIONS`
  system permission dialog, or a real WorkManager-scheduled fire days later
  — both require either manual verification or infrastructure this project
  doesn't have. This mirrors the accepted photo-picker/camera-round-trip
  gap from the photo attachments phase.
