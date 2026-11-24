import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Avatar } from "./avatar"

const meta = {
  title: "Native/Avatar",
  component: Avatar,
  decorators: [bothPlatforms],
  args: { name: "Ana Reyes" },
} satisfies Meta<typeof Avatar>
export default meta
type Story = StoryObj<typeof meta>

/** No picture: the initials on the accent colour. */
export const Initials: Story = {}
export const Sizes: Story = {
  render: () => (
    <>
      <Avatar name="Ana Reyes" size={24} />
      <Avatar name="Marco Silva" size={32} />
      <Avatar name="Jules" size={48} />
    </>
  ),
}
export const InitialsDark: Story = { parameters: dark }
