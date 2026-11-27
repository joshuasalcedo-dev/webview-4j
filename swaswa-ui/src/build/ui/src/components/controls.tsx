// The controls shadcn/ui has that the kit lacked: Tooltip, Slider, RadioGroup, Toggle, Separator, DropdownMenu.
// Drawn with the kit's tokens, so they look like Windows 11's or macOS's, light or dark.
import * as React from "react"

import { NamedIcon } from "../lib/named-icons"
import { cn } from "../lib/utils"
import { type MenuEntry, useContextMenu } from "./context-menu"
import "./controls.css"

// --- Tooltip -----------------------------------------------------------------------------------------

type TooltipProps = {
  /** What it says. */
  content: React.ReactNode
  /** Where it shows, from what it explains (above unless set). */
  side?: "top" | "bottom" | "left" | "right"
  /** How long the pointer rests before it shows, in ms (500 unless set); at once for the keyboard's focus. */
  delay?: number
  children: React.ReactNode
}

/** A short explanation that shows when the pointer rests on something, or the keyboard's focus reaches it. */
function Tooltip({ content, side = "top", delay = 500, children }: TooltipProps) {
  const id = React.useId()
  const [shown, setShown] = React.useState(false)
  const timer = React.useRef<number | undefined>(undefined)
  const show = (after: number) => {
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setShown(true), after)
  }
  const hide = () => {
    window.clearTimeout(timer.current)
    setShown(false)
  }
  React.useEffect(() => () => window.clearTimeout(timer.current), [])
  return (
    <span
      className="nt-tooltip-anchor"
      aria-describedby={shown ? id : undefined}
      onPointerEnter={() => show(delay)}
      onPointerLeave={hide}
      onFocus={() => show(0)}
      onBlur={hide}
      onKeyDown={(e) => {
        if (e.key === "Escape") hide()
      }}
    >
      {children}
      {shown && (
        <span role="tooltip" id={id} className="nt-tooltip" data-side={side}>
          {content}
        </span>
      )}
    </span>
  )
}

// --- Slider ------------------------------------------------------------------------------------------

type SliderProps = {
  /** What it sets, for screen readers ("Volume"). */
  "aria-label": string
  value: number
  onChange?: (value: number) => void
  min?: number
  max?: number
  step?: number
  disabled?: boolean
  /** Shows the value beside it. */
  showValue?: boolean
  className?: string
  style?: React.CSSProperties
}

/** A value picked by dragging along a track, as the system's slider; ← → and Home End move it too. */
function Slider({ value, onChange, min = 0, max = 100, step = 1, disabled, showValue, className, style, ...props }: SliderProps) {
  const done = max > min ? ((value - min) / (max - min)) * 100 : 0
  const digits = String(step).split(".")[1]?.length ?? 0
  return (
    <span className={cn("nt-slider", className)} style={style} data-disabled={disabled || undefined}>
      <input
        type="range"
        aria-label={props["aria-label"]}
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        style={{ "--nt-slider-done": `${Math.min(100, Math.max(0, done))}%` } as React.CSSProperties}
        onChange={(e) => onChange?.(Number(e.currentTarget.value))}
      />
      {showValue && <span className="nt-slider-value">{value.toFixed(digits)}</span>}
    </span>
  )
}

// --- RadioGroup --------------------------------------------------------------------------------------

type RadioOption = { value: string; label: string; disabled?: boolean }

type RadioGroupProps = {
  /** What is chosen, said above the choices. */
  label?: string
  options: RadioOption[]
  value?: string | null
  onChange?: (value: string) => void
  /** One under the other (the default), or side by side. */
  orientation?: "vertical" | "horizontal"
  disabled?: boolean
  className?: string
  style?: React.CSSProperties
}

/** One of a few choices, all in sight: the system's radio buttons. ↑ ↓ (or ← →) move between them. */
function RadioGroup({ label, options, value, onChange, orientation = "vertical", disabled, className, style }: RadioGroupProps) {
  const name = React.useId()
  return (
    <fieldset className={cn("nt-radio-group", className)} style={style} disabled={disabled} data-orientation={orientation}>
      {label && <legend className="nt-radio-legend">{label}</legend>}
      {options.map((o) => (
        <label key={o.value} className="nt-radio" data-disabled={o.disabled || disabled || undefined}>
          <input
            type="radio"
            name={name}
            value={o.value}
            checked={value === o.value}
            disabled={o.disabled || disabled}
            onChange={() => onChange?.(o.value)}
          />
          <span>{o.label}</span>
        </label>
      ))}
    </fieldset>
  )
}

// --- Toggle ------------------------------------------------------------------------------------------

type ToggleProps = {
  label: string
  /** An icon by name before the label, or instead of it with iconOnly. */
  icon?: string
  /** Only the icon shows; the label is for screen readers and its tooltip. */
  iconOnly?: boolean
  pressed: boolean
  onPressedChange?: (pressed: boolean) => void
  disabled?: boolean
  className?: string
  style?: React.CSSProperties
}

/** A button that stays pressed until pressed again: Bold in an editor, a filter that is on. */
function Toggle({ label, icon, iconOnly, pressed, onPressedChange, disabled, className, style }: ToggleProps) {
  return (
    <button
      type="button"
      className={cn("nt-toggle", className)}
      style={style}
      aria-pressed={pressed}
      aria-label={iconOnly ? label : undefined}
      title={iconOnly ? label : undefined}
      disabled={disabled}
      onClick={() => onPressedChange?.(!pressed)}
    >
      {icon && <NamedIcon name={icon} />}
      {!iconOnly && <span>{label}</span>}
    </button>
  )
}

// --- Separator ---------------------------------------------------------------------------------------

type SeparatorProps = {
  /** Across (the default) or up and down. */
  orientation?: "horizontal" | "vertical"
  /** A few words in its middle ("or"). */
  label?: string
  className?: string
  style?: React.CSSProperties
}

/** A thin line between groups of things. */
function Separator({ orientation = "horizontal", label, className, style }: SeparatorProps) {
  return (
    <div role="separator" aria-orientation={orientation} className={cn("nt-separator", className)} style={style} data-orientation={orientation}>
      {label && orientation === "horizontal" && <span>{label}</span>}
    </div>
  )
}

// --- DropdownMenu ------------------------------------------------------------------------------------

type DropdownMenuProps = {
  /** What the button says. */
  label: string
  /** An icon by name before the label. */
  icon?: string
  entries: MenuEntry[] | (() => MenuEntry[])
  variant?: "default" | "subtle" | "accent"
  disabled?: boolean
  className?: string
  style?: React.CSSProperties
}

/** A button that opens a menu under it: the same menu as a right-click's (icons, shortcuts, ticks, lines). */
function DropdownMenu({ label, icon, entries, variant = "default", disabled, className, style }: DropdownMenuProps) {
  const { onContextMenu } = useContextMenu(entries)
  return (
    <button
      type="button"
      className={cn("nt-button nt-dropdown", className)}
      data-variant={variant === "default" ? undefined : variant}
      data-chevron-only={!label && !icon ? "" : undefined}
      aria-label={label || "More"}
      style={style}
      aria-haspopup="menu"
      disabled={disabled}
      onClick={(e) => {
        // no pointer position: the menu opens under the button, as from the keyboard
        const button = e.currentTarget
        onContextMenu({ preventDefault: () => e.preventDefault(), currentTarget: button, target: button, clientX: 0, clientY: 0 } as unknown as React.MouseEvent)
      }}
    >
      {icon && <NamedIcon name={icon} />}
      {label && <span>{label}</span>}
      <svg className="nt-dropdown-chevron" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.4} aria-hidden>
        <path d="m4.5 6.5 3.5 3.5 3.5-3.5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </button>
  )
}

export { DropdownMenu, RadioGroup, Separator, Slider, Toggle, Tooltip }
export type { DropdownMenuProps, RadioGroupProps, RadioOption, SeparatorProps, SliderProps, ToggleProps, TooltipProps }
