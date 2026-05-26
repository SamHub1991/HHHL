# HHHL Web-Parity Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the HHHL native app toward Sharkey web parity while keeping a high-performance X-style mobile UI.

**Architecture:** Extend the current KMP layers instead of rewriting them. Add runtime instance capability discovery first, then gate UI and repositories from those capabilities. Keep API, repository, state, and UI modules independently testable.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor Client, Kotlinx Serialization, Coroutines/Flow, Android Gradle Plugin, Gradle 8.10.2 from `D:\DevTools`.

---

## File Structure

- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/model/InstanceMeta.kt` - domain model for instance metadata and capabilities.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/api/InstanceMetaApi.kt` - Ktor API for `/api/meta`.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/repository/InstanceMetaRepository.kt` - error-mapped capability repository.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/state/InstanceMetaStateHolder.kt` - app-level capability state.
- Modify: `shared/src/commonMain/kotlin/cc/hhhl/client/state/DiscoverStateHolder.kt` - accept `canSearchNotes` and avoid unsupported note search.
- Modify: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/DiscoverScreen.kt` - disable/hide note search when capability says it is unavailable.
- Modify: `shared/src/commonMain/kotlin/cc/hhhl/client/App.kt` - load instance meta once after login and apply capabilities.
- Test: `shared/src/commonTest/kotlin/cc/hhhl/client/api/SharkeyInstanceMetaApiTest.kt`
- Test: `shared/src/commonTest/kotlin/cc/hhhl/client/repository/InstanceMetaRepositoryTest.kt`
- Test: `shared/src/commonTest/kotlin/cc/hhhl/client/state/InstanceMetaStateHolderTest.kt`
- Test: update `shared/src/commonTest/kotlin/cc/hhhl/client/state/DiscoverStateHolderTest.kt`

## Task 1: Instance Meta API

- [ ] Step 1: Write failing API tests for `/api/meta`.
- [ ] Step 2: Verify tests fail because `SharkeyInstanceMetaApi` does not exist.
- [ ] Step 3: Implement `InstanceMetaApi`, DTOs, and domain mapping.
- [ ] Step 4: Run the API tests and verify they pass.

## Task 2: Instance Meta Repository And State

- [ ] Step 1: Write failing repository tests for success and network/server errors.
- [ ] Step 2: Write failing state holder tests for loading, success, and error state.
- [ ] Step 3: Implement repository and state holder.
- [ ] Step 4: Run targeted tests and verify they pass.

## Task 3: Capability-Gated Discover

- [ ] Step 1: Write failing state tests proving disabled note search switches to user mode and avoids note-search API calls.
- [ ] Step 2: Update `DiscoverUiState` with `canSearchNotes`.
- [ ] Step 3: Update `DiscoverStateHolder` to apply capabilities.
- [ ] Step 4: Update `DiscoverScreen` to omit the notes tab when note search is disabled and show a short disabled message if needed.
- [ ] Step 5: Wire `InstanceMetaStateHolder` into `HhhlApp`/`MainShell`.
- [ ] Step 6: Run targeted tests and Android build.

## Task 4: Theme Registry

- [ ] Step 1: Write tests for stable theme IDs and fallback when a stored preset no longer exists.
- [ ] Step 2: Replace enum-only theme selection with a registry-backed preset model while preserving existing persisted enum names.
- [ ] Step 3: Add X light/dark/dim and HHHL green presets.
- [ ] Step 4: Update `ThemePicker` to render registry presets.
- [ ] Step 5: Run theme tests and Android build.

## Task 5: Compose Limits From Capabilities

- [ ] Step 1: Write state tests for max note length and max CW length validation.
- [ ] Step 2: Pass instance limits into `ComposeStateHolder`.
- [ ] Step 3: Disable send and show inline errors when text/CW exceed instance limits.
- [ ] Step 4: Run compose tests and Android build.

## Task 6: Media Picker And Drive Upload

- [ ] Step 1: Add platform interface for selected media.
- [ ] Step 2: Add API tests for `/api/drive/files/create`.
- [ ] Step 3: Implement Drive upload repository and progress state.
- [ ] Step 4: Wire Android picker and upload IDs into compose.
- [ ] Step 5: Run repository/state tests and Android emulator smoke test.

## Task 7: Richer Web-Parity Features

- [ ] Add reaction picker backed by default like and common emoji.
- [ ] Expand notification mapping for Sharkey event types.
- [ ] Add trends/explore surfaces gated by `canTrend`.
- [ ] Add federation surface gated by `canViewFederation`.
- [x] Add chat shell gated by `chatAvailability`.
- [x] Add chat room message timeline and text sending.
- [x] Add chat message reactions and local reaction state updates.
- [x] Add chat file attachment display, upload, and fileId sending.
- [x] Add chat room members list via `/api/chat/rooms/members`.
- [x] Add Drive file browser with search, sorting, pagination, upload, and Profile entry.
- [x] Add Drive folder browsing with folder pagination, enter-folder navigation, and parent navigation.
- [x] Add cache interfaces and in-memory timeline feed restore.
- [x] Persist Android timeline feed cache across cold app restarts.
- [ ] Add iOS persistent timeline cache implementation on macOS target.

## Verification Commands

- Targeted shared tests:
  `D:\DevTools\gradle-8.10.2\bin\gradle.bat :shared:testDebugUnitTest --tests <test-class>`
- Full shared Android unit tests:
  `D:\DevTools\gradle-8.10.2\bin\gradle.bat :shared:testDebugUnitTest`
- Android debug build:
  `D:\DevTools\gradle-8.10.2\bin\gradle.bat :androidApp:assembleDebug`
- Install on connected emulator:
  `D:\DevTools\gradle-8.10.2\bin\gradle.bat :androidApp:installDebug`

## Self-Review

- The plan covers the new web-parity objective instead of the old lightweight-MVP boundary.
- It starts with capabilities because HHHL exposes feature policy through `/api/meta`, and unsupported endpoints should not be presented as working UI.
- It keeps implementation modular and testable using the existing API/repository/state/UI pattern.
- iOS simulator verification remains blocked on this Windows workspace and must be performed later on macOS.
