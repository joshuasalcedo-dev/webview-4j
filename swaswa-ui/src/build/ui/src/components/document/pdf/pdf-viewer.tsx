import * as React from "react"

import { cn } from "../../../lib/utils"
import { ProgressBar } from "../../progress-bar"
import "./pdf.css"

/*
 * A PDF, shown by EmbedPDF (PDFium as WebAssembly, in a Web Worker). This module is small: EmbedPDF and the
 * engine (pdf-viewer-embed.tsx, engine.ts) load the first time a PdfViewer is shown, so an app that never shows a
 * PDF never loads them.
 */

/** How the pages are sized: a scale (1 = 100%), or fitted to the viewer. */
type PdfZoom = number | "fit-page" | "fit-width" | "automatic"

/** What renderPageOverlay gets for each page: its number, its size in PDF points (unrotated), the zoom and the turn. */
type PdfPageOverlayProps = {
  pageNumber: number
  /** In PDF points (1/72 inch), as the page is in the file, before rotation. */
  pageWidth: number
  pageHeight: number
  /** CSS pixels per PDF point. */
  scale: number
  /** 0, 90, 180 or 270: the overlay turns with the page, so it is laid out as the page is in the file. */
  rotation: number
}

/** An area of a page, in percent of its width and height (0 to 100), from its top left corner, unrotated. */
type PdfPageArea = { left: number; top: number; width?: number; height?: number }

/** What a ref to a PdfViewer can do. */
type PdfViewerHandle = {
  scrollToPage: (pageNumber: number, options?: { behavior?: "auto" | "smooth" }) => void
  /** Scrolls the area into view, a quarter from the top. */
  scrollToPageArea: (pageNumber: number, area: PdfPageArea, options?: { behavior?: "auto" | "smooth" }) => void
  /** The element that scrolls the pages. */
  getViewportElement: () => HTMLDivElement | null
}

type PageEvent = (event: React.PointerEvent<HTMLDivElement>, pageNumber: number) => void

type PdfViewerProps = {
  /** The PDF: a URL (http(s), relative, blob:), a data: URL, or its bytes as base64. Left out: the upload prompt. */
  src?: string
  /** Its name, for Download (the src's last part if left out). */
  fileName?: string
  /** "automatic" unless set: fitted to the width, at most 100%. */
  defaultZoom?: PdfZoom
  showToolbar?: boolean
  showDownload?: boolean
  showRotateControls?: boolean
  /** An Open button: the person picks a PDF, it is shown, and onPdfUpload gets it. */
  showUpload?: boolean
  /** The page thumbnails at the side, open at first (the toolbar's button shows and hides them). */
  defaultShowThumbnails?: boolean
  /** Searched for as soon as the PDF is open: the matches are marked and the first is scrolled to. */
  initialSearchQuery?: string
  /** More buttons, at the toolbar's end. */
  toolbarActions?: React.ReactNode
  /** A class for a page (e.g. to mark the ones a split takes). */
  pageClassName?: (pageNumber: number) => string | undefined
  /** Drawn over each page, laid out in the page's own box (percent of its width and height are its own). */
  renderPageOverlay?: (props: PdfPageOverlayProps) => React.ReactNode
  /** The page mostly in view changed (1 for the first). */
  onActivePageChange?: (pageNumber: number) => void
  /** The PDF is open: how many pages it has. */
  onDocumentLoadSuccess?: (numPages: number) => void
  /** The PDF could not be read or opened. */
  onDocumentLoadError?: (message: string) => void
  /** A PDF the person opened with the toolbar's Open button. */
  onPdfUpload?: (file: File) => void
  /** Download saves the PDF (rotated as shown) through this instead of the browser's download, e.g. to Java. */
  onDownload?: (pdf: Blob, fileName: string) => void
  onPagePointerDown?: PageEvent
  onPagePointerMove?: PageEvent
  onPagePointerUp?: PageEvent
  onPagePointerCancel?: PageEvent
  className?: string
  style?: React.CSSProperties
  /** What it shows, for screen readers (the file's name, or "PDF", if left out). */
  "aria-label"?: string
}

const Embed = React.lazy(() => import("./pdf-viewer-embed").then((m) => ({ default: m.PdfViewerEmbed })))

/** Shown while EmbedPDF loads, and in its place if it cannot. */
function PdfViewerShell({ className, style, showToolbar = true, error }: { className?: string; style?: React.CSSProperties; showToolbar?: boolean; error?: string }) {
  return (
    <div className={cn("nt-pdf", className)} style={style} aria-busy={error ? undefined : true}>
      {showToolbar && <div className="nt-pdf-toolbar" aria-hidden="true" />}
      <div className="nt-pdf-body">
        <div className="nt-pdf-state">
          {error ? <span role="alert">{error}</span> : <ProgressBar aria-label="Loading the PDF viewer" style={{ width: 160 }} />}
        </div>
      </div>
    </div>
  )
}

class LoadFailure extends React.Component<{ fallback: (error: string) => React.ReactNode; children: React.ReactNode }, { error?: string }> {
  state: { error?: string } = {}
  static getDerivedStateFromError(error: unknown) {
    return { error: error instanceof Error ? error.message : String(error) }
  }
  render() {
    return this.state.error ? this.props.fallback(this.state.error) : this.props.children
  }
}

/**
 * A PDF viewer: the pages scroll, zoom (in, out, fit), page n of N, search, text selection (copied with
 * Ctrl+C / ⌘C), page thumbnails at the side, rotate, download and open. renderPageOverlay draws over each page
 * (boxes, marks); a ref scrolls to a page or to an area of one.
 */
const PdfViewer = React.forwardRef<PdfViewerHandle, PdfViewerProps>(function PdfViewer(props, ref) {
  const shell = (error?: string) => <PdfViewerShell className={props.className} style={props.style} showToolbar={props.showToolbar} error={error} />
  return (
    <LoadFailure fallback={() => shell("The PDF viewer could not be loaded.")}>
      <React.Suspense fallback={shell()}>
        <Embed ref={ref} {...props} />
      </React.Suspense>
    </LoadFailure>
  )
})

export { PdfViewer }
export type { PdfPageArea, PdfPageOverlayProps, PdfViewerHandle, PdfViewerProps, PdfZoom }
