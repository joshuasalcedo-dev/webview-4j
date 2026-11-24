import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../stories/side-by-side"
import { MessageBubble, TypingIndicator } from "./message-bubble"
import { people, picture } from "./sample"
import type { ChatMessage } from "./types"

const now = new Date()
const base: ChatMessage = {
  id: "x",
  senderId: "ana",
  text: "Are the photos ready?",
  sentAt: now,
}

/** One of each kind, theirs and yours; hover for the bar, right-click for the menu, click a reaction. */
function Kinds() {
  const [mine, setMine] = React.useState<ChatMessage>({
    id: "a",
    senderId: "me",
    text: "Yes! Sending them now.",
    sentAt: now,
    state: "read",
    reactions: [
      { emoji: "👍", count: 2, mine: true },
      { emoji: "❤️", count: 1 },
    ],
  })
  const react = (emoji: string) =>
    setMine((m) => {
      const has = m.reactions?.find((r) => r.emoji === emoji)
      const reactions = has
        ? (m.reactions ?? [])
            .map((r) =>
              r.emoji === emoji
                ? { ...r, mine: !r.mine, count: r.count + (r.mine ? -1 : 1) }
                : r
            )
            .filter((r) => r.count > 0)
        : [...(m.reactions ?? []), { emoji, count: 1, mine: true }]
      return { ...m, reactions }
    })
  const actions = {
    onReply: () => {},
    onForward: () => {},
    onPin: () => {},
    onDelete: () => {},
  }
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        gap: 4,
      }}
    >
      <MessageBubble
        message={base}
        mine={false}
        sender={people.ana}
        showAvatar
        showName
        {...actions}
        onReact={() => {}}
      />
      <MessageBubble
        message={mine}
        mine
        sender={people.me}
        {...actions}
        onReact={(_, e) => react(e)}
        onEdit={() => {}}
      />
      <MessageBubble
        message={{
          id: "b",
          senderId: "me",
          text: "Here they are",
          sentAt: now,
          state: "delivered",
          replyTo: "x",
        }}
        mine
        quoted={{ message: base, sender: people.ana, mine: false }}
        {...actions}
      />
      <MessageBubble
        message={{
          id: "c",
          senderId: "ana",
          sentAt: now,
          attachments: [
            { kind: "image", url: picture(200, "Beach") },
            { kind: "image", url: picture(30, "Window") },
          ],
        }}
        mine={false}
        sender={people.ana}
        showAvatar
      />
      <MessageBubble
        message={{
          id: "d",
          senderId: "ana",
          sentAt: now,
          attachments: [{ kind: "voice", duration: 14 }],
        }}
        mine={false}
        sender={people.ana}
        showAvatar
      />
      <MessageBubble
        message={{
          id: "e",
          senderId: "me",
          sentAt: now,
          state: "sent",
          attachments: [{ kind: "file", name: "contract.pdf", size: 482_000 }],
        }}
        mine
      />
      <MessageBubble
        message={{
          id: "f",
          senderId: "me",
          text: "Sending this…",
          sentAt: now,
          state: "sending",
        }}
        mine
      />
      <MessageBubble
        message={{
          id: "g",
          senderId: "me",
          text: "This one didn't go through.",
          sentAt: now,
          state: "failed",
        }}
        mine
        onRetry={() => {}}
      />
      <MessageBubble
        message={{
          id: "h",
          senderId: "ana",
          text: "Changed my mind",
          sentAt: now,
          edited: true,
          pinned: true,
        }}
        mine={false}
        sender={people.ana}
        showAvatar
      />
      <TypingIndicator people={[people.ana]} />
    </div>
  )
}

const meta = {
  title: "Chat/MessageBubble",
  component: Kinds,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Kinds>
export default meta
type Story = StoryObj<typeof meta>

export const EveryKind: Story = {}
export const EveryKindDark: Story = {
  parameters: { nativeStretch: true, ...dark },
}
