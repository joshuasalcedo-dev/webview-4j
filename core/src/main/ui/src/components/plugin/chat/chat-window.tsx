import * as React from "react"

import { cn } from "../../../lib/utils"

import { useDialogs } from "../../dialog"
import { TitleBar } from "../../title-bar"
import { conversationHeaderMenu } from "./header-menu"
import { ChatComposer } from "./chat-composer"
import { ChatDetails, type ChatDetailsParts } from "./chat-details"
import { ChatHeader } from "./chat-header"
import {
  ChatProvider,
  type ChatProviderProps,
  useChat,
  useIsTyping,
} from "./chat-provider"
import "./chat.css"
import type { ChatConfig } from "./config"
import { ConversationList } from "./conversation-list"
import { MessageList } from "./message-list"
import type { ChatAdapter } from "./ports"
import type { ChatMessage, ChatPerson } from "./types"

type ChatWindowProps = {
  /** With an adapter, the window brings its own ChatProvider; without, it uses the one around it. */
  adapter?: ChatAdapter
  config?: Partial<ChatConfig>
  onEvent?: ChatProviderProps["onEvent"]
  defaultOpenId?: string
  /** The details panel: true (the default) for ChatDetails, false for none, or the app's own panel. */
  details?: React.ReactNode | boolean
  /** The details panel is open (controlled); left out, the window keeps it. */
  detailsOpen?: boolean
  onDetailsOpenChange?: (open: boolean) => void
  /** The app's own sections in ChatDetails (a subscription, orders). */
  detailsSections?: React.ReactNode
  detailsParts?: ChatDetailsParts
  /** Narrower than this (px), the details lie over the conversation as a sheet (720 by default). */
  detailsOverlayBelow?: number
  /** Its own title bar with the window's buttons (true by default; false inside an app's own frame). */
  titleBar?: boolean
  /** The window's title (when the title bar shows one). */
  title?: string
  className?: string
  style?: React.CSSProperties
}

/**
 * A whole chat: conversations on the left, the open one's header, messages and composer, and its details on the
 * right. Everything comes from the ChatProvider (and its adapter) and the ChatConfig; this only lays it out.
 */
function ChatWindow({
  adapter,
  config,
  onEvent,
  defaultOpenId,
  ...props
}: ChatWindowProps) {
  if (adapter) {
    return (
      <ChatProvider
        adapter={adapter}
        config={config}
        onEvent={onEvent}
        defaultOpenId={defaultOpenId}
      >
        <ChatWindowInside {...props} />
      </ChatProvider>
    )
  }
  return <ChatWindowInside {...props} />
}

function ChatWindowInside({
  details = true,
  detailsOpen,
  onDetailsOpenChange,
  detailsSections,
  detailsParts,
  detailsOverlayBelow,
  titleBar = true,
  title = "Chat",
  className,
  style,
}: Omit<ChatWindowProps, "adapter" | "config" | "onEvent" | "defaultOpenId">) {
  const chat = useChat()
  const dialogs = useDialogs()
  const { store, ops } = chat
  const openId = store.openId
  const conversation = store.conversations.find((c) => c.id === openId)
  const messages = React.useMemo(
    () => (openId ? (store.messages[openId]?.items ?? []) : []),
    [store.messages, openId]
  )
  const typing = useIsTyping(openId)
  const [replyTo, setReplyTo] = React.useState<ChatMessage>()
  const [editing, setEditing] = React.useState<ChatMessage>()
  const [ownDetails, setOwnDetails] = React.useState(false)
  const [notes, setNotes] = React.useState<Record<string, string>>({})
  const jump = React.useRef<((id: string) => void) | null>(null)
  const showDetails =
    (detailsOpen ?? ownDetails) &&
    details !== false &&
    chat.features.detailsPanel &&
    !!conversation
  const setDetails = (open: boolean) => {
    if (detailsOpen === undefined) setOwnDetails(open)
    onDetailsOpenChange?.(open)
  }

  // Leaving a conversation drops what was being replied to or edited there.
  const [shownId, setShownId] = React.useState(openId)
  if (shownId !== openId) {
    setShownId(openId)
    setReplyTo(undefined)
    setEditing(undefined)
  }

  // Everyone's names and pictures, for the messages.
  const people = React.useMemo(() => {
    const all: Record<string, ChatPerson> = { [chat.me.id]: chat.me }
    for (const c of store.conversations)
      for (const p of c.people ?? []) all[p.id] = p
    return all
  }, [store.conversations, chat.me])

  const other = conversation?.people?.[0]
  const headerMenu = () =>
    conversation
      ? conversationHeaderMenu(conversation, chat, dialogs, {
          onOpenDetails: details !== false ? () => setDetails(true) : undefined,
        })
      : []

  const header = conversation && (
    <ChatHeader
      title={conversation.title}
      avatar={conversation.avatar}
      online={conversation.online}
      lastSeen={other?.lastSeen}
      typing={
        typing.length > 0
          ? conversation.group
            ? typing[0].name.split(" ")[0]
            : true
          : undefined
      }
      status={
        conversation.group
          ? `${(conversation.people?.length ?? 0) + 1} people`
          : undefined
      }
      tags={conversation.tags}
      onSearch={chat.actions.onSearch}
      onInfo={
        details !== false && chat.features.detailsPanel
          ? () => setDetails(!showDetails)
          : undefined
      }
      infoOpen={showDetails}
      onOpenInfo={() =>
        chat.actions.onOpenProfile
          ? chat.actions.onOpenProfile()
          : setDetails(true)
      }
      menu={headerMenu}
    />
  )

  return (
    <div className={cn("nt-chatwindow", className)} style={style}>
      {titleBar && <TitleBar title={title}>{header}</TitleBar>}
      <div className="nt-chatwindow-body">
        <ConversationList
          className="nt-chatwindow-list"
          conversations={store.conversations}
          selectedId={openId}
          onSelect={ops.open}
          onPin={(id, pinned) => ops.updateConversation(id, { pinned })}
          onMarkUnread={(id, unread) =>
            ops.updateConversation(
              id,
              unread
                ? { markedUnread: true }
                : { unread: 0, markedUnread: false }
            )
          }
          onMute={(id, muted, until) => void ops.mute(id, muted, until)}
          onArchive={(id) =>
            ops.updateConversation(id, {
              archived: !store.conversations.find((c) => c.id === id)?.archived,
            })
          }
          onDelete={ops.removeConversation}
          onTagsChange={(id, tags) => void ops.setTags(id, tags)}
          emptyText={
            store.conversationsLoaded
              ? "No conversations yet."
              : "Loading conversations…"
          }
        />
        <div className="nt-chatwindow-main">
          {!titleBar && header}
          {conversation ? (
            <>
              <MessageList
                className="nt-chatwindow-messages"
                conversationId={conversation.id}
                messages={messages}
                people={people}
                group={!!conversation.group}
                typing={typing}
                hasOlder={store.messages[conversation.id]?.hasOlder ?? false}
                jumpRef={jump}
                onReply={(m) => {
                  setEditing(undefined)
                  setReplyTo(m)
                }}
                onEdit={(m) => {
                  setReplyTo(undefined)
                  setEditing(m)
                }}
              />
              <div className="nt-chatwindow-composer">
                <ChatComposer
                  aria-label={`Message ${conversation.title}`}
                  placeholder={
                    chat.composer?.placeholder ?? chat.say("placeholder")
                  }
                  tools={chat.composer?.tools}
                  sendKey={chat.composer?.sendKey}
                  maxLength={chat.composer?.maxLength}
                  replyingTo={
                    replyTo
                      ? {
                          name:
                            replyTo.senderId === chat.me.id
                              ? "yourself"
                              : (people[replyTo.senderId]?.name ?? "them"),
                          text: replyTo.text ?? "Attachment",
                        }
                      : undefined
                  }
                  onCancelReply={() => setReplyTo(undefined)}
                  editing={
                    editing
                      ? { id: editing.id, text: editing.text ?? "" }
                      : undefined
                  }
                  onCancelEdit={() => setEditing(undefined)}
                  onEditLast={() => {
                    const last = [...messages]
                      .reverse()
                      .find(
                        (m) =>
                          m.senderId === chat.me.id &&
                          m.text &&
                          chat.messageCapabilities(m).canEdit
                      )
                    if (last) setEditing(last)
                  }}
                  onTyping={chat.actions.onTyping}
                  onSend={async ({ text, files }) => {
                    if (editing) {
                      await chat.actions.onEdit?.(editing, text)
                      setEditing(undefined)
                      return
                    }
                    const replying = replyTo?.id
                    setReplyTo(undefined)
                    await chat.actions.onSend?.({
                      text,
                      files,
                      replyTo: replying,
                    })
                  }}
                />
              </div>
            </>
          ) : (
            <div className="nt-chat-empty nt-chatwindow-none">
              Choose a conversation.
            </div>
          )}
        </div>
        {showDetails &&
          conversation &&
          (details === true ? (
            <ChatDetails
              open
              onOpenChange={setDetails}
              conversation={conversation}
              messages={messages}
              note={notes[conversation.id]}
              onNoteChange={(note) =>
                setNotes((n) => ({ ...n, [conversation.id]: note }))
              }
              onTagsChange={(tags) => void ops.setTags(conversation.id, tags)}
              onSearch={chat.actions.onSearch}
              onBlock={other ? () => void ops.block(other.id, true) : undefined}
              onReport={() =>
                void ops.report(
                  { conversationId: conversation.id },
                  "Reported from the details panel"
                )
              }
              onDelete={() => ops.removeConversation(conversation.id)}
              onJumpTo={(id) => jump.current?.(id)}
              sections={detailsSections}
              parts={detailsParts}
              overlayBelow={detailsOverlayBelow}
            />
          ) : (
            details
          ))}
      </div>
    </div>
  )
}

export { ChatWindow }
export type { ChatWindowProps }
