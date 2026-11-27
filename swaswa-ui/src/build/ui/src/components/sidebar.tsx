import * as React from "react"

import { ChevronRight, Hamburger, Magnifier, SidebarIcon } from "../lib/icons"
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
  /**
   * Built from parts, as shadcn/ui's sidebar is: SidebarHeader, SidebarContent (SidebarGroups of SidebarMenus),
   * SidebarFooter. Found by itself when those are its children; say it when they come wrapped.
   */
  composed?: boolean
  /** Built from parts, how it folds: to its icons, off the window, or never. */
  collapsible?: "icon" | "offcanvas" | "none"
  side?: "left" | "right"
  /** Built from parts: along the window's edge, floating on it, or beside an inset page (SidebarInset). */
  variant?: "sidebar" | "floating" | "inset"
}

/** Moves the focus between the items with the arrow keys, as the systems' sidebars do. */
function moveFocus(event: React.KeyboardEvent<HTMLElement>) {
  const by = event.key === "ArrowDown" ? 1 : event.key === "ArrowUp" ? -1 : 0
  if (!by) return
  const items = Array.from(
    event.currentTarget.querySelectorAll<HTMLButtonElement>(
      ".nt-sidebar-item:not(:disabled), .nt-sb-button:not(:disabled), .nt-sb-sub-button:not(:disabled)"
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
  collapsed: collapsedProp,
  onCollapsedChange: onCollapsedChangeProp,
  footer,
  composed: composedProp,
  collapsible = "icon",
  side = "left",
  variant = "sidebar",
  className,
  children,
  onKeyDown,
  ...props
}: SidebarProps) {
  const context = React.useContext(SidebarContext)
  const collapsed = collapsedProp ?? (context ? !context.open : false)
  const onCollapsedChange =
    onCollapsedChangeProp ?? (context ? (folded: boolean) => context.setOpen(!folded) : undefined)
  const composed =
    composedProp ??
    React.Children.toArray(children).some(
      (child) => React.isValidElement(child) && PARTS.has(child.type as React.ElementType)
    )
  const own = React.useRef<HTMLElement>(null)
  const listens = composed && !context && !!onCollapsedChange && collapsible !== "none"
  // Ctrl+B (Cmd+B) folds and unfolds it, as shadcn/ui's does; a SidebarProvider listens for its own. With several
  // sidebars on the page, the one beside the focus answers, else the first.
  React.useEffect(() => {
    if (!listens || !onCollapsedChange) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() !== "b" || !(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey) return
      const all = [...document.querySelectorAll<HTMLElement>("nav.nt-sidebar[data-ctrl-b]")]
      const near = all.filter((nav) => nav.parentElement?.contains(document.activeElement))
      if ((near.at(-1) ?? all[0]) !== own.current) return
      event.preventDefault()
      onCollapsedChange(!collapsed)
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [listens, onCollapsedChange, collapsed])

  if (composed) {
    const folded = collapsed && collapsible !== "none"
    return (
      <nav
        ref={own}
        data-ctrl-b={listens || undefined}
        data-composed=""
        data-collapsed={folded || undefined}
        data-collapsible={collapsible}
        data-side={side}
        data-variant={variant}
        className={cn("nt-sidebar", className)}
        onKeyDown={(event) => {
          moveFocus(event)
          onKeyDown?.(event)
        }}
        {...props}
      >
        <SidebarState.Provider value={{ collapsed: folded, collapsible }}>{children}</SidebarState.Provider>
      </nav>
    )
  }
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

// ---- Built from parts, as shadcn/ui's sidebar is ------------------------------------------------------------

type SidebarContextValue = { open: boolean; setOpen: (open: boolean) => void; toggle: () => void }

/** Whether the sidebar is open, for a SidebarTrigger anywhere under the SidebarProvider. */
const SidebarContext = React.createContext<SidebarContextValue | null>(null)

/** What the parts need to know: folded (to icons) or not. */
const SidebarState = React.createContext<{ collapsed: boolean; collapsible: string }>({ collapsed: false, collapsible: "none" })

function useSidebar() {
  const context = React.useContext(SidebarContext)
  if (!context) throw new Error("useSidebar needs a SidebarProvider around it")
  return context
}

type SidebarProviderProps = React.ComponentProps<"div"> & {
  defaultOpen?: boolean
  open?: boolean
  onOpenChange?: (open: boolean) => void
}

/** Holds whether the sidebar is open (Ctrl+B or Cmd+B toggles it), around the Sidebar and its SidebarInset. */
function SidebarProvider({ defaultOpen = true, open: openProp, onOpenChange, className, children, ...props }: SidebarProviderProps) {
  const [own, setOwn] = React.useState(defaultOpen)
  const open = openProp ?? own
  const setOpen = React.useCallback(
    (next: boolean) => {
      if (openProp === undefined) setOwn(next)
      onOpenChange?.(next)
    },
    [openProp, onOpenChange]
  )
  const toggle = React.useCallback(() => setOpen(!open), [open, setOpen])
  React.useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() === "b" && (event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey) {
        event.preventDefault()
        toggle()
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [toggle])
  return (
    <SidebarContext.Provider value={{ open, setOpen, toggle }}>
      <div data-open={open || undefined} className={cn("nt-sb-wrapper", className)} {...props}>
        {children}
      </div>
    </SidebarContext.Provider>
  )
}

/** The page beside the sidebar (with variant "inset", a raised surface). */
function SidebarInset({ className, ...props }: React.ComponentProps<"main">) {
  return <main className={cn("nt-sb-inset", className)} {...props} />
}

/** The button that opens and closes the sidebar: the SidebarProvider's, or onClick's. */
function SidebarTrigger({ className, onClick, ...props }: React.ComponentProps<"button">) {
  const context = React.useContext(SidebarContext)
  return (
    <button
      type="button"
      aria-label="Toggle the sidebar"
      title="Toggle the sidebar (Ctrl+B)"
      aria-expanded={context ? context.open : undefined}
      className={cn("nt-sb-trigger", className)}
      onClick={(event) => {
        onClick?.(event)
        if (!event.defaultPrevented) context?.toggle()
      }}
      {...props}
    >
      <SidebarIcon />
    </button>
  )
}

/** A thin edge on the sidebar's side: clicking it opens or closes it. */
function SidebarRail({ className, ...props }: React.ComponentProps<"button">) {
  const context = React.useContext(SidebarContext)
  return (
    <button
      type="button"
      tabIndex={-1}
      aria-label="Toggle the sidebar"
      title="Toggle the sidebar"
      className={cn("nt-sb-rail", className)}
      onClick={() => context?.toggle()}
      {...props}
    />
  )
}

/** On top, not scrolled: the app's name, a team switcher, a search. */
function SidebarHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("nt-sb-header", className)} {...props} />
}

/** At the bottom, not scrolled: the account, settings. */
function SidebarFooter({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("nt-sb-footer", className)} {...props} />
}

/** What scrolls: the SidebarGroups. */
function SidebarContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("nt-sb-content", className)} {...props} />
}

function SidebarSeparator({ className, ...props }: React.ComponentProps<"hr">) {
  return <hr className={cn("nt-sb-separator", className)} {...props} />
}

/** A search for the sidebar's header. */
function SidebarInput({ className, ...props }: React.ComponentProps<"input">) {
  return (
    <div className="nt-sb-input-wrap">
      <Magnifier />
      <input type="search" className={cn("nt-sb-input", className)} {...props} />
    </div>
  )
}

type SidebarGroupProps = Omit<React.ComponentProps<"div">, "title"> & {
  /** Its small heading; with collapsible, a button that folds the group. */
  label?: React.ReactNode
  collapsible?: boolean
  defaultOpen?: boolean
  /** A button at the heading's end, e.g. "+" to add a project (a SidebarGroupAction). */
  action?: React.ReactNode
}

/** A part of the sidebar under a small heading. Its items go in a SidebarMenu. */
function SidebarGroup({ label, collapsible = false, defaultOpen = true, action, className, children, ...props }: SidebarGroupProps) {
  const [open, setOpen] = React.useState(defaultOpen)
  const id = React.useId()
  const shown = !collapsible || open
  return (
    <div data-open={shown || undefined} className={cn("nt-sb-group", className)} role="group" aria-labelledby={label != null ? id : undefined} {...props}>
      {label != null &&
        (collapsible ? (
          <button type="button" id={id} className="nt-sb-group-label" aria-expanded={open} onClick={() => setOpen(!open)}>
            <span className="nt-sb-label">{label}</span>
            <span className="nt-sb-chevron" aria-hidden="true">
              <ChevronRight />
            </span>
          </button>
        ) : (
          <SidebarGroupLabel id={id}>{label}</SidebarGroupLabel>
        ))}
      {action}
      {shown && <SidebarGroupContent>{children}</SidebarGroupContent>}
    </div>
  )
}

function SidebarGroupLabel({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("nt-sb-group-label", className)} {...props} />
}

function SidebarGroupContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("nt-sb-group-content", className)} {...props} />
}

function SidebarGroupAction({ className, ...props }: React.ComponentProps<"button">) {
  return <button type="button" className={cn("nt-sb-group-action", className)} {...props} />
}

function SidebarMenu({ className, ...props }: React.ComponentProps<"ul">) {
  return <ul role="list" className={cn("nt-sb-menu", className)} {...props} />
}

function SidebarMenuItem({ className, ...props }: React.ComponentProps<"li">) {
  return <li className={cn("nt-sb-item", className)} {...props} />
}

type SidebarMenuButtonProps = React.ComponentProps<"button"> & {
  /** It is the page shown now. */
  isActive?: boolean
  /** 16px before the label; at size "lg", a 32px tile (the app's logo). */
  icon?: React.ReactNode
  /** A second, quieter line (size "lg"). */
  description?: React.ReactNode
  /** A count, or anything small, at its end. */
  badge?: React.ReactNode
  size?: "sm" | "default" | "lg"
  /** Shown on hover when the sidebar is folded to icons (the label by default). */
  tooltip?: string
}

/** One place in a SidebarMenu: an icon, its name, maybe a count. */
function SidebarMenuButton({ isActive = false, icon, description, badge, size = "default", tooltip, className, children, ...props }: SidebarMenuButtonProps) {
  const { collapsed } = React.useContext(SidebarState)
  const label = typeof children === "string" ? children : undefined
  return (
    <button
      type="button"
      data-active={isActive || undefined}
      data-size={size}
      aria-current={isActive ? "page" : undefined}
      title={collapsed ? (tooltip ?? label) : tooltip}
      className={cn("nt-sb-button", className)}
      {...props}
    >
      {icon != null && (
        <span className="nt-sb-icon" aria-hidden="true">
          {icon}
        </span>
      )}
      {description != null ? (
        <span className="nt-sb-label nt-sb-two-lines">
          <span className="nt-sb-title">{children}</span>
          <span className="nt-sb-description">{description}</span>
        </span>
      ) : (
        <span className="nt-sb-label">{children}</span>
      )}
      {badge != null && badge !== 0 && badge !== "" && <span className="nt-sb-badge">{badge}</span>}
    </button>
  )
}

/** A small button at a menu item's end, shown on hover (e.g. its options). */
function SidebarMenuAction({ className, ...props }: React.ComponentProps<"button">) {
  return <button type="button" className={cn("nt-sb-action", className)} {...props} />
}

function SidebarMenuBadge({ className, ...props }: React.ComponentProps<"span">) {
  return <span className={cn("nt-sb-badge nt-sb-badge-end", className)} {...props} />
}

/** Places under a menu item, indented behind a line. */
function SidebarMenuSub({ className, ...props }: React.ComponentProps<"ul">) {
  return <ul role="list" className={cn("nt-sb-sub", className)} {...props} />
}

function SidebarMenuSubItem({ className, ...props }: React.ComponentProps<"li">) {
  return <li className={cn("nt-sb-sub-item", className)} {...props} />
}

function SidebarMenuSubButton({ isActive = false, className, ...props }: React.ComponentProps<"button"> & { isActive?: boolean }) {
  return (
    <button
      type="button"
      data-active={isActive || undefined}
      aria-current={isActive ? "page" : undefined}
      className={cn("nt-sb-sub-button", className)}
      {...props}
    />
  )
}

type SidebarMenuCollapsibleProps = Omit<SidebarMenuButtonProps, "onClick"> & {
  defaultOpen?: boolean
  /** Its SidebarMenuSubItems. */
  items: React.ReactNode
}

/** A menu item that opens to the places under it (a chevron turns as it does). */
function SidebarMenuCollapsible({ defaultOpen = false, items, children, ...button }: SidebarMenuCollapsibleProps) {
  const [open, setOpen] = React.useState(defaultOpen)
  return (
    <SidebarMenuItem data-open={open || undefined}>
      <SidebarMenuButton
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        {...button}
        badge={
          <span className="nt-sb-chevron" aria-hidden="true">
            <ChevronRight />
          </span>
        }
      >
        {children}
      </SidebarMenuButton>
      {open && <SidebarMenuSub>{items}</SidebarMenuSub>}
    </SidebarMenuItem>
  )
}

/** The parts that make a Sidebar one built from parts. */
const PARTS = new Set<React.ElementType>([SidebarHeader, SidebarContent, SidebarFooter, SidebarGroup])

export {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupAction,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInput,
  SidebarInset,
  SidebarItem,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuCollapsible,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarProvider,
  SidebarRail,
  SidebarSection,
  SidebarSeparator,
  SidebarTrigger,
  useSidebar,
}
export type {
  SidebarGroupProps,
  SidebarItemProps,
  SidebarMenuButtonProps,
  SidebarMenuCollapsibleProps,
  SidebarProps,
  SidebarProviderProps,
  SidebarSectionProps,
}
