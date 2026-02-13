# Android Chat Redesign — Feature Parity with Web Dashboard

**Author:** JARVIS  
**Date:** 2026-02-13  
**Status:** Proposed  
**PR:** TBD

---

## 1. Executive Summary

The Web Dashboard chat (Control UI) has a significantly richer feature set than the Android node chat. This document details every functional gap and proposes concrete implementation steps to bring the Android chat to feature parity.

The web chat is built with Lit (TypeScript) and communicates via WebSocket (`GatewayBrowserClient`). The Android chat uses Jetpack Compose (Kotlin) and communicates via `GatewaySession`. Both use the same gateway protocol (`chat.send`, `chat.history`, `chat.abort`, `sessions.list`), so the backend is already ready — the gaps are purely on the Android UI/controller side.

---

## 2. Architecture Comparison

| Aspect | Web Dashboard | Android |
|--------|---------------|---------|
| **UI Framework** | Lit + HTML templates | Jetpack Compose |
| **State management** | Mutable props on LitElement | StateFlow + ViewModel |
| **Gateway client** | `GatewayBrowserClient` (WebSocket) | `GatewaySession` (WebSocket) |
| **Protocol** | Same (`chat.send`, `chat.history`, etc.) | Same |
| **Message normalization** | `message-normalizer.ts` | `ChatController.parseHistory()` |
| **Markdown rendering** | `marked` + DOMPurify → HTML | Custom `parseInlineMarkdown()` |
| **Tool display** | `tool-display.json` config | Same config (assets) |

---

## 3. Feature Gap Analysis

### 3.1 ✅ Features Already Implemented (Android)

- Basic chat send/receive
- Session switching (dropdown selector)
- Thinking level selector (off/low/medium/high)
- Streaming assistant text display
- Pending tool calls display (with tool display registry)
- Typing indicator (dot pulse)
- Image attachments (send)
- Image rendering in messages (base64)
- Markdown rendering (bold, italic, inline code, code blocks)
- System event categorization + compact styling
- Error display
- Connection status pill
- Message filtering (NO_REPLY, HEARTBEAT_OK, system noise)
- Auto-scroll to latest message
- Abort (stop) button
- Refresh button
- Session list fetching

### 3.2 🔴 Missing Features (Critical — Affects Core UX)

#### 3.2.1 Message Grouping (Slack-style)
**Web:** Consecutive messages from the same role are grouped into a single visual unit with one avatar and one timestamp at the bottom. This dramatically improves readability for multi-message exchanges.

**Android:** Every message is an independent bubble with no grouping. Consecutive assistant messages appear as separate disconnected bubbles.

**Implementation:**
- Create a `groupMessages()` function that merges consecutive same-role messages into `MessageGroup` objects (same logic as `ui/src/ui/views/chat.ts` `groupMessages()`)
- The `ChatMessageListCard` should render groups instead of individual messages
- Each group gets: one avatar (bottom-aligned), N message bubbles stacked, one footer with sender name + timestamp

**Files to modify:**
- `ChatMessageListCard.kt` — accept grouped messages
- `ChatMessageViews.kt` — add `ChatMessageGroupBubble` composable
- New file: `MessageGrouping.kt` — grouping logic

#### 3.2.2 Avatars
**Web:** Each message group has a circular avatar (40×40px, rounded square). Assistant gets configurable avatar (emoji, image URL, or initial letter). User gets "U" initial.

**Android:** No avatars at all.

**Implementation:**
- Add avatar composable to message groups (left for assistant, right for user)
- Support emoji avatars (just render the emoji)
- Support image URL avatars (Coil/Glide)
- Support initial-letter fallback
- Fetch avatar via `GET /avatar/<agentId>?meta=1` endpoint (same as web)

**Files to modify:**
- `ChatMessageViews.kt` — add `ChatAvatar` composable
- `ChatController.kt` — add avatar URL fetching logic

#### 3.2.3 Streaming Delta Text (Real-time token streaming)
**Web:** Uses `chat` event with `state: "delta"` to extract streaming text and display it live, character by character. The stream text accumulates and replaces the current bubble content in real-time.

**Android:** Receives streaming text via `agent` event `stream: "assistant"` but **only shows the final accumulated text** — it doesn't show streaming deltas from `chat` events. The web handles both `chat.delta` events (full message deltas) and direct streaming.

**Implementation:**
- Handle `chat` event `state: "delta"` in `ChatController.handleChatEvent()` to extract and display streaming text
- Currently Android only processes `final`, `aborted`, `error` states from chat events
- Add delta handling that calls `extractText()` on the message payload and updates `_streamingAssistantText`

**Files to modify:**
- `ChatController.kt` — add `"delta"` case to `handleChatEvent()`

#### 3.2.4 Message Queue (Queue while busy)
**Web:** When the assistant is busy (running), new messages get queued. A visual queue panel shows queued messages with remove buttons. When the current run finishes, queued messages are sent sequentially.

**Android:** Disables the send button when `pendingRunCount > 0`. No queuing mechanism. User must wait.

**Implementation:**
- Add `chatQueue: MutableStateFlow<List<QueuedMessage>>` to `ChatController`
- If busy, `sendMessage()` adds to queue instead of sending
- On `final`/`aborted` events, flush the queue (send next item)
- UI: show queue count badge on send button, or a small list above the composer
- Allow removing queued messages

**Files to modify:**
- `ChatController.kt` — add queue logic
- `ChatModels.kt` — add `QueuedMessage` data class
- `ChatComposer.kt` — show queue UI, enable send-to-queue

#### 3.2.5 Copy Message as Markdown
**Web:** Each assistant message has a copy button (appears on hover) that copies the raw markdown to clipboard.

**Android:** No copy functionality. User can only use system text selection.

**Implementation:**
- Add long-press context menu on assistant bubbles with "Copy as markdown" option
- Use `ClipboardManager` to copy raw markdown text
- Show a brief toast/snackbar "Copied"

**Files to modify:**
- `ChatMessageViews.kt` — add long-press handler with copy action

#### 3.2.6 Compaction Dividers
**Web:** When chat history is compacted, a visual divider line with "Compaction" label appears. Uses `__openclaw.kind === "compaction"` marker on messages.

**Android:** Compaction markers are silently filtered out. No visual indication of compaction boundaries.

**Implementation:**
- In `parseHistory()`, detect messages with `__openclaw.kind == "compaction"` and emit a `ChatDivider` item instead of filtering them
- Render dividers as horizontal lines with centered "Compaction" label (similar to date dividers)

**Files to modify:**
- `ChatModels.kt` — add `ChatDivider` sealed class variant
- `ChatController.kt` — detect compaction markers
- `ChatMessageListCard.kt` — render dividers

#### 3.2.7 Date Dividers
**Web:** No explicit date dividers (but compaction dividers exist).

**Android:** No date dividers either. For parity and improved UX on mobile (where scrolling through long histories is common), adding date dividers between messages from different days would be a good enhancement.

**Implementation:**
- Insert dividers between messages from different dates
- Format: "Today", "Yesterday", or "Feb 12, 2026"

### 3.3 🟡 Missing Features (Important — Improves Quality)

#### 3.3.1 Full Markdown Rendering
**Web:** Uses `marked` library with DOMPurify for full GitHub-Flavored Markdown: headings, blockquotes, nested lists, ordered lists, links, tables, horizontal rules, strikethrough, task lists.

**Android:** Only supports: bold (`**`), italic (`*`), inline code (`` ` ``), and code blocks (` ``` `). Missing: headings, links, blockquotes, lists, tables, HR, strikethrough.

**Implementation:**
- Replace custom `parseInlineMarkdown()` with a proper Markdown library (e.g., `Markwon` or `compose-markdown`)
- `Markwon` is the most mature Android Markdown renderer and has Compose support
- Alternatively, use `dev.jeziellago:compose-markdown` which is Compose-native

**Files to modify:**
- `ChatMarkdown.kt` — replace with library-based rendering
- `build.gradle.kts` — add markdown dependency

#### 3.3.2 Tool Cards (Result display)
**Web:** Tool calls and results are rendered as styled cards with:
- Icon + label header
- Command detail line
- Collapsed preview (for long output)
- Clickable to open in sidebar (shows formatted output)
- "Completed" status for tools with no output
- Short output shown inline

**Android:** Only shows pending tool calls (name + args while running). Once tools complete, their results appear as regular messages in the chat history — no special card styling.

**Implementation:**
- Parse tool call/result content blocks in messages
- Render `ToolCallCard` and `ToolResultCard` composables
- Show icon + label + detail line (reuse existing `ToolDisplayRegistry`)
- For results: show truncated preview or "Completed"
- Long-press/tap to expand full output in a bottom sheet

**Files to modify:**
- `ChatMessageViews.kt` — add `ChatToolCard` composable
- `ChatController.kt` — preserve tool messages in history parsing
- `ChatModels.kt` — add tool card types to `ChatMessageContent`

#### 3.3.3 Thinking/Reasoning Display
**Web:** When reasoning is enabled, assistant messages show a collapsible "Reasoning:" section with italic thinking text, rendered in a dashed-border box above the main response.

**Android:** Thinking content is silently stripped. No reasoning display.

**Implementation:**
- Extract `thinking` content blocks from assistant messages
- Render in a collapsible section with subtle styling (dashed border, muted text)
- Respect `showThinking` toggle (could be a setting or auto-enabled when thinkingLevel != "off")

**Files to modify:**
- `ChatController.kt` — preserve thinking blocks
- `ChatMessageViews.kt` — add thinking display section
- `ChatModels.kt` — add thinking field to `ChatMessageContent`

#### 3.3.4 New Session Button (via `/new` command)
**Web:** Has a dedicated "New session" button that sends `/new` or `/reset` to create a fresh session. The button changes to "Stop" when a run is active.

**Android:** No "New session" button. User must manually switch sessions or type `/new`. The abort button is separate.

**Implementation:**
- Add "New session" button to composer area
- When no run is active: shows "New session" (sends `/new`)
- When a run is active: handled by existing abort button
- After `/new`, refresh sessions list

**Files to modify:**
- `ChatComposer.kt` — add new session button

#### 3.3.5 Keyboard Enter-to-Send
**Web:** Enter sends message, Shift+Enter adds line break. This is the standard chat input paradigm.

**Android:** Uses `OutlinedTextField` with separate send button. No keyboard shortcut for sending. The IME action isn't configured.

**Implementation:**
- Set `imeAction = ImeAction.Send` on the text field
- Handle `onKeyboardActionSend` to trigger send
- Keep Shift+Enter for newlines (though this is harder on Android soft keyboards)

**Files to modify:**
- `ChatComposer.kt` — configure IME action

#### 3.3.6 Scroll Control (Smart auto-scroll + "New messages" indicator)
**Web:** Sophisticated scroll management:
- Auto-scrolls to bottom on new messages only if user is "near bottom" (within 450px threshold)
- If user has scrolled up, shows a "New messages ↓" floating pill
- Clicking the pill scrolls to bottom
- Tracks `chatUserNearBottom` and `chatNewMessagesBelow` state

**Android:** Always `animateScrollToItem(last)` — force-scrolls to bottom on every update. This is disruptive when reading history.

**Implementation:**
- Track whether user is near the bottom of the list
- Only auto-scroll if `isNearBottom` is true
- Show a floating "New messages ↓" button when new content arrives while scrolled up
- Use `LazyListState.layoutInfo` to compute scroll position

**Files to modify:**
- `ChatMessageListCard.kt` — add smart scroll logic and floating button

#### 3.3.7 Paste Images from Clipboard
**Web:** Supports pasting images directly from clipboard into the chat composer. Detects `image/*` types from `ClipboardEvent.clipboardData.items`.

**Android:** Only supports picking images via file picker (`ActivityResultContracts.GetMultipleContents`). No paste support.

**Implementation:**
- This is complex on Android — clipboard image paste isn't universally supported
- Could use `ClipboardManager` listener to detect image content
- Lower priority since the file picker works well on mobile

### 3.4 🔵 Missing Features (Nice-to-have)

#### 3.4.1 Markdown Sidebar (Split View)
**Web:** Has a resizable split-pane sidebar that opens when clicking tool results. Shows formatted markdown/JSON with syntax highlighting. Can toggle between rendered and raw view.

**Android:** No sidebar concept.

**Implementation:**
- Use a bottom sheet or sliding panel to show tool output details
- Not a direct port (split pane doesn't make sense on mobile)
- Bottom sheet with full markdown rendering is the mobile equivalent

#### 3.4.2 Focus Mode
**Web:** A toggle that hides the navigation sidebar and shows only the chat, maximizing screen real estate.

**Android:** The chat is already a bottom sheet — there's no navigation to hide. Not applicable.

#### 3.4.3 Compaction Status Toast
**Web:** Shows "Compacting context..." while active and "Context compacted ✓" briefly after completion.

**Android:** No compaction status display.

**Implementation:**
- Listen for compaction events from gateway
- Show a Snackbar or small banner
- Low priority

#### 3.4.4 Image Preview in Messages (Clickable zoom)
**Web:** Chat images can be clicked to open in a new tab at full resolution.

**Android:** Images are rendered inline but not clickable/zoomable.

**Implementation:**
- Add `clickable` modifier to images
- Open a full-screen image viewer dialog on tap

#### 3.4.5 Message Timestamps per Group
**Web:** Shows formatted time (e.g., "2:30 PM") in the group footer alongside the sender name.

**Android:** No timestamps on messages.

**Implementation:**
- Add timestamp to message groups (in the grouping implementation)
- Format: locale-appropriate time string

---

## 4. Implementation Priority

### Phase 1: Core UX (High Impact, Moderate Effort)
1. **Message Grouping** — Biggest visual improvement
2. **Avatars** — Essential for grouped layout
3. **Streaming Deltas** — Fix real-time feel
4. **Timestamps** — Essential context
5. **Smart Scroll** — Prevents UX frustration

### Phase 2: Feature Completion (High Impact, Higher Effort)
6. **Full Markdown** — Replace custom parser with library
7. **Message Queue** — Enable sending while busy
8. **Tool Cards** — Rich tool result display
9. **Copy as Markdown** — Essential utility
10. **New Session Button** — Quick session reset

### Phase 3: Polish (Medium Impact)
11. **Compaction Dividers** — Visual history boundaries
12. **Date Dividers** — Mobile-specific improvement
13. **Thinking/Reasoning Display** — Transparency feature
14. **Image Zoom** — Polish
15. **Keyboard Enter-to-Send** — Power user feature

---

## 5. Technical Notes

### Gateway Protocol (already supported — no backend changes needed)

All the features described use existing gateway protocol methods:
- `chat.send` — send messages (with `idempotencyKey` for queue dedup)
- `chat.history` — load history (includes tool messages, compaction markers)
- `chat.abort` — abort runs
- `sessions.list` — list sessions
- `health` — health check
- Events: `chat` (delta/final/aborted/error), `agent` (assistant/tool streams), `tick`, `health`
- `GET /avatar/<agentId>?meta=1` — avatar metadata

### Recommended Libraries
- **Markdown:** `dev.jeziellago:compose-markdown` (Compose-native) or `io.noties.markwon` (mature, Compose adapter available)
- **Image loading:** Already using base64 decode; for URL avatars, add Coil (`io.coil-kt:coil-compose`)

### Data Model Changes
```kotlin
// New sealed class for chat list items
sealed class ChatListItem {
    data class MessageGroup(
        val role: String,
        val messages: List<ChatMessage>,
        val timestamp: Long?,
        val avatarUrl: String? = null,
    ) : ChatListItem()
    
    data class Divider(
        val label: String,
        val timestamp: Long?,
    ) : ChatListItem()
    
    data class StreamingGroup(
        val text: String,
        val startedAt: Long,
    ) : ChatListItem()
    
    data class TypingIndicator(
        val toolCalls: List<ChatPendingToolCall> = emptyList(),
    ) : ChatListItem()
}

// Queue item
data class QueuedChatMessage(
    val id: String,
    val text: String,
    val attachments: List<OutgoingAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
```

---

## 6. File Inventory

### Files to Create
| File | Purpose |
|------|---------|
| `ui/chat/MessageGrouping.kt` | Grouping logic (consecutive same-role messages) |
| `ui/chat/ChatAvatar.kt` | Avatar composable (emoji, image, initial) |
| `ui/chat/ChatToolCard.kt` | Tool call/result card composables |
| `ui/chat/ChatDivider.kt` | Compaction + date divider composables |

### Files to Modify
| File | Changes |
|------|---------|
| `chat/ChatController.kt` | Delta handling, queue, avatar fetching, compaction markers |
| `chat/ChatModels.kt` | `ChatListItem` sealed class, `QueuedChatMessage`, thinking fields |
| `ui/chat/ChatMessageListCard.kt` | Grouped rendering, smart scroll, new messages indicator |
| `ui/chat/ChatMessageViews.kt` | Grouped bubbles, copy-on-long-press, thinking section |
| `ui/chat/ChatComposer.kt` | Queue UI, new session button, IME action |
| `ui/chat/ChatSheetContent.kt` | Wire new state flows |
| `ui/chat/ChatMarkdown.kt` | Replace with library or extend significantly |
| `build.gradle.kts` | Add markdown/image loading dependencies |

---

## 7. Visual Mockup (ASCII)

### Current Android Layout
```
┌────────────────────────────┐
│  [User bubble]        ←──  │
│                            │
│  [Assistant bubble]  ──→   │
│                            │
│  [User bubble]        ←──  │
│                            │
│  [Assistant bubble]  ──→   │
├────────────────────────────┤
│ [Session▼] [Think▼] [⟳][📎]│
│ ┌────────────────────────┐ │
│ │ Message OpenClaw…      │ │
│ └────────────────────────┘ │
│ [●Connected]         [▲]  │
└────────────────────────────┘
```

### Target Android Layout (After Redesign)
```
┌─────────────────────────────────┐
│                                 │
│    ┌────────────────────┐       │
│    │ User message text  │  [U]  │
│    │ Second user msg    │       │
│    │        You · 2:30 PM│      │
│    └────────────────────┘       │
│                                 │
│ [🤖] ┌────────────────────┐     │
│      │ Assistant response │     │
│      │ with **markdown**  │     │
│      ├────────────────────┤     │
│      │ 📂 Read · ~/file   │     │
│      │ ✅ Completed       │     │
│      ├────────────────────┤     │
│      │ More assistant text│     │
│      │   JARVIS · 2:31 PM │     │
│      └────────────────────┘     │
│                                 │
│  ── · Compaction · ──           │
│                                 │
│    ┌────────────────────┐       │
│    │ New user question  │  [U]  │
│    │        You · 2:45 PM│      │
│    └────────────────────┘       │
│                                 │
│ [🤖] ┌─ ─ ─ ─ ─ ─ ─ ─ ─┐     │
│      │ Streaming text…   │     │
│      └─ ─ ─ ─ ─ ─ ─ ─ ─┘     │
│                                 │
│        [ New messages ↓ ]       │
├─────────────────────────────────┤
│ [Session▼] [Think▼] [⟳][📎]    │
│ │Queued (1): "Follow up q…" ×│ │
│ ┌───────────────────────────┐   │
│ │ Message OpenClaw…         │   │
│ └───────────────────────────┘   │
│ [●Connected]    [New] [Queue▲]  │
└─────────────────────────────────┘
```

---

## 8. References

### Web Dashboard Chat Files
- `ui/src/ui/views/chat.ts` — Main chat view (grouping, rendering)
- `ui/src/ui/app-chat.ts` — Chat host logic (send, queue, refresh)
- `ui/src/ui/chat/grouped-render.ts` — Message group rendering
- `ui/src/ui/chat/message-normalizer.ts` — Role normalization
- `ui/src/ui/chat/message-extract.ts` — Text/thinking extraction
- `ui/src/ui/chat/tool-cards.ts` — Tool card rendering
- `ui/src/ui/chat/tool-helpers.ts` — Tool output formatting
- `ui/src/ui/chat/copy-as-markdown.ts` — Copy button
- `ui/src/ui/controllers/chat.ts` — Chat controller (send, abort, events)
- `ui/src/ui/gateway.ts` — Gateway WebSocket client
- `ui/src/ui/app-scroll.ts` — Smart scroll management
- `ui/src/ui/tool-display.ts` — Tool display resolution
- `ui/src/styles/chat/` — All CSS

### Android Chat Files
- `apps/android/.../chat/ChatController.kt` — Chat controller
- `apps/android/.../chat/ChatModels.kt` — Data models
- `apps/android/.../ui/chat/ChatSheetContent.kt` — Main chat sheet
- `apps/android/.../ui/chat/ChatMessageListCard.kt` — Message list
- `apps/android/.../ui/chat/ChatMessageViews.kt` — Message bubbles
- `apps/android/.../ui/chat/ChatComposer.kt` — Input composer
- `apps/android/.../ui/chat/ChatMarkdown.kt` — Markdown rendering
- `apps/android/.../ui/chat/ChatSessionsDialog.kt` — Session picker
- `apps/android/.../ui/chat/SessionFilters.kt` — Session filtering
- `apps/android/.../tools/ToolDisplay.kt` — Tool display registry
