// shadcn/ui's pickers the kit lacked: Combobox (a select you can type in) and Calendar (a month to pick a day on).
import * as React from "react"

import { cn } from "../lib/utils"
import { Portal, useDismiss, useFloating } from "./overlays"
import "./more-controls.css"

// --- Combobox ----------------------------------------------------------------------------------------

type ComboboxOption = { value: string; label: string; disabled?: boolean }

type ComboboxProps = {
  label?: string
  options: ComboboxOption[]
  value?: string | null
  onChange?: (value: string) => void
  placeholder?: string
  /** What the list says when nothing matches what is typed. */
  emptyText?: string
  disabled?: boolean
  className?: string
  style?: React.CSSProperties
}

/** A choice from a long list, found by typing part of its name: ↑ ↓ to move, Enter to take, Esc to leave. */
function Combobox({ label, options, value, onChange, placeholder = "Search…", emptyText = "No match.", disabled, className, style }: ComboboxProps) {
  const chosen = options.find((o) => o.value === value)
  const [open, setOpen] = React.useState(false)
  const [query, setQuery] = React.useState("")
  const [at, setAt] = React.useState(0)
  const box = React.useRef<HTMLDivElement>(null)
  const panel = React.useRef<HTMLDivElement>(null)
  const id = React.useId()
  const where = useFloating(box, panel, open, "bottom")
  useDismiss(open, () => setOpen(false), [box, panel])
  const q = query.trim().toLowerCase()
  const found = open && q ? options.filter((o) => o.label.toLowerCase().includes(q)) : options
  const take = (o: ComboboxOption | undefined) => {
    if (!o || o.disabled) return
    onChange?.(o.value)
    setOpen(false)
    setQuery("")
  }
  return (
    <div className={cn("nt-field nt-combobox", className)} style={style}>
      {label && (
        <label className="nt-field-label" htmlFor={id}>
          {label}
        </label>
      )}
      <div ref={box} className="nt-input nt-input-box" data-disabled={disabled || undefined}>
        <input
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={`${id}-list`}
          aria-autocomplete="list"
          aria-activedescendant={open && found[at] ? `${id}-${at}` : undefined}
          disabled={disabled}
          placeholder={chosen?.label ?? placeholder}
          value={open ? query : (chosen?.label ?? "")}
          onFocus={() => {
            setOpen(true)
            setAt(Math.max(0, options.findIndex((o) => o.value === value)))
          }}
          onClick={() => setOpen(true)}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
            setAt(0)
          }}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") setAt((a) => Math.min(found.length - 1, a + 1))
            else if (e.key === "ArrowUp") setAt((a) => Math.max(0, a - 1))
            else if (e.key === "Enter" && open) take(found[at])
            else if (e.key === "Escape") setOpen(false)
            else return
            e.preventDefault()
            setOpen(true)
          }}
        />
        <svg className="nt-combobox-chevron" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.4} aria-hidden>
          <path d="m4.5 6.5 3.5 3.5 3.5-3.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      {open && (
        <Portal>
          <div
            ref={panel}
            id={`${id}-list`}
            role="listbox"
            className="nt-popover nt-combobox-list"
            style={{ width: box.current?.offsetWidth, ...(where ? { top: where.top, left: where.left } : { visibility: "hidden", top: 0, left: 0 }) }}
          >
            {found.length === 0 && <div className="nt-command-empty">{emptyText}</div>}
            {found.map((o, i) => (
              <div
                key={o.value}
                id={`${id}-${i}`}
                role="option"
                aria-selected={o.value === value}
                aria-disabled={o.disabled || undefined}
                data-active={i === at || undefined}
                className="nt-combobox-option"
                onPointerMove={() => setAt(i)}
                onPointerDown={(e) => e.preventDefault()}
                onClick={() => take(o)}
              >
                <span className="nt-combobox-tick">{o.value === value ? "✓" : ""}</span>
                {o.label}
              </div>
            ))}
          </div>
        </Portal>
      )}
    </div>
  )
}

// --- Calendar ----------------------------------------------------------------------------------------

type CalendarProps = {
  /** The chosen day, "2026-11-25" (ISO), or none. */
  value?: string | null
  onChange?: (date: string) => void
  /** The first and last days that can be chosen, ISO. */
  min?: string
  max?: string
  /** The month shown first ("2026-11"); the chosen day's, or this month's. */
  month?: string
  /** The weeks start on Monday (the default) or Sunday. */
  weekStart?: "monday" | "sunday"
  className?: string
  style?: React.CSSProperties
}

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
const parse = (s?: string | null) => {
  const m = s ? /^(\d{4})-(\d{2})(?:-(\d{2}))?$/.exec(s) : null
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, m[3] ? Number(m[3]) : 1) : undefined
}

/** A month to pick a day on: ← → ↑ ↓ move by days and weeks, PageUp PageDown by months, Enter takes it. */
function Calendar({ value, onChange, min, max, month, weekStart = "monday", className, style }: CalendarProps) {
  const chosen = parse(value)
  const [shown, setShown] = React.useState(() => {
    const d = parse(month) ?? chosen ?? new Date()
    return new Date(d.getFullYear(), d.getMonth(), 1)
  })
  const [focus, setFocus] = React.useState(() => chosen ?? new Date())
  const grid = React.useRef<HTMLDivElement>(null)
  const low = parse(min)
  const high = parse(max)
  const allowed = (d: Date) => (!low || d >= low) && (!high || d <= high)
  const today = iso(new Date())
  const first = weekStart === "monday" ? 1 : 0
  const start = new Date(shown)
  start.setDate(1 - ((shown.getDay() - first + 7) % 7))
  const days = Array.from({ length: 42 }, (_, i) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + i))
  const names = Array.from({ length: 7 }, (_, i) => new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(new Date(2024, 0, 7 + first + i)))
  const title = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" }).format(shown)
  const move = (d: Date) => {
    setFocus(d)
    if (d.getMonth() !== shown.getMonth() || d.getFullYear() !== shown.getFullYear()) setShown(new Date(d.getFullYear(), d.getMonth(), 1))
    requestAnimationFrame(() => grid.current?.querySelector<HTMLButtonElement>(`[data-day="${iso(d)}"]`)?.focus())
  }
  const by = (d: Date, days: number, months = 0) => new Date(d.getFullYear(), d.getMonth() + months, d.getDate() + days)
  return (
    <div className={cn("nt-calendar", className)} style={style}>
      <div className="nt-calendar-head">
        <button type="button" className="nt-calendar-nav" aria-label="Previous month" onClick={() => setShown(by(shown, 0, -1))}>
          ‹
        </button>
        <div className="nt-calendar-title" aria-live="polite">
          {title}
        </div>
        <button type="button" className="nt-calendar-nav" aria-label="Next month" onClick={() => setShown(by(shown, 0, 1))}>
          ›
        </button>
      </div>
      <div ref={grid} role="grid" aria-label={title} className="nt-calendar-grid">
        {names.map((n) => (
          <div key={n} role="columnheader" className="nt-calendar-weekday">
            {n}
          </div>
        ))}
        {days.map((d) => {
          const key = iso(d)
          const outside = d.getMonth() !== shown.getMonth()
          const ok = allowed(d)
          return (
            <button
              key={key}
              type="button"
              role="gridcell"
              data-day={key}
              data-outside={outside || undefined}
              data-today={key === today || undefined}
              aria-selected={key === value}
              aria-label={new Intl.DateTimeFormat(undefined, { dateStyle: "full" }).format(d)}
              tabIndex={key === iso(focus) ? 0 : -1}
              disabled={!ok}
              className="nt-calendar-day"
              onClick={() => {
                setFocus(d)
                onChange?.(key)
              }}
              onKeyDown={(e) => {
                const next =
                  e.key === "ArrowLeft" ? by(d, -1) : e.key === "ArrowRight" ? by(d, 1) : e.key === "ArrowUp" ? by(d, -7) : e.key === "ArrowDown" ? by(d, 7)
                  : e.key === "PageUp" ? by(d, 0, -1) : e.key === "PageDown" ? by(d, 0, 1) : undefined
                if (next) {
                  e.preventDefault()
                  move(next)
                }
              }}
            >
              {d.getDate()}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export { Calendar, Combobox }
export type { CalendarProps, ComboboxOption, ComboboxProps }
