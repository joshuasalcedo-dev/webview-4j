import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Avatar } from "./avatar"
import { List, ListItem } from "./list"

const chats = [
  { id: "1", name: "Ana Reyes", last: "Thank you so much!! 💕", time: "9:41" },
  { id: "2", name: "Marco Silva", last: "Sent you a tip", time: "9:12" },
  { id: "3", name: "Jules", last: "When is the next live?", time: "Yesterday" },
  { id: "4", name: "Kim Park", last: "Typing…", time: "Mon" },
]

function Chats({ initial = "1" }: { initial?: string }) {
  const [selected, setSelected] = React.useState(initial)
  return (
    <List aria-label="Chats" style={{ width: 300 }}>
      {chats.map((chat) => (
        <ListItem
          key={chat.id}
          title={chat.name}
          subtitle={chat.last}
          trailing={chat.time}
          leading={<Avatar name={chat.name} />}
          selected={selected === chat.id}
          onSelect={() => setSelected(chat.id)}
        />
      ))}
    </List>
  )
}

const meta = {
  title: "Native/List",
  component: Chats,
  decorators: [bothPlatforms],
} satisfies Meta<typeof Chats>
export default meta
type Story = StoryObj<typeof meta>

/** A chat list: click a row (or Enter/Space on it) to choose it. */
export const ChatList: Story = {}
export const ChatListDark: Story = { parameters: dark }
export const NothingChosen: Story = { args: { initial: "" } }
export const TextOnly: Story = {
  render: () => (
    <List aria-label="Folders" style={{ width: 220 }}>
      <ListItem title="Inbox" trailing="12" selected />
      <ListItem title="Tipped" trailing="3" />
      <ListItem title="Archived" />
    </List>
  ),
}
