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
./gradlew testDebugUnitTest --tests "com.syncro.ExampleUnitTest"   # run a single unit test class
./gradlew connectedAndroidTest        # run instrumented tests (app/src/androidTest), needs a device/emulator
./gradlew lint                        # Android Lint
```

There is no ktlint/detekt config in this repo — Android Lint is the only configured static check. There is currently only placeholder test coverage (`ExampleUnitTest`, `ExampleInstrumentedTest`); don't assume existing tests describe real behavior.

## Architecture

Standard Clean Architecture layering under `app/src/main/java/com/syncro/`:

- **`domain/`** — pure Kotlin, no Android framework deps besides Compose `Color` in models.
  - `model/SyncroItem.kt` defines the app's core sealed type: `SyncroItem` is `Event`, `Task`, or `Note`. Most of the UI operates on `SyncroItem`, not on Room entities directly.
  - `repository/` — interfaces only (`TaskRepository`, `EventRepository`, `NoteRepository`, `UserRepository`, `GoogleSyncRepository`).
  - `usecase/` — one class per operation (e.g. `SaveEventUseCase`, `SyncGoogleCalendarUseCase`, `ToggleTaskCompletionUseCase`). Use cases are the only things ViewModels call; they compose repositories and are the natural place for cross-cutting logic like "save locally, then push to Google."
- **`data/`** — implementations.
  - `local/dao/` + `local/entity/` — Room DAOs/entities, wired together in `local/SyncroDatabase.kt` (version-bumped manually). Migrations live in `local/Migrations.kt` and are registered in `AppModule` via `addMigrations`; `fallbackToDestructiveMigration` is still on, so any schema bump without a matching migration wipes local data (including unsynced items) — always add a migration when changing an entity.
  - `repository/*Impl.kt` — implement the domain repository interfaces, map between Room entities and `SyncroItem`/domain models.
  - `repository/GoogleSyncRepositoryImpl.kt` — talks to Google Calendar/Tasks APIs (Credential Manager for sign-in, `google-api-client`/`google-auth-library-oauth2-http` for calls) and reconciles remote state with the local Room DB via `remoteId` fields on entities.
  - `remote/GeminiApi.kt` is an unimplemented stub for a planned AI assistant feature (`presentation/assistant/AssistantScreen.kt` is the corresponding placeholder screen).
- **`di/AppModule.kt`** — single Hilt module (`SingletonComponent`), provides the Room DB, DAOs, and repository bindings. Add new repository/DAO providers here rather than creating additional modules unless there's a real scoping reason.
- **`presentation/`** — one package per screen (`home`, `calendar`, `event`, `notes`, `login`, `assistant`), each typically with a `XyzScreen.kt` (Compose UI) and `XyzViewModel.kt` (`@HiltViewModel`, exposes `StateFlow`/Compose state, calls use cases). Shared widgets live in `presentation/components/` and per-feature subcomponents in `presentation/<feature>/components/`.
  - `presentation/navigation/` — `Navigation.kt` defines the sealed `AppScreen` route list; `MainScaffold.kt` hosts the `NavHost` plus the floating bottom nav (`FloatingBottomNav.kt`).
  - `presentation/theme/` is the theme actually used by `MainActivity`. **`com.syncro.ui.theme.*` is dead/duplicate code left over from project scaffolding — do not add to it, and prefer deleting it outright if you touch theming.**
- Dependency injection is Hilt throughout: `SyncroApp` is `@HiltAndroidApp`, `MainActivity` is `@AndroidEntryPoint`, ViewModels use `@HiltViewModel` + `@Inject constructor`.

### Known legacy/dead code

- `ui/theme/` (see above) duplicates `presentation/theme/` and isn't referenced anywhere.

### Sync model

Local Room entities carry a `remoteId` (nullable) alongside their local `id`. The general pattern used by use cases like `SaveEventUseCase` is: write to the local Room DB first (source of truth for the UI), then call `GoogleSyncRepository` to push/pull against Google Calendar/Tasks and reconcile `remoteId`. `UploadUnsyncedItemsUseCase` handles pushing anything created while offline/unsynced.

## Key third-party dependencies

- Jetpack Compose (BOM-managed) + Material3 (note: an explicit `material3:1.4.0` override exists in `app/build.gradle.kts` for Expressive APIs not yet in the BOM's version).
- Room (KSP) for local persistence, Hilt (KSP) for DI, DataStore Preferences for simple settings (`data/preferences/ThemePreferences.kt`).
- `com.kizitonwose.calendar:compose` for the calendar grid UI.
- Coil 3 (compose/gif/video/network) and Media3 ExoPlayer for media rendering.
- Google Identity/Credential Manager + `google-api-client`/`google-api-services-calendar`/`google-api-services-tasks`/`google-auth-library-oauth2-http` for Google Sign-In and Calendar/Tasks sync — these are declared as raw coordinate strings in `app/build.gradle.kts` rather than via the version catalog (`libs.versions.toml`); follow that existing pattern if adding related deps, or migrate deliberately rather than mixing styles further.
