import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import * as React from "react"

import { Code, Label, Link, Shortcut, Text, type TextVariant } from "./text"

const variants: TextVariant[] = [
  "display",
  "largeTitle",
  "title",
  "subtitle",
  "headline",
  "body",
  "caption",
  "footnote",
]

/** Every role, at each system's own size. */
function TypeRamp() {
  return (
    <div style={{ display: "grid", gap: 6, width: "100%" }}>
      {variants.map((variant) => (
        <div
          key={variant}
          style={{
            display: "grid",
            gridTemplateColumns: "84px 1fr",
            alignItems: "baseline",
            gap: 12,
          }}
        >
          <Text variant="footnote" tone="secondary" mono>
            {variant}
          </Text>
          <Text variant={variant} lines={1}>
            {variant === "display" ? "Chats" : "Messages from your subscribers"}
          </Text>
        </div>
      ))}
    </div>
  )
}

function Tones() {
  return (
    <div style={{ display: "grid", gap: 4 }}>
      <Text>Default: Ana Reyes sent a photo.</Text>
      <Text tone="secondary">Secondary: Last seen 5 minutes ago</Text>
      <Text tone="tertiary">Tertiary: No messages yet</Text>
      <Text tone="accent">Accent: 3 new messages</Text>
      <Text tone="success">Success: Payment received</Text>
      <Text tone="warning">Warning: Your subscription ends in 3 days</Text>
      <Text tone="error">Error: Couldn't send the message. Try again.</Text>
      <Text>
        A sentence with a{" "}
        <Text as="span" strong>
          strong name
        </Text>
        , a <Link onClick={() => {}}>link</Link>, an{" "}
        <Link href="https://example.com" external>
          outside link
        </Link>
        , some <Code>inline code</Code> and the shortcut{" "}
        <Shortcut keys="Mod+Shift+K" />.
      </Text>
    </div>
  )
}

/** Hover the cut-off lines: the whole text shows in a tooltip (only because it was cut). */
function Truncated() {
  const long =
    "Hi! I wanted to ask whether the photos from Saturday's shoot are ready yet, and if so whether you could send the ones by the window first."
  return (
    <div style={{ display: "grid", gap: 10, width: 300 }}>
      <Text lines={1}>{long}</Text>
      <Text lines={2}>{long}</Text>
      <Text lines={1}>Short enough: no tooltip.</Text>
    </div>
  )
}

/**
 * Double-click a word, then keep clicking: three clicks the sentence, four the paragraph, five everything.
 * Right-click selected text for Copy.
 */
function Selectable() {
  return (
    <div
      className="nt-selectable"
      style={{ display: "grid", gap: 10, maxWidth: 520 }}
    >
      <Text selectable>
        Your October report is ready. It covers 1,240 new subscribers and 18,900
        messages. Tips were up 12% on September.
      </Text>
      <Text selectable>
        The busiest day was Saturday the 12th. Most messages came in between 8
        and 11 in the evening.
      </Text>
    </div>
  )
}

/**
 * Labels belong to the controls: not selectable, the arrow pointer; a click on one focuses its box or ticks its
 * box. Clickable text: a Link (accent) or a subtle Link (the text's colour). The paragraph under them is
 * ordinary Text: select it and copy it.
 */
function LabelsAndClicks() {
  const [shown, setShown] = React.useState(false)
  const [said, setSaid] = React.useState("nothing yet")
  return (
    <div style={{ display: "grid", gap: 12, maxWidth: 420 }}>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "auto 1fr",
          gap: "8px 12px",
          alignItems: "center",
        }}
      >
        <Label htmlFor="story-name">Display name</Label>
        <input id="story-name" className="nt-input" defaultValue="Ana Reyes" />
        <Label htmlFor="story-email" strong>
          Email
        </Label>
        <input
          id="story-email"
          className="nt-input"
          placeholder="ana@example.com"
        />
        <Label htmlFor="story-off" disabled>
          Phone (off)
        </Label>
        <input id="story-off" className="nt-input" disabled />
      </div>
      <Label style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <input type="checkbox" /> Clicking these words ticks the box
      </Label>
      <Text>
        {shown
          ? "Your October report covers 1,240 new subscribers and 18,900 messages. Tips were up 12% on September, and the busiest day was Saturday the 12th."
          : "Your October report covers 1,240 new subscribers…"}{" "}
        <Link subtle onClick={() => setShown(!shown)}>
          {shown ? "Show less" : "Show more"}
        </Link>
      </Text>
      <Text variant="caption" tone="secondary">
        <Link onClick={() => setSaid("Mark all as read")}>
          Mark all as read
        </Link>{" "}
        ·{" "}
        <Link subtle onClick={() => setSaid("Clear")}>
          Clear
        </Link>{" "}
        · clicked: {said}
      </Text>
    </div>
  )
}

const meta = {
  title: "Native/Text",
  component: TypeRamp,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof TypeRamp>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeStretch: true, ...dark } }

export const Sizes: Story = {}
export const SizesDark: Story = darkToo
export const ColoursLinksCode: Story = { render: () => <Tones /> }
export const ColoursLinksCodeDark: Story = {
  render: () => <Tones />,
  ...darkToo,
}
export const CutOff: Story = { render: () => <Truncated /> }
export const ClickToSelect: Story = { render: () => <Selectable /> }
export const LabelsAndClickableText: Story = {
  render: () => <LabelsAndClicks />,
}
export const LabelsAndClickableTextDark: Story = {
  render: () => <LabelsAndClicks />,
  ...darkToo,
}
