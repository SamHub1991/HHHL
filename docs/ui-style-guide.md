# HHHL UI Style Guide

Date: 2026-05-27
Scope: Kotlin Multiplatform + Compose Multiplatform mobile UI

## Style Position

HHHL should feel like a modern native social client, not a plain utility app and not a web wrapper.

The intended visual mix is:
- X for information feeds.
- Apple for navigation, system controls, motion restraint, polish, and theme quality.
- Telegram for chat, conversations, and lightweight messaging surfaces.

These references define roles, not a license to mix every pattern everywhere. Each screen should choose the reference that matches its job.

## Reference Ownership

### X-Like Feed

Use this for timeline, notifications, note detail replies, profile notes, search results, and other note-heavy lists.

Rules:
- Notes are full-width list rows.
- Do not wrap each note in a page-level card.
- Use thin dividers between rows.
- Keep avatar, author line, content, media, and actions visually compact.
- Inline quoted notes, polls, reactions, and media may use light rounded panels, but no heavy shadows.
- Action rows should remain quiet: icons first, counts second, low visual weight.

### Apple-Like Shell

Use this for app chrome, top bars, bottom navigation, dialogs, settings, forms, pickers, and global controls.

Rules:
- Rounded controls are allowed and expected.
- Subtle floating panels are allowed for top and bottom navigation.
- Shadows should be soft and low contrast. Avoid making navigation compete with content.
- Prefer thin borders, translucent surface colors, and system-like spacing.
- Use shared controls before page-local control styling.
- Forms should use clear labels, compact inputs, and restrained grouping.

### Telegram-Like Chat

Use this for chat room lists, message views, message search, members, and the chat composer.

Rules:
- Message bubbles may be more rounded and visually distinct than feed rows.
- Incoming and outgoing messages may use separate fills.
- Chat input can use a pill/panel treatment.
- Conversation lists should still use the shared list rhythm and dividers.
- Avoid bringing chat bubble styling into timeline, settings, or admin screens.

## Screen Families

### Timeline And Notes

Primary style: X-like feed.

Keep:
- `NoteRow` as a divider-separated row.
- Dense author/content hierarchy.
- Media as inline thumbnails.
- Reply rails as subtle structure.

Avoid:
- Per-note cards.
- Large background panels around feed sections.
- Floating action surfaces inside every row.

### Profile

Primary style: X-like profile with Apple-like shortcuts.

Keep:
- Header as page content, not a card.
- Notes reuse `NoteRow`.
- The most important shortcuts may be tiles.

Avoid:
- Turning the entire profile into a dashboard.
- Making every secondary tool a tile.

Guideline:
- Primary shortcuts can be tile-like.
- Secondary account/workspace actions should trend toward compact grouped rows.

### Compose

Primary style: Apple-like form/editor with X-like posting semantics.

Keep:
- Top bar with cancel/send.
- Section headings and inline controls.
- Draft status and character count.
- Attachments and target previews as inline panels.

Avoid:
- Card stacks around every section.
- Heavy form containers.

### Settings, Drive, Admin

Primary style: Apple/TG grouped lists.

Keep:
- Dense list rows for repeatable items.
- Section headings for groups.
- Inline status and retry rows.
- Panels only when a detail surface genuinely needs framing.

Avoid:
- Dashboard card grids for ordinary settings.
- Nested cards.
- Large decorative panels.

### Chat

Primary style: Telegram-like messaging.

Keep:
- Distinct message bubbles.
- Conversation rows with avatars and unread badges.
- Compact input surface.

Avoid:
- Applying message bubble treatment to non-chat lists.
- Overusing shadows on every chat control.

## Shared Component Policy

Prefer these components when available:
- `HhhlTopBar` for screen title, back, refresh, send, and primary actions.
- `HhhlBottomNav` for root navigation.
- `HhhlIconActionButton` for icon-only commands.
- `HhhlActionChip` for compact option chips.
- `HhhlTextButton` for text commands in dialogs and low-density controls.
- `HhhlTextInput` for text fields.
- `HhhlDivider` for feed/list separation.

New shared components should be added before repeating the same styling across screens:
- `HhhlStatusRow` for loading, empty, retry, and message rows.
- `HhhlSectionHeader` for grouped settings/forms.
- `HhhlInlinePanel` for quoted content, attachment metadata, and compact framed details.

## Visual Tokens

Recommended defaults:
- Feed row horizontal padding: 12-16 dp.
- List dividers: 1 dp using `LocalHhhlColors.divider`.
- Inline panel radius: 8-12 dp.
- Control radius: 10-14 dp.
- Chat bubble radius: 16-20 dp.
- Top/bottom navigation radius: may be larger, but should remain visually quiet.
- Shadows: use sparingly; navigation and chat bubbles may have low elevation, feed rows should not.
- Letter spacing: prefer `0.sp` unless a specific brand treatment needs otherwise.

## Theming

Theme presets are product features.

Supported theme families:
- X-inspired light/dark/dim and accent variants.
- Apple-inspired light/dark/mint/graphite variants.
- HHHL/Sharkey green variants.
- OLED black.

Rules:
- Presets may change accent and surface colors, not screen structure.
- Feed readability is more important than theme flourish.
- Light root backgrounds may have very subtle depth, but avoid decorative gradients that become visible as artwork.
- Dark themes should avoid noisy contrast between nested surfaces.

## Current Code Alignment

Mostly aligned:
- `NoteRow` uses feed rows and dividers.
- `ComposeSection` keeps form sections unframed.
- `HhhlTextInput`, `HhhlActionChip`, and `HhhlIconActionButton` provide a reusable Apple-like control language.
- Chat has Telegram-like bubbles, which is acceptable inside chat.

Needs careful tuning:
- `HhhlTopBar` and `HhhlBottomNav` are intentionally floating, but should not become visually louder than content.
- `ProfileShortcutTile` should stay limited to primary shortcuts.
- Drive, Settings, and Admin should converge on grouped rows rather than dashboard cards.
- Repeated local status rows should be consolidated into `HhhlStatusRow`.

## Non-Goals

- Do not copy any one app wholesale.
- Do not make every screen a card grid.
- Do not make the feed look like a dashboard.
- Do not remove all polish in the name of consistency.
- Do not let chat styling leak into the rest of the app.
