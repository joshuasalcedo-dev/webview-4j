// The workbook and CSV views for screens built in Java (ui.components.usecase.xlsx): the file arrives as a
// data: URL in "src", and a saved workbook goes back to Java as base64.
import * as React from "react"

import { CsvViewer } from "./csv-viewer"
import { toBase64 } from "./source"
import { XlsxEditor, XlsxViewer } from "./xlsx-viewer"

type JavaNode = { t: string; p: Record<string, unknown>; c?: unknown[] }
type Send = (handler: unknown, value?: unknown) => void
type JavaProps = { node: JavaNode; send: Send }

const str = (v: unknown) => (typeof v === "string" ? v : undefined)
const num = (v: unknown) => (typeof v === "number" ? v : undefined)

/** Java's sizing (grow, width, height, min / max): it fills the room left with grow, else 480 high. */
function sizing(p: Record<string, unknown>): { height: number | string; style: React.CSSProperties } {
  const grow = p.grow === true
  return {
    height: num(p.height) ?? (grow ? "auto" : 480),
    style: {
      ...(grow ? { flex: 1, minWidth: 0, minHeight: 0 } : {}),
      ...(num(p.width) != null ? { width: num(p.width) } : {}),
      ...(num(p.minWidth) != null ? { minWidth: num(p.minWidth) } : {}),
      ...(num(p.maxWidth) != null ? { maxWidth: num(p.maxWidth) } : {}),
      ...(num(p.minHeight) != null ? { minHeight: num(p.minHeight) } : {}),
      ...(num(p.maxHeight) != null ? { maxHeight: num(p.maxHeight) } : {}),
    },
  }
}

/** XlsxViewer from Java ("xlsxViewer"). */
export function JavaXlsxViewer({ node }: JavaProps) {
  const p = node.p
  return <XlsxViewer src={str(p.src)} fileName={str(p.fileName)} showToolbar={p.showToolbar !== false} {...sizing(p)} />
}

/** XlsxEditor from Java ("xlsxEditor"): onSave gets the workbook as base64, onChange each edit. */
export function JavaXlsxEditor({ node, send }: JavaProps) {
  const p = node.p
  // the newest handlers: a save finishes a moment after the click, when the tree may have changed
  const handlers = React.useRef(p)
  handlers.current = p
  return (
    <XlsxEditor
      src={str(p.src)}
      fileName={str(p.fileName)}
      readOnly={p.readOnly === true}
      showToolbar={p.showToolbar !== false}
      onSave={typeof p.onSave === "string" ? (bytes) => send(handlers.current.onSave, toBase64(bytes)) : undefined}
      onChange={typeof p.onChange === "string" ? () => send(handlers.current.onChange) : undefined}
      {...sizing(p)}
    />
  )
}

/** CsvViewer from Java ("csvViewer"): onCellSelect gets {row, col, value}. */
export function JavaCsvViewer({ node, send }: JavaProps) {
  const p = node.p
  return (
    <CsvViewer
      src={str(p.src)}
      fileName={str(p.fileName)}
      header={p.header !== false}
      delimiter={str(p.delimiter)}
      showToolbar={p.showToolbar !== false}
      onCellSelect={typeof p.onCellSelect === "string" ? (row, col, value) => send(p.onCellSelect, { row, col, value }) : undefined}
      {...sizing(p)}
    />
  )
}
