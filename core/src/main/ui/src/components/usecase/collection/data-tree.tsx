import * as React from "react"

import { Checkbox } from "../../checkbox"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import { Spinner } from "../../infinite-scroll"
import { CellValue, type DataColumn, type DataRow } from "./data-table"
import "./collection.css"

/**
 * A tree of records with columns (a tree table, as in a file manager's details view or an org chart): rows open to
 * show their children (a click on the arrow, → and ←), chosen with check boxes, opened with a double-click, a
 * right-click menu. Children are in each row's `children`; a row with `hasChildren` and none yet asks for them when
 * it is opened (`onExpand`), as for a big tree on a server.
 */
type DataTreeProps = {
  "aria-label": string
  columns: DataColumn[]
  rows: DataRow[]
  rowKey?: string
  /** The field with a row's children ("children" by default). */
  childrenKey?: string
  /** The rows open at first (or controlled with `onExpandedChange`). */
  expanded?: string[]
  onExpandedChange?: (ids: string[]) => void
  /** A row opened for the first time that has children not loaded yet (`hasChildren: true`, no `children`). */
  onExpand?: (id: string, row: DataRow) => void
  selectable?: boolean
  selected?: string[]
  onSelectionChange?: (ids: string[]) => void
  onRowOpen?: (id: string, row: DataRow) => void
  rowMenu?: (row: DataRow) => MenuEntry[]
  empty?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

function DataTree({
  columns,
  rows,
  rowKey = "id",
  childrenKey = "children",
  expanded,
  onExpandedChange,
  onExpand,
  selectable = false,
  selected,
  onSelectionChange,
  onRowOpen,
  rowMenu,
  empty = "Nothing here.",
  className,
  style,
  ...props
}: DataTreeProps) {
  const [ownExpanded, setOwnExpanded] = React.useState<string[]>([])
  const [ownSelected, setOwnSelected] = React.useState<string[]>([])
  const open = expanded ?? ownExpanded
  const chosen = selected ?? ownSelected

  const children = (row: DataRow) => (Array.isArray(row[childrenKey]) ? (row[childrenKey] as DataRow[]) : undefined)
  const toggle = (id: string, row: DataRow) => {
    const isOpen = open.includes(id)
    const next = isOpen ? open.filter((o) => o !== id) : [...open, id]
    onExpandedChange?.(next)
    if (!expanded) setOwnExpanded(next)
    if (!isOpen && row.hasChildren === true && !children(row)) onExpand?.(id, row)
  }
  const choose = (id: string) => {
    const next = chosen.includes(id) ? chosen.filter((c) => c !== id) : [...chosen, id]
    onSelectionChange?.(next)
    if (!selected) setOwnSelected(next)
  }

  // The rows shown: each open row followed by its children, with their depth.
  const flat: { row: DataRow; id: string; depth: number; parent: boolean }[] = []
  const walk = (list: DataRow[], depth: number) => {
    list.forEach((row, i) => {
      const id = String(row[rowKey] ?? `${depth}-${i}`)
      const kids = children(row)
      const parent = (kids != null && kids.length > 0) || row.hasChildren === true
      flat.push({ row, id, depth, parent })
      if (parent && open.includes(id) && kids) walk(kids, depth + 1)
    })
  }
  walk(rows, 0)

  return (
    <div className={["nt-datatable", "nt-datatree", className].filter(Boolean).join(" ")} style={style}>
      <div className="nt-datatable-scroll">
        <table aria-label={props["aria-label"]} role="treegrid">
          <thead>
            <tr>
              {selectable && <th style={{ width: 36 }} />}
              {columns.map((c) => (
                <th key={c.key} style={{ width: c.width }} data-align={c.align ?? (c.type === "number" || c.type === "money" ? "end" : "start")}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {flat.map(({ row, id, depth, parent }) => (
              <TreeRow
                key={id}
                id={id}
                row={row}
                depth={depth}
                parent={parent}
                isOpen={open.includes(id)}
                loading={parent && open.includes(id) && row.hasChildren === true && !children(row)}
                columns={columns}
                selectable={selectable}
                isChosen={chosen.includes(id)}
                onToggle={() => toggle(id, row)}
                onChoose={() => choose(id)}
                onOpen={onRowOpen}
                rowMenu={rowMenu}
              />
            ))}
          </tbody>
        </table>
        {flat.length === 0 && <div className="nt-datatable-note">{empty}</div>}
      </div>
    </div>
  )
}

function TreeRow({
  id,
  row,
  depth,
  parent,
  isOpen,
  loading,
  columns,
  selectable,
  isChosen,
  onToggle,
  onChoose,
  onOpen,
  rowMenu,
}: {
  id: string
  row: DataRow
  depth: number
  parent: boolean
  isOpen: boolean
  loading: boolean
  columns: DataColumn[]
  selectable: boolean
  isChosen: boolean
  onToggle: () => void
  onChoose: () => void
  onOpen?: (id: string, row: DataRow) => void
  rowMenu?: (row: DataRow) => MenuEntry[]
}) {
  const { onContextMenu } = useContextMenu(() => rowMenu?.(row) ?? [])
  return (
    <tr
      aria-level={depth + 1}
      aria-expanded={parent ? isOpen : undefined}
      aria-selected={isChosen || undefined}
      tabIndex={0}
      onDoubleClick={() => onOpen?.(id, row)}
      onKeyDown={(e) => {
        if (e.key === "Enter") onOpen?.(id, row)
        if (e.key === "ArrowRight" && parent && !isOpen) onToggle()
        if (e.key === "ArrowLeft" && parent && isOpen) onToggle()
        if (e.key === " " && selectable) {
          e.preventDefault()
          onChoose()
        }
      }}
      onContextMenu={rowMenu ? onContextMenu : undefined}
    >
      {selectable && (
        <td>
          <Checkbox aria-label="Choose" checked={isChosen} onChange={onChoose} />
        </td>
      )}
      {columns.map((column, i) => (
        <td key={column.key} data-align={column.align ?? (column.type === "number" || column.type === "money" ? "end" : "start")}>
          {i === 0 ? (
            <span className="nt-datatree-first" style={{ paddingInlineStart: depth * 18 }}>
              {parent ? (
                <button type="button" className="nt-datatree-toggle" aria-label={isOpen ? "Close" : "Open"} onClick={onToggle}>
                  {isOpen ? "▾" : "▸"}
                </button>
              ) : (
                <span className="nt-datatree-toggle" aria-hidden="true" />
              )}
              <CellValue column={column} row={row} value={row[column.key]} />
              {loading && <Spinner label="Loading" size={12} />}
            </span>
          ) : (
            <CellValue column={column} row={row} value={row[column.key]} />
          )}
        </td>
      ))}
    </tr>
  )
}

export { DataTree }
export type { DataTreeProps }
