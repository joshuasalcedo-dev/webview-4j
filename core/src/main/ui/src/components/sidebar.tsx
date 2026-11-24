import * as React from "react"

import { Hamburger } from "../lib/icons"
import { cn } from "../lib/utils"
import { type MenuEntry, useContextMenu } from "./context-menu"

type SidebarProps = React.ComponentProps<"nav"> & {
  /** What it lists, for screen readers (e.g. "Sections"). */
  "aria-label": string
  /**
   * Windows: folded to an icon strip (counts become dots). macOS: hidden. With onCollapsedChange, Windows
   * shows its ☰ button; on a Mac the Toolbar's sidebar button does it (Toolbar onToggleSidebar).
   */
  collapsed?: boolean
  onCollapsedChange?: (collapsed: boolean) => void
  /** Pinned under the sections, e.g. a Settings item. */
  footer?: React.ReactNode
}

/** Moves the focus between the items with the arrow keys, as the systems' sidebars do. */
function moveFocus(event: React.KeyboardEvent<HTMLElement>) {
  const by = event.key === "ArrowDown" ? 1 : event.key === "ArrowUp" ? -1 : 0
  if (!by) return
  const items = Array.from(
    event.currentTarget.querySelectorAll<HTMLButtonElement>(
      ".nt-sidebar-item:not(:disabled)"
    )
  )
  const at = items.indexOf(document.activeElement as HTMLButtonElement)
  if (at === -1) return
  event.preventDefault()
  items[(at + by + items.length) % items.length].focus()
}

/**
 * A window's main sections, down its side: Windows 11's NavigationView pane (the accent pill on the current
 * item, ☰ folds it to icons), macOS' translucent sidebar (small grey section headings, accent icons).
 * Put SidebarSections in it, SidebarItems in those.
 */
function Sidebar({
  collapsed = false,
  onCollapsedChange,
  footer,
  className,
  children,
  onKeyDown,
  ...props
}: SidebarProps) {
  return (
    <nav
      data-collapsed={collapsed || undefined}
      className={cn("nt-sidebar", className)}
      onKeyDown={(event) => {
        moveFocus(event)
        onKeyDown?.(event)
      }}
      {...props}
    >
      {onCollapsedChange && (
        <button
          type="button"
          className="nt-sidebar-toggle"
          aria-label={collapsed ? "Open navigation" : "Close navigation"}
          aria-expanded={!collapsed}
          title={collapsed ? "Open navigation" : "Close navigation"}
          onClick={() => onCollapsedChange(!collapsed)}
        >
          <Hamburger />
        </button>
      )}
      <div className="nt-sidebar-scroll">{children}</div>
      {footer != null && <div className="nt-sidebar-footer">{footer}</div>}
    </nav>
  )
}

type SidebarSectionProps = Omit<React.ComponentProps<"section">, "title"> & {
  /** Its heading (macOS: small and grey; Windows: a line when the pane is folded). Left out: no heading. */
  title?: React.ReactNode
}

function SidebarSection({
  title,
  className,
  children,
  ...props
}: SidebarSectionProps) {
  const id = React.useId()
  return (
    <section
      aria-labelledby={title != null ? id : undefined}
      className={cn("nt-sidebar-section", className)}
      {...props}
    >
      {title != null && (
        <h4 id={id} className="nt-sidebar-heading">
          {title}
        </h4>
      )}
      <ul role="list">{children}</ul>
    </section>
  )
}

type SidebarItemProps = Omit<React.ComponentProps<"button">, "onSelect"> & {
  label: string
  /** 16px; on a Mac it takes the accent colour. */
  icon?: React.ReactNode
  /** A count (unread, new); 0 or none shows nothing. Windows: an accent pill (a dot when folded); macOS: grey. */
  badge?: number
  /** It is the section shown now. */
  selected?: boolean
  onSelect?: () => void
  /** Its right-click menu; a function builds it when it opens. */
  menu?: MenuEntry[] | (() => MenuEntry[])
}

function SidebarItem({
  label,
  icon,
  badge,
  selected = false,
  onSelect,
  menu,
  className,
  ...props
}: SidebarItemProps) {
  const { onContextMenu } = useContextMenu(menu ?? [])
  return (
    <li>
      <button
        type="button"
        aria-current={selected ? "page" : undefined}
        title={label}
        className={cn("nt-sidebar-item", className)}
        onClick={onSelect}
        onContextMenu={menu ? onContextMenu : undefined}
        {...props}
      >
        {icon != null && (
          <span className="nt-sidebar-icon" aria-hidden="true">
            {icon}
          </span>
        )}
        <span className="nt-sidebar-label">{label}</span>
        {badge ? (
          <span className="nt-sidebar-badge" aria-label={`${badge} new`}>
            {badge > 99 ? "99+" : badge}
          </span>
        ) : null}
      </button>
    </li>
  )
}

export { Sidebar, SidebarItem, SidebarSection }
export type { SidebarItemProps, SidebarProps, SidebarSectionProps }
