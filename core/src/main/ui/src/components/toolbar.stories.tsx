import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { AppLogo, ComposeIcon, InfoIcon } from "../stories/demo-icons"
import { bothPlatforms, dark } from "../stories/side-by-side"
import { SearchField } from "./search-field"
import { TitleBar } from "./title-bar"
import {
  Toolbar,
  ToolbarButton,
  ToolbarSeparator,
  ToolbarSpacer,
} from "./toolbar"

const pages = ["Chats", "Ana Reyes", "Photos"]

/** Back and forward walk the pages (also the mouse's side buttons, Alt+← / ⌘[); Details is a toggle. */
function Example({ inTitleBar = false }: { inTitleBar?: boolean }) {
  const [at, setAt] = React.useState(1)
  const [details, setDetails] = React.useState(false)
  const bar = (
    <Toolbar
      aria-label="Chat"
      title={pages[at]}
      onToggleSidebar={() => {}}
      onBack={() => setAt((n) => Math.max(0, n - 1))}
      canGoBack={at > 0}
      onForward={() => setAt((n) => Math.min(pages.length - 1, n + 1))}
      canGoForward={at < pages.length - 1}
    >
      <ToolbarSpacer />
      <ToolbarButton icon={<ComposeIcon />} label="New chat" />
      <ToolbarSeparator />
      <ToolbarButton
        icon={<InfoIcon />}
        label="Details"
        pressed={details}
        onClick={() => setDetails(!details)}
      />
      <SearchField aria-label="Search chats" style={{ width: 180 }} />
    </Toolbar>
  )
  return inTitleBar ? (
    <TitleBar title="Subscribers Chat" icon={<AppLogo />}>
      {bar}
    </TitleBar>
  ) : (
    bar
  )
}

const meta = {
  title: "Native/Toolbar",
  component: Example,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const UnderTheTitleBar: Story = {}
export const UnderTheTitleBarDark: Story = {
  parameters: { nativeChrome: true, ...dark },
}
export const InTheTitleBar: Story = { args: { inTitleBar: true } }
export const InTheTitleBarDark: Story = {
  args: { inTitleBar: true },
  parameters: { nativeChrome: true, ...dark },
}
