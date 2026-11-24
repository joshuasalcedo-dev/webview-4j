import * as React from "react"

/**
 * How many items of a row fit in its box, measured (not guessed): the items are drawn once, hidden, in a
 * measuring row (`measure`, the same gap as the real one; its last child is the "+N" / "More" that takes the
 * rest), and the box's width is watched as the window or panel resizes. Returns the count that fits, keeping
 * room for the last child when not all fit.
 */
function useFitCount<
  Box extends HTMLElement = HTMLElement,
  Ruler extends HTMLElement = HTMLElement,
>(signature: string, sharedBox?: React.RefObject<Box | null>) {
  // The box to watch: its own, or one shared with another measure of the same row.
  const ownBox = React.useRef<Box | null>(null)
  const box = sharedBox ?? ownBox
  const measure = React.useRef<Ruler | null>(null)
  const [count, setCount] = React.useState(Number.POSITIVE_INFINITY)

  React.useLayoutEffect(() => {
    const room = box.current
    const ruler = measure.current
    if (!room || !ruler) return
    const run = () => {
      const kids = Array.from(ruler.children) as HTMLElement[]
      if (kids.length === 0) return setCount(0)
      const rest = kids[kids.length - 1].offsetWidth
      const widths = kids.slice(0, -1).map((k) => k.offsetWidth)
      const gap = parseFloat(getComputedStyle(ruler).columnGap) || 0
      const available = room.clientWidth
      const all = widths.reduce((sum, w, i) => sum + w + (i ? gap : 0), 0)
      if (all <= available) return setCount(widths.length)
      let used = rest
      let n = 0
      for (const w of widths) {
        if (used + gap + w > available) break
        used += gap + w
        n++
      }
      setCount(n)
    }
    run()
    if (typeof ResizeObserver === "undefined") return
    const watch = new ResizeObserver(run)
    watch.observe(room)
    return () => watch.disconnect()
  }, [signature, box])

  return { box, measure, count }
}

export { useFitCount }
