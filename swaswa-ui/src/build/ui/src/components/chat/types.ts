import type { ReactNode } from "react"

import type { PillColor } from "../pill"
import type { MinorUnits } from "../money"
import type { ChatCapabilities, ChatMessageCapabilities } from "./config"

// What the chat components show: people, conversations and messages. Plain data, so any chat app (and any
// server) can fill them.

/** Someone in a chat. */
type ChatPerson = {
  id: string
  name: string
  /** A picture; without one, their initials. */
  avatar?: string
  online?: boolean
  /** When they were last online (for "Last seen 5 min ago"). */
  lastSeen?: Date | string
  /** The app's or the service's own fields. */
  meta?: Record<string, unknown>
}

/** Where one's own message is on its way: being sent, on the server, on their device, seen, or not sent. */
type DeliveryState = "sending" | "sent" | "delivered" | "read" | "failed"

/** Something sent with a message. */
type ChatAttachment =
  | {
      kind: "image"
      url: string
      name?: string
      width?: number
      height?: number
    }
  | { kind: "file"; name: string; size?: number; url?: string }
  | { kind: "voice"; duration: number; url?: string }
  /** Money sent to someone (a tip): shown as its own card. The sender is the message's sender. */
  | { kind: "tip"; amount: MinorUnits; currency: string; note?: string }
  /** The app's own kind (an order, a poll): drawn by ChatConfig.render.attachment[type]. */
  | { kind: "custom"; type: string; data: Record<string, unknown> }

/** The card a link in a message gets: its page's title, a line about it, a picture. */
type LinkPreview = {
  url: string
  title: string
  description?: string
  image?: string
  site?: string
}

/** An emoji people put on a message, how many did, and whether one of them is you. */
type Reaction = { emoji: string; count: number; mine?: boolean }

type ChatMessage = {
  id: string
  /** Who sent it (a ChatPerson's id). */
  senderId: string
  /** "system": a notice in the middle of the chat ("Ana joined"), not from anyone. */
  kind?: "message" | "system"
  text?: string
  /** A piece of code shown coloured (as in a code block). */
  code?: { code: string; language?: string; fileName?: string }
  sentAt: Date | string
  /** One's own messages: how far it got. */
  state?: DeliveryState
  edited?: boolean
  /** The id of the message this answers (shown quoted above it). */
  replyTo?: string
  reactions?: Reaction[]
  attachments?: ChatAttachment[]
  link?: LinkPreview
  pinned?: boolean
  /** Paid content: shown blurred, hidden or as a teaser until unlocked. */
  paywall?: ChatPaywall
  /** Rules for this message only (over the conversation's and the app's). */
  capabilities?: Partial<ChatMessageCapabilities>
  /** Extra content under or above the bubble, on its side (a tip button, quick replies). */
  below?: ReactNode
  above?: ReactNode
  /** The id the UI gave it while sending (matches the stored message to the "sending" one). */
  clientId?: string
  /** The app's or the service's own fields. */
  meta?: Record<string, unknown>
}

/** A price on a message's content (pay-per-view). */
type ChatPaywall = {
  price: { amount: MinorUnits; currency: string }
  /** What shows while locked: the pictures blurred (the default), nothing but a lock, or a teaser text. */
  preview?: "blur" | "hidden" | "teaser"
  teaserText?: string
  unlocked: boolean
  /** Pays and unlocks (or ChatConfig.actions.onUnlock); throws with a message when it fails. */
  onUnlock?: (message: ChatMessage) => Promise<void>
}

/** A label put on a conversation or a person ("VIP", "Tipped"), shown as a Pill. */
type ChatTag = {
  id: string
  label: string
  color?: PillColor
  icon?: ReactNode
}

/** A row in the list of conversations. */
type Conversation = {
  id: string
  title: string
  avatar?: string
  online?: boolean
  /** The newest message, for the line under the name. */
  lastMessage?: {
    text?: string
    fromMe?: boolean
    attachment?: "photo" | "file" | "voice" | "tip"
    /** The tip, when the last message was one. */
    tip?: { amount: MinorUnits; currency: string }
    at: Date | string
  }
  /** Someone is typing there now: true, or who ("Ana"). */
  typing?: boolean | string
  unread?: number
  /** Marked as unread by hand (a dot without a number). */
  markedUnread?: boolean
  muted?: boolean
  pinned?: boolean
  /** Text written there and not sent yet. */
  draft?: string
  /** Muted until then (the row says so on hover). */
  mutedUntil?: Date
  tags?: ChatTag[]
  archived?: boolean
  /** A group conversation (more than two people). */
  group?: boolean
  /** What may be done to this conversation from the list (left out: everything). */
  capabilities?: ChatCapabilities
  /** The people in it besides me (for presence and the details panel). */
  people?: ChatPerson[]
  /** A line under the name in the details (a handle, a role). */
  subtitle?: string
  /** The app's or the service's own fields. */
  meta?: Record<string, unknown>
}

/** The same as Conversation (the name the config documents). */
type ChatConversation = Conversation

export type {
  ChatAttachment,
  ChatConversation,
  ChatPaywall,
  ChatTag,
  ChatMessage,
  ChatPerson,
  Conversation,
  DeliveryState,
  LinkPreview,
  Reaction,
}
