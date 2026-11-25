import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { NamedIcon } from "../lib/named-icons"
import { bothPlatforms, dark } from "../stories/side-by-side"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarHeader,
  SidebarInput,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuCollapsible,
  SidebarMenuItem,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarProvider,
  SidebarRail,
  SidebarSeparator,
  SidebarTrigger,
} from "./sidebar"

/** The sidebar built from parts, as shadcn/ui's: Ctrl+B or the trigger folds it to its icons. */
function Example({ variant = "sidebar" as "sidebar" | "floating" | "inset", collapsible = "icon" as "icon" | "offcanvas" | "none" }) {
  const [page, setPage] = React.useState("Inbox")
  const place = (name: string, icon: string, badge?: number) => (
    <SidebarMenuItem key={name}>
      <SidebarMenuButton icon={<NamedIcon name={icon} />} badge={badge} isActive={page === name} onClick={() => setPage(name)}>
        {name}
      </SidebarMenuButton>
    </SidebarMenuItem>
  )
  return (
    <SidebarProvider style={{ height: 480, border: "1px solid var(--nt-separator)" }}>
      <Sidebar aria-label="Mail" variant={variant} collapsible={collapsible}>
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton size="lg" icon={<NamedIcon name="A" />} description="ada@example.com">
                Acme Mail
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
          <SidebarInput placeholder="Search mail" aria-label="Search mail" />
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup label="Mail">
            <SidebarMenu>
              {place("Inbox", "mail", 12)}
              {place("Drafts", "file", 2)}
              {place("Sent", "upload")}
              <SidebarMenuCollapsible
                icon={<NamedIcon name="bookmark" />}
                items={["2026", "2025"].map((y) => (
                  <SidebarMenuSubItem key={y}>
                    <SidebarMenuSubButton isActive={page === y} onClick={() => setPage(y)}>
                      {y}
                    </SidebarMenuSubButton>
                  </SidebarMenuSubItem>
                ))}
              >
                Archive
              </SidebarMenuCollapsible>
            </SidebarMenu>
          </SidebarGroup>
          <SidebarSeparator />
          <SidebarGroup label="Labels" collapsible>
            <SidebarMenu>
              {place("Work", "tag", 4)}
              {place("Family", "heart")}
            </SidebarMenu>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter>
          <SidebarMenu>{place("Settings", "settings")}</SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset style={{ padding: 16, gap: 8 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <SidebarTrigger />
          <strong>{page}</strong>
        </div>
        <span style={{ color: "var(--nt-text-secondary)" }}>Ctrl+B folds the sidebar.</span>
      </SidebarInset>
    </SidebarProvider>
  )
}

const meta = {
  title: "Navigation/Sidebar from parts",
  component: Example,
  decorators: bothPlatforms,
} satisfies Meta<typeof Example>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Dark: Story = { parameters: dark }
export const Floating: Story = { args: { variant: "floating" } }
export const Inset: Story = { args: { variant: "inset" } }
export const Offcanvas: Story = { args: { collapsible: "offcanvas" } }
