import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Checkbox } from "./checkbox"

const meta = {
  title: "Native/Checkbox",
  component: Checkbox,
  decorators: [bothPlatforms],
  args: { label: "Show message previews" },
} satisfies Meta<typeof Checkbox>
export default meta
type Story = StoryObj<typeof meta>

const States = () => (
  <>
    <Checkbox label="Off" />
    <Checkbox label="On" defaultChecked />
    <Checkbox label="Disabled" disabled />
    <Checkbox label="Disabled, on" disabled defaultChecked />
  </>
)

export const AllStates: Story = { render: States }
export const AllStatesDark: Story = { render: States, parameters: dark }
export const Unchecked: Story = {}
export const Checked: Story = { args: { defaultChecked: true } }
