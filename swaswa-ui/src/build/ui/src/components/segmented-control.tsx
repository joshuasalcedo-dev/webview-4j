import * as React from "react"

import { cn } from "../lib/utils"

type Segment<T extends string> = { value: T; label: React.ReactNode }

type SegmentedControlProps<T extends string> = Omit<
  React.ComponentProps<"div">,
  "onChange"
> & {
  segments: Segment<T>[]
  value: T
  onValueChange: (value: T) => void
  /** What the choice is about, for screen readers (e.g. "Show"). */
  "aria-label": string
}

/**
 * One of a few choices, side by side: macOS' segmented control, Windows' SelectorBar. Arrow keys move
 * between the choices, as in the system's.
 */
function SegmentedControl<T extends string>({
  segments,
  value,
  onValueChange,
  className,
  ...props
}: SegmentedControlProps<T>) {
  const refs = React.useRef<(HTMLButtonElement | null)[]>([])

  function move(from: number, by: number) {
    const next = (from + by + segments.length) % segments.length
    onValueChange(segments[next].value)
    refs.current[next]?.focus()
  }

  return (
    <div role="radiogroup" className={cn("nt-segmented", className)} {...props}>
      {segments.map((segment, index) => {
        const checked = segment.value === value
        return (
          <button
            key={segment.value}
            ref={(element) => {
              refs.current[index] = element
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            className="nt-segment"
            onClick={() => onValueChange(segment.value)}
            onKeyDown={(event) => {
              if (event.key === "ArrowRight" || event.key === "ArrowDown") {
                event.preventDefault()
                move(index, 1)
              } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
                event.preventDefault()
                move(index, -1)
              }
            }}
          >
            {segment.label}
          </button>
        )
      })}
    </div>
  )
}

export { SegmentedControl }
export type { Segment, SegmentedControlProps }
