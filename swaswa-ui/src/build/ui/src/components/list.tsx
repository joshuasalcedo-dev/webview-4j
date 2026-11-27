import * as React from "react"

import { cn } from "../lib/utils"
import { type MenuEntry, useContextMenu } from "./context-menu"

type ListProps = React.ComponentProps<"ul"> & {
  /** What the list is, for screen readers (e.g. "Chats"). */
  "aria-label": string
}

/**
 * A column of rows, one of which can be chosen: a chat list, a sidebar. macOS fills the chosen row with
 * the accent colour; Windows tints it and puts an accent pill at its start.
 */
function List({ className, ...props }: ListProps) {
  return <ul role="listbox" className={cn("nt-list", className)} {...props} />
}

type ListItemProps = Omit<React.ComponentProps<"li">, "title"> & {
  title: React.ReactNode
  /** A second, quieter line (e.g. the last message). */
  subtitle?: React.ReactNode
  /** Before the text: an Avatar or an icon. */
  leading?: React.ReactNode
  /** After the text: a time, a count. */
  trailing?: React.ReactNode
  selected?: boolean
  /** Chosen, by a click or Enter/Space. */
  onSelect?: () => void
  /**
   * Its right-click menu (Pin, Mark as unread, Archive, ...); a function builds it when it opens. Also opens
   * with Shift+F10 or the menu key. macOS rings the row while its menu is open.
   */
  menu?: MenuEntry[] | (() => MenuEntry[])
}

function ListItem({
  title,
  subtitle,
  leading,
  trailing,
  selected = false,
  onSelect,
  menu,
  className,
  onKeyDown,
  ...props
}: ListItemProps) {
  const { onContextMenu } = useContextMenu(menu ?? [])
  return (
    <li
      role="option"
      aria-selected={selected}
      aria-haspopup={menu ? "menu" : undefined}
      tabIndex={0}
      data-leading={leading != null}
      className={cn("nt-list-item", className)}
      onClick={onSelect}
      onContextMenu={menu ? onContextMenu : undefined}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault()
          onSelect?.()
        }
        onKeyDown?.(event)
      }}
      {...props}
    >
      {leading}
      <span className="nt-list-item-text">
        <span className="nt-list-item-title">{title}</span>
        {subtitle != null && (
          <span className="nt-list-item-subtitle">{subtitle}</span>
        )}
      </span>
      {trailing != null ? (
        <span className="nt-list-item-trailing">{trailing}</span>
      ) : (
        <span />
      )}
    </li>
  )
}

export { List, ListItem }
export type { ListItemProps, ListProps }
