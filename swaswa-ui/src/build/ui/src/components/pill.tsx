import * as React from "react"

import { Close } from "../lib/icons"
import { useFitCount } from "../lib/fit"
import { cn } from "../lib/utils"
import "./pill.css"

/** The pill colours: named, and each with a light and a dark shade (pill.css). "accent" follows the app's accent. */
type PillColor =
  | "grey"
  | "red"
  | "orange"
  | "yellow"
  | "green"
  | "teal"
  | "blue"
  | "purple"
  | "pink"
  | "accent"

type PillProps = Omit<React.HTMLAttributes<HTMLSpanElement>, "color"> & {
  color?: PillColor
  /** "subtle" (the default): a tinted background. "filled": the full colour, white text. "outline": a coloured border. */
  variant?: "subtle" | "filled" | "outline"
  size?: "small" | "medium"
  /** A 12px icon before the text; with no text, an icon-only pill (give it a `label` for its tooltip and name). */
  icon?: React.ReactNode
  /** What it says, for an icon-only pill (its tooltip and accessible name). */
  label?: string
  /** Shows an × that takes it off. */
  onRemove?: () => void
  /** Makes it a toggle (e.g. a filter): pressed or not. */
  pressed?: boolean
  onPressedChange?: (pressed: boolean) => void
}

/**
 * A small rounded label: a tag on a conversation ("VIP", "Tipped"), a status, a count, or an icon on its own
 * (a star, a crown) with a tooltip. Named colours that suit light and dark; subtle, filled or outlined.
 * With onPressedChange it is a toggle button (filters); with onRemove it has an ×.
 */
function Pill({
  color = "grey",
  variant = "subtle",
  size = "small",
  icon,
  label,
  onRemove,
  pressed,
  onPressedChange,
  className,
  children,
  title,
  ...props
}: PillProps) {
  const iconOnly = children == null && icon != null
  const toggle = onPressedChange != null
  const Element = toggle ? "button" : "span"
  return (
    <Element
      {...(props as React.HTMLAttributes<HTMLElement>)}
      {...(toggle
        ? {
            type: "button" as const,
            "aria-pressed": !!pressed,
            onClick: () => onPressedChange?.(!pressed),
          }
        : {})}
      className={cn("nt-pill", className)}
      data-color={color}
      data-variant={variant}
      data-size={size}
      data-icon-only={iconOnly || undefined}
      data-pressed={pressed || undefined}
      title={title ?? label}
      aria-label={iconOnly ? label : undefined}
      role={iconOnly && !toggle ? "img" : undefined}
    >
      {icon != null && (
        <span className="nt-pill-icon" aria-hidden="true">
          {icon}
        </span>
      )}
      {children != null && <span className="nt-pill-text">{children}</span>}
      {onRemove && (
        <button
          type="button"
          className="nt-pill-remove"
          aria-label={`Remove ${typeof children === "string" ? children : (label ?? "")}`.trim()}
          onClick={(event) => {
            event.stopPropagation()
            onRemove()
          }}
        >
          <Close />
        </button>
      )}
    </Element>
  )
}

const textOf = (p: React.ReactNode) =>
  React.isValidElement<PillProps>(p)
    ? typeof p.props.children === "string"
      ? p.props.children
      : (p.props.label ?? "")
    : ""

/** A pill as icon-only, when it has an icon (its text becomes its tooltip). */
const compact = (p: React.ReactNode) =>
  React.isValidElement<PillProps>(p) &&
  p.props.icon != null &&
  p.props.children != null
    ? React.cloneElement(p, { children: undefined, label: textOf(p) })
    : p

/**
 * Pills in a row. With `max`, that many then "+N". With `fit`, as many as the room allows, measured as the
 * window resizes: all of them; or, when they don't fit, the ones with icons as icons only; then "+N" for the
 * rest; nothing when there's no room (the text beside them keeps its room: give the group the space left over).
 */
function PillGroup({
  children,
  max = 3,
  fit = false,
  className,
}: {
  children: React.ReactNode
  max?: number
  fit?: boolean
  className?: string
}) {
  const all = React.Children.toArray(children)
  const signature = all.map(textOf).join("|")
  const {
    box: fullBox,
    measure: fullRuler,
    count: fullCount,
  } = useFitCount<HTMLSpanElement, HTMLSpanElement>(`full:${signature}`)
  const { measure: smallRuler, count: smallCount } = useFitCount<
    HTMLSpanElement,
    HTMLSpanElement
  >(`small:${signature}`, fullBox)
  const more = (rest: React.ReactNode[]) =>
    rest.length > 0 && (
      <Pill
        color="grey"
        variant="outline"
        title={rest.map(textOf).filter(Boolean).join(", ") || undefined}
      >
        +{rest.length}
      </Pill>
    )

  if (!fit) {
    const shown = all.slice(0, max)
    return (
      <span className={cn("nt-pill-group", className)}>
        {shown}
        {more(all.slice(max))}
      </span>
    )
  }

  const useSmall = fullCount < all.length && smallCount > fullCount
  const count = Math.min(useSmall ? smallCount : fullCount, all.length)
  const shown = (useSmall ? all.map(compact) : all).slice(0, count)
  const rest = all.slice(count)
  return (
    <span ref={fullBox} className={cn("nt-pill-group", className)} data-fit="">
      {/* Hidden rulers: every pill as it is, and as icon-only, each with a "+9" at the end. */}
      <span ref={fullRuler} className="nt-pill-ruler" aria-hidden="true">
        {all}
        <Pill variant="outline">+9</Pill>
      </span>
      <span ref={smallRuler} className="nt-pill-ruler" aria-hidden="true">
        {all.map(compact)}
        <Pill variant="outline">+9</Pill>
      </span>
      {count > 0 && shown}
      {count > 0 && more(rest)}
    </span>
  )
}

export { Pill, PillGroup }
export type { PillColor, PillProps }
