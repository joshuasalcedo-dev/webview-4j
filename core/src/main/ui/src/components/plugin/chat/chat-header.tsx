import * as React from "react"

import { cn } from "../../../lib/utils"
import { Avatar } from "../../avatar"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import { ToolbarButton } from "../../toolbar"
import { mutedWords, useChatCapabilities } from "./capabilities"
import "./chat.css"
import { TagPills } from "./conversation-list"
import { InfoIcon, MutedIcon, PhoneIcon, SearchIcon, VideoIcon } from "./icons"
import { formatLastSeen } from "./time"
import type { ChatTag } from "./types"

type ChatHeaderProps = Omit<React.ComponentProps<"div">, "title"> & {
  /** The person's or the group's name. */
  title: string
  avatar?: string
  online?: boolean
  /** For "Last seen 5 min ago" when not online. */
  lastSeen?: Date | string
  /** Who is typing now: shown instead of the status ("typing…", "Ana is typing…"). */
  typing?: boolean | string
  /** A status line of its own instead (e.g. "3 members, 2 online"). */
  status?: React.ReactNode
  /** Shown as pills after the name. */
  tags?: ChatTag[]
  /** Each one given (and allowed by the chat's capabilities) adds its button. */
  onSearch?: () => void
  onCall?: () => void
  onVideoCall?: () => void
  /** The ⓘ button: opens and closes the details panel. */
  onInfo?: () => void
  /** The details panel is open (ⓘ shows pressed). */
  infoOpen?: boolean
  /** A click on the picture and name (e.g. opens the details or the profile). */
  onOpenInfo?: () => void
  /** The right-click menu on the picture and name (also Shift+F10 or the menu key when it has the focus). */
  menu?: MenuEntry[] | (() => MenuEntry[])
  /** Buttons of the app's own, before the usual ones. */
  actions?: React.ReactNode
}

/**
 * The top of a conversation: picture, name (with tags and a muted mark), and a line saying whether they are
 * online, when they were last seen, or that they are typing; buttons to search the chat, call and show its
 * details (as the chat's capabilities allow). Put it in a TitleBar for one row with the window's buttons (as
 * Teams and Messages do), or above the messages.
 */
function ChatHeader({
  title,
  avatar,
  online,
  lastSeen,
  typing,
  status,
  tags,
  onSearch,
  onCall,
  onVideoCall,
  onInfo,
  infoOpen,
  onOpenInfo,
  menu,
  actions,
  className,
  ...props
}: ChatHeaderProps) {
  const rules = useChatCapabilities()
  const { onContextMenu } = useContextMenu(() => {
    const defaults = typeof menu === "function" ? menu() : (menu ?? [])
    return rules.menus?.header ? rules.menus.header(defaults) : defaults
  })
  const hasMenu = !!menu || !!rules.menus?.header
  const line = typing
    ? typeof typing === "string"
      ? `${typing} is typing…`
      : "typing…"
    : (status ??
      (online ? "Online" : lastSeen ? formatLastSeen(lastSeen) : undefined))
  return (
    <div
      className={cn("nt-chatheader", className)}
      data-webview4j-drag-region
      {...props}
    >
      <button
        type="button"
        className="nt-chatheader-who"
        onClick={onOpenInfo}
        onContextMenu={hasMenu ? onContextMenu : undefined}
        aria-haspopup={hasMenu ? "menu" : undefined}
        aria-label={`${title}${onOpenInfo ? ": details" : ""}`}
      >
        <span className="nt-conv-avatar">
          <Avatar name={title} src={avatar} size={32} />
          {online && <span className="nt-online-dot" aria-hidden="true" />}
        </span>
        <span className="nt-chatheader-text">
          <span className="nt-chatheader-title">
            <span className="nt-chatheader-name">{title}</span>
            {rules.state.muted && (
              <span
                className="nt-conv-mark"
                title={mutedWords(rules.state)}
                aria-label={mutedWords(rules.state)}
              >
                <MutedIcon />
              </span>
            )}
            <TagPills tags={tags} max={3} />
          </span>
          {line != null && (
            <span
              className="nt-chatheader-status"
              data-typing={typing ? "" : undefined}
              data-online={online && !typing ? "" : undefined}
            >
              {line}
            </span>
          )}
        </span>
      </button>
      <span className="nt-chatheader-spacer" data-webview4j-drag-region />
      <span className="nt-chatheader-actions" role="toolbar" aria-label="Chat">
        {actions}
        {onSearch && rules.features.search && (
          <ToolbarButton
            icon={<SearchIcon />}
            label="Search in chat"
            onClick={onSearch}
          />
        )}
        {onVideoCall && rules.can("videoCall") && (
          <ToolbarButton
            icon={<VideoIcon />}
            label="Video call"
            onClick={onVideoCall}
          />
        )}
        {onCall && rules.can("call") && (
          <ToolbarButton icon={<PhoneIcon />} label="Call" onClick={onCall} />
        )}
        {onInfo && (
          <ToolbarButton
            icon={<InfoIcon />}
            label="Details"
            pressed={!!infoOpen}
            aria-expanded={!!infoOpen}
            onClick={onInfo}
          />
        )}
      </span>
    </div>
  )
}

export { ChatHeader }
export type { ChatHeaderProps }
