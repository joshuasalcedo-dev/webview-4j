import * as React from "react"

import { Close } from "../lib/icons"
import { cn } from "../lib/utils"
import "./inspector.css"

const read = (key: string): string | null => {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}
const write = (key: string, value: string) => {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // Private windows and full storage: the panel still works, it just won't remember.
  }
}

type InspectorProps = Omit<React.HTMLAttributes<HTMLElement>, "title"> & {
  open: boolean
  onOpenChange?: (open: boolean) => void
  /** Its heading (e.g. "Details"). */
  title: React.ReactNode
  /** Width at first, and on a double-click of the edge (px). */
  defaultWidth?: number
  minWidth?: number
  maxWidth?: number
  /** Remembers its width (and its sections' open state) under this name. */
  storageKey?: string
  /** Narrower than this (px), the window gets the panel over its content instead of beside it (720). */
  overlayBelow?: number
}

/**
 * A details panel on the right of a window, as Teams' and Outlook's details pane on Windows and the inspector
 * sidebar on a Mac: a heading with ×, sections that fold, its own scrolling. Drag its left edge to resize it
 * (double-click the edge for its first width); Escape closes it when the focus is inside. In a narrow window
 * it lies over the content instead of squeezing it. Put it beside the main content in a row (flex).
 */
function Inspector({
  open,
  onOpenChange,
  title,
  defaultWidth = 320,
  minWidth = 240,
  maxWidth = 560,
  storageKey,
  overlayBelow = 720,
  className,
  children,
  style,
  ...props
}: InspectorProps) {
  const panel = React.useRef<HTMLElement>(null)
  const [width, setWidth] = React.useState(() => {
    const saved = storageKey ? Number(read(`${storageKey}:width`)) : NaN
    return Number.isFinite(saved) && saved > 0 ? saved : defaultWidth
  })
  const [narrow, setNarrow] = React.useState(false)

  // Narrow window (or narrow parent): over the content.
  React.useEffect(() => {
    const parent = panel.current?.parentElement
    if (!parent || typeof ResizeObserver === "undefined") return
    const watch = new ResizeObserver(() =>
      setNarrow(parent.clientWidth < overlayBelow)
    )
    watch.observe(parent)
    return () => watch.disconnect()
  }, [overlayBelow, open])

  // As a sheet over the content: the focus goes into it, and back (to the button that opened it) after.
  React.useEffect(() => {
    if (!open || !narrow) return
    const before = document.activeElement as HTMLElement | null
    panel.current
      ?.querySelector<HTMLElement>(".nt-inspector-close")
      ?.focus()
    return () => before?.focus?.()
  }, [open, narrow])

  const setAndKeep = (next: number) => {
    const clamped = Math.round(Math.min(maxWidth, Math.max(minWidth, next)))
    setWidth(clamped)
    if (storageKey) write(`${storageKey}:width`, String(clamped))
  }

  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return
    event.preventDefault()
    const from = event.clientX
    const was = width
    const edge = event.currentTarget
    edge.setPointerCapture(event.pointerId)
    const move = (e: PointerEvent) => setAndKeep(was + (from - e.clientX))
    const end = () => {
      edge.removeEventListener("pointermove", move)
      edge.removeEventListener("pointerup", end)
      edge.removeEventListener("pointercancel", end)
    }
    edge.addEventListener("pointermove", move)
    edge.addEventListener("pointerup", end)
    edge.addEventListener("pointercancel", end)
  }

  if (!open) return null
  return (
    <>
      {narrow && onOpenChange && (
        // Over the content: a click beside the sheet closes it (Windows dims it; a Mac only shadows the edge).
        <div
          className="nt-inspector-scrim"
          aria-hidden="true"
          onClick={() => onOpenChange(false)}
        />
      )}
      <aside
        ref={panel}
        className={cn("nt-inspector", className)}
        data-overlay={narrow || undefined}
        style={{ width, ...style }}
        onKeyDown={(event) => {
          if (event.key === "Escape" && onOpenChange) {
            event.stopPropagation()
            onOpenChange(false)
          }
        }}
        aria-label={typeof title === "string" ? title : undefined}
        {...props}
      >
        <div
          className="nt-inspector-edge"
          data-resize="x"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize the panel"
          aria-valuemin={minWidth}
          aria-valuemax={maxWidth}
          aria-valuenow={width}
          tabIndex={0}
          onPointerDown={startResize}
          onDoubleClick={() => setAndKeep(defaultWidth)}
          onKeyDown={(event) => {
            const by =
              event.key === "ArrowLeft"
                ? 16
                : event.key === "ArrowRight"
                  ? -16
                  : 0
            if (!by) return
            event.preventDefault()
            setAndKeep(width + by)
          }}
        />
        <header className="nt-inspector-head">
          <h2 className="nt-inspector-title">{title}</h2>
          {onOpenChange && (
            <button
              type="button"
              className="nt-inspector-close"
              aria-label="Close the panel"
              title="Close (Esc)"
              onClick={() => onOpenChange(false)}
            >
              <Close />
            </button>
          )}
        </header>
        <div className="nt-inspector-body">
          <InspectorKeyContext.Provider value={storageKey}>
            {children}
          </InspectorKeyContext.Provider>
        </div>
      </aside>
    </>
  )
}

const InspectorKeyContext = React.createContext<string | undefined>(undefined)

type InspectorSectionProps = Omit<
  React.HTMLAttributes<HTMLElement>,
  "title"
> & {
  /** Its heading; it also names it for remembering whether it is open. */
  title: string
  /** A count after the heading (e.g. 12 photos). */
  count?: number
  /** A small button at the end of the heading (e.g. "See all", "+"). */
  action?: React.ReactNode
  defaultOpen?: boolean
}

/** A part of an Inspector that folds open and shut by its heading (and remembers which, per app). */
function InspectorSection({
  title,
  count,
  action,
  defaultOpen = true,
  className,
  children,
  ...props
}: InspectorSectionProps) {
  const storageKey = React.useContext(InspectorKeyContext)
  const key = storageKey ? `${storageKey}:section:${title}` : undefined
  const [open, setOpen] = React.useState(() => {
    const saved = key ? read(key) : null
    return saved === null ? defaultOpen : saved === "1"
  })
  const id = React.useId()
  const toggle = () => {
    setOpen(!open)
    if (key) write(key, open ? "0" : "1")
  }
  return (
    <section
      className={cn("nt-inspector-section", className)}
      data-open={open || undefined}
      {...props}
    >
      <div className="nt-inspector-section-head">
        <button
          type="button"
          className="nt-inspector-section-toggle"
          aria-expanded={open}
          aria-controls={id}
          onClick={toggle}
        >
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <path
              d="m6 4 4 4-4 4"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          <span>{title}</span>
          {count != null && <span className="nt-inspector-count">{count}</span>}
        </button>
        {action}
      </div>
      {open && (
        <div id={id} className="nt-inspector-section-body">
          {children}
        </div>
      )}
    </section>
  )
}

export { Inspector, InspectorSection }
export type { InspectorProps, InspectorSectionProps }
