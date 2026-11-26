import {
  type ColumnDef,
  type RowSelectionState,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import * as React from "react"

import { Button } from "../../button"
import { Checkbox } from "../../checkbox"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import { Money } from "../../money"
import { Pill } from "../../pill"
import { SearchField } from "../../search-field"
import { type CollectionQuery, type ColumnFilter, EMPTY_QUERY, isActive, matches, type Paging, type SortOrder } from "./query"
import { CollectionState, type EmptyStateProps, LoadingBar, Pages, useLoadedOnce } from "./states"
import "./data-table.css"
import "./collection.css"

/**
 * A table of records, as the system's own (Windows' DataGrid, a Mac's table view): sortable columns (Shift-click for
 * more than one), a search box, a filter under each column, rows chosen with check boxes, a right-click menu,
 * double-click to open, cells edited in place, and pages. Built on TanStack Table.
 *
 * Everything the person asks for is one {@link CollectionQuery}: the page, its size, the order, the search, the
 * filters. With `onQueryChange`, the server answers it: the table sends the query whenever it changes and shows the
 * rows it is given, with `paging` saying where they are. Without, the table does it all itself.
 *
 * It says when there is nothing (`empty`), when nothing matches ("No matches", Clear filters), when it is loading
 * (grey rows the first time, a running bar after), and when loading failed (`error`, Try again).
 */

type ColumnType = "text" | "number" | "money" | "date" | "datetime" | "boolean" | "pill" | "link"

type FilterKind = "text" | "options" | "range" | "boolean"

type DataColumn = {
  /** The row's field it shows. */
  key: string
  header: string
  /** How its values are shown: text (the default), a number, money (minor units, `currency`), a date, yes/no, a pill. */
  type?: ColumnType
  /** For money: the currency (ISO), or the row's field that holds it (`"@currency"`). */
  currency?: string
  sortable?: boolean
  /** A filter under the column: text contains, one of some options, a range, yes/no. */
  filter?: FilterKind
  /** The options of an options filter; the values in the rows when left out. */
  options?: string[]
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
  /** A search box above the table. */
  search?: boolean
  /** Rows per page; 0: no pages. */
  pageSize?: number
  /** The query shown (controlled); the table keeps its own when left out. */
  query?: Partial<CollectionQuery>
  /** The server answers the query: it is sent whenever it changes, and `rows` are its answer (one page of them). */
  onQueryChange?: (query: CollectionQuery) => void
  /** Where the server's rows are: the page, its size, how many there are in all. */
  paging?: Paging
  /** @deprecated use onQueryChange: the sort shown, sorting by the server. */
  sort?: DataSort
  /** @deprecated use onQueryChange */
  onSortChange?: (sort: DataSort) => void
  /** @deprecated use onQueryChange: the search text, searching by the server. */
  filter?: string
  /** @deprecated use onQueryChange */
  onFilterChange?: (text: string) => void
  /** @deprecated use paging */
  page?: number
  /** @deprecated use paging */
  total?: number
  /** @deprecated use onQueryChange */
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
  /** Loading failed: what went wrong (`onRetry` gives Try again). */
  error?: { title?: string; message?: string } | null
  onRetry?: () => void
  /** What it says when there are no rows at all: a text, or a title, a message and an action. */
  empty?: React.ReactNode | EmptyStateProps
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
  search = false,
  pageSize = 0,
  query,
  onQueryChange,
  paging,
  sort,
  onSortChange,
  filter,
  onFilterChange,
  page,
  total,
  onPageChange,
  onRowOpen,
  rowMenu,
  onCellEdit,
  actions,
  loading = false,
  error,
  onRetry,
  empty,
  className,
  style,
  ...props
}: DataTableProps) {
  // The server answers when any of its handlers is given.
  const onServer = onQueryChange != null || onSortChange != null || onPageChange != null || onFilterChange != null
  const [own, setOwn] = React.useState<CollectionQuery>({ ...EMPTY_QUERY, size: pageSize })
  const [ownSelection, setOwnSelection] = React.useState<RowSelectionState>({})
  const [editing, setEditing] = React.useState<{ id: string; key: string } | null>(null)
  const [filtersShown, setFiltersShown] = React.useState(false)
  const loaded = useLoadedOnce(loading, rows.length)
  const loadedOnce = { current: loaded }

  // The query shown: controlled parts win (the new query prop, then the old separate ones), then the table's own.
  const current: CollectionQuery = {
    ...own,
    ...query,
    ...(sort !== undefined ? { sort: sort ? [{ key: sort.key, desc: sort.desc }] : [] } : {}),
    ...(filter !== undefined ? { search: filter } : {}),
    ...(page !== undefined ? { page } : {}),
    size: query?.size ?? paging?.size ?? (pageSize || own.size),
    filters: query?.filters ?? own.filters,
  }
  const change = (next: CollectionQuery) => {
    setOwn(next)
    onQueryChange?.(next)
    if (onSortChange && JSON.stringify(next.sort) !== JSON.stringify(current.sort)) onSortChange(next.sort[0] ? { key: next.sort[0].key, desc: next.sort[0].desc } : null)
    if (onFilterChange && next.search !== current.search) onFilterChange(next.search)
    if (onPageChange && next.page !== current.page) onPageChange(next.page)
  }

  // In the page: filtered, searched, sorted and cut into pages here. On the server: as given.
  const processed = React.useMemo(() => {
    if (onServer) return rows
    let out = rows
    for (const [key, f] of Object.entries(current.filters)) if (isActive(f)) out = out.filter((r) => matches(r[key], f))
    const needle = current.search.trim().toLowerCase()
    if (needle) out = out.filter((r) => columns.some((c) => String(r[c.key] ?? "").toLowerCase().includes(needle)))
    if (current.sort.length) {
      out = [...out].sort((a, b) => {
        for (const s of current.sort) {
          const x = a[s.key]
          const y = b[s.key]
          const d = typeof x === "number" && typeof y === "number" ? x - y : String(x ?? "").localeCompare(String(y ?? ""), undefined, { numeric: true })
          if (d !== 0) return s.desc ? -d : d
        }
        return 0
      })
    }
    return out
  }, [rows, onServer, columns, current.filters, current.search, current.sort])

  const size = current.size
  const all = onServer ? (paging?.total ?? total ?? rows.length) : processed.length
  const pages = size > 0 ? Math.max(1, Math.ceil(all / size)) : 1
  const at = Math.min(Math.max(1, onServer ? (paging?.page ?? current.page) : current.page), pages)
  const visible = !onServer && size > 0 ? processed.slice((at - 1) * size, at * size) : processed

  const rowSelection: RowSelectionState = selected ? Object.fromEntries(selected.map((id) => [id, true])) : ownSelection
  const columnDefs = React.useMemo<ColumnDef<DataRow>[]>(() => {
    const defs: ColumnDef<DataRow>[] = columns.map((c) => ({ id: c.key, accessorFn: (row) => row[c.key], header: c.header, size: c.width, meta: c }))
    if (selectable) {
      defs.unshift({
        id: "__select",
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
    data: visible,
    columns: columnDefs,
    getRowId: (row, index) => String(row[rowKey] ?? index),
    state: { rowSelection },
    manualSorting: true,
    manualFiltering: true,
    manualPagination: true,
    enableRowSelection: selectable,
    onRowSelectionChange: (updater) => {
      const next = typeof updater === "function" ? updater(rowSelection) : updater
      onSelectionChange?.(Object.keys(next).filter((id) => next[id]))
      if (!selected) setOwnSelection(next)
    },
    getCoreRowModel: getCoreRowModel(),
  })

  const sortBy = (key: string, adding: boolean) => {
    const existing = current.sort.find((s) => s.key === key)
    // Ascending, then descending, then not sorted by it.
    const next: SortOrder | null = !existing ? { key, desc: false } : !existing.desc ? { key, desc: true } : null
    const others = adding ? current.sort.filter((s) => s.key !== key) : []
    change({ ...current, sort: next ? [...others, next] : others, page: 1 })
  }
  const setFilter = (key: string, f: ColumnFilter | null) => {
    const filters = { ...current.filters }
    if (f && isActive(f)) filters[key] = f
    else delete filters[key]
    change({ ...current, filters, page: 1 })
  }
  const filtering = current.search.trim() !== "" || Object.values(current.filters).some(isActive)
  const clear = () => change({ ...current, search: "", filters: {}, page: 1 })
  const chosen = Object.keys(rowSelection).filter((id) => rowSelection[id])
  const filterable = columns.some((c) => c.filter)
  const align = (c?: DataColumn) => c?.align ?? (c?.type === "number" || c?.type === "money" ? "end" : "start")

  return (
    <div className={["nt-datatable", className].filter(Boolean).join(" ")} style={style}>
      {(search || filterable || (actions && actions.length > 0)) && (
        <div className="nt-datatable-bar">
          {search && (
            <SearchField
              aria-label={`Search ${props["aria-label"]}`}
              value={current.search}
              onChange={(e) => change({ ...current, search: e.target.value, page: 1 })}
              style={{ width: 240 }}
            />
          )}
          {filterable && (
            <Button variant="subtle" aria-pressed={filtersShown} onClick={() => setFiltersShown(!filtersShown)}>
              <span className={Object.values(current.filters).some(isActive) ? "nt-datatable-filter-on" : undefined}>Filters</span>
            </Button>
          )}
          {filtering && (
            <Button variant="subtle" onClick={clear}>
              Clear filters
            </Button>
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
      <div className="nt-datatable-scroll" data-reloading={loading && loadedOnce.current ? "" : undefined}>
        {loading && loadedOnce.current && <LoadingBar />}
        <table aria-label={props["aria-label"]} aria-busy={loading || undefined} aria-rowcount={onServer ? all : undefined}>
          <thead>
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => {
                  const meta = header.column.columnDef.meta as DataColumn | undefined
                  const order = meta ? current.sort.findIndex((s) => s.key === meta.key) : -1
                  const s = order >= 0 ? current.sort[order] : undefined
                  return (
                    <th
                      key={header.id}
                      style={{ width: header.column.columnDef.size }}
                      data-align={align(meta)}
                      aria-sort={s ? (s.desc ? "descending" : "ascending") : undefined}
                    >
                      {meta && meta.sortable !== false ? (
                        <button type="button" className="nt-datatable-sort" title="Shift-click to sort by more than one" onClick={(e) => sortBy(meta.key, e.shiftKey)}>
                          {meta.header}
                          <span aria-hidden="true" className="nt-datatable-arrow">
                            {s ? (s.desc ? "▼" : "▲") : ""}
                            {s && current.sort.length > 1 ? order + 1 : ""}
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
            {filtersShown && filterable && (
              <tr className="nt-datatable-filters">
                {selectable && <th />}
                {columns.map((c) => (
                  <th key={c.key}>
                    {c.filter && <FilterInput column={c} rows={rows} value={current.filters[c.key]} onChange={(f) => setFilter(c.key, f)} />}
                  </th>
                ))}
              </tr>
            )}
          </thead>
          {!(loading && !loadedOnce.current) && !error && (
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
                        data-align={align(meta)}
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
          )}
        </table>
        <CollectionState
          loading={loading}
          loadedOnce={loaded}
          error={error}
          onRetry={onRetry}
          count={visible.length}
          filtering={filtering}
          onClear={clear}
          empty={empty}
          columns={Math.min(columns.length, 6)}
        />
      </div>
      <Pages page={at} pages={pages} total={all} onPage={(n) => change({ ...current, page: n })} />
    </div>
  )
}

/** The filter under a column, by its kind. */
function FilterInput({ column, rows, value, onChange }: { column: DataColumn; rows: DataRow[]; value?: ColumnFilter; onChange: (f: ColumnFilter | null) => void }) {
  const label = `Filter ${column.header}`
  switch (column.filter) {
    case "options": {
      const options = column.options ?? [...new Set(rows.map((r) => String(r[column.key] ?? "")).filter(Boolean))].sort()
      const current = value?.kind === "options" ? value.values : []
      return (
        <select
          aria-label={label}
          className="nt-input"
          multiple={false}
          value={current[0] ?? ""}
          onChange={(e) => onChange(e.target.value ? { kind: "options", values: [e.target.value] } : null)}
        >
          <option value="">All</option>
          {options.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      )
    }
    case "boolean": {
      const current = value?.kind === "boolean" ? String(value.value) : ""
      return (
        <select aria-label={label} className="nt-input" value={current} onChange={(e) => onChange(e.target.value === "" ? null : { kind: "boolean", value: e.target.value === "true" })}>
          <option value="">All</option>
          <option value="true">Yes</option>
          <option value="false">No</option>
        </select>
      )
    }
    case "range": {
      const r = value?.kind === "range" ? value : { kind: "range" as const }
      const date = column.type === "date" || column.type === "datetime"
      const read = (t: string) => (t === "" ? undefined : date ? t : Number(t))
      return (
        <span className="nt-datatable-range">
          <input aria-label={`${label}: from`} className="nt-input" type={date ? "date" : "number"} placeholder="From" value={r.from ?? ""}
            onChange={(e) => onChange({ kind: "range", from: read(e.target.value), to: r.to })} />
          <input aria-label={`${label}: to`} className="nt-input" type={date ? "date" : "number"} placeholder="To" value={r.to ?? ""}
            onChange={(e) => onChange({ kind: "range", from: r.from, to: read(e.target.value) })} />
        </span>
      )
    }
    default: {
      const current = value?.kind === "text" ? value.text : ""
      return <input aria-label={label} className="nt-input" placeholder="Contains" value={current} onChange={(e) => onChange({ kind: "text", text: e.target.value })} />
    }
  }
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

export { CellValue, DataTable }
export type { ColumnType, DataColumn, DataRow, DataSort, DataTableProps, FilterKind }
