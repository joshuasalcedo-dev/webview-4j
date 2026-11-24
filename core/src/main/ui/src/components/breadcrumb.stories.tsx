import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { FolderIcon } from "../stories/demo-icons"
import { bothPlatforms, dark } from "../stories/side-by-side"
import { Breadcrumb } from "./breadcrumb"

const path = ["Chats", "Ana Reyes", "Photos", "October"]

/** Click a level to go back up to it. */
function Example({ depth = 4 }: { depth?: number }) {
  const [at, setAt] = React.useState(depth)
  return (
    <Breadcrumb
      items={path.slice(0, at).map((label, index) => ({
        label,
        icon: <FolderIcon />,
        onSelect: () => setAt(index + 1),
      }))}
    />
  )
}

const meta = {
  title: "Native/Breadcrumb",
  component: Example,
  decorators: [bothPlatforms],
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const FourLevels: Story = {}
export const FourLevelsDark: Story = { parameters: dark }
export const TopLevel: Story = { args: { depth: 1 } }
