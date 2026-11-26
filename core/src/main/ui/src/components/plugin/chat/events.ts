// The chat's events: one typed bus per ChatProvider. Inbound events come from the adapter (ChatEventPort),
// outbound ones from the UI (typing, opening a chat, seeing messages). Apps add their own kinds by declaring
// them on ChatAppEvents:
//
//   declare module "@webview4j/ui/components/chat/events" {
//     interface ChatAppEvents { "tip.sent": { type: "tip.sent"; amount: number } }
//   }
import type { ChatInboundEvent, ChatOutboundEvent } from "./ports"
import type { ChatMessage } from "./types"

/** The app's own events, by name (declaration merging). Each value has the same `type` as its name. */
// eslint-disable-next-line @typescript-eslint/no-empty-object-type
interface ChatAppEvents {}

/** Raised by the provider when a message arrives in a chat that is not open and visible (for notifications). */
type ChatUnreadEvent = {
  type: "unread.new"
  conversationId: string
  message: ChatMessage
}

/** Asks the chat's composer to put text in (where the caret is) and take the focus. */
type ChatComposerInsertEvent = { type: "composer.insert"; text: string }

type ChatEvent =
  | ChatInboundEvent
  | ChatOutboundEvent
  | ChatUnreadEvent
  | ChatComposerInsertEvent
  | ChatAppEvents[keyof ChatAppEvents]

/** Every event by its name. */
type ChatEvents = {
  [
    E in
      | ChatInboundEvent
      | ChatOutboundEvent
      | ChatUnreadEvent
      | ChatComposerInsertEvent as E["type"]
  ]: E
} & ChatAppEvents

type ChatEventName = keyof ChatEvents

type Handler<E> = (event: E) => void

type ChatEventBus = {
  /** Sends an event to everyone listening (its `type` is the name). */
  emit<K extends ChatEventName>(
    type: K,
    payload: Omit<ChatEvents[K], "type">
  ): void
  /** Listens to one kind of event; returns a function that stops listening. */
  on<K extends ChatEventName>(
    type: K,
    handler: Handler<ChatEvents[K]>
  ): () => void
  /** Listens to every event. */
  onAny(handler: Handler<ChatEvent>): () => void
}

/** A new, empty bus. A handler that throws is reported in the console and does not stop the others. */
function createChatEventBus(): ChatEventBus {
  const byName = new Map<string, Set<Handler<ChatEvent>>>()
  const any = new Set<Handler<ChatEvent>>()
  const call = (handler: Handler<ChatEvent>, event: ChatEvent) => {
    try {
      handler(event)
    } catch (problem) {
      console.error(
        `A chat event handler for "${String(event.type)}" failed:`,
        problem
      )
    }
  }
  return {
    emit(type, payload) {
      const event = { ...payload, type } as unknown as ChatEvent
      byName.get(type as string)?.forEach((h) => call(h, event))
      any.forEach((h) => call(h, event))
    },
    on(type, handler) {
      const set = byName.get(type as string) ?? new Set()
      const wrapped = handler as unknown as Handler<ChatEvent>
      set.add(wrapped)
      byName.set(type as string, set)
      return () => set.delete(wrapped)
    },
    onAny(handler) {
      any.add(handler)
      return () => any.delete(handler)
    },
  }
}

export { createChatEventBus }
export type {
  ChatAppEvents,
  ChatEvent,
  ChatEventBus,
  ChatEventName,
  ChatEvents,
  ChatUnreadEvent,
}
