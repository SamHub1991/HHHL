# HHHL KMP Lightweight Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a daily-usable Android+iOS lightweight native client for `https://dc.hhhl.cc` using Kotlin Multiplatform and Compose Multiplatform.

**Architecture:** Shared Kotlin code owns API access, auth state, repositories, UI state, and most Compose UI. Android and iOS targets provide only platform entry points, secure storage, browser/deep-link handling, and media picking. Implementation proceeds by vertical slices that are runnable in the Android emulator before live Sharkey integration is added.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Kotlinx Serialization, Ktor Client, Coroutines/Flow, SQLDelight, Gradle Kotlin DSL, Android Studio emulator, Xcode iOS simulator on macOS.

---

## File Structure

- Create: `settings.gradle.kts` - Gradle project includes `shared`, `androidApp`, and `iosApp`-compatible shared framework configuration.
- Create: `build.gradle.kts` - root plugin and repository declarations.
- Create: `gradle/libs.versions.toml` - centralized dependency versions.
- Create: `shared/build.gradle.kts` - KMP targets, Compose, Ktor, serialization, coroutines, SQLDelight setup.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/App.kt` - root Compose app.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/theme/HhhlTheme.kt` - minimal light/dark design tokens.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/navigation/AppRoute.kt` - app routes and bottom navigation model.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/model/*.kt` - domain models for notes, users, notifications, auth.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/fake/FakeData.kt` - fake notes/users/notifications for UI shell.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/component/*.kt` - top bar, bottom nav, note row, dividers, loading/error/empty states.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/*.kt` - login, timeline, note detail, compose, notifications, profile.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/api/*.kt` - Ktor client, DTOs, API endpoints.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/auth/*.kt` - MiAuth session handling and token storage interface.
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/repository/*.kt` - timeline, note, notification, user, compose repositories.
- Create: `shared/src/commonTest/kotlin/cc/hhhl/client/*Test.kt` - tests for URL construction, DTO parsing, pagination, and state reducers.
- Create: `androidApp/build.gradle.kts` - Android application target.
- Create: `androidApp/src/main/AndroidManifest.xml` - app metadata and deep-link intent filter.
- Create: `androidApp/src/main/kotlin/cc/hhhl/client/android/MainActivity.kt` - Android Compose entry point and deep-link handoff.
- Create: `iosApp/iosApp.xcodeproj` or generated iOS project - iOS shell for shared Compose UI.

## Task 1: Scaffold KMP Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `shared/build.gradle.kts`
- Create: `androidApp/build.gradle.kts`
- Create: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/kotlin/cc/hhhl/client/android/MainActivity.kt`

- [ ] Step 1: Verify local toolchain.

Run: `java -version`, `gradle -v`, and check Android SDK availability from Android Studio or `ANDROID_HOME`.

Expected: Java 17+ and Gradle usable from this workspace.

- [ ] Step 2: Create Gradle KMP scaffold with Android app and shared module.

Use Kotlin Multiplatform and Compose Multiplatform templates as the baseline. Set package namespace to `cc.hhhl.client`.

- [ ] Step 3: Add a minimal shared root composable.

`shared/src/commonMain/kotlin/cc/hhhl/client/App.kt` should expose:

```kotlin
@Composable
fun HhhlApp() {
    HhhlTheme {
        TimelineScreen()
    }
}
```

- [ ] Step 4: Connect Android `MainActivity` to `HhhlApp`.

`MainActivity` should call `setContent { HhhlApp() }`.

- [ ] Step 5: Run the Android app.

Run from Android Studio or CLI: `./gradlew :androidApp:installDebug`.

Expected: app launches in Android emulator and shows the initial fake-data timeline.

## Task 2: Minimal Design System

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/theme/HhhlTheme.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/component/HhhlDivider.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/component/HhhlTopBar.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/component/HhhlBottomNav.kt`

- [ ] Step 1: Define light/dark color tokens.

Use system-like backgrounds, foreground text, secondary text, hairline dividers, and one restrained accent color. Avoid gradients and card surfaces.

- [ ] Step 2: Implement `HhhlTheme`.

Expose typography and colors through Compose Material 3 where available. Keep corner radius minimal and do not introduce card styling helpers.

- [ ] Step 3: Implement top bar.

Top bar supports title, optional back button, and optional trailing icon actions. Height should stay compact and respect safe areas.

- [ ] Step 4: Implement bottom navigation.

Items: Timeline, Discover, Notifications, Profile. Use icons plus short labels. Avoid pill backgrounds except for the active affordance if needed.

- [ ] Step 5: Emulator check.

Run Android emulator on a small phone profile and a larger phone profile. Confirm no text overlaps and no card-like panels dominate the UI.

## Task 3: Fake Data Models And Note Row

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/model/User.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/model/Note.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/model/NotificationItem.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/fake/FakeData.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/component/NoteRow.kt`

- [ ] Step 1: Define domain models.

Models should include only fields needed for MVP UI: IDs, display name, username, avatar URL, note text, created time label, reply/renote/reaction counts, visibility, optional CW, optional media list.

- [ ] Step 2: Add fake data.

Create realistic Chinese sample text for HHHL usage, including one long note, one image note using local sample media metadata, one reply, and one renote.

- [ ] Step 3: Implement `NoteRow`.

Layout must be full width with avatar, name row, text, optional media grid, action row, and a divider. Do not wrap each note in a Card.

- [ ] Step 4: Run visual smoke test.

Expected: note rows read as a compact native list, not separate floating cards.

## Task 4: Shared Navigation And Core Screens

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/navigation/AppRoute.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/TimelineScreen.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/NoteDetailScreen.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/ComposeScreen.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/NotificationsScreen.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/ProfileScreen.kt`

- [ ] Step 1: Implement route enum/state.

Routes: Timeline, Discover, Notifications, Profile, NoteDetail(noteId), Compose(replyToId optional).

- [ ] Step 2: Implement timeline screen with fake data.

Add Home, Local, Global tabs and render fake note lists with `NoteRow`.

- [ ] Step 3: Implement note detail screen.

Show expanded selected fake note and fake replies using the same divider/list style.

- [ ] Step 4: Implement compose screen.

Add editor, visibility control, CW toggle, disabled image picker button with clear "media picker not connected" state, character count, and send button disabled for empty text.

- [ ] Step 5: Implement notifications screen.

Render notification rows with icon/avatar/text/time and divider.

- [ ] Step 6: Implement profile screen.

Render plain profile header, follow button, bio, stats, and note list.

- [ ] Step 7: Android emulator acceptance.

Manually navigate every screen. Confirm safe areas, bottom navigation, and compact layout on at least two phone sizes.

## Task 5: API Client And Serialization Tests

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/api/HhhlApiClient.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/api/dto/MetaDto.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/api/dto/NoteDto.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/api/dto/UserDto.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/api/dto/NotificationDto.kt`
- Create: `shared/src/commonTest/kotlin/cc/hhhl/client/api/HhhlApiClientTest.kt`

- [ ] Step 1: Add failing tests for endpoint URLs and JSON parsing.

Tests should cover `/api/meta`, timeline request body, note creation body, and a representative note JSON response.

- [ ] Step 2: Implement Ktor client wrapper.

Base URL is fixed to `https://dc.hhhl.cc`. Configure JSON with unknown keys ignored.

- [ ] Step 3: Implement DTO mapping to domain models.

Keep mapping functions outside UI files.

- [ ] Step 4: Run shared tests.

Run: `./gradlew :shared:allTests`.

Expected: API URL, serialization, and mapping tests pass.

## Task 6: MiAuth Login

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/auth/MiAuthService.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/auth/AuthTokenStore.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/LoginScreen.kt`
- Create: `androidApp/src/main/kotlin/cc/hhhl/client/android/AndroidBrowserLauncher.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Create: iOS URL callback implementation on macOS when available.

- [ ] Step 1: Add tests for MiAuth URL generation.

Verify session ID, app name, permission list, and callback URI are encoded correctly.

- [ ] Step 2: Implement `MiAuthService`.

Permissions for MVP: read account, read/write notes, read notifications, read/write reactions, read/write following, drive file upload.

- [ ] Step 3: Implement platform browser launcher on Android.

Open MiAuth URL in Custom Tabs or system browser.

- [ ] Step 4: Add Android deep-link callback.

Handle callback URI and pass session ID to shared auth logic.

- [ ] Step 5: Add login screen.

Show HHHL name/icon, instance URL, login button, and inline error state.

- [ ] Step 6: Android emulator login smoke test.

Expected: tapping login opens browser, successful auth returns to app, token is stored securely.

## Task 7: Live Timeline Repository

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/repository/TimelineRepository.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/state/TimelineState.kt`
- Modify: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/TimelineScreen.kt`
- Create: `shared/src/commonTest/kotlin/cc/hhhl/client/repository/TimelineRepositoryTest.kt`

- [ ] Step 1: Test pagination and deduplication.

Use fake API responses with overlapping note IDs and verify final list has stable order without duplicates.

- [ ] Step 2: Implement repository.

Support Home, Local, and Global timeline loading with refresh and load-more operations.

- [ ] Step 3: Wire timeline screen to repository state.

Show cached/current content while loading more. Show retry row on failure.

- [ ] Step 4: Android emulator live smoke test.

Expected: authenticated account can load Home, Local, and Global timelines.

## Task 8: Compose And Media Upload

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/repository/ComposeRepository.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/platform/MediaPicker.kt`
- Modify: `shared/src/commonMain/kotlin/cc/hhhl/client/ui/screen/ComposeScreen.kt`
- Create: Android media picker implementation.
- Create: `shared/src/commonTest/kotlin/cc/hhhl/client/repository/ComposeRepositoryTest.kt`

- [ ] Step 1: Test note creation request construction.

Verify text, visibility, CW, reply ID, renote ID, and uploaded file IDs are sent correctly.

- [ ] Step 2: Implement media picker interface.

Shared UI calls an expected platform interface; Android implementation returns local URI metadata.

- [ ] Step 3: Implement drive file upload API.

Upload selected media and map uploaded IDs into note creation.

- [ ] Step 4: Wire compose screen.

Support text post, CW toggle, visibility selection, image add/remove, upload progress, and send error.

- [ ] Step 5: Android emulator compose smoke test.

Expected: send a text note and one image note to `dc.hhhl.cc`, then see it in timeline after refresh.

## Task 9: Interactions, Notifications, Profile

**Files:**
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/repository/NoteRepository.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/repository/NotificationRepository.kt`
- Create: `shared/src/commonMain/kotlin/cc/hhhl/client/repository/UserRepository.kt`
- Modify: `NoteRow.kt`, `NotificationsScreen.kt`, `ProfileScreen.kt`, `NoteDetailScreen.kt`
- Create repository tests for each module.

- [ ] Step 1: Add tests for reaction, renote, favorite, follow, notification mapping.

Use fake API clients and assert correct endpoint/body calls.

- [ ] Step 2: Implement note actions.

Support reply navigation, reaction picker minimal default reaction, renote, favorite, delete own note.

- [ ] Step 3: Implement notification list.

Load notifications, map types to rows, and navigate to related note/user.

- [ ] Step 4: Implement user profile.

Load profile, user notes, follow/unfollow state.

- [ ] Step 5: Android emulator smoke test.

Expected: interact with a note, view notifications, open a user profile, and follow/unfollow.

## Task 10: iOS Bring-Up

**Files:**
- Create/Modify: `iosApp` project files on macOS.
- Create: iOS Keychain token store.
- Create: iOS browser/deep-link callback.
- Create: iOS photo picker implementation.

- [ ] Step 1: Open project on macOS with Xcode installed.

Expected: shared framework builds for iOS simulator.

- [ ] Step 2: Run iOS simulator smoke test with fake data.

Expected: all core screens render with correct safe areas and no card-heavy UI.

- [ ] Step 3: Test MiAuth callback on iOS.

Expected: browser auth returns to app and token persists in Keychain.

- [ ] Step 4: Test live timeline and compose on iOS.

Expected: timeline loads and text note posts successfully.

## Task 11: Final Verification

**Files:**
- Modify as needed based on test failures.
- Create: `README.md`

- [ ] Step 1: Run shared tests.

Run: `./gradlew :shared:allTests`.

Expected: all shared tests pass.

- [ ] Step 2: Run Android debug build.

Run: `./gradlew :androidApp:assembleDebug`.

Expected: APK builds successfully.

- [ ] Step 3: Run Android emulator checklist.

Check login, timelines, note detail, compose text, compose image, notifications, profile, follow/unfollow, logout.

- [ ] Step 4: Run iOS simulator checklist on macOS.

Check login, timelines, note detail, compose text, notifications, profile, logout.

- [ ] Step 5: Document setup and run commands.

`README.md` must explain Android emulator usage on Windows and iOS simulator usage on macOS.

## Self-Review

- Spec coverage: covered KMP/Compose, no-card UI, lightweight daily-use scope, MiAuth, timelines, compose, interactions, notifications, profile, Android emulator checks, and iOS simulator checks.
- Scope boundary: push notifications, multi-instance accounts, admin/moderation tools, Drive manager, and advanced Sharkey features remain outside MVP.
- Known environment constraint: iOS simulator cannot run on this Windows workspace; iOS verification requires macOS with Xcode.
