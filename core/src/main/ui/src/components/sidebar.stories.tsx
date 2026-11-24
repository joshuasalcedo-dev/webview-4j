import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import {
  ArchiveIcon,
  ChatIcon,
  GearIcon,
  PeopleIcon,
  StarIcon,
} from "../stories/demo-icons"
import { bothPlatforms, dark } from "../stories/side-by-side"
import { Sidebar, SidebarItem, SidebarSection } from "./sidebar"

/** Click an item, use the arrow keys, right-click Chats; ☰ (Windows) folds it to icons. */
function Example({
  startCollapsed = false,
  empty = false,
}: {
  startCollapsed?: boolean
  empty?: boolean
}) {
  const [selected, setSelected] = React.useState("chats")
  const [collapsed, setCollapsed] = React.useState(startCollapsed)
  const item = (
    value: string,
    label: string,
    icon: React.ReactNode,
    badge?: number
  ) => (
    <SidebarItem
      label={label}
      icon={icon}
      badge={badge}
      selected={selected === value}
      onSelect={() => setSelected(value)}
      menu={
        value === "chats"
          ? [
              { label: "Mark all as read", onSelect: () => {} },
              { label: "Open in new window", onSelect: () => {} },
            ]
          : undefined
      }
    />
  )
  return (
    <div style={{ display: "flex", height: 320 }}>
      <Sidebar
        aria-label="Sections"
        collapsed={collapsed}
        onCollapsedChange={setCollapsed}
        footer={
          <SidebarItem
            label="Settings"
            icon={<GearIcon />}
            selected={selected === "settings"}
            onSelect={() => setSelected("settings")}
          />
        }
      >
        {!empty && (
          <>
            <SidebarSection>
              {item("chats", "Chats", <ChatIcon />, 12)}
              {item("fans", "Fans", <PeopleIcon />)}
            </SidebarSection>
            <SidebarSection title="Lists">
              {item("favourites", "Favourites", <StarIcon />, 3)}
              {item("archive", "Archive", <ArchiveIcon />, 120)}
            </SidebarSection>
          </>
        )}
      </Sidebar>
    </div>
  )
}

const meta = {
  title: "Native/Sidebar",
  component: Example,
  decorators: [bothPlatforms],
} satisfies Meta<typeof Example>
export default meta
type Story = StoryObj<typeof meta>

export const Open: Story = {}
export const OpenDark: Story = { parameters: dark }
export const Collapsed: Story = { args: { startCollapsed: true } }
export const Empty: Story = { args: { empty: true } }
