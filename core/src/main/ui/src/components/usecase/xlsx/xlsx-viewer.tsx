import * as React from "react"

import { cn } from "../../../lib/utils"
import { type FileBytes, nameFromUrl, useFileBytes } from "./source"
import { FileLoading, FileState } from "./states"
import "./xlsx.css"

/*
 * Excel workbooks (.xlsx; .xls and .xlsm in part): XlsxViewer shows one, XlsxEditor edits one. The spreadsheet
 * engine is large (WebAssembly): it loads the first time a workbook is shown, so an app that never shows one does
 * not carry it.
 */

const loadWorkbook = () => import("./workbook")
const LazyWorkbook = React.lazy(async () => ({ default: (await loadWorkbook()).Workbook }))

type WorkbookSource = {
  /** Where the workbook is: a URL, or a data: URL (how Java sends one). */
  src?: string
  /** The workbook's bytes, instead of src. */
  data?: FileBytes
  /** The name it is shown and downloaded as (from src, unless given). */
  fileName?: string
  /** The zoom, search, download (and the editor's undo, redo, styles, save) bar. Shown unless false. */
  showToolbar?: boolean
  /** Its height (e.g. 480 or "60vh"); 520 unless given or set by className / style. */
  height?: number | string
  className?: string
  style?: React.CSSProperties
  /** What it shows, for screen readers (the file's name unless given). */
  "aria-label"?: string
}

type XlsxViewerProps = WorkbookSource

type XlsxEditorProps = WorkbookSource & {
  /**
   * Save (the toolbar's button, Ctrl+S / ⌘S): the workbook's bytes as .xlsx, with its name. Without it, the
   * toolbar offers a download of the edited copy only.
   */
  onSave?: (bytes: Uint8Array, fileName: string) => void
  /** The workbook was edited (a cell, a style, a sheet): each change. */
  onChange?: () => void
  /** Shows it without letting it be edited (the formula bar stays, read-only). */
  readOnly?: boolean
}

/** A workbook, read-only: its sheets as tabs (a preview on hover), zoom, search of every sheet, download. */
function XlsxViewer(props: XlsxViewerProps) {
  return <WorkbookFrame {...props} editor={false} readOnly />
}

/**
 * A workbook to edit: cells and formulas (the formula bar), bold and italic, merging, sheets added and deleted,
 * undo and redo, copy and paste, a right-click menu; onSave gets the bytes.
 */
function XlsxEditor({ readOnly = false, ...props }: XlsxEditorProps) {
  return <WorkbookFrame {...props} editor readOnly={readOnly} />
}

function WorkbookFrame({
  src,
  data,
  fileName,
  showToolbar = true,
  height,
  className,
  style,
  "aria-label": label,
  editor,
  readOnly,
  onSave,
  onChange,
}: WorkbookSource & { editor: boolean; readOnly: boolean; onSave?: XlsxEditorProps["onSave"]; onChange?: () => void }) {
  const { bytes, error, loading } = useFileBytes(src, data)
  const name = fileName ?? nameFromUrl(src, "workbook.xlsx")
  return (
    <div
      role="region"
      aria-label={label ?? name}
      className={cn("nt-xlsx", className)}
      style={{ height: height ?? (className || style?.height ? undefined : 520), ...style }}
    >
      {error ? (
        <FileState title="The workbook could not be opened" message={error} />
      ) : !bytes ? (
        loading ? <FileLoading label="Opening the workbook" /> : <FileState title="No workbook" message="Give a file to show." />
      ) : (
        <React.Suspense fallback={<FileLoading label="Opening the workbook" />}>
          <LazyWorkbook
            // a new file starts afresh (selection, undo, zoom)
            key={src ?? name}
            bytes={bytes}
            fileName={name}
            editor={editor}
            readOnly={readOnly}
            showToolbar={showToolbar}
            onSave={onSave}
            onChange={onChange}
          />
        </React.Suspense>
      )}
    </div>
  )
}

/** Starts loading the spreadsheet engine before a workbook is shown (e.g. when a file is being chosen). */
function preloadXlsx(): Promise<unknown> {
  return loadWorkbook()
}

export { preloadXlsx, XlsxEditor, XlsxViewer }
export type { XlsxEditorProps, XlsxViewerProps }
