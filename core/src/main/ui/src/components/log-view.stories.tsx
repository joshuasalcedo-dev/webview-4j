import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms } from "../stories/side-by-side"
import { LogView, type LogViewLine } from "./log-view"

const start = Date.now() - 60_000
const LINES: LogViewLine[] = [
  { id: 1, level: "info", time: start, source: "Subscribers Chat", message: "Started." },
  { id: 2, level: "info", time: start + 900, source: "Window", message: "Docked to the left edge of the screen." },
  {
    id: 3,
    level: "warn",
    time: start + 4000,
    source: "Sidecar",
    message: "The sidecar answered slowly (4 s). Chats may load late.",
  },
  {
    id: 4,
    level: "error",
    time: start + 9000,
    source: "Window",
    message: "Something went wrong in the window services. The details are below.",
    detail: "SetWindowPos failed: Os { code: 5, kind: PermissionDenied, message: \"Access is denied.\" }",
  },
  { id: 5, level: "debug", time: start + 9500, source: "Menu", message: "menu: starter.system" },
]

/** Lines of every kind: warnings and errors stand out; the error opens on a click to show its details. */
function Example({ empty = false }: { empty?: boolean }) {
  return (
    <LogView aria-label="Activity" lines={empty ? [] : LINES} style={{ height: 260, width: "100%" }}
             empty="Nothing has happened yet." />
  )
}

/** A line every second, the view following the newest (scroll up to stop it). */
function Live() {
  const [lines, setLines] = React.useState<LogViewLine[]>(LINES.slice(0, 2))
  React.useEffect(() => {
    const timer = setInterval(
      () =>
        setLines((all) => [
          ...all,
          { id: all.length + 1, level: all.length % 5 === 0 ? "warn" : "info", time: Date.now(), source: "Sync",
            message: `Synced ${all.length} chats.` },
        ]),
      1000
    )
    return () => clearInterval(timer)
  }, [])
  return <LogView aria-label="Activity" lines={lines} style={{ height: 220, width: "100%" }} />
}

const meta = {
  title: "Native/Log view",
  component: Example,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const Lines: Story = {}
export const Empty: Story = { args: { empty: true } }
export const Streaming: Story = { render: () => <Live /> }
