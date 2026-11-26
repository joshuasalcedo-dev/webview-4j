import * as React from "react"

import type { ChatComposerTool, DefaultToolsOptions } from "../../composer-toolbar"
import type { MenuEntry } from "../../context-menu"
import type { PillColor } from "../../pill"
import type { SwipeAction } from "../../swipeable"
import type {
  ChatAttachment,
  ChatMessage,
  ChatPerson,
  ChatTag,
  Conversation,
  LinkPreview,
} from "./types"

// --- rules: what the person may do -------------------------------------------------------------------

/**
 * What the person using the app may do in a conversation. Left out means allowed (so the components work on
 * their own), except deleteAny, which must be given; an app sets to false what is not allowed. Apps add their
 * own in `extra` and check them with can().
 */
type ChatCapabilities = {
  send?: boolean
  reply?: boolean
  react?: boolean
  /** Change one's own messages. */
  edit?: boolean
  /** Delete one's own messages. */
  delete?: boolean
  /** Delete anyone's messages (a moderator). Off unless given. */
  deleteAny?: boolean
  attach?: boolean
  voice?: boolean
  mention?: boolean
  call?: boolean
  videoCall?: boolean
  pin?: boolean
  forward?: boolean
  /** Copy text out of messages (off in protected chats: the text cannot be selected either). */
  copy?: boolean
  mute?: boolean
  block?: boolean
  report?: boolean
  addTags?: boolean
  /** Send money (a tip): the composer's tip tool. Off unless given (the adapter must support it). */
  sendTip?: boolean
  /** Attach files. */
  canUpload?: boolean
  /** Photos, video, voice notes, GIFs. */
  canAddMedia?: boolean
  /** The formatting tool. */
  canFormatText?: boolean
  /** The biggest file that may be sent, in bytes; bigger ones are refused with a message. */
  maxUploadBytes?: number
  /** The kinds of file that may be sent, as an <input accept> ("image/*,.pdf"). */
  acceptedFileTypes?: string
  /** The app's own, by name: can("export") is false when extra.export is false. */
  extra?: Record<string, boolean>
}

/** The names can() knows (the yes/no ones). */
type ChatCapability = {
  [K in keyof ChatCapabilities]-?: NonNullable<
    ChatCapabilities[K]
  > extends boolean
    ? K
    : never
}[keyof ChatCapabilities]

/** What state the conversation is in. */
type ChatState = {
  muted?: boolean
  /** Muted until then; left out, until turned back on. */
  mutedUntil?: Date
  blocked?: boolean
  archived?: boolean
  /** Nobody can write here; the reason replaces the box to write in (e.g. "Only admins can send messages"). */
  readOnly?: { reason: string } | false
  /** Writing works but Send is off for now; the reason is Send's tooltip. */
  sendDisabledReason?: string
}

/** A reaction people can put on messages: an emoji, or the app's own icon, with its name. */
type ReactionOption = {
  id: string
  emoji?: string
  icon?: React.ReactNode
  label: string
}

/** What may be done to one message (resolved from the conversation's rules, the app's and the message's). */
type ChatMessageCapabilities = {
  canReplyTo: boolean
  canReact: boolean
  canEdit: boolean
  /** How long after sending it can still be edited, in ms (left out: always). */
  editDuration?: number
  canForward: boolean
  canCopy: boolean
  canPin: boolean
  canDelete: boolean
  canReport: boolean
  /** How long after sending one's own message can be taken back for everyone, in ms (0 or left out: never). */
  unsendDuration?: number
  /** The reactions offered for this message (ids of ChatConfig.reactions, or options); empty: none. */
  allowedReactions?: (string | ReactionOption)[]
}

// --- actions: what the app does when people do something ----------------------------------------------

/** Every handler in one place. Each one given turns its action on (where the rules allow it). */
type ChatActions = {
  onSend?: (message: {
    text: string
    files: File[]
    replyTo?: string
  }) => void | Promise<void>
  onEdit?: (message: ChatMessage, text: string) => void | Promise<void>
  onUnsend?: (message: ChatMessage) => void | Promise<void>
  onDelete?: (message: ChatMessage) => void
  onReact?: (message: ChatMessage, reactionId: string) => void
  onReply?: (message: ChatMessage) => void
  onForward?: (message: ChatMessage) => void
  onPin?: (message: ChatMessage, pinned: boolean) => void
  onRetry?: (message: ChatMessage) => void
  onReport?: (target: ChatMessage | Conversation) => void
  /** Pays for and opens a paywalled message; throws (with a message) when it fails. */
  onUnlock?: (message: ChatMessage) => Promise<void>
  onOpenAttachment?: (
    message: ChatMessage,
    attachment: ChatAttachment | LinkPreview
  ) => void
  onMuteChange?: (muted: boolean, until?: Date) => void
  onBlock?: (blocked: boolean) => void
  onOpenProfile?: () => void
  /** Loads the messages before the first one shown (scrolling up); throws when it fails (Try again shows). */
  onLoadOlder?: () => void | Promise<void>
  onSearch?: () => void
  onTyping?: () => void
  onMarkRead?: (message: ChatMessage) => void
  /** A conversation's tags changed (its menu, or the details panel). */
  onTagsChange?: (conversation: Conversation, tags: ChatTag[]) => void
}

// --- the conversation list: categories and pill types ---------------------------------------------------

/** A filter above the list ("Unread", "VIP"). Without `match` the app filters the data it passes itself. */
type ChatCategory = {
  id: string
  label: string
  icon?: React.ReactNode
  match?: (conversation: Conversation) => boolean
  /** Show how many conversations match (needs `match`). */
  count?: boolean
}

/** A kind of tag people can put on a conversation, and how it looks. */
type PillType = {
  id: string
  label: string
  colour?: PillColor
  icon?: React.ReactNode
  /** Only the icon shows (the label is its tooltip). */
  iconOnly?: boolean
  /** A group: only one pill of the group at a time (a status such as new / regular / lapsed). */
  exclusive?: string
  /** People may add or take it off (false: the app sets it, e.g. "Tipped"). True by default. */
  editable?: boolean
}

/** Ready-made categories. */
const chatCategories = {
  all: { id: "all", label: "All" } satisfies ChatCategory,
  unread: {
    id: "unread",
    label: "Unread",
    match: (c: Conversation) => (c.unread ?? 0) > 0 || !!c.markedUnread,
    count: true,
  } satisfies ChatCategory,
  pinned: {
    id: "pinned",
    label: "Pinned",
    match: (c: Conversation) => !!c.pinned,
  } satisfies ChatCategory,
  muted: {
    id: "muted",
    label: "Muted",
    match: (c: Conversation) => !!c.muted,
  } satisfies ChatCategory,
  archived: {
    id: "archived",
    label: "Archived",
    match: (c: Conversation) => !!c.archived,
  } satisfies ChatCategory,
  groups: {
    id: "groups",
    label: "Groups",
    match: (c: Conversation) => !!c.group,
  } satisfies ChatCategory,
  /** Conversations with a tag. */
  tagged: (
    id: string,
    label: string,
    icon?: React.ReactNode
  ): ChatCategory => ({
    id: `tag-${id}`,
    label,
    icon,
    match: (c) => !!c.tags?.some((t) => t.id === id),
    count: true,
  }),
}

// --- menus, render slots, labels, features -------------------------------------------------------------

/** Changes a menu: gets the default items, returns the ones to show (add, take away, reorder). */
type MenuChange<T extends unknown[] = []> = (
  defaults: MenuEntry[],
  ...about: T
) => MenuEntry[]

type ChatMenus = {
  message?: MenuChange<[message: ChatMessage, caps: ChatMessageCapabilities]>
  conversation?: MenuChange<[conversation: Conversation]>
  header?: MenuChange
  /** The empty part of the message list. */
  space?: MenuChange
}

type ChatRender = {
  /** Under a message's bubble (a tip button, a poll, quick replies, an order). */
  below?: (message: ChatMessage) => React.ReactNode
  /** Above it. */
  above?: (message: ChatMessage) => React.ReactNode
  /** The app's own kinds of attachment ({ kind: "custom", type: "order" }), by type. */
  attachment?: Record<
    string,
    (
      attachment: Extract<ChatAttachment, { kind: "custom" }>,
      message: ChatMessage
    ) => React.ReactNode
  >
  linkPreview?: (link: LinkPreview, message: ChatMessage) => React.ReactNode
  /** An empty conversation. */
  empty?: () => React.ReactNode
  systemMessage?: (message: ChatMessage) => React.ReactNode
}

/** Every word the chat shows, in plain English by default; give your own to change or translate them. */
type ChatLabels = typeof DEFAULT_LABELS

const DEFAULT_LABELS = {
  placeholder: "Type a message",
  typing: "typing…",
  someoneTyping: "{name} is typing…",
  notSent: "Not sent.",
  tryAgain: "Click to try again.",
  edited: "Edited",
  emptyChat: "No messages yet. Say hello!",
  newMessages: "New messages",
  unlockFor: "Unlock for {price}",
  unlocking: "Unlocking…",
  lockedContent: "Locked content",
  reply: "Reply",
  react: "React…",
  copyText: "Copy text",
  edit: "Edit",
  unsend: "Unsend",
  unsendLeft: "Unsend ({time} left)",
  forward: "Forward…",
  pin: "Pin",
  unpin: "Unpin",
  delete: "Delete…",
  deleteForMe: "Delete for me…",
  report: "Report…",
  loadingOlder: "Loading older messages…",
  tip: "Tip",
  tipPreview: "💸 Tipped {amount}",
  tipAnnounce: "{name} sent you a {amount} tip",
  tipAnnounceMine: "You sent a {amount} tip",
  thank: "Thank {name}",
  thankText: "Thank you so much, {name}! 💛",
  copyAmount: "Copy amount",
  sendTip: "Send a tip",
}

/** On/off switches for whole features. */
type ChatFeatures = {
  reactions: boolean
  replies: boolean
  /** Reserved: threads are not built yet; turning it on changes nothing today. */
  threads: boolean
  readReceipts: boolean
  typingIndicator: boolean
  linkPreviews: boolean
  voiceNotes: boolean
  detailsPanel: boolean
  swipe: boolean
  search: boolean
  /** The "done" sound when a tip arrives. */
  tipSound: boolean
}

const DEFAULT_FEATURES: ChatFeatures = {
  reactions: true,
  replies: true,
  threads: false,
  readReceipts: true,
  typingIndicator: true,
  linkPreviews: true,
  voiceNotes: true,
  detailsPanel: true,
  swipe: true,
  search: true,
  tipSound: true,
}

const DEFAULT_REACTIONS: ReactionOption[] = [
  { id: "👍", emoji: "👍", label: "Like" },
  { id: "❤️", emoji: "❤️", label: "Love" },
  { id: "😂", emoji: "😂", label: "Laugh" },
  { id: "😮", emoji: "😮", label: "Wow" },
  { id: "😢", emoji: "😢", label: "Sad" },
  { id: "🙏", emoji: "🙏", label: "Thanks" },
]

// --- the configuration ---------------------------------------------------------------------------------

/** Everything about how the chat behaves, in one typed object the app fills in. Data stays the app's. */
type ChatConfig = {
  /** The person using the app (their messages are on the right). */
  me: ChatPerson
  capabilities?: ChatCapabilities
  /** The current conversation's state (muted, blocked, read-only). */
  state?: ChatState
  /** Per-message rules the app works out (e.g. who may unsend, which reactions a message takes). */
  getMessageCapabilities?: (
    message: ChatMessage,
    me: ChatPerson
  ) => Partial<ChatMessageCapabilities>
  /** The reactions on offer and how each looks (six emoji by default). */
  reactions?: ReactionOption[]
  composer?: {
    /** The row of tools under the box; left out, the usual ones (defaultComposerTools); [] for none. */
    tools?: ChatComposerTool[]
    /** What the usual tools need (people to mention, GIF search, tips…), when `tools` is left out. */
    toolOptions?: DefaultToolsOptions
    sendKey?: "enter" | "mod+enter"
    maxLength?: number
    placeholder?: string
    accept?: string
    maxUploadBytes?: number
  }
  actions?: ChatActions
  menus?: ChatMenus
  render?: ChatRender
  swipe?: {
    left?: (conversation: Conversation) => SwipeAction[]
    right?: (conversation: Conversation) => SwipeAction[]
  }
  /** @deprecated use pillTypes */
  tags?: ChatTag[]
  /** The filters above the conversation list. */
  categories?: ChatCategory[]
  /** The kinds of tag people can put on conversations (rows show them in this order). */
  pillTypes?: PillType[]
  /** People may type a tag of their own ("Add tag…"). */
  allowCustomTags?: boolean
  labels?: Partial<ChatLabels>
  features?: Partial<ChatFeatures>
}

/** The configuration as the components read it: defaults filled in, and helpers. */
type ChatApi = Omit<ChatConfig, "labels" | "features" | "reactions"> & {
  capabilities: ChatCapabilities
  state: ChatState
  actions: ChatActions
  labels: ChatLabels
  features: ChatFeatures
  reactions: ReactionOption[]
  /** Whether something is allowed (built-in names, or the app's own from `extra`). */
  can: (what: ChatCapability | (string & {})) => boolean
  /** Why nothing can be sent here, if so: read-only, blocked, or not allowed to send. */
  readOnlyReason?: string
  /** A label with its {placeholders} filled in. */
  say: (key: keyof ChatLabels, values?: Record<string, string>) => string
  /** What may be done to a message. */
  messageCapabilities: (message: ChatMessage) => ChatMessageCapabilities
}

/** Gives a config object its type (for type checking and completion); returns it unchanged. */
function defineChatConfig(config: ChatConfig): ChatConfig {
  return config
}

const NOBODY: ChatPerson = { id: "me", name: "You" }

/** Fills in the defaults and makes the helpers. */
function resolveChat(config: Partial<ChatConfig>): ChatApi {
  const capabilities = config.capabilities ?? {}
  const state = config.state ?? {}
  const labels = { ...DEFAULT_LABELS, ...config.labels }
  const features = { ...DEFAULT_FEATURES, ...config.features }
  const me = config.me ?? NOBODY
  const can = (what: string) => {
    if (what === "deleteAny") return capabilities.deleteAny === true
    if (what === "sendTip") return capabilities.sendTip === true
    if (what in capabilities && what !== "extra")
      return capabilities[what as ChatCapability] !== false
    return capabilities.extra?.[what] !== false
  }
  const readOnlyReason = state.readOnly
    ? state.readOnly.reason
    : state.blocked
      ? "You blocked this contact. Unblock them to send messages."
      : !can("send")
        ? "You can't send messages in this conversation."
        : undefined
  const say = (key: keyof ChatLabels, values: Record<string, string> = {}) =>
    labels[key].replace(
      /\{(\w+)\}/g,
      (_, name: string) => values[name] ?? `{${name}}`
    )

  const messageCapabilities = (
    message: ChatMessage
  ): ChatMessageCapabilities => {
    const mine = message.senderId === me.id
    const writable = !readOnlyReason
    const locked = !!message.paywall && !message.paywall.unlocked
    const base: ChatMessageCapabilities = {
      canReplyTo: features.replies && can("reply") && writable,
      canReact: features.reactions && can("react") && !state.blocked,
      canEdit: mine && can("edit") && writable && !!message.text,
      canForward: can("forward"),
      canCopy: can("copy"),
      canPin: can("pin"),
      canDelete: mine ? can("delete") : can("deleteAny"),
      canReport: !mine && can("report"),
    }
    const resolved = {
      ...base,
      ...config.getMessageCapabilities?.(message, me),
      ...message.capabilities,
    }
    // A message still locked behind a paywall can't be copied or forwarded.
    if (locked) {
      resolved.canCopy = false
      resolved.canForward = false
    }
    // Past its edit time, it can't be changed any more.
    if (
      resolved.editDuration &&
      Date.now() - new Date(message.sentAt).getTime() > resolved.editDuration
    )
      resolved.canEdit = false
    return resolved
  }

  return {
    ...config,
    me,
    capabilities,
    state,
    actions: config.actions ?? {},
    labels,
    features,
    reactions: config.reactions ?? DEFAULT_REACTIONS,
    can,
    readOnlyReason,
    say,
    messageCapabilities,
  }
}

/** How long is left, in words: "4 min", "45 s", "2 h". */
function timeLeft(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000))
  if (s < 60) return `${s} s`
  const m = Math.round(s / 60)
  return m < 60 ? `${m} min` : `${Math.round(m / 60)} h`
}

export {
  chatCategories,
  DEFAULT_FEATURES,
  DEFAULT_LABELS,
  DEFAULT_REACTIONS,
  defineChatConfig,
  resolveChat,
  timeLeft,
}
export type {
  ChatActions,
  ChatApi,
  ChatCapabilities,
  ChatCapability,
  ChatCategory,
  ChatConfig,
  ChatFeatures,
  ChatLabels,
  ChatMenus,
  ChatMessageCapabilities,
  ChatRender,
  ChatState,
  MenuChange,
  PillType,
  ReactionOption,
}
