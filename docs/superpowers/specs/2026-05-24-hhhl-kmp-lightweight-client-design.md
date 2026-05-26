# HHHL KMP Lightweight Client Design

Date: 2026-05-24
Target instance: https://dc.hhhl.cc/
Platform: Android and iOS
Framework: Kotlin Multiplatform + Compose Multiplatform

## Goal

Build a daily-usable lightweight native client for the HHHL Sharkey instance. The first version prioritizes reading timelines, posting, basic interactions, notifications, and user profiles. It intentionally avoids trying to reproduce the full Sharkey web client.

## Product Scope

The app is a dedicated client for `dc.hhhl.cc`, not a general multi-instance Fediverse client in the MVP. This keeps login, branding, API behavior, and QA focused.

MVP features:
- MiAuth login against `https://dc.hhhl.cc`.
- Home, local, and global timelines.
- Pull-to-refresh and infinite pagination.
- Note detail with replies.
- Compose note with text, visibility, CW, reply, quote/renote, and image upload.
- Basic interactions: reply, reaction, renote, favorite, delete own note.
- Notifications list for replies, mentions, reactions, follows, and renotes.
- User profile with avatar, display name, handle, bio, user timeline, follow/unfollow.
- Account logout and token clearing.
- Light and dark themes following system preference.

Deferred features:
- Push notifications.
- Multi-instance account management.
- Advanced Drive file manager.
- Admin and moderation tools.
- Antennas, clips, channels, pages, achievements, advanced settings.
- Rich custom theme editor.

## UX Direction

The UI should feel like a quiet native mobile app, not a website wrapper. It should be sparse, fast, and list-driven.

Visual rules:
- No card-heavy feed.
- No nested cards.
- No decorative gradients, hero areas, large rounded panels, or marketing-style layouts.
- Use system background colors, simple typography, thin dividers, and compact spacing.
- Use avatar, text hierarchy, and action icons to carry the interface.
- Keep information density high enough for repeated daily use, but avoid cramped text.
- Use platform-safe areas and predictable bottom navigation.

Primary navigation:
- Timeline
- Search/Discover
- Notifications
- Profile

Posting entry:
- Use a clear icon button in the top bar or a restrained bottom action slot.
- Do not use an oversized decorative floating action button.

## Main Screens

### Login

The login screen contains the HHHL icon/name, a short instance label, and one primary login button. The flow launches MiAuth in the system browser and returns to the app through a deep link. Login errors are shown as plain inline messages.

### Timeline

The timeline screen has a compact top bar and tabs for Home, Local, and Global. Each note is a full-width list item separated by a one-pixel divider.

Note list item layout:

```text
Avatar  Display name  @handle · time
        note text
        media grid
        reply   renote   reaction   more
------------------------------------------------
```

The list item itself has no card background. Media can use small rounded corners only where needed to make thumbnails readable.

### Note Detail

The detail screen shows one expanded note, metadata, interaction row, and replies. It uses the same page background and divider system as the timeline.

### Compose

The compose screen is a focused editor:
- Top bar: cancel, title, send.
- Text field fills the available space.
- Bottom controls: visibility, CW toggle, media picker, character count.
- Uploaded images appear as a compact grid below the editor.

### Notifications

Notifications are list rows with icon, actor avatar, message text, and timestamp. Tapping opens the note or user profile.

### Profile

Profile uses a simple header, not a card. Banner support can exist if the instance/user provides one, but the page must remain readable without it. The user note list appears below the header with the same note list component as timelines.

## Architecture

Use Kotlin Multiplatform for shared business logic and Compose Multiplatform for shared UI. Keep platform-specific code limited to secure storage, deep links, image picking, file access, and phase-two push notifications.

Proposed modules:

```text
shared/
  api/              Sharkey/Misskey API client and DTOs
  auth/             MiAuth session creation, callback parsing, token storage interface
  model/            Domain models used by UI
  repository/       Timeline, note, notification, user, and compose repositories
  cache/            SQLDelight database and paging cache
  ui/               Compose screens, components, navigation, theme
  platform/         expected interfaces for secure storage, browser, image picker, clock

androidApp/
  Android entry point, deep link handling, secure storage implementation, file picker

iosApp/
  iOS entry point, URL callback handling, keychain implementation, photo picker
```

## API Strategy

The app talks directly to the Sharkey/Misskey API exposed by `dc.hhhl.cc`. Authentication uses MiAuth because the instance reports `miauth: true`.

Core API areas:
- Meta: instance info and capability discovery.
- Auth: MiAuth session check and token retrieval.
- Notes: timeline, show, create, delete, reactions, favorites, renotes.
- Users: show, notes, follow, unfollow.
- Notifications: list and mark as read.
- Drive/files: upload media for notes.

All API calls return typed results. UI code should not parse raw JSON directly.

## State And Data Flow

UI screens call view models/state holders. State holders call repositories. Repositories combine remote API, local cache, and error mapping.

State pattern:
- Loading: show small progress indicator in the affected area.
- Content: show cached or freshly loaded list.
- Empty: show one short message.
- Error: show retry row or inline message.

Pagination:
- Use cursor-based pagination with `untilId`/`sinceId` style parameters where supported.
- Deduplicate notes by ID before rendering.
- Keep separate paging state per timeline tab.

Caching:
- Cache timeline pages and user profile basics locally.
- Cache auth token in platform secure storage, not plain preferences.
- Cache is an enhancement; the MVP should still work if the local cache is cleared.

## Error Handling

Network failures show retry controls without clearing existing content. Auth failures route to login after clearing invalid token. API validation errors in compose are shown near the send button or editor, not as blocking full-screen dialogs. File upload failures mark the failed attachment and allow retry/removal.

## Testing And Simulator Workflow

Development should be test-driven where practical.

Required verification during implementation:
- Shared unit tests for API request construction and response parsing.
- Repository tests for pagination, deduplication, and error mapping.
- UI screenshot or golden checks for key screens where tooling allows it.
- Android emulator smoke test after each major vertical slice.
- iOS simulator smoke test after shared UI/navigation stabilizes.

Simulator acceptance checks:
- Login flow opens browser and returns to app through deep link.
- Timeline loads on Android emulator and iOS simulator.
- Pull-to-refresh and pagination work.
- Compose can post text and image.
- Notification list opens related notes/users.
- Layout remains non-card, readable, and safe-area correct on small and large phone sizes.

## First Implementation Slice

The first working slice should be:
- Project scaffold.
- Shared design system: theme, top bar, bottom nav, note row, dividers.
- Fake in-memory timeline data.
- Android and iOS simulator launch.

Only after the UI shell is verified in simulators should API login and live timeline be connected.

## Non-Goals

The MVP is not a web wrapper. It should not embed the Sharkey web app in WebView for primary screens. It should not attempt full admin, moderation, or advanced Sharkey feature parity. It should not add broad customization before the core reading/posting flow is stable.

