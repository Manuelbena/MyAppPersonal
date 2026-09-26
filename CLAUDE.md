# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Syncro is a native Android app (Kotlin, Jetpack Compose) that unifies tasks, calendar events, and notes into a single timeline, with two-way sync against a user's Google account (Calendar and Tasks APIs). UI strings and comments in the codebase are in Spanish; keep that convention when touching existing screens.

## Build, lint, and test commands

Windows shell (this repo is developed on Windows) — use `gradlew.bat` when running outside Git Bash, or `./gradlew` inside it.

```
./gradlew assembleDebug              # build debug APK
./gradlew installDebug                # build and install on connected device/emulator
./gradlew test                        # run JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.syncro.data.repository.TaskRepositoryImplTest"   # run a single unit test class
./gradlew connectedAndroidTest        # run instrumented tests (app/src/androidTest), needs a device/emulator
./gradlew lint                        # Android Lint
```

There is no ktlint/detekt config in this repo — Android Lint is the only configured static check.

### Tests

JVM unit tests in `app/src/test` run on Robolectric (`src/test/resources/robolectric.properties`: SDK 35, plain `Application` so Hilt/SyncroApp don't start) against a **real in-memory Room DB** (`testutil/TestData.kt`: `createInMemoryDatabase()`, fixed `DAY` date, `aTask`/`anEvent`/`aNote`/`aSynced*Entity` builders). Don't mock DAOs: most data-layer bugs so far were in SQL behavior (REPLACE cascades, pending counters). Each repository test class opens with a KDoc test plan (responsibilities + risks); bug fixes get a regression test commented `// Regresión: …`. `GoogleSyncRepositoryImplTest` covers the sync logic against `testutil/FakeGoogleRemoteDataSource` (in-memory Google Tasks/Calendar with PATCH semantics, 404s and simulated network errors) plus a fixed `Clock`; `data/local/dao/SyncContractTest` pins the DAO queries it relies on. `architecture/PresentationLayerDependenciesTest` forbids presentation from importing repositories or Room (`data.auth` and `data.preferences` are allowed). Domain use cases are tested as plain JUnit (no Robolectric) with in-memory fakes in `testutil/Fakes.kt`; fakes share a `CallLog` so tests can assert ordering (local write before Google push). `ExampleInstrumentedTest` in `androidTest` is still a placeholder.

## Architecture

Standard Clean Architecture layering under `app/src/main/java/com/syncro/`:

- **`domain/`** — pure Kotlin: no Android or Compose imports (enforced by `architecture/DomainLayerDependenciesTest`; its `knownExceptions` list is empty — keep it that way). Colors are `ArgbColor` (value class over the ARGB Int Room stores); `Priority` is a plain enum. Conversion to Compose `Color` and priority label/color live in `presentation/theme/ColorMapping.kt` (`toColor()`, `toArgbColor()`, `Priority.label`, `Priority.color`).
  - `model/SyncroItem.kt` defines the app's core sealed type: `SyncroItem` is `Event`, `Task`, or `Note`. Most of the UI operates on `SyncroItem`, not on Room entities directly.
  - `repository/` — interfaces only (`TaskRepository`, `EventRepository`, `NoteRepository`, `UserRepository`, `GoogleSyncRepository`).
  - `usecase/` — one class per operation (e.g. `SaveEventUseCase`, `SyncGoogleCalendarUseCase`, `ToggleTaskCompletionUseCase`). Use cases are the only things ViewModels call; they compose repositories and are the natural place for cross-cutting logic like "save locally, then push to Google."
  - Times are `LocalTime` in the domain (Room stores "HH:mm"; convert only in `data/local/StoredTime.kt`). "Now" comes from an injected `java.time.Clock` (provided in `AppModule`), never `LocalDateTime.now()` in use cases, so tests use `Clock.fixed`.
  - IDs for new tasks/events/notes are generated in the save use cases (UUID); repositories `require` a non-blank id and never invent one. Validation rules live in `model/Validation.kt` and use cases return `Result.failure` (never throw) for invalid input, so the UI can show the message.
- **`data/`** — implementations.
  - `local/dao/` + `local/entity/` — Room DAOs/entities, wired together in `local/SyncroDatabase.kt` (version-bumped manually). Migrations live in `local/Migrations.kt` and are registered in `AppModule` via `addMigrations`; `fallbackToDestructiveMigration` is still on, so any schema bump without a matching migration wipes local data (including unsynced items) — always add a migration when changing an entity.
  - `repository/*Impl.kt` — implement the domain repository interfaces, map between Room entities and `SyncroItem`/domain models.
  - `repository/GoogleSyncRepositoryImpl.kt` — all sync logic (download, conflict rules, deletions, push). It never touches HTTP directly: `remote/GoogleRemoteDataSource` is the transport boundary (real impl `GoogleApiRemoteDataSource`: credentials, pagination) and `sync/SyncScheduler` the retry boundary (real impl `WorkManagerSyncScheduler`), so both are faked in tests. Keep new sync logic in the repository, not in the data source.
  - `remote/GeminiApi.kt` is an unimplemented stub for a planned AI assistant feature (`presentation/assistant/AssistantScreen.kt` is the corresponding placeholder screen).
- **`di/AppModule.kt`** — single Hilt module (`SingletonComponent`), provides the Room DB, DAOs, and repository bindings. Add new repository/DAO providers here rather than creating additional modules unless there's a real scoping reason.
- **`presentation/`** — one package per screen (`home`, `calendar`, `event`, `notes`, `login`, `assistant`), each typically with a `XyzScreen.kt` (Compose UI) and `XyzViewModel.kt` (`@HiltViewModel`, exposes `StateFlow`/Compose state, calls use cases). Shared widgets live in `presentation/components/` and per-feature subcomponents in `presentation/<feature>/components/`.
  - `presentation/navigation/` — `Navigation.kt` defines the sealed `AppScreen` route list; `MainScaffold.kt` hosts the `NavHost` plus the floating bottom nav (`FloatingBottomNav.kt`).
  - `presentation/theme/` is the theme actually used by `MainActivity`. **`com.syncro.ui.theme.*` is dead/duplicate code left over from project scaffolding — do not add to it, and prefer deleting it outright if you touch theming.**
- Google sign-in is split: `data/auth/GoogleSignInClient` shows the Credential Manager account picker (needs an Activity `Context`) and returns a `User`; `SignInWithGoogleUseCase(account)` validates and saves it. `LoginViewModel` is the one ViewModel that calls a non-use-case class directly, because the picker is system UI.
- Dependency injection is Hilt throughout: `SyncroApp` is `@HiltAndroidApp`, `MainActivity` is `@AndroidEntryPoint`, ViewModels use `@HiltViewModel` + `@Inject constructor`.

### Known legacy/dead code

- `ui/theme/` (see above) duplicates `presentation/theme/` and isn't referenced anywhere.

### Sync model

Offline-first. Room is the UI's source of truth; tasks/events carry a nullable `remoteId` (Google id) and a `pendingChanges` counter. Every local write (create/edit/toggle) increments `pendingChanges` in the DAO/repository, then the use case calls `GoogleSyncRepository.pushTask`/`pushEvent`, which read the *local* state and insert or patch in Google. `markSynced` only zeroes the counter if it didn't change during the upload. A failed push schedules `data/sync/PushPendingChangesWorker` (WorkManager, network constraint, `KEEP`), which calls `pushPendingChanges()` for everything pending on any date. Downloads (`syncTasks`, `syncCalendar(range)`) never overwrite or delete items with pending changes (local wins), and delete local items whose `remoteId` disappeared from Google. If a push gets 404/410 the Google deletion wins and the local item is removed. Subtask state is stored in the Google event description (`Subtareas:` + `- [x]`/`- [ ]`); Google Tasks stores no time, so local task time is kept.

## Key third-party dependencies

- Jetpack Compose (BOM-managed) + Material3 (note: an explicit `material3:1.4.0` override exists in `app/build.gradle.kts` for Expressive APIs not yet in the BOM's version).
- Room (KSP) for local persistence, Hilt (KSP) for DI, DataStore Preferences for simple settings (`data/preferences/ThemePreferences.kt`).
- `com.kizitonwose.calendar:compose` for the calendar grid UI.
- Coil 3 (compose/gif/video/network) and Media3 ExoPlayer for media rendering.
- Google Identity/Credential Manager + `google-api-client`/`google-api-services-calendar`/`google-api-services-tasks`/`google-auth-library-oauth2-http` for Google Sign-In and Calendar/Tasks sync — these are declared as raw coordinate strings in `app/build.gradle.kts` rather than via the version catalog (`libs.versions.toml`); follow that existing pattern if adding related deps, or migrate deliberately rather than mixing styles further.
