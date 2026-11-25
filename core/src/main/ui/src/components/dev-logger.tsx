import * as React from "react"
import { createRoot } from "react-dom/client"

import { EguiCanvas, type EguiCanvasHandle, type EguiEvent } from "./egui-canvas"
import { NativeProvider } from "./provider"

/*
 * The dev logger (egui-4j's `dev-logger`): an app's log lines, every API call with its bodies, headers, status and
 * time, the calls between the page and Java, and performance samples, in one list, with statistics, groups and
 * charts in panes that dock like an IDE's. Java feeds it (io.joshuasalcedo.webview4j.DevLogger, opened by the
 * Spring Boot starter in development); a page can feed it too, through the handle.
 */

/** One entry: the dev-logger's format (crates/egui-4j/src/dev_logger.rs). */
export type DevLogEntry =
  | { kind: "log"; time: number; level: string; target?: string; message: string }
  | {
      kind: "http"
      time: number
      source?: "page" | "java"
      method: string
      url: string
      status?: number
      durationMs?: number
      request?: { headers?: Record<string, unknown>; body?: unknown }
      response?: { headers?: Record<string, unknown>; body?: unknown }
      error?: string
    }
  | {
      kind: "bridge"
      time: number
      direction?: "toJava" | "toPage"
      name: string
      durationMs?: number
      request?: { body?: unknown }
      response?: { body?: unknown }
      error?: string
    }
  | { kind: "perf"; time: number; name: string; value: number; unit?: string }

export type DevLoggerHandle = {
  /** Adds entries (a stream: send them as they come). Throws, adding none, if one is not in the format. */
  append(entries: DevLogEntry[]): void
  clear(): void
}

type DevLoggerProps = {
  /** The latest this many are kept (2000 at first). */
  maxEntries?: number
  /**
   * Searches everything the host kept (more than the logger holds): typing in the search box calls it once the
   * box is still, and the list shows what it finds while that is the search. Left out: the search box searches
   * what the logger holds.
   */
  onSearch?: (text: string) => Promise<DevLogEntry[]>
  /** Calls at least this long are slow, in the warning colour (500 at first). */
  slowMs?: number
  onEvent?: (event: EguiEvent) => void
  onError?: (message: string) => void
  height?: number | string
  className?: string
  style?: React.CSSProperties
}

/** The dev logger. Fill it through its handle: `ref.current.append([...])`. */
export const DevLogger = React.forwardRef<DevLoggerHandle, DevLoggerProps>(function DevLogger(
  { maxEntries, onSearch, slowMs, onEvent, onError, height, className, style },
  ref
) {
  const canvas = React.useRef<EguiCanvasHandle>(null)
  // entries that came before the canvas was ready: given to it once it is
  const waiting = React.useRef<DevLogEntry[]>([])

  const send = React.useCallback((props: object) => {
    if (!canvas.current) return false
    try {
      canvas.current.update(props)
      return true
    } catch (e) {
      // not shown yet ("is not shown yet"): kept for later; anything else is the entries' fault
      if (String(e).includes("not shown yet")) return false
      throw e
    }
  }, [])

  React.useImperativeHandle(
    ref,
    () => ({
      append(entries) {
        if (entries.length === 0) return
        if (!send({ append: entries })) waiting.current.push(...entries)
      },
      clear() {
        waiting.current = []
        send({ clear: true })
      },
    }),
    [send]
  )

  // what waited, once the canvas can take it
  React.useEffect(() => {
    const timer = setInterval(() => {
      if (waiting.current.length && send({ append: waiting.current })) waiting.current = []
    }, 200)
    return () => clearInterval(timer)
  }, [send])

  const props = React.useMemo(() => {
    const p: Record<string, unknown> = {}
    if (maxEntries !== undefined) p.maxEntries = maxEntries
    if (slowMs !== undefined) p.slowMs = slowMs
    p.hostSearch = onSearch !== undefined
    return p
  }, [maxEntries, slowMs, onSearch])

  // the logger asks (a `search` event); what is found goes back to it, for that text
  const latest = React.useRef({ onSearch, onEvent })
  latest.current = { onSearch, onEvent }
  const handleEvent = React.useCallback(
    (event: EguiEvent) => {
      latest.current.onEvent?.(event)
      const search = latest.current.onSearch
      if (event.event !== "search" || !search) return
      const text = String(event.text)
      search(text)
        .then((entries) => send({ found: { text, entries } }))
        .catch((e: unknown) => {
          console.error("dev logger: the search failed", e)
          send({ found: { text, entries: [] } })
        })
    },
    [send]
  )

  return (
    <EguiCanvas
      ref={canvas}
      component="dev-logger"
      props={props}
      onEvent={handleEvent}
      onError={onError}
      height={height}
      className={className}
      style={style}
      aria-label="Dev logger: log lines, API calls, calls to Java and performance"
    />
  )
})

/** The topic Java sends entries on (an array of DevLogEntry each time). */
export const DEV_LOGGER_TOPIC = "webview4j.devlogger.append"

type Bridge = {
  listen(topic: string, handler: (data: unknown) => void): () => void
  invoke(command: string, args?: unknown): Promise<unknown>
}

/** How many the page holds; Java keeps the rest in its file (DevLogger.SHOWN). */
const SHOWN = 100

/**
 * A page that is only the dev logger, fed by Java: what io.joshuasalcedo.webview4j.DevLogger opens. Entries come
 * on {@link DEV_LOGGER_TOPIC}; it holds the latest 100, and its search box searches everything (Java's file).
 */
export function mountDevLogger(root: HTMLElement) {
  function Page() {
    const logger = React.useRef<DevLoggerHandle>(null)
    React.useEffect(() => {
      const bridge = (window as unknown as { __webview4j?: Bridge }).__webview4j
      if (!bridge) {
        console.warn("dev logger: not in a webview-4j window, nothing will come")
        return
      }
      return bridge.listen(DEV_LOGGER_TOPIC, (data) => {
        if (Array.isArray(data)) logger.current?.append(data as DevLogEntry[])
      })
    }, [])
    const search = React.useCallback(async (text: string) => {
      const bridge = (window as unknown as { __webview4j?: Bridge }).__webview4j
      if (!bridge) return []
      const found = await bridge.invoke("webview4j.devlogger.search", { text })
      return Array.isArray(found) ? (found as DevLogEntry[]) : []
    }, [])
    return (
      <NativeProvider style={{ height: "100%" }}>
        <DevLogger ref={logger} height="100%" maxEntries={SHOWN} onSearch={search} />
      </NativeProvider>
    )
  }
  createRoot(root).render(<Page />)
}
