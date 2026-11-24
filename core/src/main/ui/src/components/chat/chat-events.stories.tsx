import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms } from "../../stories/side-by-side"
import { Button } from "../button"
import { useSound } from "../sound"
import { Text } from "../text"
import { ChatProvider, useOnUnread, useUnread } from "./chat-provider"
import { ChatWindow } from "./chat-window"
import type { ChatOutboundEvent } from "./ports"
import { people } from "./sample"
import { storyAdapter, storyConfig } from "./sample-config"

/** The unread total and the notification listener (it fires only for chats that are not open and visible). */
function Notifications() {
  const sound = useSound()
  const total = useUnread()
  const [heard, setHeard] = React.useState<string[]>([])
  useOnUnread((event) => {
    sound.play("message")
    setHeard((h) =>
      [
        `${event.conversationId}: ${event.message.text ?? "attachment"}`,
        ...h,
      ].slice(0, 4)
    )
  })
  return (
    <div className="nt-events-notes" data-testid="notifications">
      <Text variant="headline">Unread: {total}</Text>
      <Text variant="caption" tone="secondary">
        Notified: {heard.length ? heard.join(" · ") : "nothing yet"}
      </Text>
    </div>
  )
}

/**
 * A fake service: buttons put inbound events through (a message in another chat, someone typing, a read
 * receipt, presence), and every outbound event (typing, opening chats, seeing messages) shows in the log.
 */
function Events() {
  const [adapter] = React.useState(() => storyAdapter({ simulate: null }))
  React.useEffect(() => () => adapter.dispose(), [adapter])
  const [log, setLog] = React.useState<string[]>([])
  const onEvent = React.useCallback((event: ChatOutboundEvent) => {
    const { type, ...rest } = event
    setLog((l) =>
      [
        `${new Date().toLocaleTimeString()}  ${type}  ${JSON.stringify(rest)}`,
        ...l,
      ].slice(0, 40)
    )
  }, [])
  let next = 0
  const say = (
    conversationId: string,
    personId: keyof typeof people,
    text: string
  ) =>
    adapter.inject({
      type: "message.received",
      conversationId,
      message: {
        id: `inj-${Date.now()}-${next++}`,
        senderId: personId,
        text,
        sentAt: new Date(),
      },
    })

  return (
    <ChatProvider
      adapter={adapter}
      config={storyConfig}
      onEvent={onEvent}
      defaultOpenId="ana"
    >
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(0, 1fr) 280px",
          gap: 12,
          width: "100%",
        }}
      >
        <ChatWindow style={{ height: 600 }} />
        <div
          style={{
            display: "grid",
            gridTemplateRows: "auto auto minmax(0, 1fr)",
            gap: 8,
            minHeight: 0,
            height: 600,
          }}
        >
          <Notifications />
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
            <Button
              onClick={() => say("kim", "kim", "Hey, are you free Sunday?")}
            >
              Message in Kim's chat
            </Button>
            <Button onClick={() => say("ana", "ana", "One more thing!")}>
              Message in Ana's chat
            </Button>
            <Button
              onClick={() => {
                adapter.inject({
                  type: "typing",
                  conversationId: "ana",
                  person: people.ana,
                  typing: true,
                })
              }}
            >
              Ana starts typing
            </Button>
            <Button
              onClick={() =>
                adapter.inject({
                  type: "typing",
                  conversationId: "ana",
                  person: people.ana,
                  typing: false,
                })
              }
            >
              Ana stops
            </Button>
            <Button
              onClick={() =>
                adapter.inject({
                  type: "presence",
                  personId: "marco",
                  online: true,
                })
              }
            >
              Marco comes online
            </Button>
            <Button
              onClick={() =>
                adapter.inject({
                  type: "message.read",
                  conversationId: "ana",
                  personId: "ana",
                  upToId: "tip-out",
                })
              }
            >
              Ana reads
            </Button>
          </div>
          <pre
            className="nt-events-log"
            data-testid="event-log"
            aria-label="Outbound events"
          >
            {log.length
              ? log.join("\n")
              : "Type, open chats and scroll: outbound events show here."}
          </pre>
        </div>
      </div>
    </ChatProvider>
  )
}

const meta = {
  title: "Chat/Events",
  component: Events,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof Events>
export default meta
type Story = StoryObj<typeof meta>

export const InboundAndOutbound: Story = {}
