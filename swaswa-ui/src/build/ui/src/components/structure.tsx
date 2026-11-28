// shadcn/ui's structural pieces the kit lacked: AspectRatio, ButtonGroup, InputGroup, NavigationMenu, Menubar.
import * as React from "react"

import { NamedIcon } from "../lib/named-icons"
import { cn } from "../lib/utils"
import { type MenuEntry, useContextMenu } from "./context-menu"
import { Portal, useDismiss, useFloating } from "./overlays"
import "./more-controls.css"

// --- AspectRatio -------------------------------------------------------------------------------------

type AspectRatioProps = {
  /** Width over height: 16/9 (the default), 4/3, 1. */
  ratio?: number
  children?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

/** Keeps what it holds at one shape as its width changes: a picture, a video, a map. */
function AspectRatio({ ratio = 16 / 9, children, className, style }: AspectRatioProps) {
  return (
    <div className={cn("nt-aspect", className)} style={{ aspectRatio: String(ratio > 0 ? ratio : 16 / 9), ...style }}>
      {children}
    </div>
  )
}

// --- ButtonGroup -------------------------------------------------------------------------------------

type ButtonGroupProps = {
  /** What the buttons do together, for screen readers ("Text alignment"). */
  label?: string
  /** Side by side (the default), or one under the other. */
  orientation?: "horizontal" | "vertical"
  children?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

/** Buttons joined into one control: shared edges, one outline. */
function ButtonGroup({ label, orientation = "horizontal", children, className, style }: ButtonGroupProps) {
  return (
    <div role="group" aria-label={label} className={cn("nt-button-group", className)} data-orientation={orientation} style={style}>
      {children}
    </div>
  )
}

// --- InputGroup --------------------------------------------------------------------------------------

type InputGroupProps = {
  label?: string
  value?: string
  onChange?: (value: string) => void
  /** Pressed Enter in it. */
  onSubmit?: (value: string) => void
  placeholder?: string
  /** Before what is typed: "https://", "$", an icon or a select. */
  before?: React.ReactNode
  /** After it: ".com", "kg", a button. */
  after?: React.ReactNode
  /** An icon by name at its start (search, mail...). */
  icon?: string
  disabled?: boolean
  className?: string
  style?: React.CSSProperties
}

/** A text field with something joined to it on either side: a prefix, a unit, an icon, a button. */
function InputGroup({ label, value, onChange, onSubmit, placeholder, before, after, icon, disabled, className, style }: InputGroupProps) {
  const id = React.useId()
  // What is typed stays while it has the focus: an answer from Java a moment late cannot undo a key typed since.
  const [text, setText] = React.useState(value ?? "")
  const focused = React.useRef(false)
  React.useEffect(() => {
    if (value !== undefined && !focused.current) setText(value)
  }, [value])
  return (
    <div className={cn("nt-field", className)} style={style}>
      {label && (
        <label className="nt-field-label" htmlFor={id}>
          {label}
        </label>
      )}
      <div className="nt-input-group" data-disabled={disabled || undefined}>
        {before != null && <span className="nt-input-group-addon">{before}</span>}
        <span className="nt-input nt-input-box nt-input-group-field">
          {icon && <NamedIcon name={icon} />}
          <input
            id={id}
            value={text}
            placeholder={placeholder}
            disabled={disabled}
            onFocus={() => (focused.current = true)}
            onBlur={() => {
              focused.current = false
              if (value !== undefined) setText(value)
            }}
            onChange={(e) => {
              setText(e.target.value)
              onChange?.(e.target.value)
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") onSubmit?.(e.currentTarget.value)
            }}
          />
        </span>
        {after != null && <span className="nt-input-group-addon">{after}</span>}
      </div>
    </div>
  )
}

// --- NavigationMenu ----------------------------------------------------------------------------------

type NavigationLink = { id: string; label: string; description?: string }
type NavigationItem = { id: string; label: string; links?: NavigationLink[] }

type NavigationMenuProps = {
  /** The top items; one with links opens a panel of them, with what each is. */
  items: NavigationItem[]
  /** The id of the page shown now (an item's or a link's). */
  current?: string
  /** An item or a link was chosen: its id. */
  onNavigate?: (id: string) => void
  className?: string
  style?: React.CSSProperties
}

/** A site's or an app's top navigation: items, some opening a panel of links with a line on what each is. */
function NavigationMenu({ items, current, onNavigate, className, style }: NavigationMenuProps) {
  const [open, setOpen] = React.useState<string | null>(null)
  const anchors = React.useRef<Record<string, HTMLButtonElement | null>>({})
  const anchor = React.useRef<HTMLElement | null>(null)
  const panel = React.useRef<HTMLDivElement>(null)
  const nav = React.useRef<HTMLElement>(null)
  anchor.current = open ? anchors.current[open] ?? null : null
  const at = useFloating(anchor, panel, open != null, "bottom")
  useDismiss(open != null, () => setOpen(null), [nav, panel])
  const shown = items.find((i) => i.id === open)
  const go = (id: string) => {
    setOpen(null)
    onNavigate?.(id)
  }
  return (
    <nav ref={nav} aria-label="Main" className={cn("nt-navmenu", className)} style={style}>
      {items.map((item) => {
        const here = current === item.id || item.links?.some((l) => l.id === current)
        return (
          <button
            key={item.id}
            ref={(el) => {
              anchors.current[item.id] = el
            }}
            type="button"
            className="nt-navmenu-item"
            aria-current={here ? "page" : undefined}
            aria-expanded={item.links ? open === item.id : undefined}
            onClick={() => (item.links?.length ? setOpen(open === item.id ? null : item.id) : go(item.id))}
            onPointerEnter={() => item.links?.length && open != null && setOpen(item.id)}
          >
            {item.label}
            {item.links?.length ? (
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.4} aria-hidden>
                <path d="m4.5 6.5 3.5 3.5 3.5-3.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            ) : null}
          </button>
        )
      })}
      {shown?.links && (
        <Portal>
          <div ref={panel} className="nt-popover nt-navmenu-panel" style={at ? { top: at.top, left: at.left } : { visibility: "hidden", top: 0, left: 0 }}>
            {shown.links.map((l) => (
              <button key={l.id} type="button" className="nt-navmenu-link" aria-current={current === l.id ? "page" : undefined} onClick={() => go(l.id)}>
                <span className="nt-navmenu-link-label">{l.label}</span>
                {l.description && <span className="nt-navmenu-link-description">{l.description}</span>}
              </button>
            ))}
          </div>
        </Portal>
      )}
    </nav>
  )
}

// --- Menubar -----------------------------------------------------------------------------------------

type MenubarMenu = { label: string; entries: MenuEntry[] | (() => MenuEntry[]) }

/** One menu of a menubar: its title, opening the same menu a right-click opens. */
function MenubarButton({ menu }: { menu: MenubarMenu }) {
  const { onContextMenu } = useContextMenu(menu.entries)
  return (
    <button
      type="button"
      role="menuitem"
      aria-haspopup="menu"
      className="nt-menubar-item"
      onClick={(e) => {
        const button = e.currentTarget
        onContextMenu({ preventDefault: () => e.preventDefault(), currentTarget: button, target: button, clientX: 0, clientY: 0 } as unknown as React.MouseEvent)
      }}
    >
      {menu.label}
    </button>
  )
}

/** A desktop app's menus across the top: File, Edit, View... each opening its menu (icons, shortcuts, ticks). */
function Menubar({ menus, className, style }: { menus: MenubarMenu[]; className?: string; style?: React.CSSProperties }) {
  return (
    <div role="menubar" className={cn("nt-menubar", className)} style={style}>
      {menus.map((m) => (
        <MenubarButton key={m.label} menu={m} />
      ))}
    </div>
  )
}

export { AspectRatio, ButtonGroup, InputGroup, Menubar, NavigationMenu }
export type { AspectRatioProps, ButtonGroupProps, InputGroupProps, MenubarMenu, NavigationItem, NavigationLink, NavigationMenuProps }
