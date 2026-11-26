import * as React from "react"

import { cn } from "../../../lib/utils"
import { Avatar } from "../../avatar"
import { CodeBlock } from "../../code-editor"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import { useDialogs } from "../../dialog"
import { FileChip } from "../../files"
import { Money } from "../../money"
import { useNative } from "../../provider"
import { Spinner } from "../../infinite-scroll"
import { Link, Text } from "../../text"
import "./chat.css"
import { useChat } from "./chat-provider"
import {
  type ChatMessageCapabilities,
  type ReactionOption,
  timeLeft,
} from "./config"
import {
  AlertIcon,
  CheckIcon,
  ClockIcon,
  CoinIcon,
  DoubleCheckIcon,
  LockIcon,
  MoreIcon,
  PauseIcon,
  PinIcon,
  PlayIcon,
  ReplyIcon,
  SmilePlusIcon,
} from "./icons"
import { formatAmount, formatDuration, formatTime, toDate } from "./time"
import type {
  ChatAttachment,
  ChatMessage,
  ChatPerson,
  DeliveryState,
  LinkPreview,
} from "./types"

/** What can be done to a message; each one given (and allowed by its rules) adds its button or menu item. */
type MessageActions = {
  onReply?: (message: ChatMessage) => void
  /** A reaction added, or taken away when it was already yours (its id: the emoji, or the app's own id). */
  onReact?: (message: ChatMessage, reactionId: string) => void
  /** Own text messages only. */
  onEdit?: (message: ChatMessage) => void
  /** Takes one's own message back for everyone (within its unsend time). */
  onUnsend?: (message: ChatMessage) => void | Promise<void>
  /** Asked first ("Delete this message?"), then called. */
  onDelete?: (message: ChatMessage) => void
  onForward?: (message: ChatMessage) => void
  onPin?: (message: ChatMessage, pinned: boolean) => void
  onReport?: (message: ChatMessage) => void
  /** A failed message sent again. */
  onRetry?: (message: ChatMessage) => void
  /** Pays for a paywalled message (or its own paywall.onUnlock); throws with a message when it fails. */
  onUnlock?: (message: ChatMessage) => Promise<void>
  /** A picture, file or link clicked. */
  onOpenAttachment?: (
    message: ChatMessage,
    attachment: ChatAttachment | LinkPreview
  ) => void
  /** More menu items, before Delete (ChatConfig.menus.message can change the whole menu). */
  menu?: (message: ChatMessage) => MenuEntry[]
}

/** Where a message is in a run of messages from the same person (its corners follow). */
type GroupPosition = "single" | "first" | "middle" | "last"

type MessageBubbleProps = MessageActions & {
  message: ChatMessage
  /** Sent by the person using the app. */
  mine: boolean
  sender?: ChatPerson
  position?: GroupPosition
  /** The sender's name above (in groups, on the first of a run). */
  showName?: boolean
  /** The sender's picture beside it (on the first of a run); otherwise its space stays empty. */
  showAvatar?: boolean
  /** The time and how far it got, under it (on the last of a run). */
  showMeta?: boolean
  /** The message it answers, quoted above it; a click calls onJumpTo. */
  quoted?: { message: ChatMessage; sender?: ChatPerson; mine: boolean }
  onJumpTo?: (id: string) => void
  /** Briefly lit (after jumping to it). */
  highlighted?: boolean
}

const URL_PATTERN = /(https?:\/\/[^\s<]+[^\s<.,;:!?)\]'"])/g

/** The message's text, with web addresses as links. */
function MessageText({
  text,
  selectable = true,
}: {
  text: string
  selectable?: boolean
}) {
  const parts = text.split(URL_PATTERN)
  return (
    <Text as="div" className="nt-msg-text" selectable={selectable}>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <Link key={i} href={part} external>
            {part.replace(/^https?:\/\//, "")}
          </Link>
        ) : (
          <React.Fragment key={i}>{part}</React.Fragment>
        )
      )}
    </Text>
  )
}

/** Pictures sent together: one large, or a grid of up to four with "+N" on the last. */
function ImageGrid({
  images,
  onOpen,
  blurred = false,
}: {
  images: Extract<ChatAttachment, { kind: "image" }>[]
  onOpen?: (image: ChatAttachment) => void
  blurred?: boolean
}) {
  const shown = images.slice(0, 4)
  const more = images.length - shown.length
  return (
    <div
      className="nt-msg-images"
      data-count={shown.length}
      data-blurred={blurred || undefined}
    >
      {shown.map((image, i) => (
        <button
          key={image.url + i}
          type="button"
          className="nt-msg-image"
          disabled={blurred}
          onClick={() => onOpen?.(image)}
          aria-label={blurred ? "Locked picture" : (image.name ?? "Picture")}
        >
          <img
            src={image.url}
            alt={blurred ? "" : (image.name ?? "")}
            loading="lazy"
            draggable={false}
          />
          {i === shown.length - 1 && more > 0 && (
            <span className="nt-msg-image-more">+{more}</span>
          )}
        </button>
      ))}
    </div>
  )
}

/** A voice message: play or pause, a bar to see (and click to move) where it is, and the time left. */
function VoiceNote({
  note,
}: {
  note: Extract<ChatAttachment, { kind: "voice" }>
}) {
  const [playing, setPlaying] = React.useState(false)
  const [at, setAt] = React.useState(0)
  const audio = React.useRef<HTMLAudioElement | null>(null)

  // Without a recording (a preview), the bar just moves along in time.
  React.useEffect(() => {
    if (!playing || note.url) return
    const started = performance.now() - at * 1000
    const timer = window.setInterval(() => {
      const now = (performance.now() - started) / 1000
      if (now >= note.duration) {
        setAt(0)
        setPlaying(false)
      } else setAt(now)
    }, 100)
    return () => window.clearInterval(timer)
    // Restart the clock only when play starts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playing, note.url, note.duration])

  const toggle = () => {
    if (note.url) {
      audio.current ??= Object.assign(new Audio(note.url), {
        ontimeupdate: () => setAt(audio.current?.currentTime ?? 0),
        onended: () => {
          setPlaying(false)
          setAt(0)
        },
      })
      if (playing) audio.current.pause()
      else void audio.current.play().catch(() => setPlaying(false))
    }
    setPlaying(!playing)
  }
  React.useEffect(() => () => audio.current?.pause(), [])

  const part = note.duration > 0 ? at / note.duration : 0
  return (
    <div className="nt-voice">
      <button
        type="button"
        className="nt-voice-play"
        aria-label={playing ? "Pause" : "Play voice message"}
        onClick={toggle}
      >
        {playing ? <PauseIcon /> : <PlayIcon />}
      </button>
      <span
        className="nt-voice-bar"
        role="slider"
        aria-label="Where in the voice message"
        aria-valuemin={0}
        aria-valuemax={Math.round(note.duration)}
        aria-valuenow={Math.round(at)}
        tabIndex={0}
        onClick={(event) => {
          const box = event.currentTarget.getBoundingClientRect()
          const next = ((event.clientX - box.left) / box.width) * note.duration
          setAt(next)
          if (audio.current) audio.current.currentTime = next
        }}
        onKeyDown={(event) => {
          const by =
            event.key === "ArrowRight" ? 5 : event.key === "ArrowLeft" ? -5 : 0
          if (!by) return
          event.preventDefault()
          const next = Math.max(0, Math.min(note.duration, at + by))
          setAt(next)
          if (audio.current) audio.current.currentTime = next
        }}
      >
        <span style={{ width: `${part * 100}%` }} />
      </span>
      <span className="nt-voice-time">
        {formatDuration(playing || at > 0 ? note.duration - at : note.duration)}
      </span>
    </div>
  )
}

/** The card a link gets: picture, site, title, a line about it. */
function LinkCard({
  link,
  onOpen,
}: {
  link: LinkPreview
  onOpen?: () => void
}) {
  return (
    <a
      className="nt-linkcard"
      href={link.url}
      target="_blank"
      rel="noreferrer noopener"
      onClick={onOpen}
    >
      {link.image && (
        <img src={link.image} alt="" loading="lazy" draggable={false} />
      )}
      <span className="nt-linkcard-text">
        <span className="nt-linkcard-site">
          {link.site ?? new URL(link.url).hostname.replace(/^www\./, "")}
        </span>
        <span className="nt-linkcard-title">{link.title}</span>
        {link.description && (
          <span className="nt-linkcard-description">{link.description}</span>
        )}
      </span>
    </a>
  )
}

const STATE_WORDS: Record<DeliveryState, string> = {
  sending: "Sending…",
  sent: "Sent",
  delivered: "Delivered",
  read: "Read",
  failed: "Not sent",
}

/** How far one's own message got: Windows draws marks (clock, ✓, ✓✓, accent ✓✓ when read); a Mac says it in words. */
function Delivery({
  state,
  receipts,
}: {
  state: DeliveryState
  receipts: boolean
}) {
  const { platform } = useNative()
  // Without read receipts, "read" looks like "delivered".
  const shown: DeliveryState =
    !receipts && state === "read" ? "delivered" : state
  if (platform === "mac")
    return (
      <span className="nt-msg-state" data-state={shown}>
        {STATE_WORDS[shown]}
      </span>
    )
  const icon =
    shown === "sending" ? (
      <ClockIcon />
    ) : shown === "sent" ? (
      <CheckIcon />
    ) : (
      <DoubleCheckIcon />
    )
  return (
    <span
      className="nt-msg-state"
      data-state={shown}
      role="img"
      aria-label={STATE_WORDS[shown]}
      title={STATE_WORDS[shown]}
    >
      {icon}
    </span>
  )
}

/** How a reaction looks: its emoji, or the app's own icon. */
function ReactionFace({
  option,
  fallback,
}: {
  option?: ReactionOption
  fallback: string
}) {
  if (option?.icon)
    return <span className="nt-reaction-icon">{option.icon}</span>
  return <span>{option?.emoji ?? fallback}</span>
}

/** Small row of reactions to choose from. */
function ReactionPicker({
  options,
  onPick,
  onClose,
  mine,
}: {
  options: ReactionOption[]
  onPick: (id: string) => void
  onClose: () => void
  mine: boolean
}) {
  const ref = React.useRef<HTMLDivElement>(null)
  React.useEffect(() => {
    ref.current?.querySelector("button")?.focus()
    const away = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) onClose()
    }
    document.addEventListener("mousedown", away)
    return () => document.removeEventListener("mousedown", away)
  }, [onClose])
  return (
    <div
      ref={ref}
      className="nt-reaction-picker"
      data-side={mine ? "mine" : "theirs"}
      role="menu"
      aria-label="React"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.stopPropagation()
          onClose()
        } else if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
          const buttons = Array.from(
            ref.current?.querySelectorAll("button") ?? []
          )
          const at = buttons.indexOf(
            document.activeElement as HTMLButtonElement
          )
          buttons[
            (at + (event.key === "ArrowRight" ? 1 : -1) + buttons.length) %
              buttons.length
          ]?.focus()
        }
      }}
    >
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          role="menuitem"
          aria-label={option.label}
          title={option.label}
          onClick={() => onPick(option.id)}
        >
          <ReactionFace option={option} fallback={option.id} />
        </button>
      ))}
    </div>
  )
}

/** The paid part of a message, while locked: the pictures blurred (or a lock, or a teaser), and the unlock button. */
function Paywall({
  message,
  onUnlock,
}: {
  message: ChatMessage
  onUnlock?: (message: ChatMessage) => Promise<void>
}) {
  const chat = useChat()
  const dialogs = useDialogs()
  const [busy, setBusy] = React.useState(false)
  const wall = message.paywall
  if (!wall) return null
  const unlock = wall.onUnlock ?? onUnlock
  const images = (message.attachments ?? []).filter(
    (a): a is Extract<ChatAttachment, { kind: "image" }> => a.kind === "image"
  )
  const preview = wall.preview ?? "blur"
  const price = (
    <Money amount={wall.price.amount} currency={wall.price.currency} />
  )

  const run = async () => {
    if (!unlock) return
    setBusy(true)
    try {
      await unlock(message)
    } catch (problem) {
      const again = await dialogs.error({
        title: "Couldn't unlock this",
        message:
          problem instanceof Error && problem.message
            ? problem.message
            : "The payment didn't go through. Nothing was charged. Try again.",
        retry: "Try again",
      })
      if (again === "retry") {
        setBusy(false)
        return run()
      }
    }
    setBusy(false)
  }

  return (
    <div className="nt-paywall" data-preview={preview}>
      {preview === "blur" && images.length > 0 && (
        <ImageGrid images={images} blurred />
      )}
      {(preview === "hidden" ||
        (preview === "blur" && images.length === 0)) && (
        <div className="nt-paywall-lock">
          <LockIcon />
          <span>{chat.say("lockedContent")}</span>
        </div>
      )}
      {preview === "teaser" && wall.teaserText && (
        <Text as="div" className="nt-msg-text">
          {wall.teaserText}
        </Text>
      )}
      <button
        type="button"
        className="nt-paywall-unlock"
        disabled={busy || !unlock}
        onClick={() => void run()}
        aria-busy={busy || undefined}
      >
        {busy ? (
          <>
            <Spinner size={14} /> {chat.say("unlocking")}
          </>
        ) : (
          <>
            <LockIcon />
            {/* "Unlock for {price}" with the price drawn by Money. */}
            {chat.say("unlockFor").split("{price}")[0]}
            {price}
            {chat.say("unlockFor").split("{price}")[1] ?? ""}
          </>
        )}
      </button>
    </div>
  )
}

const firstName = (name: string) => name.split(" ")[0]

/**
 * Money sent with a message (a tip): the amount large, a small "Tip" label and a coin, the note under it.
 * An incoming one is accent-tinted and shimmers once as it arrives. Screen readers hear "Ana sent you a
 * ₱500.00 tip".
 */
function TipCard({
  tip,
  mine,
  sender,
}: {
  tip: Extract<ChatAttachment, { kind: "tip" }>
  mine: boolean
  sender?: ChatPerson
}) {
  const chat = useChat()
  const amount = formatAmount(tip.amount, tip.currency)
  const said = mine
    ? chat.say("tipAnnounceMine", { amount })
    : chat.say("tipAnnounce", {
        name: sender ? firstName(sender.name) : "Someone",
        amount,
      })
  return (
    <div
      className="nt-tip"
      data-incoming={mine ? undefined : ""}
      role="group"
      aria-label={said}
    >
      <span className="nt-tip-head" aria-hidden="true">
        <CoinIcon />
        {chat.say("tip")}
      </span>
      <Money
        className="nt-tip-amount"
        amount={tip.amount}
        currency={tip.currency}
        aria-hidden="true"
      />
      {tip.note && (
        <Text as="div" className="nt-tip-note">
          {tip.note}
        </Text>
      )}
    </div>
  )
}

const copy = (text: string) =>
  void navigator.clipboard?.writeText(text).catch(() => undefined)

/**
 * One message: the bubble (own messages on the right in the accent colour, others on the left), with what it
 * answers quoted above, its text (links clickable, selectable when copying is allowed), pictures, files, a
 * voice note, a link card, the app's own attachments, a paywall; code under it; reactions; slots above and
 * below; on hover a bar to react, reply and open the menu; the right-click menu (Reply, React, Copy, Edit,
 * Unsend, Forward, Pin, Report, Delete), all from the message's resolved rules (ChatConfig).
 */
function MessageBubble({
  message,
  mine,
  sender,
  position = "single",
  showName = false,
  showAvatar = false,
  showMeta = true,
  quoted,
  onJumpTo,
  highlighted = false,
  ...given
}: MessageBubbleProps) {
  const { platform } = useNative()
  const chat = useChat()
  const dialogs = useDialogs()
  const [picking, setPicking] = React.useState(false)
  const closePicker = React.useCallback(() => setPicking(false), [])
  const caps: ChatMessageCapabilities = chat.messageCapabilities(message)
  const tip = message.attachments?.find(
    (a): a is Extract<ChatAttachment, { kind: "tip" }> => a.kind === "tip"
  )
  const locked = !!message.paywall && !message.paywall.unlocked
  const config = chat.actions

  // A handler counts only where the rules allow it; the props win over the config's.
  const actions: MessageActions = {
    onReply: caps.canReplyTo ? (given.onReply ?? config.onReply) : undefined,
    onReact: caps.canReact ? (given.onReact ?? config.onReact) : undefined,
    onEdit: caps.canEdit ? given.onEdit : undefined,
    onUnsend: given.onUnsend ?? config.onUnsend,
    onDelete: caps.canDelete ? (given.onDelete ?? config.onDelete) : undefined,
    onForward:
      caps.canForward && !locked
        ? (given.onForward ?? config.onForward)
        : undefined,
    onPin: caps.canPin ? (given.onPin ?? config.onPin) : undefined,
    onReport: caps.canReport ? (given.onReport ?? config.onReport) : undefined,
    onRetry: chat.readOnlyReason
      ? undefined
      : (given.onRetry ?? config.onRetry),
    onUnlock: given.onUnlock ?? config.onUnlock,
    onOpenAttachment: given.onOpenAttachment ?? config.onOpenAttachment,
    menu: given.menu,
  }

  // The reactions this message takes: its own list, or the conversation's.
  const reactionOptions = (
    caps.allowedReactions ?? chat.reactions.map((r) => r.id)
  ).map((r) =>
    typeof r === "string"
      ? (chat.reactions.find((o) => o.id === r) ?? {
          id: r,
          emoji: r,
          label: r,
        })
      : r
  )
  const canReact = !!actions.onReact && reactionOptions.length > 0

  const remove = async () => {
    const sure = await dialogs.confirm({
      title: "Delete this message?",
      message: mine
        ? "It's deleted for everyone in this chat."
        : "It's deleted for everyone in this chat, as a moderator.",
      kind: "warning",
      confirm: "Delete",
      destructive: true,
    })
    if (sure) actions.onDelete?.(message)
  }

  const menu = (): MenuEntry[] => {
    const entries: MenuEntry[] = []
    // Unsend: one's own message, within its time.
    const unsendLeft =
      caps.unsendDuration && mine
        ? caps.unsendDuration - (Date.now() - toDate(message.sentAt).getTime())
        : 0
    // Text picked out of the message: Copy copies just that.
    const picked = caps.canCopy ? String(window.getSelection() ?? "") : ""
    if (picked)
      entries.push(
        { label: "Copy", shortcut: "Mod+C", onSelect: () => copy(picked) },
        "separator"
      )
    if (tip && !mine && sender && !chat.readOnlyReason)
      entries.push({
        label: chat.say("thank", { name: firstName(sender.name) }),
        onSelect: () =>
          chat.events.emit("composer.insert", {
            text: chat.say("thankText", { name: firstName(sender.name) }),
          }),
      })
    if (tip)
      entries.push({
        label: chat.say("copyAmount"),
        onSelect: () => copy(formatAmount(tip.amount, tip.currency)),
      })
    if (actions.onReply)
      entries.push({
        label: chat.say("reply"),
        onSelect: () => actions.onReply?.(message),
      })
    if (canReact)
      entries.push({
        label: chat.say("react"),
        onSelect: () => setPicking(true),
      })
    if (message.text && caps.canCopy)
      entries.push({
        label: chat.say("copyText"),
        onSelect: () => copy(message.text ?? ""),
      })
    if (actions.onEdit)
      entries.push({
        label: chat.say("edit"),
        onSelect: () => actions.onEdit?.(message),
      })
    if (actions.onUnsend && unsendLeft > 0)
      entries.push({
        label: chat.say("unsendLeft", { time: timeLeft(unsendLeft) }),
        onSelect: () => void actions.onUnsend?.(message),
      })
    if (actions.onForward)
      entries.push({
        label: chat.say("forward"),
        onSelect: () => actions.onForward?.(message),
      })
    if (actions.onPin)
      entries.push({
        label: message.pinned ? chat.say("unpin") : chat.say("pin"),
        onSelect: () => actions.onPin?.(message, !message.pinned),
      })
    const extra = actions.menu?.(message) ?? []
    if (extra.length > 0) entries.push("separator", ...extra)
    if (actions.onReport)
      entries.push("separator", {
        label: chat.say("report"),
        onSelect: () => actions.onReport?.(message),
      })
    if (actions.onDelete)
      entries.push("separator", {
        label: chat.say("delete"),
        onSelect: () => void remove(),
      })
    // The app may change the whole menu.
    return chat.menus?.message
      ? chat.menus.message(entries, message, caps)
      : entries
  }
  const { onContextMenu } = useContextMenu(menu)

  const images = (message.attachments ?? []).filter(
    (a): a is Extract<ChatAttachment, { kind: "image" }> => a.kind === "image"
  )
  const others = (message.attachments ?? []).filter(
    (a) => a.kind !== "image" && a.kind !== "tip"
  )
  const showLink = message.link && chat.features.linkPreviews
  const onlyImages =
    !locked &&
    images.length > 0 &&
    !message.text &&
    others.length === 0 &&
    !showLink
  const sentAt = toDate(message.sentAt)
  const above = message.above ?? chat.render?.above?.(message)
  const below = message.below ?? chat.render?.below?.(message)

  return (
    <div
      className={cn("nt-msg", highlighted && "nt-msg-lit")}
      data-mine={mine || undefined}
      data-position={position}
      data-message-id={message.id}
      data-state={message.state}
    >
      {!mine && (
        <span className="nt-msg-avatar">
          {showAvatar && sender && (
            <Avatar name={sender.name} src={sender.avatar} size={28} />
          )}
        </span>
      )}
      <div className="nt-msg-column">
        {showName && sender && !mine && (
          <span className="nt-msg-name">{sender.name}</span>
        )}
        {above != null && <div className="nt-msg-slot">{above}</div>}
        <div className="nt-msg-row">
          <div
            className="nt-bubble"
            data-media={onlyImages || undefined}
            data-tip={tip ? "" : undefined}
            tabIndex={0}
            title={sentAt.toLocaleString(undefined, {
              dateStyle: "full",
              timeStyle: "short",
            })}
            onContextMenu={onContextMenu}
            onKeyDown={(event) => {
              // The keys chat apps use on a chosen message: R replies, E edits own, Delete deletes.
              if (event.target !== event.currentTarget) return
              if (event.key.toLowerCase() === "r" && actions.onReply)
                actions.onReply(message)
              else if (event.key.toLowerCase() === "e" && actions.onEdit)
                actions.onEdit(message)
              else if (
                (event.key === "Delete" ||
                  (platform === "mac" &&
                    event.metaKey &&
                    event.key === "Backspace")) &&
                actions.onDelete
              )
                void remove()
              else return
              event.preventDefault()
            }}
          >
            {quoted && (
              <button
                type="button"
                className="nt-msg-quote"
                onClick={() => onJumpTo?.(quoted.message.id)}
              >
                <span className="nt-msg-quote-name">
                  {quoted.mine ? "You" : (quoted.sender?.name ?? "Someone")}
                </span>
                <span className="nt-msg-quote-text">
                  {quoted.message.text ??
                    (quoted.message.attachments?.length
                      ? "Attachment"
                      : quoted.message.code
                        ? "Code"
                        : "Message")}
                </span>
              </button>
            )}
            {locked ? (
              <Paywall message={message} onUnlock={actions.onUnlock} />
            ) : (
              <>
                {tip && <TipCard tip={tip} mine={mine} sender={sender} />}
                {images.length > 0 && (
                  <ImageGrid
                    images={images}
                    onOpen={(a) => actions.onOpenAttachment?.(message, a)}
                  />
                )}
                {message.text && (
                  <MessageText text={message.text} selectable={caps.canCopy} />
                )}
                {others.map((a, i) =>
                  a.kind === "voice" ? (
                    chat.features.voiceNotes ? (
                      <VoiceNote key={i} note={a} />
                    ) : null
                  ) : a.kind === "file" ? (
                    <FileChip
                      key={i}
                      name={a.name}
                      size={a.size}
                      onOpen={() => actions.onOpenAttachment?.(message, a)}
                    />
                  ) : a.kind === "custom" ? (
                    <React.Fragment key={i}>
                      {chat.render?.attachment?.[a.type]?.(a, message) ?? null}
                    </React.Fragment>
                  ) : null
                )}
                {showLink &&
                  message.link &&
                  (chat.render?.linkPreview?.(message.link, message) ?? (
                    <LinkCard
                      link={message.link}
                      onOpen={() =>
                        message.link &&
                        actions.onOpenAttachment?.(message, message.link)
                      }
                    />
                  ))}
              </>
            )}
          </div>
          {(canReact || actions.onReply) && (
            <span className="nt-msg-hoverbar" data-open={picking || undefined}>
              {canReact && (
                <button
                  type="button"
                  aria-label="React"
                  title="React"
                  onClick={() => setPicking(!picking)}
                >
                  <SmilePlusIcon />
                </button>
              )}
              {actions.onReply && (
                <button
                  type="button"
                  aria-label={chat.say("reply")}
                  title={chat.say("reply")}
                  onClick={() => actions.onReply?.(message)}
                >
                  <ReplyIcon />
                </button>
              )}
              <button
                type="button"
                aria-label="More"
                title="More"
                onClick={onContextMenu}
              >
                <MoreIcon />
              </button>
              {picking && canReact && (
                <ReactionPicker
                  options={reactionOptions}
                  mine={mine}
                  onClose={closePicker}
                  onPick={(id) => {
                    setPicking(false)
                    actions.onReact?.(message, id)
                  }}
                />
              )}
            </span>
          )}
        </div>
        {!locked && message.code && (
          <CodeBlock
            className="nt-msg-code"
            code={message.code.code}
            language={message.code.language}
            fileName={message.code.fileName}
          />
        )}
        {chat.features.reactions &&
          message.reactions &&
          message.reactions.length > 0 && (
            <div className="nt-reactions">
              {message.reactions.map((r) => {
                const option = chat.reactions.find((o) => o.id === r.emoji)
                return (
                  <button
                    key={r.emoji}
                    type="button"
                    className="nt-reaction"
                    data-mine={r.mine || undefined}
                    aria-pressed={!!r.mine}
                    aria-label={`${option?.label ?? r.emoji} ${r.count}${r.mine ? ", including you" : ""}`}
                    title={option?.label}
                    disabled={!canReact}
                    onClick={() => actions.onReact?.(message, r.emoji)}
                  >
                    <ReactionFace option={option} fallback={r.emoji} />
                    {r.count > 1 && (
                      <span className="nt-reaction-count">{r.count}</span>
                    )}
                  </button>
                )
              })}
            </div>
          )}
        {below != null && <div className="nt-msg-slot">{below}</div>}
        {message.state === "failed" ? (
          <button
            type="button"
            className="nt-msg-failed"
            onClick={() => actions.onRetry?.(message)}
            disabled={!actions.onRetry}
          >
            <AlertIcon /> {chat.say("notSent")}{" "}
            {actions.onRetry ? chat.say("tryAgain") : ""}
          </button>
        ) : (
          showMeta && (
            <span className="nt-msg-meta">
              {message.pinned && (
                <span
                  className="nt-msg-pinned"
                  aria-label="Pinned"
                  title="Pinned"
                >
                  <PinIcon />
                </span>
              )}
              {message.edited && <span>{chat.say("edited")}</span>}
              <time dateTime={sentAt.toISOString()}>{formatTime(sentAt)}</time>
              {mine && message.state && (
                <Delivery
                  state={message.state}
                  receipts={chat.features.readReceipts}
                />
              )}
            </span>
          )
        )}
      </div>
    </div>
  )
}

/** "Ana is typing…" with the three moving dots, as both systems' chat apps show. */
function TypingIndicator({ people }: { people: ChatPerson[] }) {
  const chat = useChat()
  if (people.length === 0 || !chat.features.typingIndicator) return null
  const words =
    people.length === 1
      ? chat.say("someoneTyping", { name: people[0].name })
      : people.length === 2
        ? `${people[0].name} and ${people[1].name} are typing…`
        : `${people.length} people are typing…`
  return (
    <div className="nt-typing" role="status" aria-live="polite">
      <span className="nt-typing-dots" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      <span className="nt-typing-words">{words}</span>
    </div>
  )
}

export { MessageBubble, TypingIndicator, VoiceNote }
export type { GroupPosition, MessageActions, MessageBubbleProps }
