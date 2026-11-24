import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../stories/side-by-side"
import { useDialogs } from "../dialog"
import { TitleBar } from "../title-bar"
import { ChatHeader } from "./chat-header"
import { ChatProvider, useChat } from "./chat-provider"
import type { ChatState } from "./config"
import { conversationHeaderMenu } from "./header-menu"
import { seedConversations, storyAdapter, storyConfig } from "./sample-config"

/** The header of a shared sample conversation, with the same right-click menu ChatWindow gives it. */
function Header({
  id = "ana",
  typing,
  inTitleBar = false,
}: {
  id?: string
  typing?: boolean
  inTitleBar?: boolean
}) {
  const chat = useChat()
  const dialogs = useDialogs()
  const c =
    chat.store.conversations.find((x) => x.id === id) ??
    seedConversations().find((x) => x.id === id)
  const [details, setDetails] = React.useState(false)
  if (!c) return null
  const header = (
    <ChatHeader
      title={c.title}
      online={c.online}
      lastSeen={c.people?.[0]?.lastSeen}
      typing={typing}
      status={c.group ? `${(c.people?.length ?? 0) + 1} people` : undefined}
      tags={c.tags}
      onSearch={() => undefined}
      onCall={() => undefined}
      onVideoCall={() => undefined}
      onInfo={() => setDetails(!details)}
      infoOpen={details}
      onOpenInfo={() => setDetails(true)}
      menu={() =>
        conversationHeaderMenu(c, chat, dialogs, {
          onOpenDetails: () => setDetails(true),
        })
      }
    />
  )
  return inTitleBar ? <TitleBar title="Chat">{header}</TitleBar> : header
}

/** The shared adapter and config; a story only overrides a field (the state). */
function WithProvider(props: {
  id?: string
  typing?: boolean
  inTitleBar?: boolean
  state?: ChatState
}) {
  const [adapter] = React.useState(() => storyAdapter({ simulate: null }))
  React.useEffect(() => () => adapter.dispose(), [adapter])
  return (
    <ChatProvider
      adapter={adapter}
      config={{ ...storyConfig, state: props.state }}
      defaultOpenId={props.id ?? "ana"}
    >
      <Header {...props} />
    </ChatProvider>
  )
}

const meta = {
  title: "Chat/ChatHeader",
  component: WithProvider,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof WithProvider>
export default meta
type Story = StoryObj<typeof meta>

export const Online: Story = {}
export const OnlineDark: Story = { parameters: { nativeChrome: true, ...dark } }
export const LastSeen: Story = { args: { id: "marco" } }
export const Typing: Story = { args: { typing: true } }
export const Group: Story = { args: { id: "team" } }
export const Muted: Story = { args: { state: { muted: true } } }
/** In the window's title bar: one row with the window's buttons. */
export const InTheTitleBar: Story = { args: { inTitleBar: true } }
