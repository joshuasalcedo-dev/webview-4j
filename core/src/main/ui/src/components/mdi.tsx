import * as React from "react"

import { cn } from "../lib/utils"
import { TitleBar } from "./title-bar"
import "./mdi.css"

/**
 * MDI: documents in windows of their own inside the app's window, as in Windows' classic MDI apps. Each one moves
 * by its title bar, resizes from its edges and corners, minimizes to the strip at the bottom, maximizes to fill the
 * area, and comes to the front when clicked; cascade and tile arrange them. Ctrl+F6 goes to the next one.
 *
 * const mdi = useMdi()
 * mdi.open({ id: "report", title: "Report.txt", content: <Editor /> })
 * <MdiArea mdi={mdi} aria-label="Documents" style={{ flex: 1 }} />
 *
 * Inside a window's content, useMdiWindow() says which window it is in, whether it is the active one, and its state:
 * what an embedded webview needs to place itself and to hide while covered (it would draw over the windows above it).
 */

type MdiWindowState = "normal" | "minimized" | "maximized"

type MdiWindow = {
  id: string
  title: string
  /** 16px, before the title on Windows. */
  icon?: React.ReactNode
  /** Where it is in the area and its size, in CSS pixels, while "normal". */
  x: number
  y: number
  width: number
  height: number
  state: MdiWindowState
  content: React.ReactNode
}

/** What open() needs: the rest has defaults (cascaded from the last window, 480 by 320, normal). */
type MdiOpen = Pick<MdiWindow, "id" | "title" | "content"> & Partial<Omit<MdiWindow, "id" | "title" | "content">>

type MdiApi = {
  /** Back to front: the last is on top. */
  windows: MdiWindow[]
  /** The window on top that is not minimized. */
  active: string | undefined
  /** Opens a window, or brings one with that id to the front (and updates it). */
  open(window: MdiOpen): void
  close(id: string): void
  focus(id: string): void
  minimize(id: string): void
  maximize(id: string): void
  restore(id: string): void
  toggleMaximize(id: string): void
  move(id: string, x: number, y: number): void
  resize(id: string, x: number, y: number, width: number, height: number): void
  /** Each a step down and right from the last, at 60% of the area. */
  cascade(): void
  /** Side by side in a grid that fills the area. */
  tile(): void
  /** @internal MdiArea tells the hook how big it is. */
  setArea(width: number, height: number): void
}

const STEP = 28
const MIN_WIDTH = 160
const MIN_HEIGHT = 96

function topOpen(windows: MdiWindow[]): string | undefined {
  for (let i = windows.length - 1; i >= 0; i--) if (windows[i]!.state !== "minimized") return windows[i]!.id
  return undefined
}

/** The windows of one MdiArea, and what can be done to them. */
function useMdi(initial: MdiOpen[] = []): MdiApi {
  const area = React.useRef({ width: 960, height: 640 })
  const [windows, setWindows] = React.useState<MdiWindow[]>(() => initial.map((w, i) => withDefaults(w, i)))

  const update = React.useCallback((id: string, change: (w: MdiWindow) => MdiWindow, toFront = false) => {
    setWindows((all) => {
      const index = all.findIndex((w) => w.id === id)
      if (index < 0) return all
      const changed = change(all[index]!)
      const rest = all.filter((_, i) => i !== index)
      return toFront ? [...rest, changed] : all.map((w, i) => (i === index ? changed : w))
    })
  }, [])

  return React.useMemo<MdiApi>(() => {
    const api: MdiApi = {
      windows,
      active: topOpen(windows),
      open: (window) =>
        setWindows((all) => {
          const existing = all.find((w) => w.id === window.id)
          if (existing) {
            const merged = { ...existing, ...window, state: existing.state === "minimized" ? "normal" : existing.state }
            return [...all.filter((w) => w.id !== window.id), merged as MdiWindow]
          }
          return [...all, withDefaults(window, all.length)]
        }),
      close: (id) => setWindows((all) => all.filter((w) => w.id !== id)),
      focus: (id) => update(id, (w) => w, true),
      minimize: (id) =>
        setWindows((all) => {
          const window = all.find((w) => w.id === id)
          if (!window) return all
          // Minimized windows go to the back: the next one comes up.
          return [{ ...window, state: "minimized" }, ...all.filter((w) => w.id !== id)]
        }),
      maximize: (id) => update(id, (w) => ({ ...w, state: "maximized" }), true),
      restore: (id) => update(id, (w) => ({ ...w, state: "normal" }), true),
      toggleMaximize: (id) => update(id, (w) => ({ ...w, state: w.state === "maximized" ? "normal" : "maximized" }), true),
      move: (id, x, y) => update(id, (w) => ({ ...w, x, y })),
      resize: (id, x, y, width, height) =>
        update(id, (w) => ({ ...w, x, y, width: Math.max(MIN_WIDTH, width), height: Math.max(MIN_HEIGHT, height) })),
      cascade: () =>
        setWindows((all) => {
          const { width, height } = area.current
          let i = 0
          return all.map((w) =>
            w.state === "minimized"
              ? w
              : { ...w, state: "normal", x: STEP * i, y: STEP * i++, width: Math.max(MIN_WIDTH, width * 0.6), height: Math.max(MIN_HEIGHT, height * 0.6) }
          )
        }),
      tile: () =>
        setWindows((all) => {
          const open = all.filter((w) => w.state !== "minimized")
          if (open.length === 0) return all
          const { width, height } = area.current
          const columns = Math.ceil(Math.sqrt(open.length))
          const rows = Math.ceil(open.length / columns)
          const cell = { width: width / columns, height: height / rows }
          let i = 0
          return all.map((w) => {
            if (w.state === "minimized") return w
            const column = i % columns
            const row = Math.floor(i++ / columns)
            return { ...w, state: "normal", x: column * cell.width, y: row * cell.height, width: cell.width, height: cell.height }
          })
        }),
      setArea: (width, height) => {
        area.current = { width, height }
      },
    }
    return api
  }, [windows, update])
}

function withDefaults(window: MdiOpen, index: number): MdiWindow {
  return {
    x: STEP * (index % 8),
    y: STEP * (index % 8),
    width: 480,
    height: 320,
    state: "normal",
    ...window,
  }
}

type MdiWindowContextValue = { id: string; active: boolean; state: MdiWindowState }
const MdiWindowContext = React.createContext<MdiWindowContextValue | null>(null)

/** Inside an MDI window's content: which window, whether it is the active one, and its state. */
function useMdiWindow(): MdiWindowContextValue | null {
  return React.useContext(MdiWindowContext)
}

type MdiAreaProps = Omit<React.ComponentProps<"div">, "children"> & {
  mdi: MdiApi
  /** What the windows are, for screen readers (e.g. "Documents"). */
  "aria-label": string
  /** Shown when no window is open. */
  empty?: React.ReactNode
}

type Edge = "n" | "s" | "e" | "w" | "ne" | "nw" | "se" | "sw"
const EDGES: Edge[] = ["n", "s", "e", "w", "ne", "nw", "se", "sw"]

/** The area the windows live in. Give it a size (flex: 1, or a height). */
function MdiArea({ mdi, empty, className, onKeyDown, ...props }: MdiAreaProps) {
  const ref = React.useRef<HTMLDivElement>(null)
  const { setArea } = mdi

  React.useLayoutEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new ResizeObserver(() => setArea(element.clientWidth, element.clientHeight))
    observer.observe(element)
    return () => observer.disconnect()
  }, [setArea])

  /** Moving or resizing with the mouse: from where the drag started, so the window follows the pointer exactly. */
  function drag(event: React.PointerEvent, window: MdiWindow, edge: Edge | "move") {
    if (event.button !== 0 || window.state === "maximized") return
    if (edge === "move" && (event.target as Element).closest("button")) return
    event.preventDefault()
    mdi.focus(window.id)
    const start = { x: event.clientX, y: event.clientY, window }
    const target = event.currentTarget as HTMLElement
    target.setPointerCapture(event.pointerId)
    const onMove = (e: PointerEvent) => {
      const dx = e.clientX - start.x
      const dy = e.clientY - start.y
      const w = start.window
      if (edge === "move") return mdi.move(w.id, w.x + dx, Math.max(0, w.y + dy))
      let { x, y, width, height } = w
      if (edge.includes("e")) width = w.width + dx
      if (edge.includes("s")) height = w.height + dy
      if (edge.includes("w")) {
        width = Math.max(MIN_WIDTH, w.width - dx)
        x = w.x + w.width - width
      }
      if (edge.startsWith("n")) {
        height = Math.max(MIN_HEIGHT, w.height - dy)
        y = w.y + w.height - height
      }
      mdi.resize(w.id, x, y, width, height)
    }
    const onUp = () => {
      target.removeEventListener("pointermove", onMove)
      target.removeEventListener("pointerup", onUp)
      target.removeEventListener("pointercancel", onUp)
    }
    target.addEventListener("pointermove", onMove)
    target.addEventListener("pointerup", onUp)
    target.addEventListener("pointercancel", onUp)
  }

  const minimized = mdi.windows.filter((w) => w.state === "minimized")
  const order = mdi.windows.map((w) => w.id)

  return (
    <div
      ref={ref}
      role="group"
      className={cn("nt-mdi", className)}
      onKeyDown={(event) => {
        onKeyDown?.(event)
        // Ctrl+F6: the next window, as in Windows' MDI apps.
        if (event.key === "F6" && event.ctrlKey) {
          const open = mdi.windows.filter((w) => w.state !== "minimized")
          if (open.length > 1) {
            event.preventDefault()
            mdi.focus(open[0]!.id)
          }
        }
      }}
      {...props}
    >
      {mdi.windows.length === 0 && empty != null && <div className="nt-mdi-empty">{empty}</div>}
      {[...mdi.windows]
        .sort((a, b) => a.id.localeCompare(b.id))
        .map((window) => {
          // Rendered in a stable order (content keeps its state); stacked by z-index.
          const active = mdi.active === window.id
          const maximized = window.state === "maximized"
          return (
            <section
              key={window.id}
              role="dialog"
              aria-label={window.title}
              className="nt-mdi-window"
              data-active={active ? "" : undefined}
              data-state={window.state}
              hidden={window.state === "minimized"}
              style={
                maximized
                  ? { zIndex: order.indexOf(window.id) + 1 }
                  : { zIndex: order.indexOf(window.id) + 1, left: window.x, top: window.y, width: window.width, height: window.height }
              }
              onPointerDownCapture={() => !active && mdi.focus(window.id)}
            >
              <div className="nt-mdi-header" onPointerDown={(e) => drag(e, window, "move")} onDoubleClick={(e) => !(e.target as Element).closest("button") && mdi.toggleMaximize(window.id)}>
                <TitleBar
                  title={window.title}
                  icon={window.icon}
                  dragsWindow={false}
                  maximized={maximized}
                  onMinimize={() => mdi.minimize(window.id)}
                  onMaximize={() => mdi.toggleMaximize(window.id)}
                  onRestore={() => mdi.restore(window.id)}
                  onClose={() => mdi.close(window.id)}
                />
              </div>
              <div className="nt-mdi-content">
                <MdiWindowContext.Provider value={{ id: window.id, active, state: window.state }}>
                  {window.content}
                </MdiWindowContext.Provider>
              </div>
              {!maximized &&
                EDGES.map((edge) => (
                  <div key={edge} className="nt-mdi-edge" data-edge={edge} aria-hidden="true" onPointerDown={(e) => drag(e, window, edge)} />
                ))}
            </section>
          )
        })}
      {minimized.length > 0 && (
        <div className="nt-mdi-minimized" role="toolbar" aria-label="Minimized windows">
          {minimized.map((window) => (
            <button key={window.id} type="button" className="nt-mdi-chip" onClick={() => mdi.restore(window.id)} title={`Restore ${window.title}`}>
              {window.icon}
              <span>{window.title}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export { MdiArea, useMdi, useMdiWindow }
export type { MdiApi, MdiAreaProps, MdiOpen, MdiWindow, MdiWindowState }
