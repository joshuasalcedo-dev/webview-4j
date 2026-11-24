import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Pill, type PillColor, PillGroup } from "./pill"

const colors: PillColor[] = [
  "grey",
  "red",
  "orange",
  "yellow",
  "green",
  "teal",
  "blue",
  "purple",
  "pink",
  "accent",
]

const Star = () => (
  <svg viewBox="0 0 12 12" fill="currentColor" aria-hidden="true">
    <path d="m6 1 1.5 3.2 3.5.4-2.6 2.4.7 3.5L6 8.8 2.9 10.5l.7-3.5L1 4.6l3.5-.4z" />
  </svg>
)
const Crown = () => (
  <svg viewBox="0 0 12 12" fill="currentColor" aria-hidden="true">
    <path d="M1.5 4 4 6l2-4 2 4 2.5-2-1 5.5h-7z" />
  </svg>
)
const Dollar = () => (
  <svg
    viewBox="0 0 12 12"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.3"
    strokeLinecap="round"
    aria-hidden="true"
  >
    <path d="M6 1v10M8.5 3.5C8 2.8 7.1 2.5 6 2.5 4.6 2.5 3.5 3.2 3.5 4.3S4.6 5.8 6 6s2.5.8 2.5 1.8S7.4 9.5 6 9.5c-1.2 0-2.1-.4-2.6-1.1" />
  </svg>
)

function Everything() {
  const [filters, setFilters] = React.useState<string[]>(["VIP"])
  const [tags, setTags] = React.useState(["VIP", "Monthly", "Tipped"])
  return (
    <div style={{ display: "grid", gap: 12, width: "100%" }}>
      {(["subtle", "filled", "outline"] as const).map((variant) => (
        <div
          key={variant}
          style={{ display: "flex", flexWrap: "wrap", gap: 6 }}
        >
          {colors.map((c) => (
            <Pill key={c} color={c} variant={variant}>
              {c}
            </Pill>
          ))}
        </div>
      ))}
      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <Pill color="yellow" icon={<Star />} label="Favourite" />
        <Pill color="purple" icon={<Crown />} label="VIP" variant="filled" />
        <Pill color="green" icon={<Dollar />} label="Tipped" />
        <Pill color="purple" icon={<Crown />}>
          VIP
        </Pill>
        <Pill color="blue" size="medium" icon={<Star />}>
          Medium
        </Pill>
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <span className="nt-secondary">Removable:</span>
        {tags.map((t) => (
          <Pill
            key={t}
            color="blue"
            onRemove={() => setTags(tags.filter((x) => x !== t))}
          >
            {t}
          </Pill>
        ))}
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <span className="nt-secondary">Filters (toggles):</span>
        {["VIP", "Tipped", "New"].map((f) => (
          <Pill
            key={f}
            color="accent"
            size="medium"
            pressed={filters.includes(f)}
            onPressedChange={(on) =>
              setFilters(on ? [...filters, f] : filters.filter((x) => x !== f))
            }
          >
            {f}
          </Pill>
        ))}
      </div>
      <div
        style={{ display: "flex", gap: 6, alignItems: "center", width: 240 }}
      >
        <span className="nt-secondary">Many:</span>
        <PillGroup max={2}>
          <Pill color="purple">VIP</Pill>
          <Pill color="green">Tipped</Pill>
          <Pill color="blue">Monthly</Pill>
          <Pill color="orange">New</Pill>
        </PillGroup>
      </div>
    </div>
  )
}

const meta = {
  title: "Native/Pill",
  component: Everything,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Everything>
export default meta
type Story = StoryObj<typeof meta>

export const Kinds: Story = {}
export const KindsDark: Story = { parameters: { nativeStretch: true, ...dark } }
