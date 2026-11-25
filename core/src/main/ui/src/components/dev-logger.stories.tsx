import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { DevLogger, type DevLogEntry, type DevLoggerHandle } from "./dev-logger"
import { NativeProvider } from "./provider"

const meta = {
  title: "Native/DevLogger",
  component: DevLogger,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof DevLogger>
export default meta
type Story = StoryObj<typeof meta>

/** What an app does, as Java would send it: calls (some slow, some failing), calls to Java, log lines, long tasks. */
function pretendApp(tick: number): DevLogEntry[] {
  const time = Date.now()
  const out: DevLogEntry[] = []
  if (tick % 3 === 0) {
    const slow = tick % 15 === 0
    out.push({
      kind: "http", time, source: "page", method: "GET", url: "/api/todos?page=1", status: 200,
      durationMs: slow ? 720 + (tick % 7) * 30 : 20 + (tick % 11) * 4,
      request: { headers: { accept: "application/json" } },
      response: { headers: { "content-type": "application/json" }, body: JSON.stringify({ items: [{ id: 1, title: "milk", done: false }], total: 42 }) },
    })
  }
  if (tick % 10 === 0) {
    out.push({
      kind: "http", time, source: "page", method: "POST", url: "/api/todos", status: 500, durationMs: 120,
      request: { body: { title: "milk" } },
      response: { body: { title: "Internal Server Error", detail: "database is locked" } },
    })
    out.push({ kind: "log", time, level: "error", target: "app", message: "saving a todo failed: database is locked" })
  }
  if (tick % 4 === 0) {
    out.push({ kind: "bridge", time, direction: "toJava", name: "TodoController.add", durationMs: 2 + (tick % 5), request: { body: { title: "bread" } }, response: { body: { id: tick } } })
  }
  if (tick % 7 === 0) out.push({ kind: "perf", time, name: "long task", value: 60 + (tick % 9) * 10, unit: "ms" })
  if (tick % 9 === 0) out.push({ kind: "log", time, level: "info", target: "app", message: `${tick} ticks` })
  return out
}

function Live({ theme }: { theme: "light" | "dark" }) {
  const logger = React.useRef<DevLoggerHandle>(null)
  React.useEffect(() => {
    let tick = 0
    const timer = setInterval(() => logger.current?.append(pretendApp(++tick)), 100)
    return () => clearInterval(timer)
  }, [])
  return (
    <NativeProvider theme={theme} style={{ height: "100vh" }}>
      <DevLogger ref={logger} height="100%" slowMs={300} />
    </NativeProvider>
  )
}

/** Fed live, as Java feeds it: zoom, filter, search, click a row, drag the panes' tabs about. */
export const Light: Story = { render: () => <Live theme="light" /> }
export const Dark: Story = { render: () => <Live theme="dark" /> }
