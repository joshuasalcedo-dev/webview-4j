import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { AppLogo, ChatIcon } from "../stories/demo-icons"
import { bothPlatforms, dark } from "../stories/side-by-side"
import { type TabItem, Tabs } from "./tabs"
import { TitleBar } from "./title-bar"

/** Click or arrow between tabs; × or the middle button closes one; + opens one; right-click a tab. */
function Example({
  count = 3,
  inTitleBar = false,
}: {
  count?: number
  inTitleBar?: boolean
}) {
  const names = [
    "Ana Reyes",
    "Marco Silva",
    "Kim Park",
    "Lea Cruz",
    "Tom Lee",
    "Sara Diaz",
    "Ben Ortiz",
  ]
  const [open, setOpen] = React.useState(names.slice(0, count))
  const [value, setValue] = React.useState(open[0] ?? "")
  const close = (name: string) => {
    const rest = open.filter((n) => n !== name)
    setOpen(rest)
    if (value === name) setValue(rest[0] ?? "")
  }
  const tabs: TabItem<string>[] = open.map((name) => ({
    value: name,
    label: name,
    icon: <ChatIcon />,
    closable: open.length > 1,
    menu: () => [
      {
        label: "Close",
        disabled: open.length < 2,
        onSelect: () => close(name),
      },
      {
        label: "Close other tabs",
        disabled: open.length < 2,
        onSelect: () => (setOpen([name]), setValue(name)),
      },
    ],
  }))
  const strip = (
    <Tabs
      aria-label="Open chats"
      tabs={tabs}
      value={value}
      onValueChange={setValue}
      onClose={close}
      onAdd={() => {
        const next = names.find((n) => !open.includes(n))
        if (!next) return
        setOpen([...open, next])
        setValue(next)
      }}
    />
  )
  return (
    <div style={{ display: "grid" }}>
      {inTitleBar ? <TitleBar icon={<AppLogo />}>{strip}</TitleBar> : strip}
      <div
        role="tabpanel"
        style={{ padding: 16, background: "var(--nt-tab-selected)" }}
      >
        {value ? `The chat with ${value}` : "No chats open"}
      </div>
    </div>
  )
}

const meta = {
  title: "Native/Tabs",
  component: Example,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const ThreeTabs: Story = {}
export const ThreeTabsDark: Story = {
  parameters: { nativeChrome: true, ...dark },
}
export const OneTab: Story = { args: { count: 1 } }
export const Crowded: Story = { args: { count: 7 } }
export const InTheTitleBar: Story = { args: { inTitleBar: true } }
export const InTheTitleBarDark: Story = {
  args: { inTitleBar: true },
  parameters: { nativeChrome: true, ...dark },
}
