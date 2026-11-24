import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Composer, type Message } from "./composer"
import { FileChip } from "./files"
import { Text } from "./text"

/** A small chat: what is sent shows above. Enter sends, Shift+Enter is a new line; paste or drop files. */
function Chat({
  failing = false,
  sendKey,
}: {
  failing?: boolean
  sendKey?: "enter" | "mod+enter"
}) {
  const [sent, setSent] = React.useState<Message[]>([
    { text: "Hi! Are the photos ready?", files: [] },
  ])
  const [typing, setTyping] = React.useState(0)
  return (
    <div style={{ display: "grid", gap: 10, width: "100%" }}>
      <div
        style={{ display: "grid", gap: 6, minHeight: 120, alignContent: "end" }}
      >
        {sent.map((m, i) => (
          <div key={i} style={{ display: "grid", gap: 4, justifyItems: "end" }}>
            {m.text && (
              <Text
                selectable
                style={{
                  whiteSpace: "pre-wrap",
                  maxWidth: 320,
                  textAlign: "left",
                }}
              >
                {m.text}
              </Text>
            )}
            {m.files.map((f) => (
              <FileChip key={f.name} name={f.name} size={f.size} />
            ))}
          </div>
        ))}
      </div>
      <Composer
        aria-label="Message Ana Reyes"
        placeholder="Message Ana Reyes"
        sendKey={sendKey}
        maxLength={500}
        onTyping={() => setTyping((n) => n + 1)}
        onEditLast={() => {}}
        onSend={async (message) => {
          await new Promise((r) => setTimeout(r, 200))
          if (failing)
            throw new Error(
              "Couldn't send: you're offline. It stays here; send it again when you're back online."
            )
          setSent((now) => [...now, message])
        }}
      />
      <Text variant="footnote" tone="secondary">
        “Typing…” sent {typing} {typing === 1 ? "time" : "times"} (at most every
        3 seconds).
      </Text>
    </div>
  )
}

const meta = {
  title: "Native/Composer",
  component: Chat,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Chat>
export default meta
type Story = StoryObj<typeof meta>

export const EnterSends: Story = {}
export const EnterSendsDark: Story = {
  parameters: { nativeStretch: true, ...dark },
}
export const CtrlEnterSends: Story = { args: { sendKey: "mod+enter" } }
export const Offline: Story = { args: { failing: true } }
export const Disabled: Story = {
  render: () => (
    <Composer
      onSend={() => {}}
      disabled
      placeholder="You can't reply to this chat"
    />
  ),
}
