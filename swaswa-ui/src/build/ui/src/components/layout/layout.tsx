import * as React from "react"

import "./layout.css"

/**
 * Layout panes, named and behaving as JavaFX's (so a Java developer knows them): HBox and VBox in a line, GridPane in
 * rows and columns, BorderPane around a center, StackPane on top of each other, SplitPane with dividers to drag.
 * They only arrange what is in them; they draw nothing of their own.
 */

/** Where something sits in its area, as JavaFX's Pos. */
type Pos =
  | "top-left"
  | "top-center"
  | "top-right"
  | "center-left"
  | "center"
  | "center-right"
  | "bottom-left"
  | "bottom-center"
  | "bottom-right"
  | "baseline-left"
  | "baseline-center"
  | "baseline-right"

type HPos = "left" | "center" | "right"
type VPos = "top" | "center" | "bottom" | "baseline"

/** Space inside an edge, CSS pixels: the same on every side, or each side (as JavaFX's Insets). */
type Insets = number | { top?: number; right?: number; bottom?: number; left?: number }

function padding(insets?: Insets): React.CSSProperties["padding"] {
  if (insets == null) return undefined
  if (typeof insets === "number") return insets
  return `${insets.top ?? 0}px ${insets.right ?? 0}px ${insets.bottom ?? 0}px ${insets.left ?? 0}px`
}

const V: Record<string, string> = { top: "start", center: "center", bottom: "end", baseline: "baseline" }
const H: Record<string, string> = { left: "start", center: "center", right: "end" }

/** A Pos split into its vertical and horizontal parts, as CSS keywords. */
function parts(pos: Pos): { v: string; h: string } {
  const [v, h] = pos === "center" ? ["center", "center"] : pos.split("-")
  return { v: V[v] ?? "center", h: H[h] ?? "center" }
}

type BoxProps = {
  /** Space between the children (JavaFX's spacing). */
  spacing?: number
  padding?: Insets
  /** Where the children sit (JavaFX's alignment): along the line and across it. */
  alignment?: Pos
  /** Children as tall as the HBox (as wide as the VBox), as JavaFX's fillHeight/fillWidth (on by default). */
  fill?: boolean
  /** Onto the next line when there is no room (JavaFX's FlowPane). */
  wrap?: boolean
  /** Scrolls when its children do not fit (JavaFX's ScrollPane around it). */
  scroll?: boolean
  children?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

function box(direction: "row" | "column", { spacing = 0, padding: p, alignment, fill = true, wrap, scroll, children, className, style }: BoxProps) {
  const { v, h } = parts(alignment ?? (direction === "row" ? "center-left" : "top-left"))
  const across = direction === "row" ? v : h
  return (
    <div
      className={["nt-box", className].filter(Boolean).join(" ")}
      data-direction={direction}
      style={{
        flexDirection: direction,
        gap: spacing,
        padding: padding(p),
        justifyContent: direction === "row" ? h : v,
        // fill stretches the children across, unless an alignment across is asked for
        alignItems: fill && !alignment ? "stretch" : across,
        flexWrap: wrap ? "wrap" : undefined,
        overflow: scroll ? "auto" : undefined,
        ...style,
      }}
    >
      {children}
    </div>
  )
}

/** Children side by side, left to right. */
function HBox(props: BoxProps) {
  return box("row", props)
}

/** Children one under the other, top to bottom. */
function VBox(props: BoxProps) {
  return box("column", props)
}

/**
 * A child that takes the room left over in its HBox or VBox (JavaFX's HBox.setHgrow(node, ALWAYS)); with `weight`,
 * a share of it.
 */
function Grow({ weight = 1, children, style }: { weight?: number; children?: React.ReactNode; style?: React.CSSProperties }) {
  return <div style={{ flex: `${weight} 1 0`, minWidth: 0, minHeight: 0, display: "flex", flexDirection: "column", ...style }}>{children}</div>
}

/** How a GridPane's column (or row) is sized, as JavaFX's ColumnConstraints. */
type TrackSize = {
  /** CSS pixels: fixed. */
  width?: number
  /** A share of the width, 0 to 100. */
  percent?: number
  /** Takes the room left over (with others that grow, a share by weight). */
  grow?: number
  minWidth?: number
  maxWidth?: number
  /** Where the column's children sit across it. */
  halignment?: HPos
}

function track(t: TrackSize | undefined): string {
  if (!t) return "auto"
  if (t.width != null) return `${t.width}px`
  if (t.percent != null) return `${t.percent}%`
  const min = t.minWidth != null ? `${t.minWidth}px` : "min-content"
  if (t.grow != null) return `minmax(${min}, ${t.grow}fr)`
  if (t.maxWidth != null) return `minmax(${min}, ${t.maxWidth}px)`
  return t.minWidth != null ? `minmax(${min}, auto)` : "auto"
}

type GridPaneProps = {
  hgap?: number
  vgap?: number
  padding?: Insets
  /** How each column is sized, in order; columns without one fit their children. */
  columns?: TrackSize[]
  /** How each row is sized (`width` read as height). */
  rows?: TrackSize[]
  /** Where the grid sits in its area when it is smaller. */
  alignment?: Pos
  children?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

/** Children in rows and columns, each in its cell (GridCell); a form of labels and fields is the usual one. */
function GridPane({ hgap = 0, vgap = 0, padding: p, columns, rows, alignment, children, className, style }: GridPaneProps) {
  const a = alignment ? parts(alignment) : undefined
  const halign = columns?.map((c) => c.halignment)
  return (
    <GridColumns.Provider value={halign}>
      <div
        className={["nt-gridpane", className].filter(Boolean).join(" ")}
        style={{
          columnGap: hgap,
          rowGap: vgap,
          padding: padding(p),
          gridTemplateColumns: columns?.length ? columns.map(track).join(" ") : undefined,
          gridTemplateRows: rows?.length ? rows.map(track).join(" ") : undefined,
          gridAutoColumns: "auto",
          justifyContent: a?.h,
          alignContent: a?.v,
          ...style,
        }}
      >
        {children}
      </div>
    </GridColumns.Provider>
  )
}

const GridColumns = React.createContext<(HPos | undefined)[] | undefined>(undefined)

type GridCellProps = {
  /** From 0, as JavaFX's GridPane.add(node, column, row). */
  column: number
  row: number
  columnSpan?: number
  rowSpan?: number
  halignment?: HPos
  valignment?: VPos
  /** Fills the cell's width (or height) instead of fitting its child. */
  fillWidth?: boolean
  fillHeight?: boolean
  children?: React.ReactNode
  style?: React.CSSProperties
}

/** One cell of a GridPane. */
function GridCell({ column, row, columnSpan = 1, rowSpan = 1, halignment, valignment, fillWidth = true, fillHeight = false, children, style }: GridCellProps) {
  const columns = React.useContext(GridColumns)
  const h = halignment ?? columns?.[column]
  return (
    <div
      className="nt-gridcell"
      style={{
        gridColumn: `${column + 1} / span ${columnSpan}`,
        gridRow: `${row + 1} / span ${rowSpan}`,
        justifySelf: h ? H[h] : fillWidth ? "stretch" : "start",
        alignSelf: valignment ? V[valignment] : fillHeight ? "stretch" : "center",
        ...style,
      }}
    >
      {children}
    </div>
  )
}

type BorderPaneProps = {
  top?: React.ReactNode
  left?: React.ReactNode
  center?: React.ReactNode
  right?: React.ReactNode
  bottom?: React.ReactNode
  padding?: Insets
  /** Space between the center and what is around it. */
  spacing?: number
  className?: string
  style?: React.CSSProperties
}

/** A center with what goes around it: top, left, right, bottom; the center takes the room left over. */
function BorderPane({ top, left, center, right, bottom, padding: p, spacing = 0, className, style }: BorderPaneProps) {
  return (
    <div className={["nt-borderpane", className].filter(Boolean).join(" ")} style={{ padding: padding(p), gap: spacing, ...style }}>
      {top != null && <div className="nt-borderpane-top">{top}</div>}
      {left != null && <div className="nt-borderpane-left">{left}</div>}
      <div className="nt-borderpane-center">{center}</div>
      {right != null && <div className="nt-borderpane-right">{right}</div>}
      {bottom != null && <div className="nt-borderpane-bottom">{bottom}</div>}
    </div>
  )
}

type StackPaneProps = {
  /** Where the children sit (each can say its own with StackItem). */
  alignment?: Pos
  padding?: Insets
  children?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

/** Children on top of each other, the last on top: a badge over a picture, a spinner over a form. */
function StackPane({ alignment = "center", padding: p, children, className, style }: StackPaneProps) {
  const { v, h } = parts(alignment)
  return (
    <div className={["nt-stackpane", className].filter(Boolean).join(" ")} style={{ padding: padding(p), placeItems: `${v} ${h}`, ...style }}>
      {children}
    </div>
  )
}

/** A child of a StackPane that sits somewhere of its own (JavaFX's StackPane.setAlignment(node, pos)). */
function StackItem({ alignment, margin, children }: { alignment?: Pos; margin?: Insets; children?: React.ReactNode }) {
  const a = alignment ? parts(alignment) : undefined
  return <div style={{ alignSelf: a?.v, justifySelf: a?.h, margin: padding(margin) }}>{children}</div>
}

type SplitPaneProps = {
  /** Side by side (horizontal, the default) or one over the other (vertical). */
  orientation?: "horizontal" | "vertical"
  /** Where the dividers are, 0 to 1 from the start, one fewer than the children (JavaFX's dividerPositions). */
  dividerPositions?: number[]
  /** A divider was dragged: where they all are now. */
  onDividerPositionsChange?: (positions: number[]) => void
  /** A divider was let go (the end of a drag, a key released): where they all are. Once per move, not per pixel. */
  onDividerPositionsCommit?: (positions: number[]) => void
  /** The smallest a pane can be, CSS pixels. */
  minSize?: number
  children?: React.ReactNode
  className?: string
  style?: React.CSSProperties
  "aria-label"?: string
}

/**
 * Panes with dividers between them to drag (with a pointer, or the arrow keys once a divider has focus). Controlled
 * with `dividerPositions` and `onDividerPositionsChange`, or on its own.
 */
function SplitPane({ orientation = "horizontal", dividerPositions, onDividerPositionsChange, onDividerPositionsCommit, minSize = 48, children, className, style, ...props }: SplitPaneProps) {
  const panes = React.Children.toArray(children)
  const evenly = React.useMemo(() => panes.slice(1).map((_, i) => (i + 1) / panes.length), [panes.length])
  const [own, setOwn] = React.useState<number[]>(dividerPositions ?? evenly)
  const positions = dividerPositions ?? own
  const latest = React.useRef(positions)
  latest.current = positions
  const ref = React.useRef<HTMLDivElement>(null)
  const horizontal = orientation === "horizontal"

  const move = (index: number, to: number) => {
    const el = ref.current
    if (!el) return
    const size = horizontal ? el.clientWidth : el.clientHeight
    const min = size > 0 ? minSize / size : 0
    const now = latest.current
    const before = index === 0 ? 0 : now[index - 1]
    const after = index === now.length - 1 ? 1 : now[index + 1]
    const clamped = Math.min(after - min, Math.max(before + min, to))
    const next = latest.current.map((p, i) => (i === index ? clamped : p))
    latest.current = next
    if (!dividerPositions) setOwn(next)
    onDividerPositionsChange?.(next)
  }

  const drag = (index: number) => (e: React.PointerEvent) => {
    const el = ref.current
    if (!el) return
    e.preventDefault()
    const rect = el.getBoundingClientRect()
    const divider = e.currentTarget as HTMLElement
    divider.setPointerCapture(e.pointerId)
    const onMove = (ev: PointerEvent) => move(index, horizontal ? (ev.clientX - rect.left) / rect.width : (ev.clientY - rect.top) / rect.height)
    const onUp = () => {
      divider.removeEventListener("pointermove", onMove)
      divider.removeEventListener("pointerup", onUp)
      onDividerPositionsCommit?.(latest.current)
    }
    divider.addEventListener("pointermove", onMove)
    divider.addEventListener("pointerup", onUp)
  }

  const edges = [0, ...positions, 1]
  return (
    <div ref={ref} className={["nt-splitpane", className].filter(Boolean).join(" ")} data-orientation={orientation} style={style} aria-label={props["aria-label"]}>
      {panes.map((pane, i) => (
        <React.Fragment key={i}>
          <div className="nt-splitpane-pane" style={{ flex: `${edges[i + 1] - edges[i]} 1 0` }}>
            {pane}
          </div>
          {i < panes.length - 1 && (
            <div
              className="nt-splitpane-divider"
              role="separator"
              aria-orientation={horizontal ? "vertical" : "horizontal"}
              aria-valuenow={Math.round(positions[i] * 100)}
              aria-valuemin={0}
              aria-valuemax={100}
              tabIndex={0}
              onPointerDown={drag(i)}
              onKeyDown={(e) => {
                const step = e.shiftKey ? 0.1 : 0.02
                if (e.key === (horizontal ? "ArrowLeft" : "ArrowUp")) move(i, positions[i] - step)
                if (e.key === (horizontal ? "ArrowRight" : "ArrowDown")) move(i, positions[i] + step)
              }}
              onKeyUp={(e) => {
                if (e.key.startsWith("Arrow")) onDividerPositionsCommit?.(latest.current)
              }}
            />
          )}
        </React.Fragment>
      ))}
    </div>
  )
}

export { BorderPane, GridCell, GridPane, Grow, HBox, SplitPane, StackItem, StackPane, VBox }
export type { BorderPaneProps, BoxProps, GridCellProps, GridPaneProps, HPos, Insets, Pos, SplitPaneProps, StackPaneProps, TrackSize, VPos }
