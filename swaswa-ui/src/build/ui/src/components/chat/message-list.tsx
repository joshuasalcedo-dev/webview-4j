import * as React from "react"

import { cn } from "../../lib/utils"
import { Spinner, useInfiniteScroll } from "../infinite-scroll"
import { useChat } from "./chat-provider"
import "./chat.css"
import { ArrowDownIcon } from "./icons"
import {
  type GroupPosition,
  type MessageActions,
  MessageBubble,
  TypingIndicator,
} from "./message-bubble"
import { formatDay, sameDay, toDate } from "./time"
import type { ChatMessage, ChatPerson } from "./types"

/** Messages from the same person this close together run together (one name, one picture, tight corners). */
const RUN_GAP_MS = 5 * 60_000

type Row =
  | { kind: "day"; key: string; label: string }
  | { kind: "unread"; key: string }
  | { kind: "system"; key: string; message: ChatMessage }
  | {
      kind: "message"
      key: string
      message: ChatMessage
      position: GroupPosition
    }

/** The rows to draw: day lines, the "New messages" line, notices, and each message with its place in its run. */
function buildRows(messages: ChatMessage[], unreadFrom?: string): Row[] {
  const rows: Row[] = []
  let previous: ChatMessage | undefined
  messages.forEach((message, i) => {
    if (!previous || !sameDay(previous.sentAt, message.sentAt)) {
      rows.push({
        kind: "day",
        key: `day-${message.id}`,
        label: formatDay(message.sentAt),
      })
    }
    if (message.id === unreadFrom) rows.push({ kind: "unread", key: "unread" })
    if (message.kind === "system") {
      rows.push({ kind: "system", key: message.id, message })
      previous = message
      return
    }
    const runsOn = (a?: ChatMessage, b?: ChatMessage) =>
      !!a &&
      !!b &&
      a.kind !== "system" &&
      b.kind !== "system" &&
      a.senderId === b.senderId &&
      sameDay(a.sentAt, b.sentAt) &&
      Math.abs(toDate(b.sentAt).getTime() - toDate(a.sentAt).getTime()) <
        RUN_GAP_MS &&
      b.id !== unreadFrom
    const next = messages[i + 1]
    const joinsBefore = runsOn(previous, message)
    const joinsAfter = runsOn(message, next)
    const position: GroupPosition = joinsBefore
      ? joinsAfter
        ? "middle"
        : "last"
      : joinsAfter
        ? "first"
        : "single"
    rows.push({ kind: "message", key: message.id, message, position })
    previous = message
  })
  return rows
}

type MessageListProps = Omit<React.ComponentProps<"div">, "children"> &
  MessageActions & {
    /** Oldest first. */
    messages: ChatMessage[]
    /** The id of the person using the app (their messages are on the right); ChatConfig.me by default. */
    me?: string
    /** Everyone in the chat, by id (names and pictures). */
    people?: Record<string, ChatPerson>
    /** The conversation shown: with a ChatProvider, the list reports what was seen ("messages.seen"). */
    conversationId?: string
    /** More than two people: names show above others' messages. */
    group?: boolean
    /** The first message not read yet: a "New messages" line above it, and the list opens there. */
    unreadFrom?: string
    /** Who is typing now. */
    typing?: ChatPerson[]
    /** There are older messages to load: scrolling to the top calls onLoadOlder (it shows "Loading…" until it resolves). */
    hasOlder?: boolean
    onLoadOlder?: () => void | Promise<void>
    /** Shown when there are no messages (ChatConfig.render.empty, or the "emptyChat" label, by default). */
    emptyText?: React.ReactNode
    /** Filled with a function that scrolls to a message and lights it (e.g. for pinned messages elsewhere). */
    jumpRef?: React.RefObject<((messageId: string) => void) | null>
  }

/**
 * A conversation's messages, oldest at the top: day lines ("Today", "Monday, 12 October"), runs of
 * messages from the same person drawn together, notices in the middle, a "New messages" line, and who is
 * typing at the bottom. It stays at the bottom as messages come in while you are there; scrolled up, a
 * "↓ 3 new" button brings you back. Scrolling to the top loads older messages without moving what you see.
 * A quoted reply jumps to the message it answers and lights it up.
 */
function MessageList({
  messages,
  me: meProp,
  people = {},
  conversationId,
  group = false,
  unreadFrom,
  typing = [],
  hasOlder = false,
  onLoadOlder: onLoadOlderProp,
  emptyText,
  jumpRef,
  className,
  onReply,
  onReact,
  onEdit,
  onDelete,
  onForward,
  onPin,
  onRetry,
  onOpenAttachment,
  menu,
  ...props
}: MessageListProps) {
  const actions: MessageActions = {
    onReply,
    onReact,
    onEdit,
    onDelete,
    onForward,
    onPin,
    onRetry,
    onOpenAttachment,
    menu,
  }
  const chat = useChat()
  const me = meProp ?? chat.me.id
  const onLoadOlder = onLoadOlderProp ?? chat.actions.onLoadOlder
  const rows = React.useMemo(
    () => buildRows(messages, unreadFrom),
    [messages, unreadFrom]
  )
  const byId = React.useMemo(
    () => new Map(messages.map((m) => [m.id, m])),
    [messages]
  )

  const scroller = React.useRef<HTMLDivElement>(null)
  const atBottom = React.useRef(true)
  const [showJump, setShowJump] = React.useState(false)
  const [newCount, setNewCount] = React.useState(0)
  // Older messages load as the top comes near (the shared infinite scroll: one at a time, view kept still).
  const top = React.useRef<HTMLDivElement>(null)
  const older = useInfiniteScroll(scroller, top, {
    onLoadMore: () => onLoadOlder?.(),
    hasMore: hasOlder && !!onLoadOlder,
    direction: "up",
    threshold: 200,
  })
  const [lit, setLit] = React.useState<string>()
  // What was first and last before this render, and how tall it all was, to keep the view still.
  const before = React.useRef({
    first: messages[0]?.id,
    last: messages[messages.length - 1]?.id,
    height: 0,
    count: messages.length,
  })

  const toBottom = (smooth = false) => {
    const element = scroller.current
    if (!element) return
    element.scrollTo({
      top: element.scrollHeight,
      behavior: smooth ? "smooth" : "auto",
    })
    setNewCount(0)
    setShowJump(false)
  }

  // Opening: at the "New messages" line when there is one, otherwise at the bottom.
  React.useLayoutEffect(() => {
    const element = scroller.current
    if (!element) return
    const line = element.querySelector<HTMLElement>(".nt-unread-line")
    if (line) {
      element.scrollTop = line.offsetTop - 24
      atBottom.current = false
    } else element.scrollTop = element.scrollHeight
    before.current.height = element.scrollHeight
    // Only when the list appears.
  }, [])

  // Messages changed: keep the view where it was (older ones added on top), follow new ones at the bottom,
  // or count them for the jump button.
  React.useLayoutEffect(() => {
    const element = scroller.current
    if (!element) return
    const was = before.current
    const first = messages[0]?.id
    const last = messages[messages.length - 1]?.id
    if (first !== was.first && last === was.last) {
      element.scrollTop += element.scrollHeight - was.height
    } else if (last !== was.last && messages.length > was.count) {
      const added = messages.slice(was.count)
      const ownAdded = added.some((m) => m.senderId === me)
      if (atBottom.current || ownAdded) toBottom()
      else {
        setNewCount(
          (n) =>
            n +
            added.filter((m) => m.senderId !== me && m.kind !== "system").length
        )
        setShowJump(true)
      }
    } else if (atBottom.current) element.scrollTop = element.scrollHeight
    before.current = {
      first,
      last,
      height: element.scrollHeight,
      count: messages.length,
    }
    // toBottom only reads refs and sets state.
  }, [messages, me])

  // Whatever makes the messages taller while you are at the bottom (typing dots, a picture or a code block
  // that finished loading) keeps you at the bottom.
  const inner = React.useRef<HTMLDivElement>(null)
  React.useEffect(() => {
    const content = inner.current
    const element = scroller.current
    if (!content || !element || typeof ResizeObserver === "undefined") return
    const watch = new ResizeObserver(() => {
      if (atBottom.current) element.scrollTop = element.scrollHeight
    })
    watch.observe(content)
    return () => watch.disconnect()
  }, [])

  const seenUpTo = React.useRef<string | undefined>(undefined)
  const reportSeen = React.useCallback(() => {
    if (!conversationId || !atBottom.current) return
    if (document.visibilityState !== "visible" || !document.hasFocus()) return
    const last = [...messages]
      .reverse()
      .find((m) => m.senderId !== me && m.kind !== "system")
    if (!last || seenUpTo.current === last.id) return
    seenUpTo.current = last.id
    chat.events.emit("messages.seen", { conversationId, upToId: last.id })
  }, [conversationId, messages, me, chat.events])
  React.useEffect(() => {
    reportSeen()
  }, [reportSeen])
  React.useEffect(
    () => chat.events.on("window.focus", () => reportSeen()),
    [chat.events, reportSeen]
  )

  const onScroll = () => {
    const element = scroller.current
    if (!element) return
    const bottom =
      element.scrollHeight - element.scrollTop - element.clientHeight < 48
    if (bottom && !atBottom.current && conversationId)
      chat.events.emit("scrolled.to.bottom", { conversationId })
    atBottom.current = bottom
    setShowJump(!bottom)
    if (bottom) {
      setNewCount(0)
      reportSeen()
    }
  }

  const jumpTo = (id: string) => {
    const target = scroller.current?.querySelector<HTMLElement>(
      `[data-message-id="${CSS.escape(id)}"]`
    )
    if (!target) return
    target.scrollIntoView({ block: "center", behavior: "smooth" })
    setLit(id)
    window.setTimeout(
      () => setLit((now) => (now === id ? undefined : now)),
      1600
    )
  }
  React.useEffect(() => {
    if (jumpRef) jumpRef.current = jumpTo
  })

  return (
    <div className={cn("nt-messages", className)} {...props}>
      <div
        ref={scroller}
        className="nt-messages-scroll"
        onScroll={onScroll}
        role="log"
        aria-live="polite"
        aria-label="Messages"
      >
        <div
          ref={top}
          className="nt-messages-older"
          data-shown={hasOlder || undefined}
        >
          {older.state.status === "loading" && (
            <>
              <Spinner size={14} /> {chat.say("loadingOlder")}
            </>
          )}
          {older.state.status === "failed" && (
            <>
              {older.state.message}{" "}
              <button
                type="button"
                className="nt-messages-retry"
                onClick={() => void older.retry()}
              >
                Try again
              </button>
            </>
          )}
        </div>
        {messages.length === 0 && (
          <div className="nt-chat-empty">
            {emptyText ?? chat.render?.empty?.() ?? chat.say("emptyChat")}
          </div>
        )}
        <div ref={inner} className="nt-messages-inner">
          {rows.map((row) => {
            if (row.kind === "day")
              return (
                <div key={row.key} className="nt-day-line" role="separator">
                  <span>{row.label}</span>
                </div>
              )
            if (row.kind === "unread")
              return (
                <div key={row.key} className="nt-unread-line" role="separator">
                  <span>{chat.say("newMessages")}</span>
                </div>
              )
            if (row.kind === "system")
              return (
                <div
                  key={row.key}
                  className="nt-system-message"
                  data-message-id={row.message.id}
                >
                  {chat.render?.systemMessage?.(row.message) ??
                    row.message.text}
                </div>
              )
            const message = row.message
            const mine = message.senderId === me
            const replied = message.replyTo
              ? byId.get(message.replyTo)
              : undefined
            const starts = row.position === "single" || row.position === "first"
            const ends = row.position === "single" || row.position === "last"
            return (
              <MessageBubble
                key={row.key}
                message={message}
                mine={mine}
                sender={people[message.senderId]}
                position={row.position}
                showName={group && starts}
                showAvatar={starts}
                showMeta={ends || message.state === "failed"}
                quoted={
                  replied
                    ? {
                        message: replied,
                        sender: people[replied.senderId],
                        mine: replied.senderId === me,
                      }
                    : undefined
                }
                onJumpTo={jumpTo}
                highlighted={lit === message.id}
                {...actions}
              />
            )
          })}
          <TypingIndicator people={typing} />
        </div>
      </div>
      {showJump && (
        <button
          type="button"
          className="nt-jump"
          onClick={() => toBottom(true)}
          aria-label={
            newCount > 0
              ? `${newCount} new messages: go to the latest`
              : "Go to the latest message"
          }
        >
          <ArrowDownIcon />
          {newCount > 0 && <span className="nt-badge">{newCount}</span>}
        </button>
      )}
    </div>
  )
}

export { buildRows, MessageList }
export type { MessageListProps }
