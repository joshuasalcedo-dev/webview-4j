import * as React from "react"

import type { Insets, Pos } from "./layout"
import "./layout.css"

/**
 * More layout panes, as JavaFX and Swing name them: FlowPane, TilePane, AnchorPane, ScrollPane, and CardPane
 * (Swing's CardLayout: one child shown at a time, by its key).
 */

function padding(insets?: Insets): React.CSSProperties["padding"] {
  if (insets == null) return undefined
  if (typeof insets === "number") return insets
  return `${insets.top ?? 0}px ${insets.right ?? 0}px ${insets.bottom ?? 0}px ${insets.left ?? 0}px`
}

const JUSTIFY: Record<string, string> = { left: "flex-start", center: "center", right: "flex-end" }
const ALIGN: Record<string, string> = { top: "flex-start", center: "center", bottom: "flex-end", baseline: "baseline" }

function flexOf(pos: Pos | undefined) {
  if (!pos) return {}
  const [v, h] = pos === "center" ? ["center", "center"] : pos.split("-")
  return { v: ALIGN[v], h: JUSTIFY[h] }
}

type FlowPaneProps = {
  /** Lines left to right (horizontal, the default), or columns top to bottom (vertical). */
  orientation?: "horizontal" | "vertical"
  hgap?: number
  vgap?: number
  padding?: Insets
  alignment?: Pos
  children?: React.ReactNode
  style?: React.CSSProperties
}

/** Children one after the other, onto the next line when there is no room (JavaFX's FlowPane, Swing's FlowLayout). */
function FlowPane({ orientation = "horizontal", hgap = 0, vgap = 0, padding: p, alignment, children, style }: FlowPaneProps) {
  const { v, h } = flexOf(alignment)
  const horizontal = orientation === "horizontal"
  return (
    <div
      className="nt-flowpane"
      style={{
        display: "flex",
        flexDirection: horizontal ? "row" : "column",
        flexWrap: "wrap",
        columnGap: hgap,
        rowGap: vgap,
        padding: padding(p),
        justifyContent: horizontal ? h : v,
        alignItems: horizontal ? v : h,
        alignContent: horizontal ? v : h,
        minWidth: 0,
        ...style,
      }}
    >
      {children}
    </div>
  )
}

type TilePaneProps = {
  /** The narrowest a tile is, CSS pixels: as many in a row as fit (JavaFX's prefTileWidth). */
  tileWidth?: number
  /** Or a fixed number of columns (JavaFX's prefColumns). */
  columns?: number
  /** Every tile this tall (otherwise as tall as the tallest in its row). */
  tileHeight?: number
  hgap?: number
  vgap?: number
  padding?: Insets
  /** Where each child sits in its tile. */
  tileAlignment?: Pos
  children?: React.ReactNode
  style?: React.CSSProperties
}

/** Children in tiles of the same size (JavaFX's TilePane): a gallery, a dashboard of cards. */
function TilePane({ tileWidth = 160, columns, tileHeight, hgap = 0, vgap = 0, padding: p, tileAlignment, children, style }: TilePaneProps) {
  const { v, h } = flexOf(tileAlignment)
  return (
    <div
      className="nt-tilepane"
      style={{
        display: "grid",
        gridTemplateColumns: columns ? `repeat(${columns}, minmax(0, 1fr))` : `repeat(auto-fill, minmax(${tileWidth}px, 1fr))`,
        gridAutoRows: tileHeight ? `${tileHeight}px` : undefined,
        columnGap: hgap,
        rowGap: vgap,
        padding: padding(p),
        ...style,
      }}
    >
      {React.Children.map(children, (child) => (
        <div style={{ display: "flex", justifyContent: h ?? "stretch", alignItems: v ?? "stretch", minWidth: 0 }}>{child}</div>
      ))}
    </div>
  )
}

/** How far from each edge a child of an AnchorPane is pinned (JavaFX's AnchorPane.setTopAnchor and the others). */
type Anchors = { top?: number; right?: number; bottom?: number; left?: number }

/** Children pinned to its edges by offsets (JavaFX's AnchorPane); pinned to both left and right, a child stretches. */
function AnchorPane({ padding: p, children, style }: { padding?: Insets; children?: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <div className="nt-anchorpane" style={{ position: "relative", minHeight: 0, padding: padding(p), ...style }}>
      {children}
    </div>
  )
}

/** One child of an AnchorPane, where it is pinned. */
function Anchored({ anchors, children }: { anchors: Anchors; children?: React.ReactNode }) {
  const none = anchors.top == null && anchors.right == null && anchors.bottom == null && anchors.left == null
  return <div style={{ position: none ? "relative" : "absolute", ...anchors }}>{children}</div>
}

type ScrollPaneProps = {
  /** The child as wide as the pane, scrolling only up and down (JavaFX's fitToWidth). */
  fitToWidth?: boolean
  /** The child as tall as the pane, scrolling only sideways. */
  fitToHeight?: boolean
  /** When the scroll bars show: as needed (the default), always, never. */
  hbarPolicy?: "as-needed" | "always" | "never"
  vbarPolicy?: "as-needed" | "always" | "never"
  padding?: Insets
  children?: React.ReactNode
  style?: React.CSSProperties
}

const BAR = { "as-needed": "auto", always: "scroll", never: "hidden" } as const

/** A child bigger than its room, scrolled (JavaFX's ScrollPane). */
function ScrollPane({ fitToWidth = true, fitToHeight = false, hbarPolicy = "as-needed", vbarPolicy = "as-needed", padding: p, children, style }: ScrollPaneProps) {
  return (
    <div
      className="nt-scrollpane"
      tabIndex={0}
      style={{ overflowX: fitToWidth ? "hidden" : BAR[hbarPolicy], overflowY: fitToHeight ? "hidden" : BAR[vbarPolicy], minHeight: 0, minWidth: 0, ...style }}
    >
      <div style={{ padding: padding(p), width: fitToWidth ? "auto" : "max-content", minWidth: "100%", height: fitToHeight ? "100%" : undefined }}>{children}</div>
    </div>
  )
}

type CardPaneProps = {
  /** The key of the child shown: a Card's `name` (or a child's `data-card`, or its index as text). */
  shown?: string
  /** Moving between cards: a quick fade or slide, or none. */
  transition?: "none" | "fade" | "slide"
  children?: React.ReactNode
  style?: React.CSSProperties
}

/**
 * One child shown at a time, chosen by its key (Swing's CardLayout): the steps of a wizard, the pages behind a
 * sidebar. The others stay mounted but hidden, so what was typed in them stays.
 */
function CardPane({ shown, transition = "fade", children, style }: CardPaneProps) {
  const cards = React.Children.toArray(children)
  // A card's key: a <Card name>, or any child's data-card; its index otherwise.
  const keyOf = (child: React.ReactNode, i: number) => {
    if (!React.isValidElement(child)) return String(i)
    const props = child.props as { name?: string; "data-card"?: string }
    return String(props.name ?? props["data-card"] ?? i)
  }
  const current = shown ?? keyOf(cards[0], 0)
  return (
    <div className="nt-cardpane" data-transition={transition} style={{ display: "grid", minHeight: 0, ...style }}>
      {cards.map((child, i) => {
        const on = keyOf(child, i) === current
        return (
          <div key={keyOf(child, i)} className="nt-card" data-shown={on ? "" : undefined} aria-hidden={!on} inert={!on || undefined} style={{ gridArea: "1 / 1", minWidth: 0, minHeight: 0 }}>
            {child}
          </div>
        )
      })}
    </div>
  )
}

/** A child of a CardPane with its key. */
function Card({ name, children }: { name: string; children?: React.ReactNode }) {
  return <div data-card={name}>{children}</div>
}

export { Anchored, AnchorPane, Card, CardPane, FlowPane, ScrollPane, TilePane }
export type { Anchors, CardPaneProps, FlowPaneProps, ScrollPaneProps, TilePaneProps }
