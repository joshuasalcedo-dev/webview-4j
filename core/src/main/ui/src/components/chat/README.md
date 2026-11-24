# Chat kit (`@webview4j/ui/chat`)

A general chat: conversation list, header, messages, composer, details panel, for any chat app. It never
fetches anything itself. Data and commands go through **ports**, implemented by an **adapter** for one
service; how it looks and behaves is one typed **config**.

```
UI components  ↔  ChatProvider  ↔  ports  ↔  adapter  ↔  the service
(ConversationList,  (state, optimistic   (ChatCommandPort,   (memory, a websocket,
 ChatHeader,         updates, events,     ChatQueryPort,      a REST API…)
 MessageList,        config)              ChatEventPort)
 ChatComposer,
 ChatDetails)
```

## The parts

| File | What it is |
|---|---|
| `ports.ts` | The boundary: `ChatCommandPort` (send, edit, unsend, delete, react, pin, markRead, setTyping, unlock, setTags, mute, block, report, uploadAttachment), `ChatQueryPort` (loadMessages, loadConversations, search; kept apart from commands since reads can be cached and retried), `ChatEventPort` (inbound events), `ChatAdapter` (the three together). |
| `types.ts` | The data: `ChatPerson`, `Conversation` (= `ChatConversation`), `ChatMessage`, `ChatAttachment` (image, file, voice, tip, and `custom` for the app's own kinds), `ChatTag`, `ChatPaywall`. Every type has `meta` for a service's own fields. |
| `config.tsx` | `ChatConfig` and its defaults: rules (`capabilities`, `state`, `getMessageCapabilities`), `reactions`, `composer`, `actions`, `menus`, `render` slots, `swipe`, `categories` (`chatCategories`), `pillTypes`, `labels` (every visible word), `features` (on/off switches). `defineChatConfig()` types a config. |
| `chat-provider.tsx` | `ChatProvider` (`adapter`, `config`, `onEvent`) and the hooks: `useChat()`, `useChatEvent`, `useIsTyping`, `useMeTyping`, `useIsOpen`, `useUnread`, `useOnUnread`, `usePresence`. |
| `events.ts` | One typed event bus per provider: inbound (`message.received`, `typing`…) and outbound (`me.typing`, `conversation.opened`, `messages.seen`, `window.focus`…). Apps add their own kinds through `ChatAppEvents`. |
| `memory-adapter.ts` | `createMemoryChatAdapter(seed, options)`: an in-memory service with latency, failure injection, `setOffline`, `inject(event)`, and a timer that makes things happen. Every story uses it. |
| `chat-window.tsx` | `ChatWindow`: list, header, messages, composer and details, wired to the provider. |
| Components | `ConversationList` / `ConversationRow`, `ChatHeader`, `MessageList` / `MessageBubble` / `TypingIndicator`, `ChatComposer` (`Composer` + reply/edit bars + rules), `ChatDetails` (on `Inspector`). |

Rules are left out = allowed, except `deleteAny` and `sendTip`, which must be given. A missing capability hides
its button or menu item. Send, when off, shows its reason. When nothing can be sent, the composer becomes a
notice with the reason.

## Minimal example

```tsx
import { ChatWindow } from "@webview4j/ui/chat"
import { createMemoryChatAdapter } from "@webview4j/ui/chat"

const adapter = createMemoryChatAdapter({ me, people, conversations, messages })

<NativeProvider>
  <ChatWindow adapter={adapter} config={{ me }} defaultOpenId={conversations[0].id} />
</NativeProvider>
```

## Full example

```tsx
const config = defineChatConfig({
  me,
  capabilities: { sendTip: true, maxUploadBytes: 25_000_000, acceptedFileTypes: "image/*,.pdf" },
  state: { muted: false },
  getMessageCapabilities: (m, me) => ({ unsendDuration: m.senderId === me.id ? 24 * 3_600_000 : 0 }),
  reactions: [{ id: "👍", emoji: "👍", label: "Like" }, { id: "star", icon: <Star />, label: "Star" }],
  composer: { toolOptions: { people, gifSearch, tip: { currency: "PHP", onSend } } },
  menus: { message: (defaults, message) => [...defaults, { label: "Translate", onSelect: () => translate(message) }] },
  render: { below: (m) => (m.senderId !== me.id ? <TipButtons message={m} /> : undefined), attachment: { order: (a) => <OrderCard {...a.data} /> } },
  categories: [chatCategories.all, chatCategories.unread, chatCategories.tagged("vip", "VIP")],
  pillTypes: [{ id: "vip", label: "VIP", colour: "purple", icon: <Crown /> }, { id: "new", label: "New", colour: "blue", exclusive: "status" }],
  allowCustomTags: true,
  labels: { placeholder: "Write to your fans" },
  features: { readReceipts: false },
})

<ChatProvider adapter={myAdapter} config={config} onEvent={track}>
  <ChatWindow details detailsSections={<SubscriptionSection />} />
</ChatProvider>
```

`ChatWindow` takes `adapter` + `config` directly too, and puts in its own `ChatProvider`.

## Write an adapter

An adapter is `{ commands, queries, events, me? }` (see `ports.ts`):

1. **Commands** are async. Resolve with what the service stored (`sendMessage` returns the stored message with
   its real id and `clientId`, so it replaces the "sending" bubble). On failure, throw an `Error` whose message a
   person can read. The provider shows it, takes back the optimistic change, and marks a failed send "Not sent".
2. **Queries** return pages: `{ items, nextCursor }`. No cursor means nothing more. Messages come oldest first.
3. **Events**: `subscribe(handler)` returns an unsubscribe function. Push every change from the service as a
   `ChatInboundEvent`: new, updated and deleted messages, read receipts, typing (it ends by itself after about
   6 s), presence, conversation changes, reactions.
4. Keep vendor fields out of the types: put them in `meta`.

`memory-adapter.ts` is the example to copy.

## Stories

Everything is under **Chat** in Storybook (`script.jar storybook open`): ChatWindow (working, details, narrow
sheet, read-only, muted, blocked, moderator, config changed live), MessageList (tips, paywall, per-message
rules, slots), ConversationList (categories and pill types), ChatComposer (default, minimal and app tools),
ChatHeader, MessageBubble, Events. Stories only configure: data and tools come from `sample.ts`,
`sample-messages.tsx` and `sample-config.tsx`; defaults come from the library.
