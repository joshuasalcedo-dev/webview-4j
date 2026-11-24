import * as React from "react"

import { Avatar } from "../../avatar"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import { Pill } from "../../pill"
import type { DataRow } from "./data-table"
import "./collection.css"

/**
 * Records as cards in a grid that fills the width (a gallery, a dashboard of things): each card a picture (or the
 * initials of its title), a title, a quieter line, a pill, a footnote; chosen with a click (Ctrl/⌘ for several),
 * opened with a double-click or Enter, a right-click menu.
 */
type DataGridProps = {
  "aria-label": string
  items: DataRow[]
  itemKey?: string
  /** The fields shown on each card. */
  title: string
  subtitle?: string
  image?: string
  pill?: string
  footer?: string
  /** The narrowest a card is, CSS pixels (220 by default): as many fit in a row as the width allows. */
  minWidth?: number
  selected?: string[]
  onSelectionChange?: (ids: string[]) => void
  multiple?: boolean
  onOpen?: (id: string, item: DataRow) => void
  itemMenu?: (item: DataRow) => MenuEntry[]
  empty?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

function DataGrid({
  items,
  itemKey = "id",
  title,
  subtitle,
  image,
  pill,
  footer,
  minWidth = 220,
  selected,
  onSelectionChange,
  multiple = false,
  onOpen,
  itemMenu,
  empty = "Nothing here.",
  className,
  style,
  ...props
}: DataGridProps) {
  const [ownSelected, setOwnSelected] = React.useState<string[]>([])
  const chosen = selected ?? ownSelected
  const choose = (id: string, add: boolean) => {
    const next = multiple && add ? (chosen.includes(id) ? chosen.filter((c) => c !== id) : [...chosen, id]) : [id]
    onSelectionChange?.(next)
    if (!selected) setOwnSelected(next)
  }
  const field = (item: DataRow, key?: string) => (key && item[key] != null ? String(item[key]) : undefined)
  return (
    <div
      role="listbox"
      aria-label={props["aria-label"]}
      aria-multiselectable={multiple || undefined}
      className={["nt-datagrid", className].filter(Boolean).join(" ")}
      style={{ gridTemplateColumns: `repeat(auto-fill, minmax(${minWidth}px, 1fr))`, ...style }}
    >
      {items.map((item, i) => {
        const id = String(item[itemKey] ?? i)
        return (
          <Card
            key={id}
            item={item}
            chosen={chosen.includes(id)}
            onClick={(e) => choose(id, e.ctrlKey || e.metaKey)}
            onOpen={() => onOpen?.(id, item)}
            itemMenu={itemMenu}
          >
            {image && field(item, image) ? (
              <img className="nt-datagrid-image" src={field(item, image)} alt="" />
            ) : (
              <div className="nt-datagrid-image" data-initials="">
                <Avatar name={field(item, title) ?? "?"} size={48} />
              </div>
            )}
            <div className="nt-datagrid-body">
              <div className="nt-datagrid-title">{field(item, title)}</div>
              {subtitle && field(item, subtitle) && <div className="nt-datagrid-subtitle">{field(item, subtitle)}</div>}
              {(pill && field(item, pill)) || (footer && field(item, footer)) ? (
                <div className="nt-datagrid-footer">
                  {pill && field(item, pill) && <Pill size="small">{field(item, pill)}</Pill>}
                  <span>{field(item, footer)}</span>
                </div>
              ) : null}
            </div>
          </Card>
        )
      })}
      {items.length === 0 && <div className="nt-datatable-note" style={{ gridColumn: "1 / -1" }}>{empty}</div>}
    </div>
  )
}

function Card({
  item,
  chosen,
  onClick,
  onOpen,
  itemMenu,
  children,
}: {
  item: DataRow
  chosen: boolean
  onClick: (e: React.MouseEvent) => void
  onOpen: () => void
  itemMenu?: (item: DataRow) => MenuEntry[]
  children: React.ReactNode
}) {
  const { onContextMenu } = useContextMenu(() => itemMenu?.(item) ?? [])
  return (
    <div
      role="option"
      aria-selected={chosen}
      tabIndex={0}
      className="nt-datagrid-card"
      onClick={onClick}
      onDoubleClick={onOpen}
      onKeyDown={(e) => {
        if (e.key === "Enter") onOpen()
      }}
      onContextMenu={itemMenu ? onContextMenu : undefined}
    >
      {children}
    </div>
  )
}

export { DataGrid }
export type { DataGridProps }
