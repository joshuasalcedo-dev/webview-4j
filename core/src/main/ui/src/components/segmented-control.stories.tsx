import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { SegmentedControl } from "./segmented-control"

function Choice() {
  const [value, setValue] = React.useState("all")
  return (
    <SegmentedControl
      aria-label="Show"
      value={value}
      onValueChange={setValue}
      segments={[
        { value: "all", label: "All" },
        { value: "unread", label: "Unread" },
        { value: "tipped", label: "Tipped" },
      ]}
    />
  )
}

const meta = {
  title: "Native/SegmentedControl",
  component: Choice,
  decorators: [bothPlatforms],
} satisfies Meta<typeof Choice>
export default meta
type Story = StoryObj<typeof meta>

/** Click a choice, or use the arrow keys. */
export const Default: Story = {}
export const DefaultDark: Story = { parameters: dark }
