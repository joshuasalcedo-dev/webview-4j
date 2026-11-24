import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../stories/side-by-side"
import { InspectorSection } from "../inspector"
import { Money } from "../money"
import { Switch } from "../switch"
import { Text } from "../text"
import { ChatWindow } from "./chat-window"
import type { ChatConfig } from "./config"
import { storyAdapter, storyConfig } from "./sample-config"

/** A whole chat from one adapter and one config: send, reply, react, edit with ↑, delete, unlock, tags, details. */
function Window({
  config = storyConfig,
  detailsOpen,
  overlayBelow,
  sections = false,
}: {
  config?: ChatConfig
  detailsOpen?: boolean
  overlayBelow?: number
  sections?: boolean
}) {
  // One adapter per window, kept for the story's life.
  const [adapter] = React.useState(() => storyAdapter())
  React.useEffect(() => () => adapter.dispose(), [adapter])
  const [open, setOpen] = React.useState(detailsOpen ?? false)
  return (
    <ChatWindow
      adapter={adapter}
      config={config}
      defaultOpenId="ana"
      detailsOpen={open}
      onDetailsOpenChange={setOpen}
      style={{ height: 640, width: "100%" }}
      detailsOverlayBelow={overlayBelow}
      detailsSections={
        sections ? (
          <InspectorSection title="Subscription">
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "auto 1fr",
                gap: "4px 12px",
              }}
            >
              <Text tone="secondary">Plan</Text>
              <Text>Monthly</Text>
              <Text tone="secondary">Since</Text>
              <Text>12 March 2026</Text>
              <Text tone="secondary">Tips in total</Text>
              <Money amount={1_245_000} currency="PHP" />
            </div>
          </InspectorSection>
        ) : undefined
      }
    />
  )
}

/** The same window, with the config changed live by a few switches. */
function LiveConfig() {
  const [readOnly, setReadOnly] = React.useState(false)
  const [reactions, setReactions] = React.useState(true)
  const [protectedChat, setProtected] = React.useState(false)
  const [tools, setTools] = React.useState(true)
  const [tips, setTips] = React.useState(false)
  const config: ChatConfig = {
    ...storyConfig,
    capabilities: {
      ...storyConfig.capabilities,
      copy: !protectedChat,
      forward: !protectedChat,
      sendTip: tips,
    },
    state: readOnly
      ? {
          readOnly: {
            reason: "Only admins can send messages in this channel.",
          },
        }
      : undefined,
    features: { reactions },
    // Tools on: the shared config (no `tools`, so the usual set); off: none.
    composer: { ...storyConfig.composer, tools: tools ? undefined : [] },
  }
  return (
    <div style={{ display: "grid", gap: 10, width: "100%" }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 16 }}>
        <Switch
          label="Read-only"
          checked={readOnly}
          onChange={(e) => setReadOnly(e.target.checked)}
        />
        <Switch
          label="Reactions"
          checked={reactions}
          onChange={(e) => setReactions(e.target.checked)}
        />
        <Switch
          label="Protected (no copy)"
          checked={protectedChat}
          onChange={(e) => setProtected(e.target.checked)}
        />
        <Switch
          label="Composer tools"
          checked={tools}
          onChange={(e) => setTools(e.target.checked)}
        />
        <Switch
          label="Tips"
          checked={tips}
          onChange={(e) => setTips(e.target.checked)}
        />
      </div>
      <Window config={config} />
    </div>
  )
}

const meta = {
  title: "Chat/ChatWindow",
  component: Window,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof Window>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeChrome: true, ...dark } }

export const Working: Story = {}
export const WorkingDark: Story = darkToo
export const WithDetails: Story = {
  args: { detailsOpen: true, sections: true },
}
export const WithDetailsDark: Story = {
  args: { detailsOpen: true, sections: true },
  ...darkToo,
}
/** A narrow window: the details lie over the conversation. */
export const NarrowWithDetails: Story = {
  args: { detailsOpen: true, overlayBelow: 1200 },
}
export const ReadOnlyChannel: Story = {
  args: {
    config: {
      ...storyConfig,
      state: {
        readOnly: { reason: "Only admins can send messages in this channel." },
      },
    },
  },
}
export const Muted: Story = {
  args: {
    config: {
      ...storyConfig,
      state: { muted: true, mutedUntil: new Date(Date.now() + 8 * 3_600_000) },
    },
  },
}
export const BlockedContact: Story = {
  args: { config: { ...storyConfig, state: { blocked: true } } },
}
/** A moderator can delete anyone's messages (right-click a message from Ana). */
export const Moderator: Story = {
  args: {
    config: {
      ...storyConfig,
      capabilities: { ...storyConfig.capabilities, deleteAny: true },
    },
  },
}
export const ConfigChangedLive: Story = { render: () => <LiveConfig /> }
