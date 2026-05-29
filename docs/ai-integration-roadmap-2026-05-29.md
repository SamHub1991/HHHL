# HHHL AI Integration Roadmap

This document records the complete AI layer planned and implemented for HHHL. The goal is not a separate chat bot page. AI is an operation layer that helps with writing, reading, triage, automation, and background work while keeping user-controlled actions explicit.

## Product Shape

- Keep the current visual language: X-like timeline actions, Telegram-like chat ergonomics, Apple-like grouped settings and switches.
- AI appears where the user already works: compose editor, post overflow menu, chat detail toolbar, notification inbox actions, settings AI status, and automation rule editor.
- Generated output lands as drafts, summaries, local logs, or suggestions by default. Sending, deleting, muting, blocking, reporting, and bulk actions remain confirmation-gated.
- Long AI work is queued locally. The Android app schedules WorkManager so queued jobs can continue after the app goes to background, and new queue work appends instead of cancelling an already-running AI worker.

## Model And Provider Configuration

- Enable or disable AI per account.
- Provider presets: OpenAI-compatible, OpenAI, DeepSeek, Qwen, SiliconFlow, Ollama, LM Studio, and Custom.
- Editable Base URL and API key.
- Separate model slots: chat model, fast model, long-context model, vision model, embeddings model.
- Connection test from settings uses the configured Base URL, key, and chat model.
- Local providers may omit API key. Cloud providers can use custom keys.
- Settings persist per account. API keys are stored in the platform store, and Android uses the app-private store for the AI settings payload.

## Permissions And Privacy

- Read permissions are separated for timeline/posts, notifications, chat, profile context, and drafts.
- Private chat upload can be disabled independently. When disabled, chat AI actions do not send private messages to the model.
- Sensitive content upload can be disabled independently. When disabled, CW posts send only the CW label plus a redaction marker, and sensitive media descriptions are omitted.
- AI background tasks can be disabled while keeping foreground actions available.
- Automation AI can be disabled while keeping manual AI actions available.
- Background work supports a Wi-Fi-only preference in the settings model so Android scheduling can be tightened later without changing shared data.

## Foreground Features

- Compose assistant:
  - Polish current draft.
  - Shorten current draft.
  - Expand current draft.
  - Translate to Chinese.
  - Generate a content warning.
  - Suggest hashtags.
  - Reply-aware drafting when composing against a target note.
- Post actions:
  - Summarize a post and its quoted context.
  - Generate a reply draft for a post.
  - Summarize a full thread and draft a context-aware thread reply from root post, conversation and replies.
- Profile actions:
  - Summarize a user profile, relationship state, bio and recent visible posts.
  - Suggest follow, chat, special-care, list, mute or avoid-interaction decisions without executing them.
- Timeline actions:
  - Summarize visible posts.
  - Suggest interaction opportunities.
  - Suggest muted words, muted instances, saved searches or later-reading targets without applying filters automatically.
- Global action plan:
  - Build a cross-context plan from the allowed current draft, visible timeline, notification inbox, selected chat and automation logs.
  - Suggest next replies, @ targets, later-reading items, filtering ideas and automation opportunities without claiming anything was executed.
  - Group output by priority and mark all write/tool actions as confirmation-gated.
- Chat actions:
  - Summarize the visible conversation.
  - Suggest a reply from current visible messages.
  - Extract action items, owners, deadlines and blockers.
  - Summarize decisions, open questions and next steps.
  - Fill generated replies into the composer rather than sending automatically.
- Notification actions:
  - Summarize the current inbox/filter.
  - Detect which items likely need a reply or follow-up.
  - Prioritize notifications into high, medium and low buckets.

## Automation Features

- AI semantic condition:
  - Example: `有人问我问题`, `反馈 bug`, `情绪不满`, `提到更新失败`, `需要我回复`.
  - Runs after normal trigger and cheap filters, so it does not waste model calls.
- AI generated action:
  - Generate local log text.
  - Generate a system notification body.
  - Generate a webhook/body template result for lower-risk handoff.
  - Explain a rule's matching logic, action risk and possible improvements from the automation center.
  - Suggest 3 to 6 new or improved automation rules from existing rules and recent execution logs.
- Safety tiers:
  - Read-only: summarize, classify, extract tasks, rank priority.
  - Low-risk write: save local log, fill draft, create local reminder.
  - Medium-risk write: reply, post, react, follow, add to list. Requires user confirmation or explicit future allowlist.
  - High-risk write: mute, block, report, delete, bulk operation, auto-send. Disabled by default.

## Background Execution

- AI tasks are stored in a local per-account queue.
- Foreground actions enqueue and process immediately.
- Android schedules a one-time AI worker when a task is queued.
- The worker processes pending AI tasks over network even if the app is no longer foregrounded.
- The worker respects Wi-Fi-only settings and daily request accounting while processing queue work in the background; it also self-checks the active Android network before each account so stale WorkManager constraints do not leak work onto metered networks.
- Existing background notification sync also triggers automation and can hand events to AI semantic rules.
- Completed task results are persisted so reopening the app can show the latest result.

## Tool Use Surface

- Read tools:
  - Current draft.
  - Visible post and quoted post.
  - Visible chat messages.
  - Current notification list.
  - Automation event fields.
- Write tools currently implemented:
  - Replace or append compose draft.
  - Fill chat composer.
  - Write automation log.
  - Generate local/system notification text through automation.
  - Generate confirmation-gated global action plans and automation rule suggestions.
- Future confirmation-gated tools:
  - Send post/reply.
  - Send chat message.
  - React/favorite/follow.
  - Create clip/list entries.
  - Mute/block/report/delete with explicit confirmation.

## Prompting Rules

- HHHL sends compact structured context, not whole app state.
- Prompts instruct the model to return only the requested output, without extra meta commentary.
- Automation semantic conditions request strict `YES` or `NO` first, reducing fragile parsing.
- Generated drafts preserve the user's language unless the chosen action says otherwise.
- The app truncates large context before sending to control latency and cost.

## Testing Gates

- AI settings and task queue codecs round trip and trim large payloads.
- Prompt builders include the right context and exclude disabled/private content.
- OpenAI-compatible API maps success, unauthorized, server errors, and empty responses.
- Automation AI condition/action serialization and execution pass fake AI bridge tests.
- Settings presentation exposes the AI configuration surface.
- Settings presentation exposes AI health: daily usage, remaining requests, queue counts, background status, privacy redaction and tools-permission warnings.
- Android debug build must pass before install.

## Second-Pass Strengthening Implemented

- Added profile-level AI so the assistant can reason about users, bios, relationship state and recent posts.
- Added chat action-item and decision extraction so room conversations can become follow-up lists instead of only summaries.
- Added timeline filter suggestions for muted words, noisy instances, saved-search ideas and later-reading candidates.
- Added automation rule explanation from the automation center, keeping writes confirmation-gated.
- Added automation rule suggestions from the automation center, using existing rules, execution logs, available triggers, conditions and actions.
- Added global AI action plan from settings, combining allowed timeline, notification, chat, draft and automation context into prioritized next steps.
- Added AI health/status details in settings, including daily usage, remaining quota, queue state, background processing and privacy/tool warnings.
- Added CW/sensitive-media redaction before AI upload when sensitive upload permission is disabled.
- Added daily AI request accounting in the shared AI store and Android background worker.
- Android AI queue scheduling now uses append-or-replace WorkManager behavior so queued work can continue while the app is backgrounded without cancelling current AI work.
- Android AI background worker now self-checks unmetered/Wi-Fi network state per account when Wi-Fi-only is enabled.
- Android AI settings/task snapshots are encrypted with AndroidKeyStore AES/GCM while still migrating old plaintext snapshots.
- External automation handoff actions, including AI-generated webhooks, are gated by the AI tools permission.

## Implementation Notes

- Shared code owns AI settings, queue, prompt building, repository, and state holder.
- Android owns persisted AI store, scheduler, and worker.
- UI receives `AiUiState` and callbacks; screens do not call the network directly.
- The implementation keeps the provider OpenAI-compatible so users can choose OpenAI, DeepSeek, Qwen, SiliconFlow, Ollama, LM Studio, or another compatible endpoint.
