import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Button } from "./button"

const meta = {
  title: "Native/Button",
  component: Button,
  decorators: [bothPlatforms],
  args: { children: "Button" },
} satisfies Meta<typeof Button>
export default meta
type Story = StoryObj<typeof meta>

const Variants = () => (
  <>
    <Button>Cancel</Button>
    <Button variant="accent">Send</Button>
    <Button variant="subtle">Edit</Button>
    <Button disabled>Disabled</Button>
    <Button variant="accent" disabled>
      Send
    </Button>
  </>
)

/** Each kind: standard, the window's main action (accent), toolbar (subtle), and disabled. */
export const Kinds: Story = { render: Variants }
export const KindsDark: Story = { render: Variants, parameters: dark }

export const Standard: Story = {}
export const Accent: Story = { args: { variant: "accent", children: "Send" } }
export const Subtle: Story = { args: { variant: "subtle", children: "Edit" } }
export const Disabled: Story = { args: { disabled: true } }
