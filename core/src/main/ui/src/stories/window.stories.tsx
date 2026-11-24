import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { Breadcrumb } from "../components/breadcrumb"
import { Content } from "../components/content"
import { NativeProvider } from "../components/provider"
import { SearchField } from "../components/search-field"
import { Sidebar, SidebarItem, SidebarSection } from "../components/sidebar"
import {
  StatusBar,
  StatusBarSpacer,
  StatusDot,
  StatusItem,
} from "../components/status-bar"
import { TitleBar } from "../components/title-bar"
import { Toolbar, ToolbarButton, ToolbarSpacer } from "../components/toolbar"
import type { Platform, Theme } from "../lib/platform"
import {
  AppLogo,
  ArchiveIcon,
  ChatIcon,
  ComposeIcon,
  GearIcon,
  PeopleIcon,
  StarIcon,
} from "./demo-icons"

const sections = [
  { value: "chats", label: "Chats", icon: <ChatIcon />, badge: 12 },
  { value: "fans", label: "Fans", icon: <PeopleIcon /> },
  { value: "favourites", label: "Favourites", icon: <StarIcon />, badge: 3 },
  { value: "archive", label: "Archive", icon: <ArchiveIcon /> },
]

/** A whole window: title bar with the toolbar in it, sidebar, content, status bar. */
function Window({ platform, theme }: { platform: Platform; theme: Theme }) {
  const [selected, setSelected] = React.useState("chats")
  const [collapsed, setCollapsed] = React.useState(false)
  const current =
    sections.find((s) => s.value === selected)?.label ?? "Settings"
  return (
    <NativeProvider
      platform={platform}
      theme={theme}
      style={{
        display: "grid",
        gridTemplateRows: "auto 1fr auto",
        height: 440,
        borderRadius: 8,
        overflow: "hidden",
      }}
    >
      <TitleBar title="Subscribers Chat" icon={<AppLogo />}>
        <Toolbar
          aria-label="Chats"
          title={current}
          onToggleSidebar={() => setCollapsed(!collapsed)}
        >
          <ToolbarSpacer />
          <ToolbarButton icon={<ComposeIcon />} label="New chat" />
          <SearchField aria-label="Search chats" style={{ width: 160 }} />
        </Toolbar>
      </TitleBar>
      <div style={{ display: "flex", minHeight: 0 }}>
        <Sidebar
          aria-label="Sections"
          collapsed={collapsed}
          onCollapsedChange={platform === "windows" ? setCollapsed : undefined}
          footer={
            <SidebarItem
              label="Settings"
              icon={<GearIcon />}
              selected={selected === "settings"}
              onSelect={() => setSelected("settings")}
            />
          }
        >
          <SidebarSection>
            {sections.map((s) => (
              <SidebarItem
                key={s.value}
                label={s.label}
                icon={s.icon}
                badge={s.badge}
                selected={selected === s.value}
                onSelect={() => setSelected(s.value)}
              />
            ))}
          </SidebarSection>
        </Sidebar>
        <Content style={{ flex: 1, padding: 20 }}>
          <Breadcrumb
            items={[
              { label: "Home", onSelect: () => setSelected("chats") },
              { label: current },
            ]}
          />
          <p className="nt-secondary">The {current.toLowerCase()} page.</p>
        </Content>
      </div>
      <StatusBar>
        <StatusItem icon={<StatusDot state="online" aria-label="Connected" />}>
          Connected
        </StatusItem>
        <StatusBarSpacer />
        <StatusItem>128 chats</StatusItem>
      </StatusBar>
    </NativeProvider>
  )
}

function BothWindows({ theme }: { theme: Theme }) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(520px, 1fr))",
        gap: 16,
      }}
    >
      <Window platform="windows" theme={theme} />
      <Window platform="mac" theme={theme} />
    </div>
  )
}

const meta = {
  title: "Native/Window",
  component: BothWindows,
  args: { theme: "light" },
} satisfies Meta<typeof BothWindows>
export default meta
type Story = StoryObj<typeof meta>

export const Light: Story = {}
export const Dark: Story = { args: { theme: "dark" } }
