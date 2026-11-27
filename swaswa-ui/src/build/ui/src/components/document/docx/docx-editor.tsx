import * as React from "react"

import { DocxBoundary, DocxPlaceholder, loadDocxImpl } from "./docx-viewer"
import type { DocxEditorProps } from "./types"
import "./docx.css"

export type { DocxEditorProps } from "./types"

const Editor = React.lazy(() => loadDocxImpl().then((m) => ({ default: m.DocxEditorImpl })))

/**
 * A Word document (.docx) to edit, as in Word: typing on the pages, undo and redo, paragraph styles, fonts and sizes,
 * bold, italic, underline, strikethrough, super- and subscript, text colour and highlight, alignment, bulleted and
 * numbered lists (and their levels), line spacing, borders, links, tables (rows and columns from the right-click
 * menu), pictures; comments in the margin, tracked changes shown (read-only while shown), a read-only lock. New,
 * Open, Save (onSave gets the .docx bytes; Ctrl+S, ⌘S on a Mac), Download. Everything DocxViewer has, too: page
 * thumbnails, the outline, sections, find, zoom.
 *
 * The DOCX library and its WebAssembly (about 2.5 MB) load the first time one is shown.
 */
export function DocxEditor(props: DocxEditorProps) {
  const label = props["aria-label"] ?? "Word document"
  return (
    <DocxBoundary height={props.height} style={props.style} onError={props.onError}>
      <React.Suspense fallback={<DocxPlaceholder height={props.height} className={props.className} style={props.style} label={label} />}>
        <Editor {...props} />
      </React.Suspense>
    </DocxBoundary>
  )
}
