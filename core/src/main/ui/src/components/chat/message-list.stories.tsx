import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../stories/side-by-side"
import { ChatProvider, useChat } from "./chat-provider"
import type { ChatConfig } from "./config"
import { MessageList } from "./message-list"
import { people, sampleMessages } from "./sample"
import { oneConversationAdapter, storyConfig } from "./sample-config"
import {
  samplePaywalls,
  sampleRules,
  sampleSlotConfig,
  sampleSlots,
  sampleTips,
} from "./sample-messages"
import type { ChatMessage } from "./types"

/** The list as the provider gives it (messages from the adapter; actions through the ports). */
function Connected({
  group,
  typing,
  unread,
}: {
  group: boolean
  typing: boolean
  unread?: string
}) {
  const { store } = useChat()
  return (
    <MessageList
      style={{ height: 520, width: "100%" }}
      conversationId="c"
      onReply={() => undefined}
      messages={store.messages.c?.items ?? []}
      people={people}
      group={group}
      unreadFrom={unread}
      typing={typing ? [people.ana, ...(group ? [people.lea] : [])] : []}
    />
  )
}

/** One conversation's messages, on the shared sample adapter and config (a story only overrides fields). */
function Messages({
  initial,
  config = {},
  group = false,
  typing = false,
  unread,
}: {
  initial: () => ChatMessage[]
  config?: Partial<ChatConfig>
  group?: boolean
  typing?: boolean
  unread?: string
}) {
  const [adapter] = React.useState(() =>
    oneConversationAdapter(initial(), { group })
  )
  React.useEffect(() => () => adapter.dispose(), [adapter])
  return (
    <ChatProvider
      adapter={adapter}
      config={{ ...storyConfig, ...config }}
      defaultOpenId="c"
    >
      <Connected group={group} typing={typing} unread={unread} />
    </ChatProvider>
  )
}

const meta = {
  title: "Chat/MessageList",
  component: Messages,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
  args: { initial: sampleMessages },
} satisfies Meta<typeof Messages>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeChrome: true, ...dark } }

export const OneToOne: Story = {}
export const OneToOneDark: Story = darkToo
export const Group: Story = { args: { group: true, typing: true } }
export const NewMessagesLine: Story = { args: { unread: "m9" } }
export const Tips: Story = { args: { initial: sampleTips } }
export const TipsDark: Story = { args: { initial: sampleTips }, ...darkToo }
/** Locked photos: blurred, hidden or a teaser; unlock (the second one fails once, then Try again works). */
export const Paywall: Story = { args: { initial: samplePaywalls } }
export const PaywallDark: Story = {
  args: { initial: samplePaywalls },
  ...darkToo,
}
/** Limited reactions, no reactions, no reply, and Unsend within its 15 minutes or past them. */
export const PerMessageRules: Story = {
  args: {
    initial: sampleRules,
    config: { getMessageCapabilities: () => ({ unsendDuration: 15 * 60_000 }) },
  },
}
export const Slots: Story = {
  args: { initial: sampleSlots, config: sampleSlotConfig },
}
export const Empty: Story = { args: { initial: () => [] } }
