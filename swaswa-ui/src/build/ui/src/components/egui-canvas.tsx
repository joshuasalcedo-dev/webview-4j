import * as React from "react"

import { cn } from "../lib/utils"
import { useNative } from "./provider"

/*
 * An egui-4j component (crates/egui-4j): drawn by egui on a canvas, for what the DOM is slow at (charts that
 * stream, editors that draw). The module is large (wasm): it loads the first time an EguiCanvas is shown.
 * Build it with `bun run egui` (cargo + wasm-bindgen, into src/egui4j/).
 */

type Module = typeof import("../egui4j/egui4j")
type Mounted = import("../egui4j/egui4j").EguiComponent

let loading: Promise<Module> | undefined
function loadEgui(): Promise<Module> {
  loading ??= import("../egui4j/egui4j").then(async (m) => {
    await m.default()
    return m
  })
  return loading
}

/**
 * How much egui-4j logs to the console, which webview-4j passes to Java's log (LogRecord, source PAGE):
 * "info" at first (a component shown, an error), "warn" (props refused), "debug" (props given), "trace" (also
 * each event and each point streamed in), or "off".
 */
export async function setEguiLogLevel(level: "off" | "error" | "warn" | "info" | "debug" | "trace") {
  const m = await loadEgui()
  m.EguiComponent.set_log_level(level)
}

/** What an egui component reports: `{ event: "click", ...its fields }` (each component lists its own). */
export type EguiEvent = { event: string; [field: string]: unknown }

/** Passes props to the component without a render, e.g. points streaming in: `ref.current.update({ append })`. */
export type EguiCanvasHandle = {
  /** Throws, changing nothing, when the component refuses the props. */
  update(props: object): void
}

type EguiCanvasProps = Omit<React.ComponentProps<"div">, "children" | "onError"> & {
  /** Which component ("line-chart"). */
  component: string
  /** Its props: given again (changed), they are passed again. For streams, the handle's update is cheaper. */
  props?: object
  /** What the user did. */
  onEvent?: (event: EguiEvent) => void
  /** The module failed to load, the component refused its first props, or it stopped. */
  onError?: (message: string) => void
  /** Its height (e.g. 320 or "40vh"); left out, it fills its parent, which needs a height. */
  height?: number | string
  /** What it shows, for screen readers (e.g. "CPU and memory over the last minute"). */
  "aria-label": string
}

/** The kit's colour `name` where `el` is, as #rrggbb (the canvas's own parser reads any CSS colour). */
function cssColor(el: Element, name: string): string | undefined {
  const value = getComputedStyle(el).getPropertyValue(name).trim()
  if (!value) return undefined
  const ctx = document.createElement("canvas").getContext("2d")
  if (!ctx) return undefined
  ctx.fillStyle = "#000"
  ctx.fillStyle = value
  const parsed = ctx.fillStyle
  if (parsed.startsWith("#")) return parsed
  const rgb = parsed.match(/[\d.]+/g)?.map(Number)
  if (!rgb || rgb.length < 3) return undefined
  return "#" + rgb.slice(0, 3).map((c) => Math.round(c).toString(16).padStart(2, "0")).join("")
}

/**
 * An egui component on a canvas: props in (JSON), events out. It takes the window's theme and accent, and its
 * background is the page's.
 */
export const EguiCanvas = React.forwardRef<EguiCanvasHandle, EguiCanvasProps>(function EguiCanvas(
  { component, props, onEvent, onError, height, className, style, "aria-label": label, ...rest },
  ref
) {
  const { theme } = useNative()
  const dark = theme === "dark"
  const canvas = React.useRef<HTMLCanvasElement>(null)
  const [mounted, setMounted] = React.useState<Mounted>()
  const [error, setError] = React.useState<string>()

  const json = React.useMemo(() => JSON.stringify(props ?? {}), [props])
  const latest = React.useRef({ json, dark, onEvent, onError })
  latest.current = { json, dark, onEvent, onError }

  const fail = React.useCallback((message: string) => {
    setError(message)
    latest.current.onError?.(message)
  }, [])

  // mount once per component name; unmount stops it (also when the mount was still on its way)
  React.useEffect(() => {
    const el = canvas.current
    if (!el) return
    let gone = false
    let current: Mounted | undefined
    setError(undefined)
    loadEgui()
      .then((m) =>
        m.EguiComponent.mount(
          el,
          component,
          latest.current.json,
          (event: string) => latest.current.onEvent?.(JSON.parse(event) as EguiEvent),
          latest.current.dark
        )
      )
      .then((c) => {
        if (gone) return c.destroy()
        current = c
        setMounted(c)
      })
      .catch((e: unknown) => !gone && fail(String(e)))
    return () => {
      gone = true
      current?.destroy()
      setMounted(undefined)
    }
  }, [component, fail])

  // new props: passed on (the first ones went with the mount)
  const sent = React.useRef(json)
  React.useEffect(() => {
    if (!mounted || sent.current === json) return
    sent.current = json
    try {
      mounted.update(json)
    } catch (e) {
      console.error(`EguiCanvas ${component}:`, e)
      latest.current.onError?.(String(e))
    }
  }, [mounted, json, component])

  // the window's look: light or dark, and its accent
  React.useEffect(() => {
    const el = canvas.current
    if (!mounted || !el) return
    mounted.set_theme(dark, cssColor(el, "--nt-accent"), undefined)
  }, [mounted, dark])

  React.useImperativeHandle(
    ref,
    () => ({
      update(next: object) {
        if (!mounted) throw new Error(`EguiCanvas ${component} is not shown yet`)
        mounted.update(JSON.stringify(next))
      },
    }),
    [mounted, component]
  )

  return (
    <div
      className={cn("nt-egui", className)}
      style={{ position: "relative", minWidth: 0, minHeight: 0, height: height ?? "100%", ...style }}
      {...rest}
    >
      <canvas
        ref={canvas}
        role="img"
        aria-label={label}
        style={{ display: "block", width: "100%", height: "100%" }}
      />
      {error && (
        <div role="alert" style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", padding: 16, color: "var(--nt-error)", font: "inherit", textAlign: "center" }}>
          {error}
        </div>
      )}
    </div>
  )
})
