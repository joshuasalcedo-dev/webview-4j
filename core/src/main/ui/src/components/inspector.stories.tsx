import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Button } from "./button"
import { Inspector, InspectorSection } from "./inspector"
import { Text } from "./text"

/** Drag the left edge (double-click it to reset), fold the sections, Escape inside closes it. */
function Example({ width = 760 }: { width?: number }) {
  const [open, setOpen] = React.useState(true)
  return (
    <div
      style={{
        position: "relative",
        display: "flex",
        height: 380,
        width: "100%",
        maxWidth: width,
        overflow: "hidden",
      }}
    >
      <div style={{ flex: 1, padding: 16, minWidth: 0 }}>
        <Text>The main content.</Text>
        {!open && (
          <Button onClick={() => setOpen(true)} style={{ marginTop: 12 }}>
            Show details
          </Button>
        )}
      </div>
      <Inspector
        open={open}
        onOpenChange={setOpen}
        title="Details"
        storageKey="story-inspector"
      >
        <InspectorSection title="About">
          <Text tone="secondary">Created 12 October. Owned by Ana Reyes.</Text>
        </InspectorSection>
        <InspectorSection
          title="Photos"
          count={12}
          action={<Button variant="subtle">See all</Button>}
        >
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(3, 1fr)",
              gap: 4,
            }}
          >
            {Array.from({ length: 6 }, (_, i) => (
              <div
                key={i}
                style={{
                  aspectRatio: "1",
                  borderRadius: 4,
                  background: `hsl(${i * 50} 60% 70%)`,
                }}
              />
            ))}
          </div>
        </InspectorSection>
        <InspectorSection title="Notes" defaultOpen={false}>
          <Text>Folded at first.</Text>
        </InspectorSection>
      </Inspector>
    </div>
  )
}

const meta = {
  title: "Native/Inspector",
  component: Example,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const BesideContent: Story = {}
export const BesideContentDark: Story = {
  parameters: { nativeChrome: true, ...dark },
}
/** A narrow window: the panel lies over the content. */
export const NarrowOverlay: Story = { args: { width: 520 } }
