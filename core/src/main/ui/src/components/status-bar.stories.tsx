import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { ProgressBar } from "./progress-bar"
import { StatusBar, StatusBarSpacer, StatusDot, StatusItem } from "./status-bar"

type State = "online" | "connecting" | "offline" | "error"

const words: Record<State, string> = {
  online: "Connected",
  connecting: "Connecting…",
  offline: "Offline: messages send when you're back",
  error: "Can't reach the server. Check your connection, then try again.",
}

function Example({
  state = "online",
  syncing = false,
}: {
  state?: State
  syncing?: boolean
}) {
  return (
    <StatusBar>
      <StatusItem icon={<StatusDot state={state} aria-label={words[state]} />}>
        {words[state]}
      </StatusItem>
      <StatusBarSpacer />
      {syncing && (
        <StatusItem>
          Syncing 42 of 120
          <ProgressBar aria-label="Syncing" value={35} style={{ width: 80 }} />
        </StatusItem>
      )}
      <StatusItem>128 chats</StatusItem>
    </StatusBar>
  )
}

const meta = {
  title: "Native/StatusBar",
  component: Example,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const Online: Story = {}
export const OnlineDark: Story = { parameters: { nativeChrome: true, ...dark } }
export const Syncing: Story = { args: { syncing: true } }
export const Connecting: Story = { args: { state: "connecting" } }
export const Offline: Story = { args: { state: "offline" } }
export const CantConnect: Story = { args: { state: "error" } }
