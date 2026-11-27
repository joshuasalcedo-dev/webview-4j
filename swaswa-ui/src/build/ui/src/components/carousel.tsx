import * as React from "react"

import "./carousel.css"

/**
 * Pages to go through one at a time: arrows at the sides, a dot per page, swipes and the arrow keys; it can move on
 * by itself (`interval`), and stops while the pointer is over it. A product's pictures, a welcome tour.
 */
type CarouselProps = {
  "aria-label": string
  children?: React.ReactNode
  /** The page shown (from 0), controlled; the carousel keeps its own when left out. */
  index?: number
  onIndexChange?: (index: number) => void
  /** Moves on by itself every this many milliseconds; 0: never. */
  interval?: number
  /** From the last page on to the first. */
  loop?: boolean
  /** Arrows and dots (on by default). */
  arrows?: boolean
  dots?: boolean
  className?: string
  style?: React.CSSProperties
}

function Carousel({ children, index, onIndexChange, interval = 0, loop = true, arrows = true, dots = true, className, style, ...props }: CarouselProps) {
  const pages = React.Children.toArray(children)
  const [own, setOwn] = React.useState(0)
  const [paused, setPaused] = React.useState(false)
  const at = Math.min(Math.max(0, index ?? own), Math.max(0, pages.length - 1))
  const go = React.useCallback(
    (next: number) => {
      const n = pages.length
      if (n === 0) return
      const to = loop ? (next + n) % n : Math.min(Math.max(0, next), n - 1)
      if (index === undefined) setOwn(to)
      onIndexChange?.(to)
    },
    [pages.length, loop, index, onIndexChange]
  )

  React.useEffect(() => {
    if (!interval || paused || pages.length < 2) return
    const timer = setInterval(() => go(at + 1), interval)
    return () => clearInterval(timer)
  }, [interval, paused, at, go, pages.length])

  const start = React.useRef<number | null>(null)
  return (
    <div
      className={["nt-carousel", className].filter(Boolean).join(" ")}
      style={style}
      role="region"
      aria-roledescription="carousel"
      aria-label={props["aria-label"]}
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "ArrowLeft") go(at - 1)
        if (e.key === "ArrowRight") go(at + 1)
      }}
      onPointerEnter={() => setPaused(true)}
      onPointerLeave={() => setPaused(false)}
      onPointerDown={(e) => (start.current = e.clientX)}
      onPointerUp={(e) => {
        if (start.current == null) return
        const dx = e.clientX - start.current
        start.current = null
        if (Math.abs(dx) > 40) go(dx < 0 ? at + 1 : at - 1)
      }}
    >
      <div className="nt-carousel-track" style={{ transform: `translateX(-${at * 100}%)` }}>
        {pages.map((page, i) => (
          <div key={i} className="nt-carousel-page" role="group" aria-roledescription="slide" aria-label={`${i + 1} of ${pages.length}`} aria-hidden={i !== at} inert={i !== at || undefined}>
            {page}
          </div>
        ))}
      </div>
      {arrows && pages.length > 1 && (
        <>
          <button type="button" className="nt-carousel-arrow" data-side="previous" aria-label="Previous" disabled={!loop && at === 0} onClick={() => go(at - 1)}>
            ‹
          </button>
          <button type="button" className="nt-carousel-arrow" data-side="next" aria-label="Next" disabled={!loop && at === pages.length - 1} onClick={() => go(at + 1)}>
            ›
          </button>
        </>
      )}
      {dots && pages.length > 1 && (
        <div className="nt-carousel-dots" role="tablist" aria-label="Pages">
          {pages.map((_, i) => (
            <button key={i} type="button" role="tab" aria-selected={i === at} aria-label={`Page ${i + 1}`} className="nt-carousel-dot" onClick={() => go(i)} />
          ))}
        </div>
      )}
    </div>
  )
}

export { Carousel }
export type { CarouselProps }
