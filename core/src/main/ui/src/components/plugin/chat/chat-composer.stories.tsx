import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../../stories/side-by-side"
import {
  type ChatComposerTool,
  composerTools,
  defaultComposerTools,
} from "../../composer-toolbar"
import { Text } from "../../text"
import { ChatComposer } from "./chat-composer"
import { ChatProvider } from "./chat-provider"
import type { ChatConfig } from "./config"
import { insertPriceTool, storyConfig } from "./sample-config"

/**
 * The chat's composer on the shared config (the same one ChatWindow uses): with no `tools`, the usual set.
 * A story only overrides fields of that config.
 */
function WithBars({
  mode = "none",
  tools,
  sendTip,
}: {
  mode?: "reply" | "edit" | "none"
  tools?: ChatComposerTool[]
  sendTip?: boolean
}) {
  const [now, setNow] = React.useState(mode)
  const [last, setLast] = React.useState("Nothing sent yet.")
  const config: ChatConfig = {
    ...storyConfig,
    capabilities: sendTip === undefined ? storyConfig.capabilities : { ...storyConfig.capabilities, sendTip },
    composer: { ...storyConfig.composer, tools },
  }
  return (
    <ChatProvider config={config}>
      <div style={{ display: "grid", gap: 8, width: "100%" }}>
        <ChatComposer
          aria-label="Message Ana Reyes"
          placeholder="Message Ana Reyes"
          replyingTo={
            now === "reply"
              ? {
                  name: "Ana Reyes",
                  text: "Can you send the ones by the window too?",
                }
              : undefined
          }
          onCancelReply={() => setNow("none")}
          editing={
            now === "edit"
              ? { id: "m2", text: "Almost! Editing the last few now." }
              : undefined
          }
          onCancelEdit={() => setNow("none")}
          onSend={(m) => {
            setLast(
              `${now === "edit" ? "Saved" : now === "reply" ? "Replied" : "Sent"}: ${m.text}`
            )
            setNow("none")
          }}
        />
        <Text variant="footnote" tone="secondary">
          {last}
        </Text>
      </div>
    </ChatProvider>
  )
}

const meta = {
  title: "Chat/ChatComposer",
  component: WithBars,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof WithBars>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeStretch: true, ...dark } }
const withPrice = (): ChatComposerTool[] => [
  ...defaultComposerTools(storyConfig.composer?.toolOptions),
  insertPriceTool,
]

/** The config gives no tools: the usual set. */
export const DefaultTools: Story = {}
export const DefaultToolsDark: Story = darkToo
export const MinimalTools: Story = {
  args: { tools: [composerTools.attach(), composerTools.emoji()] },
}
/** The usual set plus an app's own tool ("Insert price"). */
export const AppTool: Story = { args: { tools: withPrice(), sendTip: true } }
export const AppToolDark: Story = { args: { tools: withPrice(), sendTip: true }, ...darkToo }
export const NoTools: Story = { args: { tools: [] } }
export const Replying: Story = { args: { mode: "reply" } }
export const Editing: Story = { args: { mode: "edit" } }
