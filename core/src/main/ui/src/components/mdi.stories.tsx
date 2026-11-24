import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { GridCell, GridPane, SplitPane } from "./layout/layout"
import { applyLayout, layoutOf, type MdiLayout, MdiArea, useMdi } from "./mdi"
import { NativeProvider } from "./provider"

/** Windows in the window, any component inside (layouts too), Cascade and Tile, and a layout kept and put back. */
function Workspace({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const mdi = useMdi([
    {
      id: "form",
      title: "Customer",
      content: (
        <GridPane hgap={8} vgap={8} columns={[{ halignment: "right" }, { grow: 1 }]} padding={12}>
          <GridCell column={0} row={0}>Name</GridCell>
          <GridCell column={1} row={0}><input className="nt-input" /></GridCell>
          <GridCell column={0} row={1}>Email</GridCell>
          <GridCell column={1} row={1}><input className="nt-input" /></GridCell>
        </GridPane>
      ),
    },
    { id: "split", title: "Editor", content: <SplitPane><div style={{ padding: 8 }}>Outline</div><div style={{ padding: 8 }}>Text</div></SplitPane> },
    { id: "notes", title: "Notes", content: <div style={{ padding: 12 }}>Any component goes in a window.</div> },
  ])
  const [kept, setKept] = React.useState<MdiLayout | null>(null)
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 16, display: "grid", gap: 8, height: 560 }}>
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={() => setKept(layoutOf(mdi.windows))}>Keep this layout</button>
        <button disabled={!kept} onClick={() => kept && applyLayout(mdi, kept)}>Put it back</button>
      </div>
      <MdiArea mdi={mdi} aria-label="Workspace" arrange style={{ height: 480 }} />
    </NativeProvider>
  )
}

const meta = { title: "Native/Use cases/MDI", component: Workspace, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof Workspace>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
