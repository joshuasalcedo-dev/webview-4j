// The workbook itself, drawn by @extend-ai/react-xlsx (MIT) with its spreadsheet engine in WebAssembly. Large:
// xlsx-viewer.tsx loads this module the first time a workbook is shown, so an app that never shows one does
// not carry it.
import * as X from "@extend-ai/react-xlsx"
import wasmUrl from "@extend-ai/react-xlsx/duke_sheets_wasm_bg.wasm?url"
import * as React from "react"

import { cn } from "../../../lib/utils"
import { Button } from "../../button"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import { useNative } from "../../provider"
import { SearchField } from "../../search-field"
import { Select } from "../../select"
import { Tabs } from "../../tabs"
import { Text } from "../../text"
import { Bold, ChevronDown, ChevronUp, Download, Formula, Italic, Merge, Redo, Save, SortMenu, Undo, ZoomIn, ZoomOut } from "./icons"
import { cellName, useDebounced, withExtension } from "./source"
import { FileLoading, FileState } from "./states"

// The engine's binary, from the package itself (bundled as an asset), before the first workbook is parsed.
X.setWasmSource(wasmUrl)

const ZOOMS = [25, 50, 75, 100, 125, 150, 200, 300, 400]
const MAX_BYTES = 50 * 1024 * 1024
const SEARCH_BATCH = 500
// Where the grid's cells start (its column header row and row header column), at 100%.
const HEADER_HEIGHT = 24
const ROW_HEADER_WIDTH = 40
// Stable, so the thumbnails are not painted again on each render.
const THUMBNAILS = { resolution: { maxWidth: 320, maxHeight: 200 } } as const

type WorkbookProps = {
  bytes: ArrayBuffer
  fileName: string
  /** The editor's bars (the formula bar, undo, styles, save); else the viewer's. */
  editor: boolean
  /** Nothing can be changed (the viewer's always). */
  readOnly: boolean
  showToolbar: boolean
  onSave?: (bytes: Uint8Array, fileName: string) => void
  onChange?: () => void
}

/** A workbook: sheets as tabs, zoom, search; the editor adds the formula bar, undo and redo, styles, saving. */
export function Workbook({ bytes, fileName, editor, readOnly, showToolbar, onSave, onChange }: WorkbookProps) {
  const { theme } = useNative()
  const controller = X.useXlsxViewerController(
    React.useMemo(
      () => ({
        file: bytes,
        fileName,
        readOnly: !editor || readOnly,
        allowResizeInReadOnly: true,
        maxFileSizeBytes: MAX_BYTES,
        // In the page itself: the workbook is then at hand for search and saving, and no worker script has to be
        // found next to the bundle.
        useWorker: false,
      }),
      [bytes, fileName, editor, readOnly]
    )
  )
  return (
    <X.XlsxViewerProvider controller={controller} isDark={theme === "dark"}>
      <WorkbookSurface editor={editor} showToolbar={showToolbar} onSave={onSave} onChange={onChange} />
    </X.XlsxViewerProvider>
  )
}

type Viewport = React.RefObject<HTMLDivElement | null>

function WorkbookSurface({
  editor,
  showToolbar,
  onSave,
  onChange,
}: Omit<WorkbookProps, "bytes" | "fileName" | "readOnly">) {
  const controller = X.useXlsxViewer()
  const editable = editor && !controller.readOnly
  const { theme, platform } = useNative()
  const root = React.useRef<HTMLDivElement | null>(null)
  const viewport = React.useRef<HTMLDivElement | null>(null)
  const [accent, setAccent] = React.useState<string>()
  const [saving, setSaving] = React.useState(false)
  const edited = useEdited(controller, onChange)

  // The selection in the window's accent.
  React.useEffect(() => {
    if (root.current) setAccent(getComputedStyle(root.current).getPropertyValue("--nt-accent").trim() || undefined)
  }, [theme, platform])

  const save = React.useCallback(async () => {
    if (!onSave || saving) return
    setSaving(true)
    try {
      const saved = await exportBytes(controller)
      if (saved) onSave(saved, withExtension(controller.displayFileName, ".xlsx"))
      edited.reset()
    } finally {
      setSaving(false)
    }
  }, [controller, edited, onSave, saving])

  const renderScroller = React.useCallback(
    (props: X.XlsxScrollerRenderProps) => <Scroller {...props} viewport={viewport} />,
    []
  )

  const renderTableHeaderMenu = React.useCallback(
    (props: X.XlsxTableHeaderMenuRenderProps) => <TableHeaderMenu {...props} />,
    []
  )

  const { onContextMenu } = useContextMenu(() => gridMenu(controller, editable))

  return (
    <div
      ref={root}
      className="nt-xlsx-body"
      onKeyDown={(event) => {
        if (editable && onSave && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
          event.preventDefault()
          void save()
        }
      }}
    >
      {showToolbar && (
        <WorkbookBar
          editor={editor}
          editable={editable}
          viewport={viewport}
          edited={edited.value}
          saving={saving}
          onSave={onSave && editable ? save : undefined}
        />
      )}
      {editor && <FormulaBar />}
      <SheetTabs editable={editable} />
      <div className="nt-xlsx-grid" onContextMenu={onContextMenu}>
        <X.XlsxViewer
          className="nt-xlsx-viewer"
          height="100%"
          isDark={theme === "dark"}
          readOnly={!editable}
          allowResizeInReadOnly
          rounded={false}
          showDefaultToolbar={false}
          showImages
          selectionColor={accent}
          renderScroller={renderScroller}
          renderTableHeaderMenu={renderTableHeaderMenu}
          loadingState={<Loading />}
          errorState={(error: Error) => <Problem title="The workbook could not be shown" message={error.message} />}
          fileTooLargeState={
            <Problem title="The workbook is too large" message="It is larger than this view opens. Download it to see all of it." />
          }
        />
      </div>
    </div>
  )
}

/** Whether the workbook changed since it was loaded or last saved; onChange on each change. */
function useEdited(controller: X.XlsxViewerController, onChange?: () => void) {
  const [value, setValue] = React.useState(false)
  const base = React.useRef<number | null>(null)
  const last = React.useRef<number | null>(null)
  const change = React.useRef(onChange)
  change.current = onChange
  const loaded = !controller.isLoading && controller.workbook != null

  React.useEffect(() => {
    if (!loaded) {
      base.current = null
      return
    }
    if (base.current == null) {
      base.current = controller.revision
      last.current = controller.revision
      return
    }
    if (controller.revision === last.current) return
    last.current = controller.revision
    // Only edits make an undo step: a column resized in the viewer, or a recalculation, is not a change.
    if (controller.canUndo) {
      setValue(true)
      change.current?.()
    }
  }, [loaded, controller.revision, controller.canUndo])

  return React.useMemo(() => ({ value, reset: () => setValue(false) }), [value])
}

/**
 * The workbook's bytes as the package exports them (its images kept): exportXlsx() offers them as a download, so
 * the download is caught here instead, synchronously. Without a catch (a package change), the engine's own save.
 */
async function exportBytes(controller: X.XlsxViewerController): Promise<Uint8Array | null> {
  const workbook = controller.workbook
  if (!workbook) return null
  const caught: { blob?: Blob } = {}
  const createObjectURL = URL.createObjectURL
  const click = HTMLAnchorElement.prototype.click
  URL.createObjectURL = (object: Blob | MediaSource) => {
    if (object instanceof Blob) caught.blob = object
    return "blob:webview4j-xlsx-export"
  }
  HTMLAnchorElement.prototype.click = function () {}
  try {
    controller.exportXlsx()
  } finally {
    URL.createObjectURL = createObjectURL
    HTMLAnchorElement.prototype.click = click
  }
  if (caught.blob) return new Uint8Array(await caught.blob.arrayBuffer())
  return workbook.saveXlsxBytes()
}

/** The grid's scrolling viewport, which the search scrolls too. */
function Scroller({ children, viewportProps, viewport }: X.XlsxScrollerRenderProps & { viewport: Viewport }) {
  const { ref, className, ...rest } = viewportProps
  const both = React.useCallback(
    (element: HTMLDivElement | null) => {
      viewport.current = element
      assignRef(ref, element)
    },
    [ref, viewport]
  )
  return (
    <div {...rest} className={cn(className, "nt-xlsx-viewport")} ref={both}>
      {children}
    </div>
  )
}

function assignRef<T>(ref: React.Ref<T> | undefined, value: T | null) {
  if (typeof ref === "function") ref(value)
  else if (ref) (ref as React.RefObject<T | null>).current = value
}

/** The right-click menu on the grid. */
function gridMenu(c: X.XlsxViewerController, editable: boolean): MenuEntry[] {
  const hasCell = c.activeCell != null
  const copy: MenuEntry = { label: "Copy", shortcut: "Mod+C", disabled: !hasCell, onSelect: () => void c.copySelectionToClipboard() }
  if (!editable) return [copy]
  return [
    { label: "Undo", shortcut: "Mod+Z", disabled: !c.canUndo, onSelect: c.undo },
    { label: "Redo", shortcut: "Mod+Shift+Z", disabled: !c.canRedo, onSelect: c.redo },
    "separator",
    copy,
    { label: "Paste", shortcut: "Mod+V", disabled: !hasCell, onSelect: () => void c.pasteFromClipboard() },
    { label: "Clear", shortcut: "Delete", disabled: !hasCell, onSelect: c.clearSelectedCells },
    "separator",
    { label: "Merge cells", disabled: !c.selection, onSelect: c.mergeSelection },
    { label: "Unmerge cells", disabled: !hasCell, onSelect: c.unmergeSelection },
  ]
}

// ---- the toolbar ---------------------------------------------------------------------------------------

function IconButton({
  label,
  pressed,
  children,
  ...props
}: Omit<React.ComponentProps<"button">, "aria-pressed"> & { label: string; pressed?: boolean }) {
  return (
    <Button
      variant="subtle"
      className="nt-xlsx-icon"
      aria-label={label}
      title={label}
      aria-pressed={pressed}
      {...props}
    >
      {children}
    </Button>
  )
}

function Separator() {
  return <span className="nt-xlsx-separator" role="separator" aria-orientation="vertical" />
}

function WorkbookBar({
  editor,
  editable,
  viewport,
  edited,
  saving,
  onSave,
}: {
  editor: boolean
  editable: boolean
  viewport: Viewport
  edited: boolean
  saving: boolean
  onSave?: () => void
}) {
  const c = X.useXlsxViewer()
  const ready = !c.isLoading && !c.error && c.sheets.length > 0
  return (
    <div className="nt-xlsx-bar" role="toolbar" aria-label="Workbook">
      {editor ? (
        <>
          <IconButton label="Undo" disabled={!c.canUndo} onClick={c.undo}>
            <Undo />
          </IconButton>
          <IconButton label="Redo" disabled={!c.canRedo} onClick={c.redo}>
            <Redo />
          </IconButton>
          <Separator />
          <StyleButtons />
          <IconButton label="Merge cells" disabled={!editable || !c.selection} onClick={c.mergeSelection}>
            <Merge />
          </IconButton>
        </>
      ) : (
        <Text as="span" className="nt-xlsx-name" tone="secondary" lines={1} selectable={false}>
          {c.displayFileName}
        </Text>
      )}
      <span className="nt-xlsx-spacer" />
      <ZoomControls />
      <Separator />
      <WorkbookSearch viewport={viewport} disabled={!ready} />
      <Separator />
      {editable && edited && (
        <Text variant="caption" tone="secondary" className="nt-xlsx-status" selectable={false}>
          Edited
        </Text>
      )}
      {editable && onSave && (
        <IconButton label="Save" disabled={!ready || saving} onClick={onSave}>
          <Save />
        </IconButton>
      )}
      <IconButton
        label={editable ? "Download a copy" : "Download"}
        disabled={editable ? !c.canExport : !c.canDownload}
        onClick={editable ? c.exportXlsx : c.download}
      >
        <Download />
      </IconButton>
    </div>
  )
}

/** Bold and italic for the chosen cells, pressed when the active cell has them. */
function StyleButtons() {
  const c = X.useXlsxViewer()
  const font = React.useMemo(() => {
    const cell = c.activeCell
    if (!cell) return {}
    try {
      const style = c.getActiveWorksheet()?.getCellStyleAt(cell.row, cell.col) as { font?: { bold?: boolean; italic?: boolean } } | undefined
      return style?.font ?? {}
    } catch {
      return {}
    }
    // the revision: the style changed
  }, [c, c.activeCell, c.revision])
  const apply = (patch: X.XlsxCellFontStyleInput) => {
    if (c.selection) c.setRangeStyle(c.selection, { font: patch })
    else c.setSelectedCellStyle({ font: patch })
  }
  return (
    <>
      <IconButton label="Bold" pressed={font.bold === true} disabled={c.readOnly || !c.activeCell} onClick={() => apply({ bold: !font.bold })}>
        <Bold />
      </IconButton>
      <IconButton label="Italic" pressed={font.italic === true} disabled={c.readOnly || !c.activeCell} onClick={() => apply({ italic: !font.italic })}>
        <Italic />
      </IconButton>
    </>
  )
}

function ZoomControls() {
  const zoom = X.useXlsxViewerZoom()
  const current = Math.round(zoom.zoomScale)
  const options = (ZOOMS.includes(current) ? ZOOMS : [...ZOOMS, current].sort((a, b) => a - b)).map((z) => ({
    value: String(z),
    label: `${z}%`,
  }))
  return (
    <span className="nt-xlsx-zoom">
      <IconButton label="Zoom out" disabled={!zoom.canZoomOut} onClick={zoom.zoomOut}>
        <ZoomOut />
      </IconButton>
      <Select
        aria-label="Zoom"
        className="nt-xlsx-zoom-select"
        value={String(current)}
        options={options}
        onChange={(event) => zoom.setZoomScale(Number(event.target.value))}
      />
      <IconButton label="Zoom in" disabled={!zoom.canZoomIn} onClick={zoom.zoomIn}>
        <ZoomIn />
      </IconButton>
    </span>
  )
}

// ---- search ---------------------------------------------------------------------------------------------

type Found = { sheetIndex: number; sheetName: string; workbookSheetIndex: number; cell: X.XlsxCellAddress }

type BatchRow = { index?: unknown; cells?: unknown }
type BatchCell = { col?: unknown; value?: unknown; formula?: unknown }

const asText = (v: unknown) => (typeof v === "string" ? v : v == null ? "" : String(v))

type Worksheet = NonNullable<ReturnType<X.XlsxViewerController["getActiveWorksheet"]>>

/** What a cell shows, and its formula, for the search. */
function cellText(worksheet: Worksheet, row: number, col: number) {
  const formula = worksheet.getFormulaAt(row, col) ?? ""
  const shown = worksheet.getFormattedValueAt(row, col)
  if (shown) return { shown, formula }
  const value = worksheet.getCalculatedValueAt(row, col)
  if (value.is_empty) return { shown: "", formula }
  if (value.is_error) return { shown: value.asError() ?? "", formula }
  if (value.is_text) return { shown: value.asText() ?? "", formula }
  if (value.is_number) return { shown: asText(value.asNumber()), formula }
  if (value.is_boolean) return { shown: value.asBoolean() ? "TRUE" : "FALSE", formula }
  return { shown: value.toString(), formula }
}

/** Every cell of every sheet whose value or formula has `query` in it, in order. */
async function search(c: X.XlsxViewerController, query: string): Promise<Found[]> {
  const q = query.trim().toLowerCase()
  if (!q) return []
  const found: Found[] = []
  const matches = (shown: string, formula: string) => shown.toLowerCase().includes(q) || formula.toLowerCase().includes(q)
  for (const [sheetIndex, sheet] of c.sheets.entries()) {
    if (sheet.maxUsedRow < sheet.minUsedRow) continue
    const cols = sheet.visibleCols.filter((col) => col >= sheet.minUsedCol && col <= sheet.maxUsedCol)
    if (!cols.length) continue
    const rowSet = new Set(sheet.visibleRows)
    const colSet = new Set(cols)
    const add = (row: number, col: number) =>
      found.push({ sheetIndex, sheetName: sheet.name, workbookSheetIndex: sheet.workbookSheetIndex, cell: { row, col } })
    const worksheet = c.workbook?.getSheet(sheet.workbookSheetIndex)
    const batch = worksheet as unknown as { getRowsBatch?: (start: number, count: number, options?: object) => unknown } | undefined
    const start = Math.max(0, sheet.minUsedRow)
    for (let from = start; from <= sheet.maxUsedRow; from += SEARCH_BATCH) {
      const count = Math.min(SEARCH_BATCH, sheet.maxUsedRow - from + 1)
      const rows = c.getRowsBatchAsync
        ? await c.getRowsBatchAsync(sheet.workbookSheetIndex, from, count)
        : batch?.getRowsBatch?.(from, count, { includeFormulas: true, useFormattedValues: true })
      if (Array.isArray(rows)) {
        for (const entry of rows as BatchRow[]) {
          const row = Number(entry.index)
          if (!Number.isInteger(row) || !rowSet.has(row) || !Array.isArray(entry.cells)) continue
          for (const cell of entry.cells as BatchCell[]) {
            const col = Number(cell.col)
            if (Number.isInteger(col) && colSet.has(col) && matches(asText(cell.value), asText(cell.formula))) add(row, col)
          }
        }
      } else if (worksheet) {
        for (const row of sheet.visibleRows) {
          if (row < from || row >= from + count) continue
          for (const col of cols) {
            const { shown, formula } = cellText(worksheet, row, col)
            if (matches(shown, formula)) add(row, col)
          }
        }
      }
      // the page keeps answering during a long search
      await new Promise((resolve) => window.setTimeout(resolve, 0))
    }
  }
  return found
}

/** Scrolls the grid so the cell is in view (below the frozen headers). */
function scrollIntoView(c: X.XlsxViewerController, found: Found, element: HTMLDivElement | null) {
  const sheet = c.sheets[found.sheetIndex]
  if (!element || !sheet) return
  const rowIndex = sheet.visibleRows.indexOf(found.cell.row)
  const colIndex = sheet.visibleCols.indexOf(found.cell.col)
  if (rowIndex < 0 || colIndex < 0) return
  const zoom = Math.max(0.1, c.zoomScale / 100)
  const sum = (values: number[], end: number) => {
    let total = 0
    for (let i = 0; i < end; i++) total += (values[i] ?? 0) * zoom
    return total
  }
  const header = HEADER_HEIGHT * zoom
  const rowHeader = ROW_HEADER_WIDTH * zoom
  const top = header + sum(sheet.rowHeights, rowIndex)
  const left = rowHeader + sum(sheet.colWidths, colIndex)
  const bottom = top + (sheet.rowHeights[rowIndex] ?? sheet.defaultRowHeightPx) * zoom
  const right = left + (sheet.colWidths[colIndex] ?? sheet.defaultColWidthPx) * zoom
  let nextTop = element.scrollTop
  let nextLeft = element.scrollLeft
  if (top < element.scrollTop + header) nextTop = top - header
  else if (bottom > element.scrollTop + element.clientHeight) nextTop = bottom - element.clientHeight
  if (left < element.scrollLeft + rowHeader) nextLeft = left - rowHeader
  else if (right > element.scrollLeft + element.clientWidth) nextLeft = right - element.clientWidth
  element.scrollTo({ top: Math.max(0, nextTop), left: Math.max(0, nextLeft) })
}

function WorkbookSearch({ viewport, disabled }: { viewport: Viewport; disabled: boolean }) {
  const c = X.useXlsxViewer()
  const latest = React.useRef(c)
  latest.current = c
  const [draft, setDraft] = React.useState("")
  const query = useDebounced(draft.trim(), 300)
  const [found, setFound] = React.useState<Found[]>([])
  const [index, setIndex] = React.useState(0)
  const [searching, setSearching] = React.useState(false)
  const shown = React.useRef("")

  // a new workbook: a new search
  React.useEffect(() => {
    setDraft("")
  }, [c.workbook])

  React.useEffect(() => {
    let current = true
    shown.current = ""
    setIndex(0)
    if (!query) {
      setFound([])
      setSearching(false)
      return
    }
    setSearching(true)
    search(latest.current, query)
      .then((results) => current && setFound(results), () => current && setFound([]))
      .finally(() => current && setSearching(false))
    return () => {
      current = false
    }
  }, [query])

  const active = found[index] ?? null
  React.useEffect(() => {
    if (!active) return
    const key = `${active.workbookSheetIndex}:${active.cell.row}:${active.cell.col}`
    if (c.activeSheetIndex !== active.sheetIndex) {
      shown.current = ""
      c.setActiveSheetIndex(active.sheetIndex)
      return
    }
    if (shown.current === key) return
    shown.current = key
    c.selectCell(active.cell)
    const frame = window.requestAnimationFrame(() => scrollIntoView(c, active, viewport.current))
    return () => window.cancelAnimationFrame(frame)
  }, [active, c, c.activeSheetIndex, viewport])

  const step = (by: 1 | -1) => found.length && setIndex((i) => (i + by + found.length) % found.length)
  const count = searching
    ? "Searching…"
    : !query
      ? ""
      : found.length
        ? `${index + 1} of ${found.length}`
        : "No results"

  return (
    <span className="nt-xlsx-search">
      <SearchField
        aria-label="Search the workbook"
        placeholder="Search"
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
        <Text variant="caption" tone="secondary" className="nt-xlsx-count" selectable={false} aria-live="polite" title={active ? `${active.sheetName}!${cellName(active.cell.row, active.cell.col)}` : undefined}>
          {count}
        </Text>
      )}
      {query && (
        <>
          <IconButton label="Previous result" disabled={searching || !found.length} onClick={() => step(-1)}>
            <ChevronUp />
          </IconButton>
          <IconButton label="Next result" disabled={searching || !found.length} onClick={() => step(1)}>
            <ChevronDown />
          </IconButton>
        </>
      )}
    </span>
  )
}

// ---- the formula bar ------------------------------------------------------------------------------------

/** The chosen cell's address and its formula (or value), edited in place: Enter writes it, Escape takes it back. */
function FormulaBar() {
  const c = X.useXlsxViewer()
  const formula = c.selectedFormula
  const current = formula ? (formula.startsWith("=") ? formula : `=${formula}`) : c.selectedValue
  const [draft, setDraft] = React.useState(current)
  const [editing, setEditing] = React.useState(false)
  React.useEffect(() => {
    if (!editing) setDraft(current)
  }, [current, editing])
  const disabled = c.readOnly || (!c.activeCell && c.selectedFormulaTarget == null)
  // Escape: the blur that follows writes nothing
  const cancelled = React.useRef(false)
  const commit = () => {
    setEditing(false)
    if (cancelled.current) {
      cancelled.current = false
      return
    }
    if (draft === current) return
    if (draft.startsWith("=")) c.setSelectedFormula(draft)
    else c.setSelectedCellValue(draft)
  }
  return (
    <div className="nt-xlsx-formula">
      <input
        className="nt-input nt-xlsx-address"
        aria-label="Cell"
        readOnly
        value={c.selectedRangeAddress ?? c.activeCellAddress ?? ""}
      />
      <span className="nt-xlsx-fx" aria-hidden="true">
        <Formula />
      </span>
      <input
        className="nt-input nt-xlsx-formula-input"
        aria-label="Formula"
        spellCheck={false}
        disabled={disabled}
        value={draft}
        onFocus={() => setEditing(true)}
        onChange={(event) => {
          setEditing(true)
          setDraft(event.target.value)
        }}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            event.preventDefault()
            event.currentTarget.blur()
          } else if (event.key === "Escape") {
            event.preventDefault()
            cancelled.current = true
            setDraft(current)
            event.currentTarget.blur()
          }
        }}
      />
    </div>
  )
}

// ---- the sheets -----------------------------------------------------------------------------------------

/** The workbook's sheets as tabs, a preview of one when the pointer rests on it; the editor adds and deletes them. */
function SheetTabs({ editable }: { editable: boolean }) {
  const c = X.useXlsxViewer()
  const { thumbnails } = X.useXlsxViewerThumbnails(THUMBNAILS)
  const [preview, setPreview] = React.useState<{ tab: number; left: number; top: number } | null>(null)
  const timer = React.useRef<number | undefined>(undefined)
  React.useEffect(() => () => window.clearTimeout(timer.current), [])

  if (!c.tabs.length || (!editable && c.tabs.length < 2)) return null

  const tabs = c.tabs.map((tab, i) => ({
    value: String(i),
    label: tab.name,
    menu: editable
      ? (): MenuEntry[] => [
          { label: "New sheet", onSelect: () => c.addSheet() },
          {
            label: "Delete sheet",
            disabled: i !== c.activeTabIndex || c.sheets.length < 2 || tab.kind !== "sheet",
            onSelect: c.removeActiveSheet,
          },
        ]
      : undefined,
  }))

  const shown = preview ? c.tabs[preview.tab] : null
  const thumbnail = shown?.sheetIndex != null ? thumbnails.find((t) => t.sheetIndex === shown.sheetIndex) : undefined

  return (
    <div
      className="nt-xlsx-sheets"
      // a sheet tab is not where the window is dragged from
      data-no-drag=""
      onMouseOver={(event) => {
        const tab = (event.target as Element).closest(".nt-tab")
        if (!tab) return
        const index = Array.prototype.indexOf.call(tab.parentElement?.children ?? [], tab)
        if (index < 0 || preview?.tab === index) return
        window.clearTimeout(timer.current)
        const box = tab.getBoundingClientRect()
        const place = () => setPreview({ tab: index, left: Math.max(8, Math.min(box.left, window.innerWidth - 248)), top: box.bottom + 6 })
        if (preview) place()
        else timer.current = window.setTimeout(place, 600)
      }}
      onMouseLeave={() => {
        window.clearTimeout(timer.current)
        setPreview(null)
      }}
      onMouseDown={() => {
        window.clearTimeout(timer.current)
        setPreview(null)
      }}
    >
      <Tabs
        aria-label="Sheets"
        tabs={tabs}
        value={String(c.activeTabIndex)}
        onValueChange={(value) => c.setActiveTabIndex(Number(value))}
        onAdd={editable ? () => c.addSheet() : undefined}
      />
      {preview && shown && thumbnail && (
        <div className="nt-xlsx-preview" style={{ left: preview.left, top: preview.top }} aria-hidden="true">
          <Thumbnail thumbnail={thumbnail} />
          <Text variant="caption" tone="secondary" className="nt-xlsx-preview-name" lines={1} selectable={false}>
            {shown.name}
          </Text>
        </div>
      )}
    </div>
  )
}

function Thumbnail({ thumbnail }: { thumbnail: X.XlsxSheetThumbnail }) {
  const canvas = React.useRef<HTMLCanvasElement | null>(null)
  React.useEffect(() => {
    thumbnail.paint(canvas.current)
  }, [thumbnail])
  return (
    <canvas
      ref={canvas}
      width={thumbnail.width}
      height={thumbnail.height}
      className="nt-xlsx-thumbnail"
      style={{ aspectRatio: `${thumbnail.width} / ${thumbnail.height}` }}
    />
  )
}

// ---- small parts ----------------------------------------------------------------------------------------

/** A table's header button: sorts the table by its column, from the kit's menu. */
function TableHeaderMenu({ direction, sortAscending, sortDescending, triggerIcon, triggerProps }: X.XlsxTableHeaderMenuRenderProps) {
  const { onContextMenu } = useContextMenu(() => [
    { label: "Sort ascending", checked: direction === "ascending", onSelect: sortAscending },
    { label: "Sort descending", checked: direction === "descending", onSelect: sortDescending },
  ])
  return (
    <button
      {...triggerProps}
      type="button"
      className={cn("nt-xlsx-header-menu", triggerProps.className)}
      aria-label="Sort this column"
      onClick={(event) => {
        triggerProps.onClick?.(event)
        onContextMenu(event)
      }}
    >
      {triggerIcon && triggerIcon.length <= 2 ? triggerIcon : <SortMenu />}
    </button>
  )
}

const Loading = () => <FileLoading label="Opening the workbook" />

const Problem = FileState
