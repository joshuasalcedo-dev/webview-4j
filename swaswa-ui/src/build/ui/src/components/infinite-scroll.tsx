import * as React from "react"

import { cn } from "../lib/utils"
import { Button } from "./button"

// --- Spinner -----------------------------------------------------------------------------------------------

type SpinnerProps = Omit<React.HTMLAttributes<HTMLSpanElement>, "children"> & {
  /** In px: 16 by default (inline), 32 for a page. */
  size?: number
  /** What is happening, for screen readers ("Loading messages"). */
  label?: string
}

/**
 * Work going on, of unknown length: Windows 11's ProgressRing (an accent arc going round), a Mac's spinning
 * grey lines. Still (a static ring) when the person asked for less motion.
 */
function Spinner({
  size = 16,
  label = "Loading",
  className,
  style,
  ...props
}: SpinnerProps) {
  return (
    <span
      role="progressbar"
      aria-label={label}
      className={cn("nt-spinner", className)}
      style={{ width: size, height: size, ...style }}
      {...props}
    >
      {/* Windows: a ring; a Mac: twelve spokes (CSS shows the one for the system). */}
      <svg className="nt-spinner-ring" viewBox="0 0 16 16" aria-hidden="true">
        <circle
          cx="8"
          cy="8"
          r="6.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeDasharray="12 40"
        />
      </svg>
      <svg className="nt-spinner-spokes" viewBox="0 0 16 16" aria-hidden="true">
        {Array.from({ length: 12 }, (_, i) => (
          <line
            key={i}
            x1="8"
            y1="1.5"
            x2="8"
            y2="4.5"
            stroke="currentColor"
            strokeWidth="1.4"
            strokeLinecap="round"
            opacity={0.25 + (0.75 * i) / 11}
            transform={`rotate(${i * 30} 8 8)`}
          />
        ))}
      </svg>
    </span>
  )
}

// --- useInfiniteScroll: the logic, for any element that scrolls ------------------------------------------

type LoadState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "failed"; message: string }
  | { status: "done" }

type InfiniteScrollOptions = {
  /** Fetches the next page (or, upwards, the earlier one) and adds it to the list. */
  onLoadMore: () => void | Promise<void>
  /** There is more to load; false when the last page came. */
  hasMore: boolean
  /** "down" (the default): more at the bottom. "up": earlier items at the top (a chat's history). */
  direction?: "down" | "up"
  /** How close to the end it starts loading, in px (400 by default: about a screen early, so no waiting). */
  threshold?: number
  /** Turns it off for a while (e.g. while a search is being typed). */
  disabled?: boolean
}

/**
 * Loads the next page when the end of a scrolling element comes near. Give it the scrolling element and an
 * element at the end (`sentinel`, an empty div at the bottom, or the top for "up"). It never asks twice at
 * once; keeps asking while the content does not yet fill the element; and, upwards, keeps what was on screen
 * where it was when earlier items are added above.
 */
function useInfiniteScroll(
  scroller: React.RefObject<HTMLElement | null>,
  sentinel: React.RefObject<HTMLElement | null>,
  {
    onLoadMore,
    hasMore,
    direction = "down",
    threshold = 400,
    disabled = false,
  }: InfiniteScrollOptions
) {
  const [state, setState] = React.useState<LoadState>({ status: "idle" })
  const busy = React.useRef(false)
  // The newest props, for the loads that run after a page came (a render later).
  const now = React.useRef({
    onLoadMore,
    hasMore,
    disabled,
    direction,
    threshold,
  })
  React.useEffect(() => {
    now.current = { onLoadMore, hasMore, disabled, direction, threshold }
  })

  /** Whether the end mark is within `threshold` of the visible area. */
  const nearEnd = React.useCallback(() => {
    const box = scroller.current
    const end = sentinel.current
    if (!box || !end) return false
    const area = box.getBoundingClientRect()
    const mark = end.getBoundingClientRect()
    const { direction: way, threshold: margin } = now.current
    return way === "down"
      ? mark.top - area.bottom < margin
      : area.top - mark.bottom < margin
  }, [scroller, sentinel])

  // The next load, called again after a short page (through a ref: load cannot name itself).
  const again = React.useRef<(() => Promise<void>) | null>(null)
  const load = React.useCallback(async (): Promise<void> => {
    const { hasMore: more, disabled: off, direction: way } = now.current
    if (busy.current || !more || off) return
    busy.current = true
    const box = scroller.current
    // Upwards: how far the view is from the bottom, to keep it there once earlier items are added above.
    const fromBottom =
      way === "up" && box ? box.scrollHeight - box.scrollTop : null
    setState({ status: "loading" })
    try {
      await now.current.onLoadMore()
      setState({ status: "idle" })
    } catch (problem) {
      setState({
        status: "failed",
        message:
          problem instanceof Error && problem.message
            ? problem.message
            : "Couldn't load more.",
      })
      busy.current = false
      return
    }
    busy.current = false
    // Once the new page is drawn (before it is painted): keep the view still, and if the content still ends
    // within reach (short pages), ask for the next one.
    requestAnimationFrame(() => {
      const after = scroller.current
      if (after && fromBottom !== null)
        after.scrollTop = after.scrollHeight - fromBottom
      if (nearEnd()) void again.current?.()
    })
  }, [scroller, nearEnd])

  React.useEffect(() => {
    again.current = load
  }, [load])

  // Watch the end mark: when it comes within `threshold` of the visible area, load.
  React.useEffect(() => {
    const box = scroller.current
    const end = sentinel.current
    if (!box || !end || !hasMore || disabled) return
    const margin =
      direction === "down"
        ? `0px 0px ${threshold}px 0px`
        : `${threshold}px 0px 0px 0px`
    const watcher = new IntersectionObserver(
      (seen) => {
        if (seen.some((s) => s.isIntersecting)) void load()
      },
      { root: box, rootMargin: margin }
    )
    watcher.observe(end)
    return () => watcher.disconnect()
  }, [scroller, sentinel, hasMore, disabled, direction, threshold, load])

  const status: LoadState =
    !hasMore && state.status === "idle" ? { status: "done" } : state
  return { state: status, retry: load }
}

// --- InfiniteScroll: a scrolling area that loads more by itself ------------------------------------------

type InfiniteScrollProps = Omit<
  React.HTMLAttributes<HTMLDivElement>,
  "onLoad"
> &
  InfiniteScrollOptions & {
    /** Said while loading ("Loading more…" by default). */
    loadingText?: React.ReactNode
    /** Said once everything has loaded (nothing by default). */
    endText?: React.ReactNode
  }

/**
 * A scrolling area that asks for the next page by itself before the end is reached: a spinner and "Loading
 * more…" while it comes, the reason and a Try again button when it fails, and (optionally) a line saying
 * everything is there. `direction="up"` for histories that grow upwards, like a chat's.
 */
function InfiniteScroll({
  onLoadMore,
  hasMore,
  direction = "down",
  threshold,
  disabled,
  loadingText = "Loading more…",
  endText,
  className,
  children,
  ...props
}: InfiniteScrollProps) {
  const scroller = React.useRef<HTMLDivElement>(null)
  const sentinel = React.useRef<HTMLDivElement>(null)
  const { state, retry } = useInfiniteScroll(scroller, sentinel, {
    onLoadMore,
    hasMore,
    direction,
    threshold,
    disabled,
  })

  // Upwards, "Loading more…" and a failure float over the top instead of taking room: a row appearing and
  // going above the messages would move them while the view is being held still.
  const floating = direction === "up" && state.status !== "done"
  const status = (
    <div
      className="nt-load-more"
      data-floating={floating || undefined}
      aria-live="polite"
    >
      {state.status === "loading" && (
        <>
          <Spinner label="Loading more" />
          <span>{loadingText}</span>
        </>
      )}
      {state.status === "failed" && (
        <>
          <span className="nt-load-more-error">{state.message}</span>
          <Button onClick={() => void retry()}>Try again</Button>
        </>
      )}
      {state.status === "done" && endText != null && <span>{endText}</span>}
    </div>
  )
  const mark = (
    <div ref={sentinel} className="nt-load-more-mark" aria-hidden="true" />
  )

  return (
    <div
      ref={scroller}
      className={cn("nt-infinite", className)}
      aria-busy={state.status === "loading"}
      data-direction={direction}
      {...props}
    >
      {direction === "up" && mark}
      {direction === "up" && status}
      {children}
      {direction === "down" && status}
      {direction === "down" && mark}
    </div>
  )
}

export { InfiniteScroll, Spinner, useInfiniteScroll }
export type {
  InfiniteScrollOptions,
  InfiniteScrollProps,
  LoadState,
  SpinnerProps,
}
