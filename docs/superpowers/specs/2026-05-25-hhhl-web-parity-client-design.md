# HHHL Web-Parity Native Client Design

Date: 2026-05-25
Target instance: https://dc.hhhl.cc/
Observed server: Sharkey 2025.5.2-dev
Platform: Android and iOS
Framework: Kotlin Multiplatform + Compose Multiplatform

## Goal

Build HHHL as a high-performance native mobile client that tracks the Sharkey web client closely enough for daily use. The app should keep the X-like information density already started in the codebase, while matching Sharkey web behavior for navigation, posting, reactions, profile/social flows, Drive media, chat, search, settings, and theme customization where the instance enables those capabilities.

## Current Baseline

The repository already contains:
- MiAuth login and Android deep-link return.
- Secure Android token storage.
- Shared Compose UI shell with bottom navigation.
- Home/local/global timelines.
- Note detail and replies.
- Text compose with visibility, CW, reply, quote/renote, and file-id support.
- Discover for notes/users.
- Notifications.
- User profile, user notes, follow/unfollow, followers/following.
- Basic note actions: reaction, renote, favorite, delete.
- Theme presets: system, light, dark, dim.

The old MVP spec intentionally deferred full Sharkey parity. This spec supersedes that limit.

## Instance Capability Snapshot

`https://dc.hhhl.cc/api/meta` currently reports:
- Instance name: `hhhl`.
- Description: `期待AGI时代来临`.
- MiAuth enabled.
- Max note length: 3000.
- Max CW length: 500.
- Default like: `❤️`.
- Local and global timelines enabled.
- Bubble timeline disabled.
- Public notes enabled.
- Notes search policy is global, but `policies.canSearchNotes` is false for the observed anonymous meta response.
- Drive capacity: 100 MB.
- Max upload size: 25 MB in policy, with server-level max file size also exposed.
- Chat available.
- Trends and federation view available.
- Achievements enabled.

The app must treat this as runtime configuration, not hardcoded assumptions.

## Product Scope

Primary parity targets:
- Auth and account: MiAuth login, restore, logout, account identity, session invalidation.
- Timelines: home, local, global, capability-gated bubble/social variants when supported, refresh, pagination, cache.
- Notes: detail, replies, quote/renote, reactions with picker/default like, favorite, delete own note, translated/error states where available.
- Compose: text, CW, visibility, reply, quote, media upload, alt text, upload progress, validation from instance limits, scheduled notes if enabled.
- Discover/search: users, notes only when enabled, trends, federation/explore surfaces where available.
- Notifications: replies, mentions, reactions, follows, renotes, quotes, poll/achievement/system-style events, mark read.
- Profiles: header, avatar/banner, bio, stats, user notes, followers/following, follow/unfollow, pinned notes.
- Drive/media: pick images, upload, preview, remove, retry failed upload; richer Drive manager later.
- Chat: capability-gated chat list and conversation surface because HHHL reports chat available.
- Settings: theme, density, account/session, basic client preferences.
- Themes: X-style light/dark/dim, HHHL/Sharkey green, instance-aware accent, and a registry that can accept more presets without UI rewrites.

Deferred until the above is stable:
- Admin/moderation.
- Full custom theme editor equivalent to web.
- Push notifications.
- Multi-instance account switching.

## Architecture Direction

Keep the existing layered shape:
- `api`: typed Sharkey API clients and DTO mapping.
- `model`: UI-safe domain models.
- `repository`: token/capability-aware operations, pagination, deduplication, error mapping.
- `state`: screen state holders with `StateFlow`.
- `ui`: Compose screens and reusable components.
- `platform`: platform-owned browser, secure storage, media picker, file upload source, future push.

Add two cross-cutting modules:
- `instance`: runtime metadata and capabilities from `/api/meta`.
- `theme`: a palette registry with presets and instance-accent support.

State holders must not parse JSON or call Ktor directly. UI must not know endpoint details. Platform code must remain thin.

## Performance Requirements

- Lazy lists for all large feeds.
- Cursor pagination with `endReached` to avoid repeated empty loads.
- Deduplicate by stable IDs before rendering.
- Keep existing content visible during refresh/load-more errors.
- Use instance capabilities to avoid calling unsupported endpoints.
- Keep image/media loading behind small, replaceable abstractions so native image caching can be added without rewriting rows.
- Prefer small immutable UI state objects and focused state holders over a monolithic app view model.

## Theme Requirements

Theme support must be registry-driven:
- Presets have stable IDs, labels, Material color scheme, and HHHL extra tokens.
- The picker renders all registered presets.
- Stored theme IDs survive enum reordering or renaming.
- Instance accent from meta can feed an HHHL/Sharkey preset without affecting X-style presets.
- The theme layer should remain UI-only; repositories must not depend on it.

## UI Style Baseline

The current product direction is defined in `docs/ui-style-guide.md`: X-like feeds, Apple-like app chrome and controls, and Telegram-like chat. This supersedes older blanket restrictions against rounded panels or subtle navigation polish. The feed must remain list-first and non-card-heavy, while navigation, dialogs, form controls, and chat may use restrained rounded surfaces when they fit their screen family.

## Web Interaction Parity Rules

- If the web client hides or disables a feature because the instance policy disables it, the app should do the same instead of showing a broken button.
- Error messages should prefer server-provided Sharkey error messages, then friendly app-level fallback text.
- Note action flows must keep row state local and avoid full-feed reloads unless the action creates a new note.
- Navigation should preserve obvious web expectations: tapping actor opens profile, tapping note opens detail, notifications deep-link to note/user, profile social counts open followers/following lists.
- The app is native, not a WebView wrapper.

## Testing Requirements

Each feature slice needs:
- API tests for endpoint, request body, and representative response mapping.
- Repository tests for token handling, unsupported capabilities, pagination, deduplication, and error mapping.
- State holder tests for loading, empty, error, unauthorized, end-of-list, and one-shot events.
- Android build verification after each slice.
- Emulator smoke checks without screenshots unless a visual bug requires one.

## First Parity Slices

1. Instance meta and capability discovery.
2. Capability-gated Discover and timeline variants.
3. Theme registry and HHHL/Sharkey accent preset.
4. Compose validation from instance limits.
5. Media picker and Drive upload.
6. Reaction picker and richer notification mapping.
7. Trends/explore and federation entry points.
8. Chat shell and message loading.
9. Cache and offline-first feed restore.
