import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { Button } from "../button"
import { NativeProvider } from "../provider"
import { Surface } from "../surface"
import { BorderPane, GridCell, GridPane, Grow, HBox, SplitPane, StackItem, StackPane, VBox } from "./layout"

const Tile = ({ children, tall }: { children: React.ReactNode; tall?: boolean }) => (
  <div style={{ padding: 8, minHeight: tall ? 80 : undefined, border: "1px dashed var(--nt-surface-border)", borderRadius: 6 }}>{children}</div>
)

function Layouts({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [positions, setPositions] = React.useState([0.3])
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24 }}>
      <VBox spacing={24}>
        <Surface title="HBox: spacing 8, the middle one grows">
          <HBox spacing={8}>
            <Button>One</Button>
            <Grow>
              <Tile>Grows</Tile>
            </Grow>
            <Button>Three</Button>
          </HBox>
        </Surface>
        <Surface title="VBox: alignment center">
          <VBox spacing={4} alignment="center">
            <Tile>Under</Tile>
            <Tile>each</Tile>
            <Tile>other</Tile>
          </VBox>
        </Surface>
        <Surface title="GridPane: a form, labels on the right, the fields growing">
          <GridPane hgap={12} vgap={8} columns={[{ halignment: "right" }, { grow: 1 }]}>
            <GridCell column={0} row={0}>Name</GridCell>
            <GridCell column={1} row={0}><input className="nt-input" /></GridCell>
            <GridCell column={0} row={1}>Email</GridCell>
            <GridCell column={1} row={1}><input className="nt-input" /></GridCell>
            <GridCell column={0} row={2} columnSpan={2} halignment="right"><Button variant="accent">Save</Button></GridCell>
          </GridPane>
        </Surface>
        <Surface title="BorderPane">
          <BorderPane spacing={8} style={{ height: 200 }}
            top={<Tile>Top</Tile>} left={<Tile tall>Left</Tile>} center={<Tile tall>Center</Tile>} right={<Tile tall>Right</Tile>} bottom={<Tile>Bottom</Tile>} />
        </Surface>
        <Surface title="StackPane: a badge on a picture">
          <StackPane style={{ width: 160, height: 100, background: "color-mix(in srgb, var(--nt-accent) 15%, transparent)", borderRadius: 8 }}>
            <span>Picture</span>
            <StackItem alignment="top-right" margin={6}><span className="nt-pill">3</span></StackItem>
          </StackPane>
        </Surface>
        <Surface title={`SplitPane: divider at ${Math.round(positions[0] * 100)}%`}>
          <div style={{ height: 220 }}>
            <SplitPane dividerPositions={positions} onDividerPositionsChange={setPositions}>
              <Tile>Navigator</Tile>
              <SplitPane orientation="vertical">
                <Tile>Editor</Tile>
                <Tile>Console</Tile>
              </SplitPane>
            </SplitPane>
          </div>
        </Surface>
      </VBox>
    </NativeProvider>
  )
}

const meta = { title: "Native/Layout", component: Layouts, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof Layouts>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
