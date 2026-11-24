import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { RichTextEditor } from "./rich-text"

const everything = `
<h1>October update</h1>
<p>Thanks for <strong>subscribing</strong>! This month: <em>new photo sets</em>, a <u>live stream</u> on Friday,
and the <s>old</s> new price for bundles. The code is <code>FALL25</code>.</p>
<h2>What's new</h2>
<ul><li><p>Two photo sets a week</p></li><li><p>Voice notes in chat</p></li></ul>
<h3>How to join the stream</h3>
<ol><li><p>Open the app on Friday at 8 PM</p></li><li><p>Tap <a href="https://example.com/live">Live</a></p></li></ol>
<blockquote><p>The best month yet. Thank you all.</p></blockquote>
<pre><code>Bundle: 3 months for the price of 2</code></pre>
`

/** Type, press Ctrl+B / ⌘B, use the bar, Ctrl+K / ⌘K for a link, right-click for the menu. */
function Editor(props: Partial<React.ComponentProps<typeof RichTextEditor>>) {
  return (
    <div style={{ width: "100%" }}>
      <RichTextEditor aria-label="Message" {...props} />
    </div>
  )
}

/** The value the editor gives back as it changes (HTML), underneath it. */
function WithOutput() {
  const [html, setHtml] = React.useState("<p>Hello <strong>there</strong></p>")
  return (
    <div style={{ display: "grid", gap: 8, width: "100%" }}>
      <RichTextEditor
        aria-label="Message"
        value={html}
        onChange={(v) => setHtml(v.html)}
        minHeight={100}
      />
      <code
        className="nt-selectable"
        style={{
          fontSize: 11,
          whiteSpace: "pre-wrap",
          overflowWrap: "anywhere",
        }}
      >
        {html}
      </code>
    </div>
  )
}

const meta = {
  title: "Native/RichTextEditor",
  component: Editor,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Editor>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeStretch: true, ...dark } }

export const Empty: Story = {
  args: { placeholder: "Write a message to your subscribers…" },
}
export const EmptyDark: Story = {
  args: { placeholder: "Write a message to your subscribers…" },
  ...darkToo,
}
export const EveryKindOfFormatting: Story = {
  args: { defaultValue: everything, minHeight: 360 },
}
export const EveryKindOfFormattingDark: Story = {
  args: { defaultValue: everything, minHeight: 360 },
  ...darkToo,
}
export const ReadOnly: Story = {
  args: { defaultValue: everything, readOnly: true, minHeight: 0 },
}
export const WithoutToolbar: Story = {
  args: {
    toolbar: false,
    defaultValue:
      "<p>No bar: the keys and the right-click menu still format.</p>",
    minHeight: 80,
  },
}
export const OnChangeOutput: Story = { render: () => <WithOutput /> }
