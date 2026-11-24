// An adapter that keeps everything in memory: for previews, stories and tests. It behaves like a service:
// answers after a delay, can fail on purpose, and makes things happen by itself (someone types, then a message
// arrives; read receipts; people come online and go away). It is also the example to follow when writing an
// adapter for a real service (see README.md, "Write an adapter").
import type {
  ChatAdapter,
  ChatInboundEvent,
  OutgoingMessage,
  Page,
} from "./ports"
import type {
  ChatAttachment,
  ChatMessage,
  ChatPerson,
  ChatTag,
  Conversation,
} from "./types"

type MemorySeed = {
  me: ChatPerson
  conversations: Conversation[]
  /** Messages by conversation id, oldest first. */
  messages: Record<string, ChatMessage[]>
  /** Everyone, by id (for typing and replies). */
  people: Record<string, ChatPerson>
}

type MemoryOptions = {
  /** How long each answer takes, in ms (300 by default). */
  latency?: number
  /** How often a command fails, 0 to 1 (0 by default); or a test per command name. */
  failRate?: number | ((command: string) => boolean)
  /** Messages per page when loading (20 by default). */
  pageSize?: number
  /**
   * Things happening by themselves: after each message I send, the other person reads it, types and answers;
   * every few seconds someone else does something (null to turn it off).
   */
  simulate?: { replies?: string[]; everyMs?: number } | null
  /** The price check for paywalls: true unlocks, a string fails with that message. */
  unlock?: (message: ChatMessage) => true | string
}

type MemoryAdapter = ChatAdapter & {
  /** Puts an inbound event through, as if the service sent it (for stories and tests). */
  inject: (event: ChatInboundEvent) => void
  /** Turns failures on or off while running ("offline"). */
  setOffline: (offline: boolean) => void
  /** Stops its timers. */
  dispose: () => void
}

const OFFLINE = "You're offline. Check the connection, then try again."

/** An in-memory chat service with the chat's ports (see MemoryOptions). */
function createMemoryChatAdapter(
  seed: MemorySeed,
  options: MemoryOptions = {}
): MemoryAdapter {
  const latency = options.latency ?? 300
  const pageSize = options.pageSize ?? 20
  const conversations = new Map(seed.conversations.map((c) => [c.id, { ...c }]))
  const messages = new Map(
    Object.entries(seed.messages).map(([id, list]) => [id, [...list]])
  )
  const handlers = new Set<(event: ChatInboundEvent) => void>()
  const timers = new Set<number>()
  let offline = false
  let next = 1

  const emit = (event: ChatInboundEvent) => handlers.forEach((h) => h(event))
  const later = (ms: number, run: () => void) => {
    const t = window.setTimeout(() => {
      timers.delete(t)
      run()
    }, ms)
    timers.add(t)
  }
  /** Waits like a network would, then fails sometimes (as configured). */
  const answer = async <T>(command: string, value: () => T): Promise<T> => {
    await new Promise((r) =>
      setTimeout(r, latency * (0.6 + Math.random() * 0.8))
    )
    const fails =
      offline ||
      (typeof options.failRate === "function"
        ? options.failRate(command)
        : Math.random() < (options.failRate ?? 0))
    if (fails) throw new Error(OFFLINE)
    return value()
  }
  const list = (conversationId: string) => {
    const found = messages.get(conversationId)
    if (found) return found
    const fresh: ChatMessage[] = []
    messages.set(conversationId, fresh)
    return fresh
  }
  const find = (conversationId: string, messageId: string) =>
    list(conversationId).find((m) => m.id === messageId)
  const otherPerson = (conversationId: string): ChatPerson | undefined => {
    const c = conversations.get(conversationId)
    return (
      c?.people?.[0] ??
      Object.values(seed.people).find((p) => p.id !== seed.me.id)
    )
  }

  /** The other person reads, types, then answers. */
  const reply = (conversationId: string, after: ChatMessage) => {
    const sim = options.simulate === undefined ? {} : options.simulate
    if (!sim) return
    const person = otherPerson(conversationId)
    if (!person) return
    const replies = sim.replies ?? [
      "Got it, thanks!",
      "Haha 😂",
      "Sounds good.",
      "Can we talk later today?",
      "Love these!",
    ]
    later(900, () =>
      emit({
        type: "message.read",
        conversationId,
        personId: person.id,
        upToId: after.id,
      })
    )
    later(1400, () =>
      emit({ type: "typing", conversationId, person, typing: true })
    )
    later(3400, () => {
      const message: ChatMessage = {
        id: `m-${next++}`,
        senderId: person.id,
        text: replies[Math.floor(Math.random() * replies.length)],
        sentAt: new Date(),
      }
      list(conversationId).push(message)
      emit({ type: "message.received", conversationId, message })
    })
  }

  // Someone else, now and then: presence changes, and a message in a chat that may not be open.
  if (options.simulate !== null) {
    const every = options.simulate?.everyMs ?? 15000
    const tick = () => {
      const others = [...conversations.values()].filter(
        (c) => !c.group && c.people?.length
      )
      const c = others[Math.floor(Math.random() * others.length)]
      const person = c?.people?.[0]
      if (c && person) {
        if (Math.random() < 0.5)
          emit({
            type: "presence",
            personId: person.id,
            online: Math.random() < 0.6,
            lastSeen: new Date(),
          })
        else {
          emit({ type: "typing", conversationId: c.id, person, typing: true })
          later(2500, () => {
            const message: ChatMessage = {
              id: `m-${next++}`,
              senderId: person.id,
              text: "Are you there?",
              sentAt: new Date(),
            }
            list(c.id).push(message)
            emit({ type: "message.received", conversationId: c.id, message })
          })
        }
      }
      later(every, tick)
    }
    later(every, tick)
  }

  const adapter: MemoryAdapter = {
    me: seed.me,
    queries: {
      loadMessages: (conversationId, cursor) =>
        answer("loadMessages", (): Page<ChatMessage> => {
          const all = list(conversationId)
          const end = cursor ? Number(cursor) : all.length
          const start = Math.max(0, end - pageSize)
          return {
            items: all.slice(start, end),
            nextCursor: start > 0 ? String(start) : undefined,
          }
        }),
      loadConversations: (cursor, category) =>
        answer("loadConversations", (): Page<Conversation> => {
          const all = [...conversations.values()].filter(
            (c) =>
              !category ||
              category === "all" ||
              !!c.tags?.some((t) => t.id === category)
          )
          const start = cursor ? Number(cursor) : 0
          const items = all.slice(start, start + 50)
          return {
            items,
            nextCursor:
              start + 50 < all.length ? String(start + 50) : undefined,
          }
        }),
      search: (query) =>
        answer("search", () => {
          const q = query.toLowerCase()
          return {
            conversations: [...conversations.values()].filter((c) =>
              c.title.toLowerCase().includes(q)
            ),
            messages: [...messages.values()]
              .flat()
              .filter((m) => m.text?.toLowerCase().includes(q)),
          }
        }),
    },
    commands: {
      sendMessage: (outgoing: OutgoingMessage) =>
        answer("sendMessage", () => {
          const message: ChatMessage = {
            id: `m-${next++}`,
            clientId: outgoing.clientId,
            senderId: seed.me.id,
            text: outgoing.text || undefined,
            attachments: outgoing.attachments,
            replyTo: outgoing.replyTo,
            sentAt: new Date(),
            state: "sent",
          }
          list(outgoing.conversationId).push(message)
          later(500, () =>
            emit({
              type: "message.updated",
              conversationId: outgoing.conversationId,
              message: { ...message, state: "delivered" },
            })
          )
          reply(outgoing.conversationId, message)
          return message
        }),
      editMessage: (conversationId, messageId, text) =>
        answer("editMessage", () => {
          const m = find(conversationId, messageId)
          if (!m) throw new Error("That message is gone.")
          Object.assign(m, { text, edited: true })
          return { ...m }
        }),
      unsendMessage: (conversationId, messageId) =>
        answer("unsendMessage", () => {
          messages.set(
            conversationId,
            list(conversationId).filter((m) => m.id !== messageId)
          )
        }),
      deleteMessage: (conversationId, messageId) =>
        answer("deleteMessage", () => {
          messages.set(
            conversationId,
            list(conversationId).filter((m) => m.id !== messageId)
          )
        }),
      react: (conversationId, messageId, reactionId) =>
        answer("react", () => void [conversationId, messageId, reactionId]),
      unreact: (conversationId, messageId, reactionId) =>
        answer("unreact", () => void [conversationId, messageId, reactionId]),
      forward: (messageId, to) => answer("forward", () => void [messageId, to]),
      pin: (conversationId, messageId, pinned) =>
        answer("pin", () => {
          const m = find(conversationId, messageId)
          if (m) m.pinned = pinned
        }),
      markRead: (conversationId) =>
        answer("markRead", () => {
          const c = conversations.get(conversationId)
          if (c) c.unread = 0
        }),
      setTyping: () => answer("setTyping", () => undefined),
      unlock: (conversationId, messageId) =>
        answer("unlock", () => {
          const m = find(conversationId, messageId)
          if (!m?.paywall)
            throw new Error("That message has nothing to unlock.")
          const verdict = options.unlock?.(m) ?? true
          if (verdict !== true) throw new Error(verdict)
          m.paywall = { ...m.paywall, unlocked: true }
          return { ...m }
        }),
      setTags: (conversationId, tags: ChatTag[]) =>
        answer("setTags", () => {
          const c = conversations.get(conversationId)
          if (c) c.tags = tags
        }),
      mute: (conversationId, until) =>
        answer("mute", () => {
          const c = conversations.get(conversationId)
          if (c)
            Object.assign(c, {
              muted: until !== null,
              mutedUntil: until ?? undefined,
            })
        }),
      block: (personId, blocked) =>
        answer("block", () => void [personId, blocked]),
      report: (target, reason) => answer("report", () => void [target, reason]),
      uploadAttachment: (file, onProgress) =>
        answer("uploadAttachment", (): ChatAttachment => {
          onProgress?.(1)
          return file.type.startsWith("image/")
            ? { kind: "image", url: URL.createObjectURL(file), name: file.name }
            : { kind: "file", name: file.name, size: file.size }
        }),
    },
    events: {
      subscribe(handler) {
        handlers.add(handler)
        return () => handlers.delete(handler)
      },
    },
    inject: emit,
    setOffline(value) {
      offline = value
    },
    dispose() {
      timers.forEach((t) => window.clearTimeout(t))
      timers.clear()
      handlers.clear()
    },
  }
  return adapter
}

export { createMemoryChatAdapter }
export type { MemoryAdapter, MemoryOptions, MemorySeed }
