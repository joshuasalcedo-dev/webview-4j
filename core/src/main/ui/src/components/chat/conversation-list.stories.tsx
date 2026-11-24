import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../stories/side-by-side"
import { ChatProvider } from "./chat-provider"
import { ConversationList } from "./conversation-list"
import { seedConversations, storyConfig } from "./sample-config"
import type { Conversation } from "./types"

/**
 * Categories above the list (counts change as you mark read or unread), pill types (an exclusive status
 * group, an app-only "Tipped" icon pill), swipes and the right-click menu with Tags.
 */
function Conversations({ empty = false }: { empty?: boolean }) {
  const [list, setList] = React.useState<Conversation[]>(
    empty ? [] : seedConversations()
  )
  const [selected, setSelected] = React.useState("ana")
  const change = (id: string, edit: Partial<Conversation>) =>
    setList((now) => now.map((c) => (c.id === id ? { ...c, ...edit } : c)))
  return (
    <ChatProvider config={storyConfig}>
      <ConversationList
        style={{ height: 520, width: "100%" }}
        conversations={list}
        selectedId={selected}
        onSelect={(id) => {
          setSelected(id)
          change(id, { unread: 0, markedUnread: false })
        }}
        onPin={(id, pinned) => change(id, { pinned })}
        onMarkUnread={(id, unread) =>
          change(
            id,
            unread ? { markedUnread: true } : { unread: 0, markedUnread: false }
          )
        }
        onMute={(id, muted, until) => change(id, { muted, mutedUntil: until })}
        onArchive={(id) =>
          change(id, { archived: !list.find((c) => c.id === id)?.archived })
        }
        onDelete={(id) => setList((now) => now.filter((c) => c.id !== id))}
        onTagsChange={(id, tags) => change(id, { tags })}
        emptyText="No conversations yet. Start one with New chat."
      />
    </ChatProvider>
  )
}

const meta = {
  title: "Chat/ConversationList",
  component: Conversations,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof Conversations>
export default meta
type Story = StoryObj<typeof meta>

export const CategoriesAndPills: Story = {}
export const CategoriesAndPillsDark: Story = {
  parameters: { nativeChrome: true, ...dark },
}
export const Empty: Story = { args: { empty: true } }
