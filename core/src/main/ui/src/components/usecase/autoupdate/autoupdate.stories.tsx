import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { NativeProvider } from "../../provider"
import { Changelog } from "./changelog"
import { UpdatePrompt, type UpdateStatus } from "./update-prompt"

const CHANGELOG = `# Changelog

## 0.3.0 — 2026-12-01

- Auto-update: the app finds, checks and installs its next version
- A **changelog** in the app

## 0.2.0 — 2026-11-25

- Layout panes named as JavaFX's
- State kept on disk, and the web

## 0.1.0 — 2026-10-01

- The first version
`

function Updates({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [status, setStatus] = React.useState<UpdateStatus>("available")
  const [progress, setProgress] = React.useState(0)
  React.useEffect(() => {
    if (status !== "downloading") return
    const timer = setInterval(() => setProgress((p) => {
      if (p >= 1) {
        setStatus("ready")
        return 1
      }
      return p + 0.1
    }), 200)
    return () => clearInterval(timer)
  }, [status])
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24, display: "grid", gap: 32 }}>
      <UpdatePrompt app="Notes" version="0.3.0" current="0.2.0" notes={CHANGELOG} status={status} progress={progress}
        onUpdate={() => { setProgress(0); setStatus("downloading") }} onInstall={() => setStatus("installing")}
        onLater={() => setStatus("available")} onSkip={() => setStatus("available")} />
      <div>
        <h3>What's new since 0.1.0</h3>
        <Changelog markdown={CHANGELOG} since="0.1.0" />
      </div>
    </NativeProvider>
  )
}

const meta = { title: "Native/Use cases/Auto-update", component: Updates, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof Updates>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
