import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Checkbox } from "./checkbox"
import { Surface } from "./surface"
import { Switch } from "./switch"

function Settings() {
  return (
    <Surface
      title="Notifications"
      style={{ width: 300, display: "grid", gap: 12 }}
    >
      <Switch label="New messages" defaultChecked />
      <Switch label="Tips" defaultChecked />
      <Checkbox label="Play a sound" />
    </Surface>
  )
}

const meta = {
  title: "Native/Surface",
  component: Settings,
  decorators: [bothPlatforms],
} satisfies Meta<typeof Settings>
export default meta
type Story = StoryObj<typeof meta>

export const WithControls: Story = {}
export const WithControlsDark: Story = { parameters: dark }
