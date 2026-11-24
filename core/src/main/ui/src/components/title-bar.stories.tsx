import type { Meta, StoryObj } from "@storybook/react-vite"

import { AppLogo } from "../stories/demo-icons"
import { bothPlatforms, dark } from "../stories/side-by-side"
import { Button } from "./button"
import { TitleBar } from "./title-bar"

const meta = {
  title: "Native/TitleBar",
  component: TitleBar,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
  args: { title: "Subscribers Chat", icon: <AppLogo /> },
} satisfies Meta<typeof TitleBar>
export default meta
type Story = StoryObj<typeof meta>

/** Windows: caption buttons on the right (close turns red on hover). macOS: the traffic lights show their symbols on hover. */
export const Plain: Story = {}
export const WithToolbar: Story = {
  args: {
    children: (
      <Button variant="subtle" aria-label="New chat">
        New chat
      </Button>
    ),
  },
}
export const NoIcon: Story = { args: { icon: undefined } }
export const PlainDark: Story = { parameters: { nativeChrome: true, ...dark } }
