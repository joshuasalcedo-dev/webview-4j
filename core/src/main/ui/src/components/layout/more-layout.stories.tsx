import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { Button } from "../button"
import { Carousel } from "../carousel"
import { NativeProvider } from "../provider"
import { Surface } from "../surface"
import { Accordion, TitledPane } from "../titled-pane"
import { VBox } from "./layout"
import { Anchored, AnchorPane, Card, CardPane, FlowPane, ScrollPane, TilePane } from "./more-layout"

const Tile = ({ children }: { children: React.ReactNode }) => (
  <div style={{ padding: 8, border: "1px dashed var(--nt-surface-border)", borderRadius: 6 }}>{children}</div>
)

function MoreLayouts({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [step, setStep] = React.useState("account")
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24 }}>
      <VBox spacing={24}>
        <Surface title="FlowPane">
          <FlowPane hgap={6} vgap={6}>
            {["java", "desktop", "react", "rust", "webview", "ffm", "tao", "wry"].map((t) => <Tile key={t}>{t}</Tile>)}
          </FlowPane>
        </Surface>
        <Surface title="TilePane: tiles at least 120 wide">
          <TilePane tileWidth={120} hgap={8} vgap={8}>
            {Array.from({ length: 7 }, (_, i) => <Tile key={i}>Tile {i + 1}</Tile>)}
          </TilePane>
        </Surface>
        <Surface title="AnchorPane">
          <AnchorPane style={{ height: 140, border: "1px dashed var(--nt-surface-border)" }}>
            <Anchored anchors={{ top: 8, left: 8 }}><Tile>top-left</Tile></Anchored>
            <Anchored anchors={{ top: 8, right: 8 }}><Tile>top-right</Tile></Anchored>
            <Anchored anchors={{ bottom: 8, left: 8, right: 8 }}><Tile>stretched along the bottom</Tile></Anchored>
          </AnchorPane>
        </Surface>
        <Surface title="ScrollPane">
          <ScrollPane style={{ height: 100 }}>
            {Array.from({ length: 12 }, (_, i) => <div key={i}>Line {i + 1}</div>)}
          </ScrollPane>
        </Surface>
        <Surface title={`CardPane: ${step}`}>
          <CardPane shown={step} transition="slide">
            <Card name="account"><Tile>Your account</Tile></Card>
            <Card name="plan"><Tile>Your plan</Tile></Card>
            <Card name="done"><Tile>All set</Tile></Card>
          </CardPane>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            {["account", "plan", "done"].map((s) => <Button key={s} onClick={() => setStep(s)}>{s}</Button>)}
          </div>
        </Surface>
        <Surface title="Carousel">
          <Carousel aria-label="Pictures" interval={3000} style={{ height: 160 }}>
            {["#4f8cff", "#2fbf71", "#f5a524"].map((c, i) => (
              <div key={c} style={{ height: "100%", display: "grid", placeItems: "center", background: c, color: "white", fontSize: 24 }}>Slide {i + 1}</div>
            ))}
          </Carousel>
        </Surface>
        <TitledPane title="Advanced">Things most people never change.</TitledPane>
        <Accordion panes={[{ title: "General", content: "Name, language" }, { title: "Privacy", content: "Who sees what" }, { title: "About", content: "Version 0.2.0" }]} />
      </VBox>
    </NativeProvider>
  )
}

const meta = { title: "Native/Layout/More", component: MoreLayouts, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof MoreLayouts>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
