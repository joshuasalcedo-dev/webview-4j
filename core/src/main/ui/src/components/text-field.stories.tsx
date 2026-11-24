import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { TextField } from "./text-field"

const meta = {
  title: "Native/TextField",
  component: TextField,
  decorators: [bothPlatforms],
  args: {
    label: "Display name",
    placeholder: "Ana Reyes",
    style: { width: 260 },
  },
} satisfies Meta<typeof TextField>
export default meta
type Story = StoryObj<typeof meta>

export const Empty: Story = {}
export const Filled: Story = { args: { defaultValue: "Ana Reyes" } }
export const WithHint: Story = {
  args: { hint: "Shown to your fans next to your messages." },
}
export const WithError: Story = {
  args: { defaultValue: "A", error: "Use at least 2 letters." },
}
export const Disabled: Story = {
  args: { defaultValue: "Ana Reyes", disabled: true },
}
export const FilledDark: Story = {
  args: { defaultValue: "Ana Reyes", hint: "Shown to your fans." },
  parameters: dark,
}
export const WithErrorDark: Story = {
  args: { defaultValue: "A", error: "Use at least 2 letters." },
  parameters: dark,
}
