# Fingerprint Unlock — Design

## Purpose

Let the user require biometric authentication (fingerprint, or whatever
strong biometric the device offers, with a device PIN/pattern/password
fallback) before they can see any journal content when the app launches.
Off by default — an opt-in toggle in the existing Settings screen, the same
shape as the daily reminder toggle.

## Scope

**In scope:**
- A "Fingerprint unlock" toggle in Settings, off by default
- A lock screen shown in front of the entire app (Entry List, editor, trend,
  everything) on cold start, when the toggle is on
- Falling back to the device's own PIN/pattern/password if biometric auth
  isn't available or fails, via Android's standard `BiometricPrompt`
- Disabling the toggle (or leaving it off) when the device has no enrolled
  biometric or device credential at all

**Explicitly out of scope for this phase:**
- Re-locking when the app is merely backgrounded and resumed (confirmed
  with the user) — only a fresh process re-prompts. Rotating the screen or
  switching apps briefly does not re-lock.
- Per-entry or per-screen locks (e.g. locking just the editor) — this is a
  whole-app gate only
- Any server-side or cross-device concept of identity — this is purely a
  local "is the person holding this phone allowed to see it" gate,
  consistent with the rest of the app being local-first
- Automated testing of the real system biometric prompt (see
  [Testing](#testing))

## Architecture

A new subsystem, following the same interface + real-impl + fake-for-tests
shape as `JournalRepository`, `PhotoStorage`, and the reminder subsystem —
but sitting *above* the nav graph rather than behind a screen route, since
it needs to gate the whole app rather than one destination:

```
MainActivity
  └─ AppLockGate (ui/lock) ── locked? ──> LockScreen (shows biometric prompt)
                          └── unlocked ─> MoodJournalNavHost (unchanged graph)
```

`AppLockGate` wraps the existing `MoodJournalNavHost` call in `MainActivity`;
`Routes` and the nav graph itself are untouched. Gating above the graph
(rather than as a nav destination) means there's no back-stack entry to pop
past — there is no route to navigate around.

`androidx.biometric.BiometricPrompt` requires a `FragmentActivity`, so
`MainActivity` changes its supertype from `ComponentActivity` to
`androidx.fragment.app.FragmentActivity`. `FragmentActivity` extends
`ComponentActivity`, so this is a safe supertype swap with no other
behavior change.

**New dependency**: `androidx.biometric:biometric:1.1.0` (the latest stable
release — the 1.2.0 line has never left alpha) — Jetpack's
standard biometric library, not the deprecated `FingerprintManager` API. Its
`USE_BIOMETRIC` permission is a normal permission merged into the manifest
automatically; no manifest edits needed.

## Data model & persistence

No new Room entity — whether the lock is on is a single boolean, the same
scale as the reminder's enabled flag:

```kotlin
interface AppLockPreferences {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
```

- `SharedPreferencesAppLockPreferences(context)` — production
  implementation, backed by its own `SharedPreferences` file
  (`"app_lock_prefs"`), a single `enabled: Boolean` key, default `false`.
- `FakeAppLockPreferences` — in-memory, for `SettingsViewModel` and
  `AppLockViewModel` unit tests.

## Authentication

```kotlin
interface BiometricAuthenticator {
    fun isAvailable(): Boolean
    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit)
}
```

- `SystemBiometricAuthenticator` — production implementation.
  - `isAvailable()`: `BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BIOMETRIC_SUCCESS`.
  - `authenticate()`: builds a `BiometricPrompt` with
    `PromptInfo.Builder().setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`
    (no negative button — it's mutually exclusive with allowing a device
    credential fallback) and `ContextCompat.getMainExecutor(activity)`.
    `onAuthenticationSucceeded` calls `onSuccess`; both
    `onAuthenticationError` and `onAuthenticationFailed` call `onFailure`
    (see [Error handling](#error-handling) for why these aren't
    distinguished further).
- `FakeBiometricAuthenticator` — settable canned result
  (`isAvailable`, and whether `authenticate()` calls `onSuccess` or
  `onFailure`), for unit tests. No real `BiometricPrompt` is touched in unit
  tests.

## Lock screen & gating

- `ui/lock/AppLockViewModel.kt`: holds `StateFlow<AppLockUiState>` where
  `AppLockUiState(val locked: Boolean)`. Initializes
  `locked = appLockPreferences.isEnabled()`. `onAuthenticated()` sets
  `locked = false`. Because this state lives in a `ViewModel`, it survives
  configuration changes (screen rotation doesn't re-prompt) but not process
  death — a fresh process always re-reads `isEnabled()` and starts locked
  again if the toggle is on. This is what gives the "whole app, cold start
  only" behavior without any extra lifecycle-observer code.
- `ui/lock/AppLockScreen.kt`: exposes `AppLockGate(activity, appLockPreferences, biometricAuthenticator, content: @Composable () -> Unit)`.
  - If `!locked`, renders `content()` (the nav host) directly.
  - If `locked`, renders a simple lock screen (app name/icon + a "Unlock"
    button) and fires the biometric prompt once via `LaunchedEffect(Unit)`,
    calling `biometricAuthenticator.authenticate(activity, viewModel::onAuthenticated, onFailure = {})`.
    The visible "Unlock" button calls the same `authenticate()` again, for
    the case where the user dismissed the system prompt (e.g. back button)
    and wants to retry without relaunching the app.
- `MainActivity` builds one `AppLockPreferences` and one
  `BiometricAuthenticator` alongside its existing repository/photo-storage/
  reminder objects, and wraps its `MoodJournalNavHost` call in `AppLockGate`.

## Settings screen

- A new `Switch` row, "Fingerprint unlock", below the existing reminder
  section, off by default.
- `SettingsViewModel` gains `appLockPreferences: AppLockPreferences` and
  `biometricAuthenticator: BiometricAuthenticator` constructor params, and
  `appLockEnabled: Boolean` + `appLockUnavailable: Boolean` fields on
  `SettingsUiState`.
- Turning the toggle on calls `biometricAuthenticator.isAvailable()` first:
  - Available: `appLockPreferences.setEnabled(true)`, state flips on.
  - Not available (no enrolled biometric and no device
    PIN/pattern/password): the toggle stays off, `appLockUnavailable = true`,
    and an inline message appears ("No fingerprint or device lock set up on
    this device") — mirrors the existing notification-permission-denied
    inline message exactly.
- Turning the toggle off calls `appLockPreferences.setEnabled(false)`. No
  scheduler-style cancellation is needed (unlike the reminder) — there's no
  running background component to stop, just a flag `AppLockGate` reads on
  next launch.
- `MoodJournalNavHost` and `SettingsScreen` both gain an
  `appLockPreferences: AppLockPreferences` parameter (passed through the
  same way `reminderPreferences` already is) plus `biometricAuthenticator`
  for the availability check.

## Error handling

- **Biometric/device-credential failure or cancel** (including the user
  dismissing the system prompt): stays locked, no crash, "Unlock" button
  available to retry. `onAuthenticationError` and `onAuthenticationFailed`
  are both treated as "not authenticated, stay locked" rather than
  distinguishing lockout/timeout/no-match reasons — Android's biometric
  stack already owns retry-limit and lockout policy internally (it disables
  its own UI temporarily after too many failed attempts), so there's
  nothing this app needs to additionally enforce or surface.
- **No biometric/device credential enrolled when toggling on**: see
  [Settings screen](#settings-screen) — the toggle simply refuses to turn
  on and explains why.
- **Enrollment changes after the toggle is already on** (e.g. the user
  later removes their only fingerprint in system settings): the next
  `authenticate()` call will fail every attempt since there's nothing to
  match; the user can still get in via the device-credential fallback
  (PIN/pattern/password), since that was the point of allowing it. Falling
  back further to "auto-disable the app's lock" is deliberately not handled
  — it would mean silently turning off the user's own security setting,
  which is a worse failure mode than requiring their device PIN once.

## Testing

- `FakeAppLockPreferences` + `FakeBiometricAuthenticator` →
  `AppLockViewModelTest` (unit, new): starts locked when the preference is
  enabled, starts unlocked when it isn't, `onAuthenticated()` unlocks.
- `SettingsViewModelTest` (unit, extended): toggling on with
  `isAvailable() == true` persists `enabled = true`; toggling on with
  `isAvailable() == false` leaves it off and sets `appLockUnavailable`;
  toggling off persists `enabled = false`.
- **Known gap**: no automated test drives the real system `BiometricPrompt`
  dialog — `androidx.biometric` doesn't expose a way to fake it from a
  Compose UI test, and there's no Robolectric in this project to shadow it
  either. This is verified manually on an emulator with a virtual
  fingerprint enrolled (Extended Controls → Fingerprint → enroll, then
  `adb -e emu finger touch 1` to simulate a scan), covering: toggle on →
  relaunch → prompt appears → scan succeeds → app opens; wrong/no
  fingerprint → device-credential fallback still works; toggle off →
  relaunch → no prompt. This mirrors the accepted photo-picker/camera and
  notification-permission-dialog gaps from the earlier fast-follow phases.
