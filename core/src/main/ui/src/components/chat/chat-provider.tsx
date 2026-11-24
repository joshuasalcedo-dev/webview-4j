import * as React from "react"

import { useDialogs } from "../dialog"
import { useSound } from "../sound"
import {
  type ChatActions,
  type ChatApi,
  type ChatCapabilities,
  type ChatConfig,
  type ChatState,
  resolveChat,
} from "./config"
import {
  type ChatEventBus,
  type ChatEventName,
  type ChatEvents,
  createChatEventBus,
} from "./events"
import type { ChatAdapter, ChatInboundEvent, ChatOutboundEvent } from "./ports"
import type {
  ChatAttachment,
  ChatMessage,
  ChatPerson,
  ChatTag,
  Conversation,
} from "./types"

// --- the store: what the provider knows ----------------------------------------------------------------

type MessagesOf = {
  items: ChatMessage[]
  cursor?: string
  hasOlder: boolean
  loaded: boolean
}

type ChatStore = {
  conversations: Conversation[]
  conversationsCursor?: string
  conversationsLoaded: boolean
  messages: Record<string, MessagesOf>
  /** The conversation shown now. */
  openId?: string
  /** Who is typing where, and until when (ms). */
  typing: Record<string, { person: ChatPerson; until: number }[]>
  presence: Record<string, { online: boolean; lastSeen?: Date }>
  /** Where I am typing now. */
  meTyping: Record<string, boolean>
  /** The window is focused and visible. */
  active: boolean
}

const EMPTY_STORE: ChatStore = {
  conversations: [],
  conversationsLoaded: false,
  messages: {},
  typing: {},
  presence: {},
  meTyping: {},
  active: true,
}

/** What the provider does for the components and the app (through the adapter's ports, optimistically). */
type ChatOperations = {
  open: (conversationId: string) => void
  close: () => void
  send: (
    conversationId: string,
    message: { text: string; files: File[]; replyTo?: string }
  ) => Promise<void>
  retry: (conversationId: string, message: ChatMessage) => Promise<void>
  edit: (
    conversationId: string,
    messageId: string,
    text: string
  ) => Promise<void>
  unsend: (conversationId: string, messageId: string) => Promise<void>
  remove: (conversationId: string, messageId: string) => Promise<void>
  /** Adds my reaction, or takes it away when it was there. */
  react: (
    conversationId: string,
    messageId: string,
    reactionId: string
  ) => Promise<void>
  pin: (
    conversationId: string,
    messageId: string,
    pinned: boolean
  ) => Promise<void>
  unlock: (conversationId: string, message: ChatMessage) => Promise<void>
  setTags: (conversationId: string, tags: ChatTag[]) => Promise<void>
  mute: (conversationId: string, muted: boolean, until?: Date) => Promise<void>
  markRead: (conversationId: string, upToId: string) => Promise<void>
  loadOlder: (conversationId: string) => Promise<void>
  loadConversations: (more?: boolean) => Promise<void>
  /** I typed (the composer calls it); a stop follows a pause, a send or leaving. */
  typed: (conversationId: string) => void
  /** Changes a conversation in the store (pin, archive, mark unread). */
  updateConversation: (id: string, change: Partial<Conversation>) => void
  /** Takes a conversation out of the list (after "Delete chat"). */
  removeConversation: (id: string) => void
  /** Empties a conversation's messages on this device ("Clear chat history"). */
  clearMessages: (id: string) => void
  block: (personId: string, blocked: boolean) => Promise<void>
  report: (
    target: { conversationId: string; messageId?: string },
    reason: string
  ) => Promise<void>
}

type ChatProviderValue = ChatApi & {
  /** The data the provider holds (from the adapter, plus what is on its way). */
  store: ChatStore
  /** The events (inbound from the adapter, outbound from the UI). */
  events: ChatEventBus
  /** What it can do (through the adapter). */
  ops: ChatOperations
  /** Whether there is an adapter (otherwise the components work from their props). */
  connected: boolean
}

const noOp = async () => undefined
const NO_OPS: ChatOperations = {
  open: () => undefined,
  close: () => undefined,
  send: noOp,
  retry: noOp,
  edit: noOp,
  unsend: noOp,
  remove: noOp,
  react: noOp,
  pin: noOp,
  unlock: noOp,
  setTags: noOp,
  mute: noOp,
  markRead: noOp,
  loadOlder: noOp,
  loadConversations: noOp,
  typed: () => undefined,
  updateConversation: () => undefined,
  removeConversation: () => undefined,
  clearMessages: () => undefined,
  block: noOp,
  report: noOp,
}

const ChatProviderContext = React.createContext<ChatProviderValue>({
  ...resolveChat({}),
  store: EMPTY_STORE,
  events: createChatEventBus(),
  ops: NO_OPS,
  connected: false,
})

const TYPING_LASTS = 6000
const ME_TYPING_PAUSE = 4000

const updateMessage = (
  list: ChatMessage[],
  id: string,
  change: (m: ChatMessage) => ChatMessage
) => list.map((m) => (m.id === id || m.clientId === id ? change(m) : m))

const mineReaction = (m: ChatMessage, reactionId: string) =>
  !!m.reactions?.find((r) => r.emoji === reactionId)?.mine

const toggleReaction = (
  m: ChatMessage,
  reactionId: string,
  on: boolean,
  mine: boolean
): ChatMessage => {
  const has = m.reactions?.find((r) => r.emoji === reactionId)
  if (!has && !on) return m
  const reactions = has
    ? (m.reactions ?? [])
        .map((r) =>
          r.emoji === reactionId
            ? { ...r, count: r.count + (on ? 1 : -1), mine: mine ? on : r.mine }
            : r
        )
        .filter((r) => r.count > 0)
    : [...(m.reactions ?? []), { emoji: reactionId, count: 1, mine }]
  return { ...m, reactions }
}

type ChatProviderProps = {
  /** The ports to the service (commands, queries, events). Without one, the components work from props. */
  adapter?: ChatAdapter
  /** The UI options: tools, labels, menus, render slots, features, categories, pill types, rules. */
  config?: Partial<ChatConfig>
  /** Shortcuts for a part used alone (they override the config's). */
  capabilities?: ChatCapabilities
  state?: ChatState
  onMuteChange?: (muted: boolean, until?: Date) => void
  /** Every outbound event (typing, opening, seeing, focus) in one place. */
  onEvent?: (event: ChatOutboundEvent) => void
  /** The conversation open at first. */
  defaultOpenId?: string
  children: React.ReactNode
}

/**
 * The chat's heart: holds the conversations and messages from the adapter, talks to it through the ports
 * (showing changes at once and taking them back with "Not sent" or a message when they fail), runs one
 * event bus (inbound from the adapter, outbound from the UI), and gives the components the configuration.
 * The UI never fetches anything itself.
 */
function ChatProvider({
  adapter,
  config,
  capabilities,
  state,
  onMuteChange,
  onEvent,
  defaultOpenId,
  children,
}: ChatProviderProps) {
  const dialogs = useDialogs()
  const sound = useSound()
  const [store, setStore] = React.useState<ChatStore>(() => ({
    ...EMPTY_STORE,
    openId: defaultOpenId,
  }))
  const bus = React.useMemo(() => createChatEventBus(), [])
  const storeRef = React.useRef(store)
  React.useEffect(() => {
    storeRef.current = store
  })
  const me: ChatPerson = React.useMemo(
    () => config?.me ?? adapter?.me ?? { id: "me", name: "You" },
    [config?.me, adapter?.me]
  )
  const tipSound = React.useRef(true)
  React.useEffect(() => {
    tipSound.current = config?.features?.tipSound !== false
  })

  /** A command that failed: the person sees why (the adapter's message). */
  const failed = React.useCallback(
    (title: string, problem: unknown) =>
      void dialogs.error({
        title,
        message:
          problem instanceof Error && problem.message
            ? problem.message
            : "Something went wrong. Try again.",
      }),
    [dialogs]
  )

  // Outbound events go to the app.
  React.useEffect(() => {
    if (!onEvent) return
    const outbound = new Set<string>([
      "me.typing",
      "conversation.opened",
      "conversation.closed",
      "messages.seen",
      "window.focus",
      "window.blur",
      "scrolled.to.bottom",
      "draft.changed",
    ])
    return bus.onAny((event) => {
      if (outbound.has(event.type)) onEvent(event as ChatOutboundEvent)
    })
  }, [bus, onEvent])

  // The window's focus and visibility: unread counts and "seen" depend on them.
  React.useEffect(() => {
    const update = () => {
      const active =
        document.visibilityState === "visible" && document.hasFocus()
      if (active === storeRef.current.active) return
      setStore((s) => ({ ...s, active }))
      bus.emit(active ? "window.focus" : "window.blur", {})
    }
    window.addEventListener("focus", update)
    window.addEventListener("blur", update)
    document.addEventListener("visibilitychange", update)
    return () => {
      window.removeEventListener("focus", update)
      window.removeEventListener("blur", update)
      document.removeEventListener("visibilitychange", update)
    }
  }, [bus])

  // Inbound events from the adapter: into the store and onto the bus.
  React.useEffect(() => {
    if (!adapter) return
    return adapter.events.subscribe((event: ChatInboundEvent) => {
      setStore((s) => applyInbound(s, event, me.id))
      bus.emit(event.type, event as Omit<ChatEvents[typeof event.type], "type">)
      // A tip arriving: the "done" sound (unless the config turns it off).
      if (
        event.type === "message.received" &&
        event.message.senderId !== me.id &&
        event.message.attachments?.some((a) => a.kind === "tip") &&
        tipSound.current
      )
        sound.play("done")
      if (
        event.type === "message.received" &&
        event.message.senderId !== me.id
      ) {
        const s = storeRef.current
        const visible = s.openId === event.conversationId && s.active
        if (!visible)
          bus.emit("unread.new", {
            conversationId: event.conversationId,
            message: event.message,
          })
      }
    })
  }, [adapter, bus, me.id, sound])

  // Typing that nobody refreshed ends by itself.
  React.useEffect(() => {
    const anyTyping = Object.values(store.typing).some(
      (list) => list.length > 0
    )
    if (!anyTyping) return
    const timer = window.setInterval(() => {
      const now = Date.now()
      setStore((s) => {
        let changed = false
        const typing: ChatStore["typing"] = {}
        for (const [id, list] of Object.entries(s.typing)) {
          const kept = list.filter((t) => t.until > now)
          if (kept.length !== list.length) changed = true
          typing[id] = kept
        }
        return changed ? { ...s, typing } : s
      })
    }, 1000)
    return () => window.clearInterval(timer)
  }, [store.typing])

  // My typing: one "typing" per few seconds while it goes on, and a stop after a pause.
  const meTypingTimers = React.useRef(new Map<string, number>())
  const stopTyping = React.useCallback(
    (conversationId: string) => {
      const timer = meTypingTimers.current.get(conversationId)
      if (timer) window.clearTimeout(timer)
      meTypingTimers.current.delete(conversationId)
      if (!storeRef.current.meTyping[conversationId]) return
      setStore((s) => ({
        ...s,
        meTyping: { ...s.meTyping, [conversationId]: false },
      }))
      bus.emit("me.typing", { conversationId, typing: false })
      void adapter?.commands
        .setTyping(conversationId, false)
        .catch(() => undefined)
    },
    [adapter, bus]
  )

  const ops = React.useMemo<ChatOperations>(() => {
    if (!adapter) return NO_OPS
    const { commands, queries } = adapter
    const setMessages = (
      conversationId: string,
      change: (list: ChatMessage[]) => ChatMessage[]
    ) =>
      setStore((s) => {
        const now = s.messages[conversationId] ?? {
          items: [],
          hasOlder: true,
          loaded: false,
        }
        return {
          ...s,
          messages: {
            ...s.messages,
            [conversationId]: { ...now, items: change(now.items) },
          },
        }
      })
    const updateConversation = (id: string, change: Partial<Conversation>) =>
      setStore((s) => ({
        ...s,
        conversations: s.conversations.map((c) =>
          c.id === id ? { ...c, ...change } : c
        ),
      }))

    const loadMessages = async (conversationId: string, older: boolean) => {
      const now = storeRef.current.messages[conversationId]
      const page = await queries.loadMessages(
        conversationId,
        older ? now?.cursor : undefined
      )
      setStore((s) => {
        const was = s.messages[conversationId]
        const items = older && was ? [...page.items, ...was.items] : page.items
        return {
          ...s,
          messages: {
            ...s.messages,
            [conversationId]: {
              items,
              cursor: page.nextCursor,
              hasOlder: !!page.nextCursor,
              loaded: true,
            },
          },
        }
      })
    }

    const deliver = async (
      conversationId: string,
      draft: ChatMessage,
      files: File[]
    ) => {
      try {
        const attachments: ChatAttachment[] = []
        for (const file of files)
          attachments.push(await commands.uploadAttachment(file))
        const stored = await commands.sendMessage({
          conversationId,
          text: draft.text ?? "",
          attachments: [
            ...(draft.attachments?.filter(
              (a) => a.kind !== "image" || !a.url.startsWith("blob:")
            ) ?? []),
            ...attachments,
          ],
          replyTo: draft.replyTo,
          clientId: draft.clientId ?? draft.id,
        })
        setMessages(conversationId, (list) =>
          updateMessage(list, draft.clientId ?? draft.id, () => ({
            ...stored,
            clientId: draft.clientId,
          }))
        )
      } catch (problem) {
        setMessages(conversationId, (list) =>
          updateMessage(list, draft.clientId ?? draft.id, (m) => ({
            ...m,
            state: "failed",
            meta: {
              ...m.meta,
              error:
                problem instanceof Error ? problem.message : String(problem),
            },
          }))
        )
        sound.play("error")
      }
    }

    return {
      open(conversationId) {
        const was = storeRef.current.openId
        if (was === conversationId) return
        if (was) {
          stopTyping(was)
          bus.emit("conversation.closed", { conversationId: was })
        }
        setStore((s) => ({ ...s, openId: conversationId }))
        bus.emit("conversation.opened", { conversationId })
        if (!storeRef.current.messages[conversationId]?.loaded)
          void loadMessages(conversationId, false).catch((p) =>
            failed("Couldn't load the messages", p)
          )
      },
      close() {
        const was = storeRef.current.openId
        if (!was) return
        stopTyping(was)
        bus.emit("conversation.closed", { conversationId: was })
        setStore((s) => ({ ...s, openId: undefined }))
      },
      async send(conversationId, { text, files, replyTo }) {
        stopTyping(conversationId)
        const clientId = `local-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
        const draft: ChatMessage = {
          id: clientId,
          clientId,
          senderId: me.id,
          text: text || undefined,
          replyTo,
          sentAt: new Date(),
          state: "sending",
          // Pictures show at once from the device while they upload.
          attachments: files.map((f) =>
            f.type.startsWith("image/")
              ? {
                  kind: "image" as const,
                  url: URL.createObjectURL(f),
                  name: f.name,
                }
              : { kind: "file" as const, name: f.name, size: f.size }
          ),
        }
        setMessages(conversationId, (list) => [...list, draft])
        updateConversation(conversationId, {
          draft: undefined,
          lastMessage: { text, fromMe: true, at: new Date() },
        })
        await deliver(conversationId, draft, files)
      },
      async retry(conversationId, message) {
        setMessages(conversationId, (list) =>
          updateMessage(list, message.clientId ?? message.id, (m) => ({
            ...m,
            state: "sending",
          }))
        )
        await deliver(conversationId, message, [])
      },
      async edit(conversationId, messageId, text) {
        const before = storeRef.current.messages[conversationId]?.items.find(
          (m) => m.id === messageId
        )
        setMessages(conversationId, (list) =>
          updateMessage(list, messageId, (m) => ({ ...m, text, edited: true }))
        )
        try {
          const stored = await commands.editMessage(
            conversationId,
            messageId,
            text
          )
          setMessages(conversationId, (list) =>
            updateMessage(list, messageId, () => stored)
          )
        } catch (problem) {
          if (before)
            setMessages(conversationId, (list) =>
              updateMessage(list, messageId, () => before)
            )
          failed("Couldn't save the change", problem)
        }
      },
      async unsend(conversationId, messageId) {
        const before = storeRef.current.messages[conversationId]?.items
        setMessages(conversationId, (list) =>
          list.filter((m) => m.id !== messageId)
        )
        try {
          await commands.unsendMessage(conversationId, messageId)
        } catch (problem) {
          if (before) setMessages(conversationId, () => before)
          failed("Couldn't unsend the message", problem)
        }
      },
      async remove(conversationId, messageId) {
        const before = storeRef.current.messages[conversationId]?.items
        setMessages(conversationId, (list) =>
          list.filter((m) => m.id !== messageId)
        )
        try {
          await commands.deleteMessage(conversationId, messageId)
        } catch (problem) {
          if (before) setMessages(conversationId, () => before)
          failed("Couldn't delete the message", problem)
        }
      },
      async react(conversationId, messageId, reactionId) {
        const message = storeRef.current.messages[conversationId]?.items.find(
          (m) => m.id === messageId
        )
        if (!message) return
        const on = !mineReaction(message, reactionId)
        setMessages(conversationId, (list) =>
          updateMessage(list, messageId, (m) =>
            toggleReaction(m, reactionId, on, true)
          )
        )
        try {
          await (on ? commands.react : commands.unreact)(
            conversationId,
            messageId,
            reactionId
          )
        } catch (problem) {
          setMessages(conversationId, (list) =>
            updateMessage(list, messageId, (m) =>
              toggleReaction(m, reactionId, !on, true)
            )
          )
          failed("Couldn't react", problem)
        }
      },
      async pin(conversationId, messageId, pinned) {
        setMessages(conversationId, (list) =>
          updateMessage(list, messageId, (m) => ({ ...m, pinned }))
        )
        try {
          await commands.pin(conversationId, messageId, pinned)
        } catch (problem) {
          setMessages(conversationId, (list) =>
            updateMessage(list, messageId, (m) => ({ ...m, pinned: !pinned }))
          )
          failed(
            pinned ? "Couldn't pin the message" : "Couldn't unpin the message",
            problem
          )
        }
      },
      async unlock(conversationId, message) {
        // Errors go back to the paywall, which offers Try again.
        const stored = await commands.unlock(conversationId, message.id)
        setMessages(conversationId, (list) =>
          updateMessage(list, message.id, () => stored)
        )
      },
      async setTags(conversationId, tags) {
        const before = storeRef.current.conversations.find(
          (c) => c.id === conversationId
        )?.tags
        updateConversation(conversationId, { tags })
        try {
          await commands.setTags(conversationId, tags)
        } catch (problem) {
          updateConversation(conversationId, { tags: before })
          failed("Couldn't change the tags", problem)
        }
      },
      async mute(conversationId, muted, until) {
        updateConversation(conversationId, {
          muted,
          mutedUntil: muted ? until : undefined,
        })
        try {
          await commands.mute(conversationId, muted ? until : null)
        } catch (problem) {
          updateConversation(conversationId, { muted: !muted })
          failed("Couldn't change notifications", problem)
        }
      },
      async markRead(conversationId, upToId) {
        updateConversation(conversationId, { unread: 0, markedUnread: false })
        await commands.markRead(conversationId, upToId).catch(() => undefined)
      },
      loadOlder: (conversationId) => loadMessages(conversationId, true),
      async loadConversations(more = false) {
        try {
          const page = await queries.loadConversations(
            more ? storeRef.current.conversationsCursor : undefined
          )
          setStore((s) => ({
            ...s,
            conversations: more
              ? [...s.conversations, ...page.items]
              : page.items,
            conversationsCursor: page.nextCursor,
            conversationsLoaded: true,
          }))
        } catch (problem) {
          failed("Couldn't load the conversations", problem)
        }
      },
      typed(conversationId) {
        if (!storeRef.current.meTyping[conversationId]) {
          setStore((s) => ({
            ...s,
            meTyping: { ...s.meTyping, [conversationId]: true },
          }))
          bus.emit("me.typing", { conversationId, typing: true })
          void commands.setTyping(conversationId, true).catch(() => undefined)
        }
        const timer = meTypingTimers.current.get(conversationId)
        if (timer) window.clearTimeout(timer)
        meTypingTimers.current.set(
          conversationId,
          window.setTimeout(() => stopTyping(conversationId), ME_TYPING_PAUSE)
        )
      },
      updateConversation,
      removeConversation(id) {
        if (storeRef.current.openId === id)
          setStore((s) => ({ ...s, openId: undefined }))
        setStore((s) => ({
          ...s,
          conversations: s.conversations.filter((c) => c.id !== id),
        }))
      },
      clearMessages(id) {
        setMessages(id, () => [])
      },
      async block(personId, blocked) {
        try {
          await commands.block(personId, blocked)
        } catch (problem) {
          failed(
            blocked ? "Couldn't block them" : "Couldn't unblock them",
            problem
          )
        }
      },
      async report(target, reason) {
        try {
          await commands.report(target, reason)
        } catch (problem) {
          failed("Couldn't send the report", problem)
        }
      },
    }
  }, [adapter, bus, failed, me.id, sound, stopTyping])

  // The first load, and the open conversation's messages.
  React.useEffect(() => {
    if (!adapter) return
    void ops.loadConversations()
    const open = storeRef.current.openId
    if (open) {
      bus.emit("conversation.opened", { conversationId: open })
      void adapter.queries.loadMessages(open).then((page) =>
        setStore((s) => ({
          ...s,
          messages: {
            ...s.messages,
            [open]: {
              items: page.items,
              cursor: page.nextCursor,
              hasOlder: !!page.nextCursor,
              loaded: true,
            },
          },
        }))
      )
    }
  }, [adapter, ops, bus])

  // "Seen" from the message list: mark it read through the port.
  React.useEffect(
    () =>
      bus.on(
        "messages.seen",
        (e) => void ops.markRead(e.conversationId, e.upToId)
      ),
    [bus, ops]
  )

  const value = React.useMemo<ChatProviderValue>(() => {
    const open = store.openId
    const conversation = store.conversations.find((c) => c.id === open)
    // Actions from the store (for the open conversation), unless the app gives its own.
    const fromStore: ChatActions =
      adapter && open
        ? {
            onSend: (m) => ops.send(open, m),
            onEdit: (m, text) => ops.edit(open, m.id, text),
            onUnsend: (m) => ops.unsend(open, m.id),
            onDelete: (m) => void ops.remove(open, m.id),
            onReact: (m, id) => void ops.react(open, m.id, id),
            onPin: (m, pinned) => void ops.pin(open, m.id, pinned),
            onRetry: (m) => void ops.retry(open, m),
            onUnlock: (m) => ops.unlock(open, m),
            onMuteChange: (muted, until) => void ops.mute(open, muted, until),
            onLoadOlder: () => ops.loadOlder(open),
            onTyping: () => ops.typed(open),
            onTagsChange: (c, tags) => void ops.setTags(c.id, tags),
          }
        : {}
    const resolved = resolveChat({
      ...config,
      me,
      capabilities: capabilities ?? config?.capabilities,
      state:
        state ??
        config?.state ??
        (conversation
          ? {
              muted: conversation.muted,
              mutedUntil: conversation.mutedUntil,
              archived: conversation.archived,
            }
          : undefined),
      actions: {
        ...fromStore,
        ...config?.actions,
        ...(onMuteChange ? { onMuteChange } : {}),
      },
    })
    return { ...resolved, store, events: bus, ops, connected: !!adapter }
  }, [config, capabilities, state, onMuteChange, store, bus, ops, adapter, me])

  return (
    <ChatProviderContext.Provider value={value}>
      {children}
    </ChatProviderContext.Provider>
  )
}

/** Puts an inbound event into the store. */
function applyInbound(
  s: ChatStore,
  event: ChatInboundEvent,
  meId: string
): ChatStore {
  const messagesOf = (id: string) =>
    s.messages[id] ?? { items: [], hasOlder: true, loaded: false }
  const withMessages = (id: string, items: ChatMessage[]) => ({
    ...s.messages,
    [id]: { ...messagesOf(id), items },
  })
  switch (event.type) {
    case "message.received": {
      const list = messagesOf(event.conversationId).items
      // Already here (my own, sent from this window): replace the "sending" one.
      const known = list.some(
        (m) =>
          m.id === event.message.id ||
          (event.message.clientId && m.clientId === event.message.clientId)
      )
      const items = known
        ? updateMessage(
            list,
            event.message.clientId ?? event.message.id,
            () => event.message
          )
        : [...list, event.message]
      const visible = s.openId === event.conversationId && s.active
      const mine = event.message.senderId === meId
      return {
        ...s,
        messages: withMessages(event.conversationId, items),
        // The sender stops typing when their message arrives.
        typing: {
          ...s.typing,
          [event.conversationId]: (s.typing[event.conversationId] ?? []).filter(
            (t) => t.person.id !== event.message.senderId
          ),
        },
        conversations: s.conversations.map((c) =>
          c.id === event.conversationId
            ? {
                ...c,
                typing: undefined,
                unread: !mine && !visible ? (c.unread ?? 0) + 1 : c.unread,
                lastMessage: previewOf(event.message, mine),
              }
            : c
        ),
      }
    }
    case "message.updated":
      return {
        ...s,
        messages: withMessages(
          event.conversationId,
          updateMessage(
            messagesOf(event.conversationId).items,
            event.message.id,
            () => event.message
          )
        ),
      }
    case "message.deleted":
      return {
        ...s,
        messages: withMessages(
          event.conversationId,
          messagesOf(event.conversationId).items.filter(
            (m) => m.id !== event.messageId
          )
        ),
      }
    case "message.read": {
      // My messages up to that one are read.
      const items = messagesOf(event.conversationId).items
      const until = items.findIndex((m) => m.id === event.upToId)
      if (until < 0) return s
      return {
        ...s,
        messages: withMessages(
          event.conversationId,
          items.map((m, i) =>
            i <= until &&
            m.senderId === meId &&
            m.state &&
            m.state !== "failed" &&
            m.state !== "sending"
              ? { ...m, state: "read" }
              : m
          )
        ),
      }
    }
    case "typing": {
      const others = (s.typing[event.conversationId] ?? []).filter(
        (t) => t.person.id !== event.person.id
      )
      const list = event.typing
        ? [
            ...others,
            { person: event.person, until: Date.now() + TYPING_LASTS },
          ]
        : others
      return {
        ...s,
        typing: { ...s.typing, [event.conversationId]: list },
        conversations: s.conversations.map((c) =>
          c.id === event.conversationId
            ? {
                ...c,
                typing:
                  list.length > 0
                    ? list.length === 1
                      ? list[0].person.name.split(" ")[0]
                      : true
                    : undefined,
              }
            : c
        ),
      }
    }
    case "presence":
      return {
        ...s,
        presence: {
          ...s.presence,
          [event.personId]: { online: event.online, lastSeen: event.lastSeen },
        },
        conversations: s.conversations.map((c) =>
          c.people?.some((p) => p.id === event.personId) && !c.group
            ? { ...c, online: event.online }
            : c
        ),
      }
    case "conversation.updated": {
      const exists = s.conversations.some((c) => c.id === event.conversation.id)
      return {
        ...s,
        conversations: exists
          ? s.conversations.map((c) =>
              c.id === event.conversation.id
                ? { ...c, ...event.conversation }
                : c
            )
          : [
              { title: "", ...event.conversation } as Conversation,
              ...s.conversations,
            ],
      }
    }
    case "reaction": {
      const mine = event.personId === meId
      return {
        ...s,
        messages: withMessages(
          event.conversationId,
          updateMessage(
            messagesOf(event.conversationId).items,
            event.messageId,
            (m) => toggleReaction(m, event.reactionId, event.added, mine)
          )
        ),
      }
    }
  }
}

// --- hooks ----------------------------------------------------------------------------------------------

/** The chat's configuration, data, events and operations. */
function useChat(): ChatProviderValue {
  return React.useContext(ChatProviderContext)
}

/** Listens to one kind of chat event while the component is shown (the handler may change freely). */
function useChatEvent<K extends ChatEventName>(
  type: K,
  handler: (event: ChatEvents[K]) => void
) {
  const { events } = useChat()
  const latest = React.useRef(handler)
  React.useEffect(() => {
    latest.current = handler
  })
  React.useEffect(
    () => events.on(type, (event) => latest.current(event)),
    [events, type]
  )
}

/** Who is typing in a conversation now (it ends by itself a few seconds after the last sign). */
function useIsTyping(conversationId: string | undefined): ChatPerson[] {
  const { store } = useChat()
  if (!conversationId) return []
  return (store.typing[conversationId] ?? []).map((t) => t.person)
}

/** Whether I am typing in a conversation (what the composer reported). */
function useMeTyping(conversationId: string | undefined): boolean {
  const { store } = useChat()
  return !!conversationId && !!store.meTyping[conversationId]
}

/** Whether a conversation is the open one, in a focused and visible window. */
function useIsOpen(conversationId: string | undefined): boolean {
  const { store } = useChat()
  return !!conversationId && store.openId === conversationId && store.active
}

/** Unread messages in one conversation, or in all of them. */
function useUnread(conversationId?: string): number {
  const { store } = useChat()
  if (conversationId)
    return store.conversations.find((c) => c.id === conversationId)?.unread ?? 0
  return store.conversations.reduce(
    (sum, c) => sum + (c.muted ? 0 : (c.unread ?? 0)),
    0
  )
}

/** Calls the handler when a message arrives in a conversation that is not open and visible (for notifications). */
function useOnUnread(handler: (event: ChatEvents["unread.new"]) => void) {
  useChatEvent("unread.new", handler)
}

/** Whether someone is online, and when they were last seen. */
function usePresence(
  personId: string | undefined
): { online: boolean; lastSeen?: Date } | undefined {
  const { store } = useChat()
  return personId ? store.presence[personId] : undefined
}

export {
  ChatProvider,
  ChatProviderContext,
  useChat,
  useChatEvent,
  useIsOpen,
  useIsTyping,
  useMeTyping,
  useOnUnread,
  usePresence,
  useUnread,
}
export type { ChatOperations, ChatProviderProps, ChatProviderValue, ChatStore }

/** What a conversation row says about its last message. */
function previewOf(
  message: ChatMessage,
  fromMe: boolean
): NonNullable<Conversation["lastMessage"]> {
  const first = message.attachments?.[0]
  const tip = message.attachments?.find((a) => a.kind === "tip")
  return {
    text: message.text,
    fromMe,
    attachment: tip
      ? "tip"
      : first?.kind === "image"
        ? "photo"
        : first?.kind === "voice"
          ? "voice"
          : first
            ? "file"
            : undefined,
    tip: tip ? { amount: tip.amount, currency: tip.currency } : undefined,
    at: message.sentAt,
  }
}
