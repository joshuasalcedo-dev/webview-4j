// The chat's ports: the boundary between the chat UI and whatever service carries the messages.
//
//   UI components ↔ ChatProvider (state, optimistic updates, events) ↔ ports ↔ adapter ↔ the service
//
// The UI only talks to these interfaces; an adapter implements them for one service (a websocket, a REST API,
// memory for previews). Nothing here names a vendor: extras ride along in `meta` on people, conversations and
// messages. Every command is async and throws an Error whose message a person can read ("You're offline.
// Try again when you're back online.") when it fails; the UI shows that message.
import type {
  ChatAttachment,
  ChatMessage,
  ChatPerson,
  ChatTag,
  Conversation,
} from "./types"

/** A page of something, and where the next one starts (no cursor: no more). */
type Page<T> = { items: T[]; nextCursor?: string }

/** What the UI sends. */
type OutgoingMessage = {
  conversationId: string
  text: string
  attachments?: ChatAttachment[]
  /** The id of the message this answers. */
  replyTo?: string
  /** The id the UI gave it while sending (so the sent message replaces the "sending" one). */
  clientId: string
}

/** Commands: what the UI asks the outside world to do. */
type ChatCommandPort = {
  /** Resolves with the message as stored (its real id, time, state). */
  sendMessage(message: OutgoingMessage): Promise<ChatMessage>
  editMessage(
    conversationId: string,
    messageId: string,
    text: string
  ): Promise<ChatMessage>
  /** Takes one's own message back for everyone. */
  unsendMessage(conversationId: string, messageId: string): Promise<void>
  deleteMessage(conversationId: string, messageId: string): Promise<void>
  react(
    conversationId: string,
    messageId: string,
    reactionId: string
  ): Promise<void>
  unreact(
    conversationId: string,
    messageId: string,
    reactionId: string
  ): Promise<void>
  forward(messageId: string, toConversationIds: string[]): Promise<void>
  pin(conversationId: string, messageId: string, pinned: boolean): Promise<void>
  /** Everything up to (and with) this message has been seen. */
  markRead(conversationId: string, upToId: string): Promise<void>
  /** I started or stopped typing there. */
  setTyping(conversationId: string, typing: boolean): Promise<void>
  /** Pays for a paywalled message; resolves with it unlocked. */
  unlock(conversationId: string, messageId: string): Promise<ChatMessage>
  setTags(conversationId: string, tags: ChatTag[]): Promise<void>
  /** Muted until then (null: unmuted; undefined: until turned back on). */
  mute(conversationId: string, until: Date | null | undefined): Promise<void>
  block(personId: string, blocked: boolean): Promise<void>
  report(
    target: { conversationId: string; messageId?: string },
    reason: string
  ): Promise<void>
  /** Uploads a file before it is sent; onProgress gets 0 to 1. */
  uploadAttachment(
    file: File,
    onProgress?: (part: number) => void
  ): Promise<ChatAttachment>
}

/** Queries: what the UI reads (kept apart from commands: they can be cached and retried freely). */
type ChatQueryPort = {
  /** Messages before the cursor, newest last (no cursor: the newest page). */
  loadMessages(
    conversationId: string,
    cursor?: string
  ): Promise<Page<ChatMessage>>
  /** Conversations, newest activity first, for a category ("all", "unread", the app's own) if given. */
  loadConversations(
    cursor?: string,
    category?: string
  ): Promise<Page<Conversation>>
  search(
    query: string
  ): Promise<{ conversations: Conversation[]; messages: ChatMessage[] }>
}

/** What happened outside, as the service tells it (inbound). */
type ChatInboundEvent =
  | { type: "message.received"; conversationId: string; message: ChatMessage }
  | { type: "message.updated"; conversationId: string; message: ChatMessage }
  | { type: "message.deleted"; conversationId: string; messageId: string }
  /** Someone read up to this message. */
  | {
      type: "message.read"
      conversationId: string
      personId: string
      upToId: string
    }
  /** Someone is typing (or stopped); it ends by itself after a few seconds without another. */
  | {
      type: "typing"
      conversationId: string
      person: ChatPerson
      typing: boolean
    }
  | { type: "presence"; personId: string; online: boolean; lastSeen?: Date }
  | {
      type: "conversation.updated"
      conversation: Partial<Conversation> & { id: string }
    }
  | {
      type: "reaction"
      conversationId: string
      messageId: string
      reactionId: string
      personId: string
      added: boolean
    }

/** Events: how the outside world tells the UI what happened. Returns a function that stops listening. */
type ChatEventPort = {
  subscribe(handler: (event: ChatInboundEvent) => void): () => void
}

/** What the UI does, as events (outbound), for the app's analytics or the adapter's own use. */
type ChatOutboundEvent =
  | { type: "me.typing"; conversationId: string; typing: boolean }
  | { type: "conversation.opened"; conversationId: string }
  | { type: "conversation.closed"; conversationId: string }
  /** Messages were on screen in a focused, visible window. */
  | { type: "messages.seen"; conversationId: string; upToId: string }
  | { type: "window.focus" }
  | { type: "window.blur" }
  | { type: "scrolled.to.bottom"; conversationId: string }
  | { type: "draft.changed"; conversationId: string; text: string }

/** An adapter: the three ports for one service. */
type ChatAdapter = {
  commands: ChatCommandPort
  queries: ChatQueryPort
  events: ChatEventPort
  /** Who is signed in (when the service knows it). */
  me?: ChatPerson
}

export type {
  ChatAdapter,
  ChatCommandPort,
  ChatEventPort,
  ChatInboundEvent,
  ChatOutboundEvent,
  ChatQueryPort,
  OutgoingMessage,
  Page,
}
