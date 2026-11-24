import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Select } from "./select"

const options = [
  { value: "all", label: "All chats" },
  { value: "unread", label: "Unread" },
  { value: "tipped", label: "Tipped" },
  { value: "expired", label: "Expired subscriptions" },
]

const meta = {
  title: "Native/Select",
  component: Select,
  decorators: [bothPlatforms],
  args: { options, defaultValue: "all", "aria-label": "Show" },
} satisfies Meta<typeof Select>
export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Disabled: Story = { args: { disabled: true } }
export const DefaultDark: Story = { parameters: dark }
