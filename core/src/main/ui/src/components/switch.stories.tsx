import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Switch } from "./switch"

const meta = {
  title: "Native/Switch",
  component: Switch,
  decorators: [bothPlatforms],
  args: { label: "Notifications" },
} satisfies Meta<typeof Switch>
export default meta
type Story = StoryObj<typeof meta>

const States = () => (
  <>
    <Switch label="Off" />
    <Switch label="On" defaultChecked />
    <Switch label="Disabled" disabled />
    <Switch label="Disabled, on" disabled defaultChecked />
  </>
)

export const AllStates: Story = { render: States }
export const AllStatesDark: Story = { render: States, parameters: dark }
export const Off: Story = {}
export const On: Story = { args: { defaultChecked: true } }
