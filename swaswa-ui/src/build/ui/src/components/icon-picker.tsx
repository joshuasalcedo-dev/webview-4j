import * as React from "react"

import { cn } from "../lib/utils"
import { useNative } from "./provider"
import { SearchField } from "./search-field"
import { Select } from "./select"

/** One icon the picker can offer. Where icons come from is the app's business (a catalog, a font, SVGs). */
type PickableIcon = {
  id: string
  label: string
  /** Which set it belongs to (for the set chooser). */
  group?: string
  /** Draws it at a size, in px. Called only once the icon is scrolled into view. */
  render: (size: number) => React.ReactNode
}

type IconPickerProps = {
  icons: PickableIcon[]
  /** The sets, in the order to offer them; left out, no set chooser. */
  groups?: { id: string; label: string }[]
  value?: string | null
  onChange: (id: string | null) => void
  /** Offers "No icon" first. */
  allowNone?: boolean
  /** Size of each icon, in px (24 by default). */
  size?: number
  /** Height of the grid, in px (240 by default); it scrolls. */
  height?: number
  "aria-label"?: string
  className?: string
}

/**
 * Choosing an icon: a search box, a set chooser, and a grid of icons that only draws the ones scrolled into
 * view (thousands are fine). Arrow keys move, Enter or a click chooses; each icon says its name on hover.
 */
function IconPicker({
  icons,
  groups,
  value = null,
  onChange,
  allowNone = false,
  size = 24,
  height = 240,
  "aria-label": label = "Icons",
  className,
}: IconPickerProps) {
  const { platform } = useNative()
  const [search, setSearch] = React.useState("")
  const [group, setGroup] = React.useState("all")
  const [seen, setSeen] = React.useState<Set<string>>(() => new Set())
  const grid = React.useRef<HTMLDivElement>(null)
  const [focus, setFocus] = React.useState(0)

  const words = search.toLowerCase().split(/\s+/).filter(Boolean)
  const shown = icons.filter(
    (i) =>
      (group === "all" || i.group === group) &&
      words.every(
        (w) =>
          i.label.toLowerCase().includes(w) ||
          (i.group ?? "").toLowerCase().includes(w)
      )
  )
  const cells: (PickableIcon | null)[] =
    allowNone && !words.length ? [null, ...shown] : shown

  // Draw an icon only once its cell has been near the visible area (and keep it drawn after).
  React.useEffect(() => {
    const root = grid.current
    if (!root) return
    const watcher = new IntersectionObserver(
      (entries) => {
        const now = entries
          .filter((e) => e.isIntersecting)
          .map((e) => (e.target as HTMLElement).dataset.id ?? "")
        if (now.length) setSeen((was) => new Set([...was, ...now]))
      },
      { root, rootMargin: "120px 0px" }
    )
    root.querySelectorAll("[data-id]").forEach((cell) => watcher.observe(cell))
    return () => watcher.disconnect()
  }, [search, group, icons])

  const across = () => {
    const children = Array.from(grid.current?.children ?? []) as HTMLElement[]
    const top = children[0]?.offsetTop
    return Math.max(1, children.filter((c) => c.offsetTop === top).length)
  }
  const move = (to: number) => {
    const next = Math.max(0, Math.min(cells.length - 1, to))
    setFocus(next)
    ;(grid.current?.children[next] as HTMLElement | undefined)?.focus()
  }

  return (
    <div className={cn("nt-iconpicker", className)} data-platform={platform}>
      <div className="nt-iconpicker-bar">
        <SearchField
          aria-label={`Search ${label.toLowerCase()}`}
          placeholder="Search icons"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setFocus(0)
          }}
          style={{ flex: 1 }}
        />
        {groups && groups.length > 1 && (
          <Select
            aria-label="Icon set"
            value={group}
            onChange={(event) => {
              setGroup(event.target.value)
              setFocus(0)
            }}
            options={[
              { value: "all", label: "All sets" },
              ...groups.map((g) => ({ value: g.id, label: g.label })),
            ]}
          />
        )}
      </div>
      <div
        ref={grid}
        role="listbox"
        aria-label={label}
        className="nt-iconpicker-grid"
        style={
          { height, "--nt-icon-cell": `${size + 16}px` } as React.CSSProperties
        }
      >
        {cells.map((icon, index) => {
          const id = icon?.id ?? ""
          const chosen = (icon ? icon.id : null) === value
          return (
            <button
              key={id || "none"}
              type="button"
              role="option"
              aria-selected={chosen}
              aria-label={icon?.label ?? "No icon"}
              title={icon?.label ?? "No icon"}
              data-id={id}
              tabIndex={index === Math.min(focus, cells.length - 1) ? 0 : -1}
              className="nt-iconpicker-cell"
              onClick={() => onChange(icon ? icon.id : null)}
              onFocus={() => setFocus(index)}
              onKeyDown={(event) => {
                const by: Record<string, number> = {
                  ArrowRight: 1,
                  ArrowLeft: -1,
                  ArrowDown: across(),
                  ArrowUp: -across(),
                }
                if (event.key in by) {
                  event.preventDefault()
                  move(index + by[event.key])
                } else if (event.key === "Home") {
                  event.preventDefault()
                  move(0)
                } else if (event.key === "End") {
                  event.preventDefault()
                  move(cells.length - 1)
                }
              }}
            >
              {icon ? (
                seen.has(id) ? (
                  icon.render(size)
                ) : (
                  <span style={{ width: size, height: size }} />
                )
              ) : (
                <svg
                  viewBox="0 0 16 16"
                  width={size * 0.7}
                  height={size * 0.7}
                  aria-hidden="true"
                >
                  <circle
                    cx="8"
                    cy="8"
                    r="6.5"
                    fill="none"
                    stroke="currentColor"
                  />
                  <path d="m3.5 12.5 9-9" stroke="currentColor" />
                </svg>
              )}
            </button>
          )
        })}
        {cells.length === 0 && (
          <div className="nt-files-empty">No icon matches “{search}”.</div>
        )}
      </div>
      <div className="nt-iconpicker-foot" aria-live="polite">
        {shown.length} {shown.length === 1 ? "icon" : "icons"}
        {value
          ? ` · chosen: ${icons.find((i) => i.id === value)?.label ?? value}`
          : ""}
      </div>
    </div>
  )
}

export { IconPicker }
export type { IconPickerProps, PickableIcon }
