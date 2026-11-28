// shadcn/ui's overlays the kit lacked: Popover, HoverCard, Sheet (and Drawer: a sheet from the bottom), CommandPalette.
import * as React from "react"
import { createPortal } from "react-dom"

import { NamedIcon } from "../lib/named-icons"
import { cn } from "../lib/utils"
import { formatShortcut } from "./context-menu"
import { useNative } from "./provider"
import "./more-controls.css"

type Side = "top" | "bottom" | "left" | "right"

/**
 * Where a floating panel goes beside `anchor`, in the window's pixels (position: fixed, so no scrolling box or
 * overflow: hidden cuts it), kept inside the window; again as the page scrolls or the window resizes.
 */
function useFloating(anchor: React.RefObject<HTMLElement | null>, panel: React.RefObject<HTMLElement | null>, open: boolean, side: Side) {
  const [at, setAt] = React.useState<{ top: number; left: number } | null>(null)
  React.useLayoutEffect(() => {
    if (!open) {
      setAt(null)
      return
    }
    let tries = 0
    let frame = 0
    const place = () => {
      const a = anchor.current?.getBoundingClientRect()
      const p = panel.current?.getBoundingClientRect()
      if (!a || !p) {
        // the panel goes into the page through a portal a moment after this runs: measure on the next frames
        if (tries++ < 20) frame = requestAnimationFrame(place)
        return
      }
      const gap = 6
      let top = side === "top" ? a.top - p.height - gap : side === "bottom" ? a.bottom + gap : a.top
      let left = side === "left" ? a.left - p.width - gap : side === "right" ? a.right + gap : a.left
      // no room: the other side
      if (side === "bottom" && top + p.height > innerHeight && a.top - p.height - gap > 0) top = a.top - p.height - gap
      if (side === "top" && top < 0) top = a.bottom + gap
      top = Math.max(4, Math.min(top, innerHeight - p.height - 4))
      left = Math.max(4, Math.min(left, innerWidth - p.width - 4))
      setAt({ top, left })
    }
    place()
    addEventListener("resize", place)
    addEventListener("scroll", place, true)
    return () => {
      cancelAnimationFrame(frame)
      removeEventListener("resize", place)
      removeEventListener("scroll", place, true)
    }
  }, [open, side, anchor, panel])
  return at
}

/** Closes on Esc, and on a press outside `inside` (the panel and what opened it). */
function useDismiss(open: boolean, close: () => void, inside: React.RefObject<HTMLElement | null>[]) {
  React.useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation()
        close()
      }
    }
    const onDown = (e: PointerEvent) => {
      if (!inside.some((r) => r.current?.contains(e.target as Node))) close()
    }
    addEventListener("keydown", onKey, true)
    addEventListener("pointerdown", onDown, true)
    return () => {
      removeEventListener("keydown", onKey, true)
      removeEventListener("pointerdown", onDown, true)
    }
  }) // eslint-disable-line react-hooks/exhaustive-deps
}

/** Floating panels go to the kit's root, inside its theme, over everything. */
function Portal({ children }: { children: React.ReactNode }) {
  const [host, setHost] = React.useState<HTMLElement | null>(null)
  const probe = React.useRef<HTMLSpanElement>(null)
  React.useLayoutEffect(() => setHost((probe.current?.closest(".nt-root") as HTMLElement | null) ?? document.body), [])
  return (
    <>
      <span ref={probe} hidden />
      {host && createPortal(children, host)}
    </>
  )
}

// --- Popover -----------------------------------------------------------------------------------------

type PopoverProps = {
  /** What opens it when clicked: a button, an icon. */
  trigger: React.ReactNode
  /** What it shows. */
  children?: React.ReactNode
  /** A title over what it shows. */
  title?: string
  side?: Side
  /** Open or closed, when the app decides; left out, it keeps its own. */
  open?: boolean
  onOpenChange?: (open: boolean) => void
  className?: string
  style?: React.CSSProperties
}

/** A panel that a click opens beside something and Esc, or a click elsewhere, closes: settings, a small form. */
function Popover({ trigger, children, title, side = "bottom", open: openProp, onOpenChange, className, style }: PopoverProps) {
  const [own, setOwn] = React.useState(false)
  const open = openProp ?? own
  const set = (next: boolean) => {
    if (openProp === undefined) setOwn(next)
    onOpenChange?.(next)
  }
  const anchor = React.useRef<HTMLSpanElement>(null)
  const panel = React.useRef<HTMLDivElement>(null)
  const id = React.useId()
  const at = useFloating(anchor, panel, open, side)
  useDismiss(open, () => set(false), [anchor, panel])
  return (
    <>
      <span
        ref={anchor}
        className="nt-popover-anchor"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? id : undefined}
        onClick={() => set(!open)}
      >
        {/* words alone are a button: something that opens must look like it does */}
        {typeof trigger === "string" ? (
          <button type="button" className="nt-button">
            {trigger}
          </button>
        ) : (
          trigger
        )}
      </span>
      {open && (
        <Portal>
          <div
            ref={panel}
            id={id}
            role="dialog"
            aria-label={title}
            className={cn("nt-popover", className)}
            style={{ ...style, ...(at ? { top: at.top, left: at.left } : { visibility: "hidden", top: 0, left: 0 }) }}
          >
            {title && <div className="nt-popover-title">{title}</div>}
            {children}
          </div>
        </Portal>
      )}
    </>
  )
}

// --- HoverCard ---------------------------------------------------------------------------------------

type HoverCardProps = {
  /** What the pointer rests on: a name, a link. */
  trigger: React.ReactNode
  /** The card: more about it. */
  children?: React.ReactNode
  side?: Side
  /** How long the pointer rests before it shows, in ms (600 unless set). */
  delay?: number
  className?: string
  style?: React.CSSProperties
}

/** A card with more about something, shown while the pointer rests on it (or the keyboard's focus is there). */
function HoverCard({ trigger, children, side = "bottom", delay = 600, className, style }: HoverCardProps) {
  const [open, setOpen] = React.useState(false)
  const anchor = React.useRef<HTMLSpanElement>(null)
  const panel = React.useRef<HTMLDivElement>(null)
  const timer = React.useRef<number | undefined>(undefined)
  const at = useFloating(anchor, panel, open, side)
  const later = (next: boolean, ms: number) => {
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setOpen(next), ms)
  }
  React.useEffect(() => () => window.clearTimeout(timer.current), [])
  return (
    <>
      <span
        ref={anchor}
        className="nt-popover-anchor"
        onPointerEnter={() => later(true, delay)}
        onPointerLeave={() => later(false, 200)}
        onFocus={() => later(true, 0)}
        onBlur={() => later(false, 0)}
      >
        {trigger}
      </span>
      {open && (
        <Portal>
          <div
            ref={panel}
            role="tooltip"
            className={cn("nt-popover nt-hovercard", className)}
            style={{ ...style, ...(at ? { top: at.top, left: at.left } : { visibility: "hidden", top: 0, left: 0 }) }}
            onPointerEnter={() => later(true, 0)}
            onPointerLeave={() => later(false, 200)}
          >
            {children}
          </div>
        </Portal>
      )}
    </>
  )
}

// --- Sheet -------------------------------------------------------------------------------------------

type SheetProps = {
  open: boolean
  onOpenChange?: (open: boolean) => void
  /** Which edge it slides in from: right (the default), left, or bottom (a drawer). */
  side?: "right" | "left" | "bottom"
  title?: string
  description?: string
  /** How wide (or, from the bottom, how tall), in px (380 unless set). */
  size?: number
  children?: React.ReactNode
  className?: string
}

/**
 * A panel that slides in over the window from an edge, the rest dimmed: details, filters, a form (shadcn's Sheet;
 * from the bottom, its Drawer). Esc, the × or a click on the dimmed part closes it.
 */
function Sheet({ open, onOpenChange, side = "right", title, description, size = 380, children, className }: SheetProps) {
  const panel = React.useRef<HTMLDivElement>(null)
  React.useEffect(() => {
    if (!open) return
    const before = document.activeElement as HTMLElement | null
    panel.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onOpenChange?.(false)
    }
    addEventListener("keydown", onKey)
    return () => {
      removeEventListener("keydown", onKey)
      before?.focus?.()
    }
  }, [open]) // eslint-disable-line react-hooks/exhaustive-deps
  if (!open) return null
  return (
    <Portal>
      <div className="nt-sheet-backdrop" onClick={() => onOpenChange?.(false)} />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className={cn("nt-sheet", className)}
        data-side={side}
        style={side === "bottom" ? { height: size } : { width: size }}
      >
        <div className="nt-sheet-head">
          <div>
            {title && <div className="nt-sheet-title">{title}</div>}
            {description && <div className="nt-sheet-description">{description}</div>}
          </div>
          <button type="button" className="nt-sheet-close" aria-label="Close" onClick={() => onOpenChange?.(false)}>
            ×
          </button>
        </div>
        <div className="nt-sheet-body">{children}</div>
      </div>
    </Portal>
  )
}

// --- CommandPalette ----------------------------------------------------------------------------------

type Command = { id: string; label: string; group?: string; shortcut?: string; icon?: string; onSelect?: () => void }

type CommandPaletteProps = {
  open: boolean
  onOpenChange?: (open: boolean) => void
  commands: Command[]
  /** The keys that open it from anywhere in the window ("Mod+K" unless set; "" for none). */
  shortcut?: string
  placeholder?: string
  /** What it says when nothing matches. */
  emptyText?: string
}

/** Search the app's commands by name and run one: Ctrl+K (⌘K), type, ↑ ↓, Enter. */
function CommandPalette({ open, onOpenChange, commands, shortcut = "Mod+K", placeholder = "Type a command or search…", emptyText = "No results." }: CommandPaletteProps) {
  const { platform } = useNative()
  const [query, setQuery] = React.useState("")
  const [at, setAt] = React.useState(0)
  const input = React.useRef<HTMLInputElement>(null)
  const list = React.useRef<HTMLDivElement>(null)

  // its shortcut, from anywhere in the window
  React.useEffect(() => {
    if (!shortcut) return
    const parts = shortcut.toLowerCase().split("+")
    const key = parts[parts.length - 1]
    const onKey = (e: KeyboardEvent) => {
      const mod = parts.includes("mod") ? e.ctrlKey || e.metaKey : true
      const shift = parts.includes("shift") === e.shiftKey
      if (mod && shift && e.key.toLowerCase() === key) {
        e.preventDefault()
        onOpenChange?.(!open)
      }
    }
    addEventListener("keydown", onKey)
    return () => removeEventListener("keydown", onKey)
  }, [shortcut, open, onOpenChange])
  React.useEffect(() => {
    if (open) {
      setQuery("")
      setAt(0)
      requestAnimationFrame(() => input.current?.focus())
    }
  }, [open])

  const q = query.trim().toLowerCase()
  const found = q ? commands.filter((c) => c.label.toLowerCase().includes(q) || c.group?.toLowerCase().includes(q)) : commands
  const run = (c: Command | undefined) => {
    if (!c) return
    onOpenChange?.(false)
    c.onSelect?.()
  }
  React.useEffect(() => {
    list.current?.querySelector(`[data-index="${at}"]`)?.scrollIntoView({ block: "nearest" })
  }, [at])
  if (!open) return null

  const groups: [string, { c: Command; i: number }[]][] = []
  found.forEach((c, i) => {
    const g = c.group ?? ""
    const last = groups[groups.length - 1]
    if (last && last[0] === g) last[1].push({ c, i })
    else groups.push([g, [{ c, i }]])
  })
  return (
    <Portal>
      <div className="nt-sheet-backdrop nt-command-backdrop" onClick={() => onOpenChange?.(false)} />
      <div className="nt-command" role="dialog" aria-modal="true" aria-label="Commands">
        <div className="nt-command-search">
          <NamedIcon name="search" />
          <input
            ref={input}
            role="combobox"
            aria-expanded="true"
            aria-controls="nt-command-list"
            aria-activedescendant={found[at] ? `nt-command-${at}` : undefined}
            placeholder={placeholder}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setAt(0)
            }}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") setAt((a) => Math.min(found.length - 1, a + 1))
              else if (e.key === "ArrowUp") setAt((a) => Math.max(0, a - 1))
              else if (e.key === "Enter") run(found[at])
              else if (e.key === "Escape") onOpenChange?.(false)
              else return
              e.preventDefault()
            }}
          />
        </div>
        <div ref={list} id="nt-command-list" role="listbox" className="nt-command-list">
          {found.length === 0 && <div className="nt-command-empty">{emptyText}</div>}
          {groups.map(([group, items]) => (
            <div key={group + items[0].i} role="group" aria-label={group || undefined}>
              {group && <div className="nt-command-group">{group}</div>}
              {items.map(({ c, i }) => (
                <div
                  key={c.id}
                  id={`nt-command-${i}`}
                  role="option"
                  aria-selected={i === at}
                  data-index={i}
                  className="nt-command-item"
                  onPointerMove={() => setAt(i)}
                  onClick={() => run(c)}
                >
                  {c.icon ? <NamedIcon name={c.icon} /> : <span className="nt-command-noicon" />}
                  <span className="nt-command-label">{c.label}</span>
                  {c.shortcut && <kbd>{formatShortcut(c.shortcut, platform)}</kbd>}
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
    </Portal>
  )
}

export { CommandPalette, HoverCard, Popover, Portal, Sheet, useDismiss, useFloating }
export type { Command, CommandPaletteProps, HoverCardProps, PopoverProps, SheetProps }
