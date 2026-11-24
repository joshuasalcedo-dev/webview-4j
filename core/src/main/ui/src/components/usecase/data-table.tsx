import {
  type ColumnDef,
  type RowSelectionState,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table"
import * as React from "react"

import { Button } from "../button"
import { Checkbox } from "../checkbox"
import { type MenuEntry, useContextMenu } from "../context-menu"
import { Spinner } from "../infinite-scroll"
import { Money } from "../money"
import { Pill } from "../pill"
import { SearchField } from "../search-field"
import "./data-table.css"

/**
 * A table of records, as the system's own (Windows' DataGrid, a Mac's table view): sortable columns, a search box,
 * rows chosen with check boxes (Ctrl/⌘ and Shift as in a list), a right-click menu, double-click to open, cells edited
 * in place, and pages. Built on TanStack Table.
 *
 * Sorting, searching and paging happen here by default. For a long list on a server, give `onSortChange`,
 * `onFilterChange` and `onPageChange` (with `page` and `total`): the table then only says what was asked for, and
 * shows the rows it is given (one page of them).
 */

type ColumnType = "text" | "number" | "money" | "date" | "datetime" | "boolean" | "pill" | "link"

type DataColumn = {
  /** The row's field it shows. */
  key: string
  header: string
  /** How its values are shown: text (the default), a number, money (minor units, `currency`), a date, yes/no, a pill. */
  type?: ColumnType
  /** For money: the currency (ISO), or the row's field that holds it (`"@currency"`). */
  currency?: string
  sortable?: boolean
  /** Edited in place with a double-click (text and numbers): `onCellEdit` gets the new value. */
  editable?: boolean
  /** CSS pixels. */
  width?: number
  align?: "start" | "center" | "end"
}

type DataRow = Record<string, unknown>

type DataSort = { key: string; desc: boolean } | null

type DataTableProps = {
  "aria-label": string
  columns: DataColumn[]
  rows: DataRow[]
  /** The field that tells rows apart ("id" by default). */
  rowKey?: string
  /** Check boxes to choose rows. */
  selectable?: boolean
  selected?: string[]
  onSelectionChange?: (ids: string[]) => void
  /** The sort shown; with `onSortChange`, sorting is the server's. */
  sort?: DataSort
  onSortChange?: (sort: DataSort) => void
  /** A search box above the table; with `onFilterChange`, searching is the server's. */
  search?: boolean
  filter?: string
  onFilterChange?: (text: string) => void
  /** Rows per page (here), or the page size the server uses. 0: no pages. */
  pageSize?: number
  /** The page shown (from 1) and how many rows there are in all: paging is the server's. */
  page?: number
  total?: number
  onPageChange?: (page: number) => void
  /** A row opened (double-click, Enter). */
  onRowOpen?: (id: string, row: DataRow) => void
  /** A row's right-click menu. */
  rowMenu?: (row: DataRow) => MenuEntry[]
  /** A cell edited in place. */
  onCellEdit?: (id: string, key: string, value: string) => void
  /** Buttons that act on the chosen rows, above the table. */
  actions?: { label: string; onSelect: (ids: string[]) => void; destructive?: boolean }[]
  loading?: boolean
  empty?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

function DataTable({
  columns,
  rows,
  rowKey = "id",
  selectable = false,
  selected,
  onSelectionChange,
  sort,
  onSortChange,
  search = false,
  filter,
  onFilterChange,
  pageSize = 0,
  page,
  total,
  onPageChange,
  onRowOpen,
  rowMenu,
  onCellEdit,
  actions,
  loading = false,
  empty = "Nothing here.",
  className,
  style,
  ...props
}: DataTableProps) {
  const serverPaging = onPageChange != null && total != null
  const [ownSorting, setOwnSorting] = React.useState<SortingState>([])
  const [ownFilter, setOwnFilter] = React.useState("")
  const [ownSelection, setOwnSelection] = React.useState<RowSelectionState>({})
  const [ownPage, setOwnPage] = React.useState(0)
  const [editing, setEditing] = React.useState<{ id: string; key: string } | null>(null)

  const sorting: SortingState = sort !== undefined ? (sort ? [{ id: sort.key, desc: sort.desc }] : []) : ownSorting
  const globalFilter = filter ?? ownFilter
  const rowSelection: RowSelectionState = selected ? Object.fromEntries(selected.map((id) => [id, true])) : ownSelection

  const columnDefs = React.useMemo<ColumnDef<DataRow>[]>(() => {
    const defs: ColumnDef<DataRow>[] = columns.map((c) => ({
      id: c.key,
      accessorFn: (row) => row[c.key],
      header: c.header,
      enableSorting: c.sortable !== false,
      size: c.width,
      meta: c,
      sortingFn: c.type === "number" || c.type === "money" ? "basic" : "alphanumeric",
    }))
    if (selectable) {
      defs.unshift({
        id: "__select",
        enableSorting: false,
        size: 36,
        header: ({ table }) => (
          <Checkbox
            aria-label="Choose all"
            checked={table.getIsAllPageRowsSelected()}
            ref={(el) => {
              if (el) el.indeterminate = table.getIsSomePageRowsSelected()
            }}
            onChange={table.getToggleAllPageRowsSelectedHandler()}
          />
        ),
        cell: ({ row }) => (
          <Checkbox aria-label="Choose" checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()} onClick={(e) => e.stopPropagation()} />
        ),
      })
    }
    return defs
  }, [columns, selectable])

  const table = useReactTable({
    data: rows,
    columns: columnDefs,
    getRowId: (row, index) => String(row[rowKey] ?? index),
    state: {
      sorting,
      globalFilter,
      rowSelection,
      ...(pageSize > 0 && !serverPaging ? { pagination: { pageIndex: ownPage, pageSize } } : {}),
    },
    manualSorting: onSortChange != null,
    manualFiltering: onFilterChange != null,
    manualPagination: serverPaging,
    enableRowSelection: selectable,
    onSortingChange: (updater) => {
      const next = typeof updater === "function" ? updater(sorting) : updater
      if (onSortChange) onSortChange(next[0] ? { key: next[0].id, desc: next[0].desc } : null)
      if (sort === undefined) setOwnSorting(next)
    },
    onGlobalFilterChange: (value: string) => {
      onFilterChange?.(value)
      if (filter === undefined) setOwnFilter(value)
      setOwnPage(0)
    },
    onRowSelectionChange: (updater) => {
      const next = typeof updater === "function" ? updater(rowSelection) : updater
      onSelectionChange?.(Object.keys(next).filter((id) => next[id]))
      if (!selected) setOwnSelection(next)
    },
    onPaginationChange: (updater) => {
      const next = typeof updater === "function" ? updater({ pageIndex: ownPage, pageSize }) : updater
      setOwnPage(next.pageIndex)
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: onSortChange ? undefined : getSortedRowModel(),
    getFilteredRowModel: onFilterChange ? undefined : getFilteredRowModel(),
    getPaginationRowModel: pageSize > 0 && !serverPaging ? getPaginationRowModel() : undefined,
  })

  const chosen = Object.keys(rowSelection).filter((id) => rowSelection[id])
  const pages = serverPaging ? Math.max(1, Math.ceil(total / Math.max(1, pageSize || rows.length || 1))) : pageSize > 0 ? table.getPageCount() : 1
  const current = serverPaging ? (page ?? 1) : ownPage + 1
  const goTo = (n: number) => (serverPaging ? onPageChange?.(n) : setOwnPage(n - 1))

  return (
    <div className={["nt-datatable", className].filter(Boolean).join(" ")} style={style}>
      {(search || (actions && actions.length > 0)) && (
        <div className="nt-datatable-bar">
          {search && (
            <SearchField
              aria-label={`Search ${props["aria-label"]}`}
              value={globalFilter}
              onChange={(e) => table.setGlobalFilter(e.target.value)}
              style={{ width: 240 }}
            />
          )}
          <span style={{ flex: 1 }} />
          {chosen.length > 0 && <span className="nt-datatable-count">{chosen.length} chosen</span>}
          {actions?.map((a) => (
            <Button key={a.label} variant="default" disabled={chosen.length === 0} onClick={() => a.onSelect(chosen)}>
              {a.label}
            </Button>
          ))}
        </div>
      )}
      <div className="nt-datatable-scroll">
        <table aria-label={props["aria-label"]} aria-busy={loading || undefined} aria-rowcount={serverPaging ? total : undefined}>
          <thead>
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => {
                  const meta = header.column.columnDef.meta as DataColumn | undefined
                  const sorted = header.column.getIsSorted()
                  return (
                    <th
                      key={header.id}
                      style={{ width: header.column.columnDef.size }}
                      data-align={meta?.align ?? (meta?.type === "number" || meta?.type === "money" ? "end" : "start")}
                      aria-sort={sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : undefined}
                    >
                      {header.column.getCanSort() ? (
                        <button type="button" className="nt-datatable-sort" onClick={header.column.getToggleSortingHandler()}>
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          <span aria-hidden="true" className="nt-datatable-arrow">
                            {sorted === "asc" ? "▲" : sorted === "desc" ? "▼" : ""}
                          </span>
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <DataRowView
                key={row.id}
                id={row.id}
                row={row.original}
                selected={row.getIsSelected()}
                rowMenu={rowMenu}
                onOpen={onRowOpen}
                onChoose={selectable ? () => row.toggleSelected() : undefined}
              >
                {row.getVisibleCells().map((cell) => {
                  const meta = cell.column.columnDef.meta as DataColumn | undefined
                  const isEditing = editing?.id === row.id && editing.key === cell.column.id
                  return (
                    <td
                      key={cell.id}
                      data-align={meta?.align ?? (meta?.type === "number" || meta?.type === "money" ? "end" : "start")}
                      onDoubleClick={(e) => {
                        if (meta?.editable && onCellEdit) {
                          e.stopPropagation()
                          setEditing({ id: row.id, key: cell.column.id })
                        }
                      }}
                    >
                      {isEditing ? (
                        <input
                          className="nt-input nt-datatable-edit"
                          autoFocus
                          defaultValue={String(cell.getValue() ?? "")}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              onCellEdit?.(row.id, cell.column.id, (e.target as HTMLInputElement).value)
                              setEditing(null)
                            }
                            if (e.key === "Escape") setEditing(null)
                          }}
                          onBlur={(e) => {
                            onCellEdit?.(row.id, cell.column.id, e.target.value)
                            setEditing(null)
                          }}
                        />
                      ) : meta ? (
                        <CellValue column={meta} row={row.original} value={cell.getValue()} />
                      ) : (
                        flexRender(cell.column.columnDef.cell, cell.getContext())
                      )}
                    </td>
                  )
                })}
              </DataRowView>
            ))}
          </tbody>
        </table>
        {loading && (
          <div className="nt-datatable-note">
            <Spinner label="Loading" />
          </div>
        )}
        {!loading && table.getRowModel().rows.length === 0 && <div className="nt-datatable-note">{empty}</div>}
      </div>
      {pages > 1 && (
        <div className="nt-datatable-pages" role="navigation" aria-label="Pages">
          <Button variant="subtle" disabled={current <= 1} onClick={() => goTo(current - 1)}>
            Previous
          </Button>
          <span className="nt-datatable-count">
            Page {current} of {pages}
            {serverPaging ? ` · ${total} in all` : ""}
          </span>
          <Button variant="subtle" disabled={current >= pages} onClick={() => goTo(current + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  )
}

function DataRowView({
  id,
  row,
  selected,
  rowMenu,
  onOpen,
  onChoose,
  children,
}: {
  id: string
  row: DataRow
  selected: boolean
  rowMenu?: (row: DataRow) => MenuEntry[]
  onOpen?: (id: string, row: DataRow) => void
  onChoose?: () => void
  children: React.ReactNode
}) {
  const { onContextMenu } = useContextMenu(() => rowMenu?.(row) ?? [])
  return (
    <tr
      aria-selected={selected || undefined}
      tabIndex={0}
      onClick={(e) => {
        if (onChoose && (e.ctrlKey || e.metaKey)) onChoose()
      }}
      onDoubleClick={() => onOpen?.(id, row)}
      onKeyDown={(e) => {
        if (e.key === "Enter") onOpen?.(id, row)
        if (e.key === " " && onChoose) {
          e.preventDefault()
          onChoose()
        }
      }}
      onContextMenu={rowMenu ? onContextMenu : undefined}
    >
      {children}
    </tr>
  )
}

/** A value as its column's type shows it. */
function CellValue({ column, row, value }: { column: DataColumn; row: DataRow; value: unknown }) {
  if (value == null || value === "") return <span className="nt-datatable-none">—</span>
  switch (column.type) {
    case "number":
      return <>{typeof value === "number" ? value.toLocaleString() : String(value)}</>
    case "money": {
      const currency = column.currency?.startsWith("@") ? String(row[column.currency.slice(1)] ?? "USD") : (column.currency ?? "USD")
      return <Money amount={Number(value)} currency={currency} />
    }
    case "date":
    case "datetime": {
      const date = new Date(String(value))
      if (Number.isNaN(date.getTime())) return <>{String(value)}</>
      return <>{column.type === "date" ? date.toLocaleDateString() : date.toLocaleString()}</>
    }
    case "boolean":
      return <>{value ? "Yes" : "No"}</>
    case "pill":
      return <Pill>{String(value)}</Pill>
    case "link":
      return (
        <a className="nt-link" href={String(value)}>
          {String(value)}
        </a>
      )
    default:
      return <>{typeof value === "object" ? JSON.stringify(value) : String(value)}</>
  }
}

export { DataTable }
export type { ColumnType, DataColumn, DataRow, DataSort, DataTableProps }
