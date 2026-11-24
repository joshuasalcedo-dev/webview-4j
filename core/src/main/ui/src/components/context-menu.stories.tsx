import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Avatar } from "./avatar"
import { ContextMenu, type MenuEntry } from "./context-menu"
import { List, ListItem } from "./list"
import { TextField } from "./text-field"
import { TitleBar } from "./title-bar"

/** Right-click (or Shift+F10 / the menu key) each part; what was chosen shows underneath. */
function Everything() {
  const [last, setLast] = React.useState("nothing yet")
  const [pinned, setPinned] = React.useState(false)
  const said = (what: string) => () => setLast(what)
  const chatMenu = (): MenuEntry[] => [
    {
      label: pinned ? "Unpin" : "Pin",
      shortcut: "Mod+P",
      onSelect: () => (setPinned(!pinned), setLast(pinned ? "Unpin" : "Pin")),
    },
    {
      label: "Mark as unread",
      shortcut: "Mod+U",
      onSelect: said("Mark as unread"),
    },
    { label: "Mute notifications", checked: false, onSelect: said("Mute") },
    "separator",
    { label: "Archive", onSelect: said("Archive") },
    { label: "Delete chat…", shortcut: "Delete", onSelect: said("Delete") },
  ]
  return (
    <div style={{ display: "grid", gap: 14, width: "100%" }}>
      <TitleBar
        title="Right-click this title bar (Windows)"
        onMinimize={said("Minimize")}
        onMaximize={said("Maximize")}
        onClose={said("Close")}
      />
      <List aria-label="Chats" style={{ width: 300 }}>
        <ListItem
          title={pinned ? "📌 Ana Reyes" : "Ana Reyes"}
          subtitle="Right-click me"
          leading={<Avatar name="Ana Reyes" />}
          menu={chatMenu}
          selected
        />
        <ListItem
          title="Marco Silva"
          subtitle="No menu of its own"
          leading={<Avatar name="Marco Silva" />}
        />
      </List>
      <TextField
        label="A text box: the edit menu"
        defaultValue="Select some of this, then right-click"
        style={{ width: 300 }}
      />
      <ContextMenu
        entries={[
          { label: "View profile", onSelect: said("View profile") },
          {
            label: "Copy username",
            shortcut: "Mod+Shift+C",
            onSelect: said("Copy username"),
          },
        ]}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Avatar name="Kim Park" />
          <span>Any element, with ContextMenu around it</span>
        </div>
      </ContextMenu>
      <p className="nt-selectable" style={{ margin: 0 }}>
        Selectable text: select a few words and right-click for Copy. Elsewhere
        a right-click shows nothing.
      </p>
      <span className="nt-secondary">Chosen: {last}</span>
    </div>
  )
}

const meta = {
  title: "Native/ContextMenu",
  component: Everything,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Everything>
export default meta
type Story = StoryObj<typeof meta>

export const RightClick: Story = {}
export const RightClickDark: Story = {
  parameters: { nativeStretch: true, ...dark },
}
