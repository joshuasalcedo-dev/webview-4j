import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms } from "../stories/side-by-side"
import { Button } from "./button"
import { useBusy } from "./provider"
import { TextField } from "./text-field"

const pause = (ms: number) => new Promise((done) => setTimeout(done, ms))

/** Move the pointer over each part; press a button and keep the pointer still to see it change. */
function Pointers() {
  const busy = useBusy()
  const [status, setStatus] = React.useState("Idle")
  return (
    <div style={{ display: "grid", gap: 12, width: "100%" }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <Button
          onClick={async () => {
            setStatus("Sending (the app still answers)…")
            await busy(pause(3000))
            setStatus("Sent")
          }}
        >
          Send in the background (3 s)
        </Button>
        <Button
          onClick={async () => {
            setStatus("Loading (wait)…")
            await busy(pause(3000), "wait")
            setStatus("Loaded")
          }}
        >
          Load and wait (3 s)
        </Button>
      </div>
      <span className="nt-secondary">{status}</span>
      <TextField
        label="A text box: the I-beam"
        placeholder="Type here"
        style={{ width: 280 }}
      />
      <p className="nt-selectable" style={{ margin: 0 }}>
        Selectable text (nt-selectable): the I-beam. Everywhere else, buttons
        included: the arrow, as in a desktop app.
      </p>
    </div>
  )
}

const meta = {
  title: "Native/Pointer",
  component: Pointers,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Pointers>
export default meta
type Story = StoryObj<typeof meta>

export const WhatThePointerShows: Story = {}
