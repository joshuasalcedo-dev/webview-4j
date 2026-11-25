import * as React from "react"

import { cn } from "../../../lib/utils"
import { ProgressBar } from "../../progress-bar"
import { loadDocx } from "./load"
import type { DocxViewerProps } from "./types"
import "./docx.css"

export type { DocxCommonProps, DocxPanel, DocxSource, DocxViewerProps, DocxZoom } from "./types"

/** The viewer and the editor, with the DOCX library: loaded the first time one is shown. */
export const loadDocxImpl = () => loadDocx().then(() => import("./docx-impl"))

const Viewer = React.lazy(() => loadDocxImpl().then((m) => ({ default: m.DocxViewerImpl })))

/** While the library loads: the component's frame, with a progress bar. */
export function DocxPlaceholder({ height, className, style, label }: { height?: number | string; className?: string; style?: React.CSSProperties; label: string }) {
  return (
    <div
      className={cn("nt-docx nt-docx-placeholder", className)}
      role="region"
      aria-label={label}
      aria-busy="true"
      style={{ height: height ?? (style?.height != null || style?.flex != null ? undefined : 640), ...style }}
    >
      <ProgressBar aria-label="Opening the document" className="nt-docx-loading-bar" />
    </div>
  )
}

/** What a failed load says, in the component's place (the library did not load, or broke). */
export class DocxBoundary extends React.Component<
  { children: React.ReactNode; height?: number | string; style?: React.CSSProperties; onError?: (message: string) => void },
  { error?: string }
> {
  state: { error?: string } = {}
  static getDerivedStateFromError(error: unknown) {
    return { error: error instanceof Error ? error.message : String(error) }
  }
  componentDidCatch(error: unknown) {
    this.props.onError?.(error instanceof Error ? error.message : String(error))
  }
  render() {
    if (this.state.error == null) return this.props.children
    const { height, style } = this.props
    return (
      <div className="nt-docx" role="alert" style={{ height: height ?? (style?.height != null || style?.flex != null ? undefined : 640), ...style }}>
        <div className="nt-docx-empty">
          <div className="nt-docx-empty-card" data-error="">
            <div className="nt-docx-empty-title">The document viewer did not load</div>
            <div className="nt-docx-empty-text">{this.state.error}</div>
          </div>
        </div>
      </div>
    )
  }
}

/**
 * A Word document (.docx), as Word shows it: its pages (their size, margins, headers and footers, pictures, tables),
 * page thumbnails, the outline and the sections in a side panel, comments and tracked changes in the margin and in a
 * list, find (the results marked in the pages), zoom (a percentage, fit width, whole page), page by page, Download
 * and Open. Dark pages in the dark theme (the text light, pictures in their colours). Read-only; DocxEditor edits.
 *
 * The DOCX library and its WebAssembly (about 2.5 MB) load the first time one is shown.
 */
export function DocxViewer(props: DocxViewerProps) {
  const label = props["aria-label"] ?? "Word document"
  return (
    <DocxBoundary height={props.height} style={props.style} onError={props.onError}>
      <React.Suspense fallback={<DocxPlaceholder height={props.height} className={props.className} style={props.style} label={label} />}>
        <Viewer {...props} />
      </React.Suspense>
    </DocxBoundary>
  )
}
