import * as React from "react"

import { cn } from "../lib/utils"
import { useSound } from "./sound"
import "./swipeable.css"

/** One action behind a row. */
type SwipeAction = {
  label: string
  /** 16px, above the label. */
  icon?: React.ReactNode
  /** Its colour, from the theme: accent (the default), success, warning, error, or neutral (grey). */
  tone?: "accent" | "success" | "warning" | "error" | "neutral"
  onAction: () => void
}

type SwipeableProps = Omit<React.HTMLAttributes<HTMLElement>, "children"> & {
  /** Revealed on the left when the row is dragged to the right (e.g. Mark as unread). */
  swipeRight?: SwipeAction[]
  /** Revealed on the right when the row is dragged to the left (e.g. Archive, Delete). */
  swipeLeft?: SwipeAction[]
  /** The element it is (e.g. "li" in a list). */
  as?: React.ElementType
  children: React.ReactNode
}

const DEAD_ZONE = 6
const BUTTON = 76
/** Past this share of the row's width, letting go does the first action straight away. */
const FULL = 0.55

/**
 * A row with actions behind it, as Mail on a Mac and Outlook have: drag it left or right (mouse, pen, touch,
 * or two fingers on a trackpad) to show them. A short drag shows the buttons; a long one, past half the row,
 * does the first action at once (its colour and icon grow as you get there). It springs back when let go
 * early. A small movement is still a click, and dragging up or down still scrolls. The same actions belong in
 * the row's right-click menu, for the keyboard.
 */
function Swipeable({
  swipeRight = [],
  swipeLeft = [],
  as: Element = "div",
  className,
  children,
  ...props
}: SwipeableProps) {
  const sound = useSound()
  const box = React.useRef<HTMLElement>(null)
  const [offset, setOffset] = React.useState(0)
  const [dragging, setDragging] = React.useState(false)
  const [rowWidth, setRowWidth] = React.useState(320)
  const drag = React.useRef<{
    x: number
    y: number
    from: number
    id: number
    moved: boolean
  } | null>(null)
  const wheel = React.useRef<{ total: number; timer: number } | null>(null)
  const width = () => box.current?.offsetWidth ?? 320

  const limitFor = (x: number) => {
    if (x > 0 && swipeRight.length === 0) return 0
    if (x < 0 && swipeLeft.length === 0) return 0
    return x
  }

  const run = (action: SwipeAction | undefined) => {
    if (!action) return
    sound.play("tap")
    action.onAction()
  }

  /** Where it rests after being let go at `x`: open on the buttons, closed, or the first action done. */
  const settle = (x: number) => {
    const full = width() * FULL
    const actions = x > 0 ? swipeRight : swipeLeft
    if (Math.abs(x) >= full && actions.length > 0) {
      run(actions[0])
      setOffset(0)
    } else if (Math.abs(x) >= (actions.length * BUTTON) / 2) {
      setOffset(Math.sign(x) * actions.length * BUTTON)
    } else setOffset(0)
  }

  const onPointerDown = (event: React.PointerEvent<HTMLElement>) => {
    if (
      event.button !== 0 ||
      (swipeLeft.length === 0 && swipeRight.length === 0)
    )
      return
    if ((event.target as Element).closest(".nt-swipe-actions")) return
    setRowWidth(width())
    drag.current = {
      x: event.clientX,
      y: event.clientY,
      from: offset,
      id: event.pointerId,
      moved: false,
    }
  }

  const onPointerMove = (event: React.PointerEvent<HTMLElement>) => {
    const d = drag.current
    if (!d || d.id !== event.pointerId) return
    const dx = event.clientX - d.x
    const dy = event.clientY - d.y
    if (!d.moved) {
      // Small movements stay clicks; mostly-vertical ones stay scrolling.
      if (Math.abs(dx) < DEAD_ZONE || Math.abs(dy) > Math.abs(dx)) {
        if (Math.abs(dy) > DEAD_ZONE) drag.current = null
        return
      }
      d.moved = true
      setDragging(true)
      box.current?.setPointerCapture(event.pointerId)
    }
    const raw = limitFor(d.from + dx)
    // Past the full width it moves slower (rubber band).
    const max = width() * 0.9
    setOffset(
      Math.abs(raw) > max
        ? Math.sign(raw) * (max + (Math.abs(raw) - max) * 0.25)
        : raw
    )
  }

  const onPointerEnd = (event: React.PointerEvent<HTMLElement>) => {
    const d = drag.current
    drag.current = null
    if (!d || !d.moved) return
    setDragging(false)
    box.current?.releasePointerCapture?.(event.pointerId)
    settle(offset)
  }

  // A dragged row is not clicked when let go; an open one closes on a click.
  const onClickCapture = (event: React.MouseEvent) => {
    if (dragging) {
      event.preventDefault()
      event.stopPropagation()
    } else if (
      offset !== 0 &&
      !(event.target as Element).closest(".nt-swipe-actions")
    ) {
      event.preventDefault()
      event.stopPropagation()
      setOffset(0)
    }
  }

  // Two fingers sideways on a trackpad (a horizontal wheel): the same, settling when the fingers stop.
  const onWheel = (event: React.WheelEvent<HTMLElement>) => {
    if (
      Math.abs(event.deltaX) <= Math.abs(event.deltaY) ||
      (swipeLeft.length === 0 && swipeRight.length === 0)
    )
      return
    if (!wheel.current) setRowWidth(width())
    const w = wheel.current ?? { total: offset, timer: 0 }
    window.clearTimeout(w.timer)
    w.total = limitFor(w.total - event.deltaX)
    setDragging(true)
    setOffset(w.total)
    w.timer = window.setTimeout(() => {
      wheel.current = null
      setDragging(false)
      settle(w.total)
    }, 160)
    wheel.current = w
  }

  // Escape closes an open row.
  const onKeyDown = (event: React.KeyboardEvent<HTMLElement>) => {
    if (event.key === "Escape" && offset !== 0) {
      event.stopPropagation()
      setOffset(0)
    }
  }

  const side = offset > 0 ? "right" : offset < 0 ? "left" : undefined
  const actions =
    side === "right" ? swipeRight : side === "left" ? swipeLeft : []
  const toward = Math.min(1, Math.abs(offset) / (rowWidth * FULL))
  const committing = toward >= 1

  return (
    <Element
      ref={box}
      className={cn("nt-swipe", className)}
      data-dragging={dragging || undefined}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerEnd}
      onPointerCancel={onPointerEnd}
      onClickCapture={onClickCapture}
      onWheel={onWheel}
      onKeyDown={onKeyDown}
      {...props}
    >
      {side && (
        <span
          className="nt-swipe-actions"
          data-side={side}
          data-committing={committing || undefined}
          style={{ width: Math.abs(offset) }}
        >
          {actions.map((action, i) => (
            <button
              key={action.label}
              type="button"
              className="nt-swipe-action"
              data-tone={action.tone ?? "accent"}
              // The first action takes the whole width once a long drag will do it.
              style={
                committing && i === 0
                  ? { flexGrow: 1 }
                  : committing
                    ? { display: "none" }
                    : undefined
              }
              tabIndex={-1}
              onClick={() => {
                run(action)
                setOffset(0)
              }}
            >
              {action.icon && (
                <span
                  className="nt-swipe-icon"
                  style={{
                    transform: `scale(${i === 0 ? 1 + toward * 0.3 : 1})`,
                  }}
                >
                  {action.icon}
                </span>
              )}
              <span>{action.label}</span>
            </button>
          ))}
        </span>
      )}
      <div
        className="nt-swipe-content"
        style={{ transform: offset ? `translateX(${offset}px)` : undefined }}
      >
        {children}
      </div>
    </Element>
  )
}

export { Swipeable }
export type { SwipeAction, SwipeableProps }
