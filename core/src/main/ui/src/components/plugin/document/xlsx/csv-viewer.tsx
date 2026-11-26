import { useVirtualizer } from "@tanstack/react-virtual"
import * as React from "react"

import { cn } from "../../../../lib/utils"
import { Button } from "../../../button"
import { type MenuEntry, useContextMenu } from "../../../context-menu"
import { SearchField } from "../../../search-field"
import { Select } from "../../../select"
import { Text } from "../../../text"
import { parseCsv, writeCsv } from "./csv"
import { ChevronDown, ChevronUp, Download, ZoomIn, ZoomOut } from "./icons"
import { cellName, downloadFile, type FileBytes, nameFromUrl, useDebounced, useFileBytes, withExtension } from "./source"
import { FileLoading, FileState } from "./states"
import "./xlsx.css"

const ZOOMS = [75, 100, 125, 150, 200]
const ROW_HEIGHT = 26
const HEADER_HEIGHT = 30

type CsvViewerProps = {
  /** Where the file is: a URL, or a data: URL (how Java sends one). */
  src?: string
  /** The text itself, or its bytes (UTF-8), instead of src. */
  data?: string | FileBytes
  /** The name it is shown and downloaded as (from src, unless given). */
  fileName?: string
  /** The first row names the columns (true unless false); else they are Column 1, 2... */
  header?: boolean
  /** The character between fields; guessed from the text (comma, tab, semicolon, bar) unless given. */
  delimiter?: string
  /** The zoom, search and download bar. Shown unless false. */
  showToolbar?: boolean
  /** A cell was chosen: its row (0 is the first after the headers), its column, its text. */
  onCellSelect?: (row: number, col: number, value: string) => void
  /** Its height (e.g. 480 or "60vh"); 520 unless given or set by className / style. */
  height?: number | string
  className?: string
  style?: React.CSSProperties
  /** What it shows, for screen readers (the file's name unless given). */
  "aria-label"?: string
}

type Cell = { row: number; col: number }
type Sort = { col: number; descending: boolean } | null

/**
 * Delimited text (CSV, TSV) as a table: its columns sized to their text, a row number each, sorting by a header
 * click, search with the next and previous result, zoom, arrow keys between cells, a right-click to copy, download.
 * Long files scroll smoothly: only the rows in view are drawn.
 */
function CsvViewer({
  src,
  data,
  fileName,
  header = true,
  delimiter,
  showToolbar = true,
  onCellSelect,
  height,
  className,
  style,
  "aria-label": label,
}: CsvViewerProps) {
  const text = typeof data === "string" ? data : undefined
  const { bytes, error, loading } = useFileBytes(text != null ? undefined : src, typeof data === "string" ? undefined : data)
  const source = React.useMemo(() => text ?? (bytes ? new TextDecoder().decode(bytes) : null), [text, bytes])
  const parsed = React.useMemo(() => (source == null ? null : parseCsv(source, { delimiter, header })), [source, delimiter, header])
  const name = fileName ?? nameFromUrl(src, "data.csv")
  return (
    <div
      role="region"
      aria-label={label ?? name}
      className={cn("nt-xlsx", "nt-csv", className)}
      style={{ height: height ?? (className || style?.height ? undefined : 520), ...style }}
    >
      {error ? (
        <FileState title="The file could not be opened" message={error} />
      ) : !parsed ? (
        loading ? <FileLoading label="Opening the file" /> : <FileState title="No file" message="Give a CSV file to show." />
      ) : (
        <CsvTable key={src ?? name} parsed={parsed} name={name} showToolbar={showToolbar} onCellSelect={onCellSelect} />
      )}
    </div>
  )
}

function CsvTable({
  parsed,
  name,
  showToolbar,
  onCellSelect,
}: {
  parsed: ReturnType<typeof parseCsv>
  name: string
  showToolbar: boolean
  onCellSelect?: CsvViewerProps["onCellSelect"]
}) {
  const { headers, rows } = parsed
  const [zoom, setZoom] = React.useState(100)
  const [sort, setSort] = React.useState<Sort>(null)
  const [active, setActive] = React.useState<Cell | null>(null)
  const scroller = React.useRef<HTMLDivElement | null>(null)
  const scale = zoom / 100

  // The rows in the order shown: indexes into rows.
  const order = React.useMemo(() => {
    const indexes = rows.map((_, i) => i)
    if (!sort) return indexes
    const compare = new Intl.Collator(undefined, { numeric: true, sensitivity: "base" }).compare
    indexes.sort((a, b) => compare(rows[a][sort.col] ?? "", rows[b][sort.col] ?? ""))
    return sort.descending ? indexes.reverse() : indexes
  }, [rows, sort])

  // Each column as wide as its text (the header and the first rows), within reason.
  const widths = React.useMemo(
    () =>
      headers.map((h, col) => {
        let chars = h.length + 2
        for (let r = 0; r < Math.min(rows.length, 200); r++) chars = Math.max(chars, (rows[r][col] ?? "").length)
        return Math.round(Math.min(320, Math.max(64, chars * 7.5 + 20)) * scale)
      }),
    [headers, rows, scale]
  )
  const numberWidth = Math.round(Math.max(40, String(rows.length).length * 8 + 20) * scale)
  const columns = `${numberWidth}px ${widths.map((w) => `${w}px`).join(" ")}`
  const width = numberWidth + widths.reduce((sum, w) => sum + w, 0)
  const rowHeight = Math.round(ROW_HEIGHT * scale)
  const headerHeight = Math.round(HEADER_HEIGHT * scale)

  const virtual = useVirtualizer({
    count: order.length,
    getScrollElement: () => scroller.current,
    estimateSize: () => rowHeight,
    overscan: 12,
  })
  React.useEffect(() => virtual.measure(), [rowHeight, virtual])

  const valueAt = React.useCallback((cell: Cell) => rows[order[cell.row]]?.[cell.col] ?? "", [rows, order])

  const choose = React.useCallback(
    (cell: Cell, reveal = false) => {
      setActive(cell)
      onCellSelect?.(order[cell.row], cell.col, valueAt(cell))
      if (!reveal) return
      virtual.scrollToIndex(cell.row, { align: "auto" })
      // the column too, once the row is drawn
      window.requestAnimationFrame(() => {
        const element = scroller.current
        if (!element) return
        let left = numberWidth
        for (let i = 0; i < cell.col; i++) left += widths[i]
        const right = left + widths[cell.col]
        if (left - numberWidth < element.scrollLeft) element.scrollLeft = left - numberWidth
        else if (right > element.scrollLeft + element.clientWidth) element.scrollLeft = right - element.clientWidth
      })
    },
    [numberWidth, onCellSelect, order, valueAt, virtual, widths]
  )

  const copy = (value: string) => void navigator.clipboard?.writeText(value)
  const { onContextMenu } = useContextMenu((): MenuEntry[] =>
    active
      ? [
          { label: "Copy", shortcut: "Mod+C", onSelect: () => copy(valueAt(active)) },
          {
            label: "Copy row",
            onSelect: () => copy(writeCsv(headers, [rows[order[active.row]] ?? []], parsed.delimiter).split("\r\n")[1] ?? ""),
          },
        ]
      : [{ label: "Copy", disabled: true }]
  )

  const download = () =>
    downloadFile(writeCsv(headers, rows, parsed.delimiter), withExtension(name, ".csv", [".csv", ".tsv", ".txt"]), "text/csv;charset=utf-8")

  return (
    <>
      {showToolbar && (
        <div className="nt-xlsx-bar" role="toolbar" aria-label="Table">
          <Text as="span" className="nt-xlsx-name" tone="secondary" lines={1} selectable={false}>
            {name} · {rows.length} {rows.length === 1 ? "row" : "rows"}
          </Text>
          <span className="nt-xlsx-spacer" />
          <span className="nt-xlsx-zoom">
            <IconButton label="Zoom out" disabled={zoom <= ZOOMS[0]} onClick={() => setZoom(ZOOMS[Math.max(0, ZOOMS.indexOf(zoom) - 1)])}>
              <ZoomOut />
            </IconButton>
            <Select
              aria-label="Zoom"
              className="nt-xlsx-zoom-select"
              value={String(zoom)}
              options={ZOOMS.map((z) => ({ value: String(z), label: `${z}%` }))}
              onChange={(event) => setZoom(Number(event.target.value))}
            />
            <IconButton
              label="Zoom in"
              disabled={zoom >= ZOOMS[ZOOMS.length - 1]}
              onClick={() => setZoom(ZOOMS[Math.min(ZOOMS.length - 1, ZOOMS.indexOf(zoom) + 1)])}
            >
              <ZoomIn />
            </IconButton>
          </span>
          <span className="nt-xlsx-separator" role="separator" aria-orientation="vertical" />
          <CsvSearch headers={headers} rows={rows} order={order} disabled={!rows.length} onFound={(cell) => choose(cell, true)} />
          <span className="nt-xlsx-separator" role="separator" aria-orientation="vertical" />
          <IconButton label="Download" onClick={download}>
            <Download />
          </IconButton>
        </div>
      )}
      {!rows.length ? (
        <FileState title="No rows" message={headers.length ? "The file has its headers only." : "The file is empty."} />
      ) : (
        <div
          ref={scroller}
          className="nt-csv-scroll"
          role="grid"
          aria-rowcount={rows.length + 1}
          aria-colcount={headers.length}
          tabIndex={0}
          style={{ fontSize: `calc(var(--nt-font-size, 13px) * ${scale})` }}
          onContextMenu={onContextMenu}
          onKeyDown={(event) => {
            const moves: Record<string, [number, number]> = {
              ArrowUp: [-1, 0],
              ArrowDown: [1, 0],
              ArrowLeft: [0, -1],
              ArrowRight: [0, 1],
              PageUp: [-20, 0],
              PageDown: [20, 0],
            }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "c" && active) {
              event.preventDefault()
              copy(valueAt(active))
              return
            }
            const move = moves[event.key]
            if (!move) return
            event.preventDefault()
            const from = active ?? { row: 0, col: 0 }
            choose(
              {
                row: Math.min(order.length - 1, Math.max(0, from.row + (active ? move[0] : 0))),
                col: Math.min(headers.length - 1, Math.max(0, from.col + (active ? move[1] : 0))),
              },
              true
            )
          }}
        >
          <div className="nt-csv-sheet" style={{ width, height: virtual.getTotalSize() + headerHeight }}>
            <div className="nt-csv-row nt-csv-head" role="row" style={{ gridTemplateColumns: columns, width, height: headerHeight }}>
              <span className="nt-csv-number" role="columnheader" aria-label="Row" />
              {headers.map((h, col) => (
                <button
                  key={col}
                  type="button"
                  role="columnheader"
                  className="nt-csv-header"
                  aria-sort={sort?.col === col ? (sort.descending ? "descending" : "ascending") : "none"}
                  title={`${h}: sort`}
                  onClick={() =>
                    setSort((s) => (s?.col !== col ? { col, descending: false } : !s.descending ? { col, descending: true } : null))
                  }
                >
                  <span className="nt-csv-header-text">{h}</span>
                  {sort?.col === col && (sort.descending ? <ChevronDown /> : <ChevronUp />)}
                </button>
              ))}
            </div>
            {virtual.getVirtualItems().map((item) => {
              const values = rows[order[item.index]] ?? []
              return (
                <div
                  key={item.key}
                  className="nt-csv-row"
                  role="row"
                  aria-rowindex={item.index + 2}
                  style={{ gridTemplateColumns: columns, width, height: rowHeight, transform: `translateY(${item.start + headerHeight}px)` }}
                >
                  <span className="nt-csv-number" role="rowheader">
                    {order[item.index] + 1}
                  </span>
                  {headers.map((_, col) => {
                    const chosen = active?.row === item.index && active.col === col
                    return (
                      <span
                        key={col}
                        role="gridcell"
                        className="nt-csv-cell"
                        data-active={chosen || undefined}
                        aria-selected={chosen}
                        title={values[col]}
                        onMouseDown={() => choose({ row: item.index, col })}
                      >
                        {values[col] ?? ""}
                      </span>
                    )
                  })}
                </div>
              )
            })}
          </div>
        </div>
      )}
    </>
  )
}

function IconButton({ label, children, ...props }: React.ComponentProps<"button"> & { label: string }) {
  return (
    <Button variant="subtle" className="nt-xlsx-icon" aria-label={label} title={label} {...props}>
      {children}
    </Button>
  )
}

/** The search: every cell with the text in it, Enter for the next, Shift+Enter for the one before. */
function CsvSearch({
  headers,
  rows,
  order,
  disabled,
  onFound,
}: {
  headers: string[]
  rows: string[][]
  order: number[]
  disabled: boolean
  onFound: (cell: Cell) => void
}) {
  const [draft, setDraft] = React.useState("")
  const query = useDebounced(draft.trim().toLowerCase(), 250)
  const [index, setIndex] = React.useState(0)
  const found = React.useMemo(() => {
    if (!query) return [] as Cell[]
    const cells: Cell[] = []
    order.forEach((r, row) => {
      const values = rows[r]
      for (let col = 0; col < headers.length; col++) if ((values[col] ?? "").toLowerCase().includes(query)) cells.push({ row, col })
    })
    return cells
  }, [query, rows, order, headers.length])
  React.useEffect(() => setIndex(0), [found])
  const reveal = React.useRef(onFound)
  reveal.current = onFound
  const current = found[index]
  React.useEffect(() => {
    if (current) reveal.current(current)
  }, [current])
  const step = (by: 1 | -1) => found.length && setIndex((i) => (i + by + found.length) % found.length)
  return (
    <span className="nt-xlsx-search">
      <SearchField
        aria-label="Search the table"
        className="nt-xlsx-search-field"
        value={draft}
        disabled={disabled}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key !== "Enter") return
          event.preventDefault()
          step(event.shiftKey ? -1 : 1)
        }}
      />
      {query && (
        <>
          <Text
            variant="caption"
            tone="secondary"
            className="nt-xlsx-count"
            selectable={false}
            aria-live="polite"
            title={current ? `${headers[current.col]}, ${cellName(current.row + 1, current.col)}` : undefined}
          >
            {found.length ? `${index + 1} of ${found.length}` : "No results"}
          </Text>
          <IconButton label="Previous result" disabled={!found.length} onClick={() => step(-1)}>
            <ChevronUp />
          </IconButton>
          <IconButton label="Next result" disabled={!found.length} onClick={() => step(1)}>
            <ChevronDown />
          </IconButton>
        </>
      )}
    </span>
  )
}

export { CsvViewer }
export type { CsvViewerProps }
