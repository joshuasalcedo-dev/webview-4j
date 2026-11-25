import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { GrammarTextArea } from "./grammar-text-area"

/** Type, or put the caret in an underlined word: what it is and its fixes show under the field. */
function Example({ dialect = "en-US" as "en-US" | "en-GB" }) {
  const [text, setText] = React.useState("I recieved there package yesterday and its an great day. The colour of the centre is nice.")
  return (
    <div style={{ width: 480 }}>
      <GrammarTextArea aria-label="Message" value={text} onChange={setText} dialect={dialect} />
    </div>
  )
}

const meta = {
  title: "Input/Grammar text area",
  component: Example,
  decorators: bothPlatforms,
} satisfies Meta<typeof Example>

export default meta
type Story = StoryObj<typeof meta>

export const American: Story = {}
export const British: Story = { args: { dialect: "en-GB" } }
export const Dark: Story = { parameters: dark }
