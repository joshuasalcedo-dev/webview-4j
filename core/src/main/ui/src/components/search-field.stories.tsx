import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { SearchField } from "./search-field"

/** Type, then Escape clears it. */
function Example({ start = "" }: { start?: string }) {
  const [text, setText] = React.useState(start)
  return (
    <SearchField
      aria-label="Search chats"
      value={text}
      onChange={(event) => setText(event.target.value)}
      style={{ width: 220 }}
    />
  )
}

const meta = {
  title: "Native/SearchField",
  component: Example,
  decorators: [bothPlatforms],
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const Empty: Story = {}
export const EmptyDark: Story = { parameters: dark }
export const Filled: Story = { args: { start: "Ana" } }
export const Disabled: Story = {
  render: () => (
    <SearchField aria-label="Search chats" disabled style={{ width: 220 }} />
  ),
}
