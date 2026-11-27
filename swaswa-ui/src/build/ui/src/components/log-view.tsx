import * as React from "react"

import "./log-view.css"
import { cn } from "../lib/utils"

type LogLevel = "trace" | "debug" | "info" | "warn" | "error"

type LogViewLine = {
  /** Stays the same for the line (its place in the log). */
  id: string | number
  level: LogLevel
  /** When it was written (ms since 1970). */
  time: number
  /** What happened, as people read it. */
  message: React.ReactNode
  /** Where it came from (a part of the app). */
  source?: string
  /** The technical text behind a message written for people: shown when the line is opened. */
  detail?: string
}

type LogViewProps = Omit<React.ComponentProps<"div">, "children"> & {
  /** What the log is, for screen readers (e.g. "Activity"). */
  "aria-label": string
  /** Oldest first. */
  lines: LogViewLine[]
  /** Shown when there are no lines. */
  empty?: React.ReactNode
  /** Keep the newest line in view as lines come (true by default); scrolling up stops it until the bottom again. */
  follow?: boolean
  /** Times with seconds (true by default) or hours and minutes. */
  seconds?: boolean
}

const LEVEL_WORD: Record<LogLevel, string> = {
  trace: "Trace",
  debug: "Debug",
  info: "Info",
  warn: "Warning",
  error: "Error",
}

/**
 * A log that grows as things happen: each line its time, how serious it is, where it came from and what happened.
 * Warnings and errors stand out; a line with technical details opens on a click (or Enter) to show them. New lines
 * keep the newest in view, unless the person scrolled up to read.
 */
function LogView({
  lines,
  empty,
  follow = true,
  seconds = true,
  className,
  ...props
}: LogViewProps) {
  const box = React.useRef<HTMLDivElement>(null)
  const [atBottom, setAtBottom] = React.useState(true)
  const [open, setOpen] = React.useState<Set<string | number>>(new Set())

  React.useLayoutEffect(() => {
    const el = box.current
    if (el && follow && atBottom) el.scrollTop = el.scrollHeight
  }, [lines, follow, atBottom])

  const toggle = (id: string | number) =>
    setOpen((now) => {
      const next = new Set(now)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const time = (at: number) =>
    new Date(at).toLocaleTimeString(undefined, seconds ? undefined : { hour: "2-digit", minute: "2-digit" })

  return (
    <div
      ref={box}
      role="log"
      aria-live="polite"
      className={cn("nt-log", className)}
      onScroll={(e) => {
        const el = e.currentTarget
        setAtBottom(el.scrollHeight - el.scrollTop - el.clientHeight < 24)
      }}
      {...props}
    >
      {lines.length === 0 ? (
        <div className="nt-log-empty">{empty ?? "Nothing yet."}</div>
      ) : (
        lines.map((line) => {
          const opened = open.has(line.id)
          return (
            <div
              key={line.id}
              className="nt-log-line"
              data-level={line.level}
              data-open={opened || undefined}
              tabIndex={line.detail ? 0 : undefined}
              aria-expanded={line.detail ? opened : undefined}
              onClick={line.detail ? () => toggle(line.id) : undefined}
              onKeyDown={line.detail ? (e) => (e.key === "Enter" || e.key === " ") && (e.preventDefault(), toggle(line.id)) : undefined}
            >
              <span className="nt-log-time">{time(line.time)}</span>
              <span className="nt-log-level" title={LEVEL_WORD[line.level]}>
                {LEVEL_WORD[line.level]}
              </span>
              <span className="nt-log-body">
                {line.source && <span className="nt-log-source">{line.source}</span>}
                <span className="nt-log-message">{line.message}</span>
                {line.detail && opened && <code className="nt-log-detail">{line.detail}</code>}
              </span>
            </div>
          )
        })
      )}
    </div>
  )
}

export { LogView }
export type { LogLevel, LogViewLine, LogViewProps }
