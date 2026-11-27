import type { MenuEntry } from "../context-menu"
import type { DialogsApi } from "../dialog"
import { muteMenuItem } from "./capabilities"
import type { ChatProviderValue } from "./chat-provider"
import { tagEntries } from "./conversation-list"
import type { Conversation } from "./types"

/**
 * The conversation header's right-click menu (on the picture and name): View profile, Search in chat, Mute
 * notifications (ticked while muted), Pin chat, Tags, Clear chat history…, Block…, Delete chat…; the risky
 * ones are asked first. It works through the ChatProvider's operations, so any app gets the same menu;
 * ChatConfig.menus.header can still change it.
 */
function conversationHeaderMenu(
  c: Conversation,
  chat: ChatProviderValue,
  dialogs: DialogsApi,
  options: { onOpenDetails?: () => void } = {}
): MenuEntry[] {
  const { ops } = chat
  const other = c.people?.[0]
  const confirm = (title: string, message: string, label: string) =>
    dialogs.confirm({
      title,
      message,
      kind: "warning",
      confirm: label,
      destructive: true,
    })
  const entries: MenuEntry[] = []
  const openProfile = chat.actions.onOpenProfile ?? options.onOpenDetails
  if (openProfile)
    entries.push({ label: "View profile", onSelect: openProfile })
  if (chat.actions.onSearch)
    entries.push({
      label: "Search in chat",
      shortcut: "Mod+F",
      onSelect: () => chat.actions.onSearch?.(),
    })
  entries.push(
    ...muteMenuItem(
      !!c.muted,
      (muted, until) => void ops.mute(c.id, muted, until),
      dialogs,
      chat.can("mute")
    )
  )
  entries.push({
    label: c.pinned ? "Unpin chat" : "Pin chat",
    onSelect: () => ops.updateConversation(c.id, { pinned: !c.pinned }),
  })
  if (chat.can("addTags"))
    entries.push(
      ...tagEntries(
        c,
        chat.pillTypes ?? [],
        !!chat.allowCustomTags,
        (tags) => void ops.setTags(c.id, tags),
        dialogs
      )
    )
  entries.push("separator", {
    label: "Clear chat history…",
    onSelect: async () => {
      if (
        await confirm(
          `Clear the chat with ${c.title}?`,
          "Its messages are removed from this device. They stay with the other person.",
          "Clear"
        )
      )
        ops.clearMessages(c.id)
    },
  })
  if (other && chat.can("block"))
    entries.push({
      label: "Block…",
      onSelect: async () => {
        if (
          await confirm(
            `Block ${c.title}?`,
            "They won't be able to send you messages. You can unblock them later.",
            "Block"
          )
        )
          void ops.block(other.id, true)
      },
    })
  entries.push({
    label: "Delete chat…",
    onSelect: async () => {
      if (
        await confirm(
          `Delete the chat with ${c.title}?`,
          "Its messages are deleted from this device. This can't be undone.",
          "Delete"
        )
      )
        ops.removeConversation(c.id)
    },
  })
  return entries
}

export { conversationHeaderMenu }
