import * as React from "react"
import { createPluginRegistration } from "@embedpdf/core"
import { EmbedPDF } from "@embedpdf/core/react"
import type { PdfDocumentObject, PdfEngine, Rotation } from "@embedpdf/models"
import { DocumentManagerPluginPackage, useActiveDocument, useDocumentManagerCapability } from "@embedpdf/plugin-document-manager/react"
import { GlobalPointerProvider, InteractionManagerPluginPackage, PagePointerProvider } from "@embedpdf/plugin-interaction-manager/react"
import { RenderLayer, RenderPluginPackage } from "@embedpdf/plugin-render/react"
import { Rotate, RotatePluginPackage, useRotate } from "@embedpdf/plugin-rotate/react"
import { ScrollPluginPackage, Scroller, useScroll, type PageLayout } from "@embedpdf/plugin-scroll/react"
import { SearchLayer, SearchPluginPackage, useSearch } from "@embedpdf/plugin-search/react"
import { CopyToClipboard, SelectionLayer, SelectionPluginPackage, useSelectionCapability } from "@embedpdf/plugin-selection/react"
import { ThumbImg, ThumbnailPluginPackage, ThumbnailsPane, useThumbnailCapability, type ThumbMeta } from "@embedpdf/plugin-thumbnail/react"
import { TilingLayer, TilingPluginPackage } from "@embedpdf/plugin-tiling/react"
import { useViewportElement, Viewport, ViewportPluginPackage } from "@embedpdf/plugin-viewport/react"
import { useZoom, ZoomMode, ZoomPluginPackage, type ZoomLevel } from "@embedpdf/plugin-zoom/react"

import { cn } from "../../../lib/utils"
import { Button } from "../../button"
import { ProgressBar } from "../../progress-bar"
import { SearchField } from "../../search-field"
import { Select } from "../../select"
import { loadPdfEngine, readPdf } from "./engine"
import { ChevronDownIcon, ChevronUpIcon, DownloadIcon, PagesIcon, RotateLeftIcon, RotateRightIcon, UploadIcon, ZoomInIcon, ZoomOutIcon } from "./icons"
import type { PdfViewerHandle, PdfViewerProps, PdfZoom } from "./pdf-viewer"

/*
 * The PdfViewer's EmbedPDF side, loaded when one is first shown. The plugins: document manager (opens the PDF),
 * viewport and scroll (the pages, only those near the view drawn), render and tiling (a page drawn small at once,
 * then sharp in tiles), zoom, rotate, search, text selection, thumbnails, interaction manager (pointer events).
 */

const ZOOMS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3]
const PAGE_GAP = 16
const THUMB = { width: 112, gap: 8, imagePadding: 6, labelHeight: 20, paddingY: 12 }
const SEARCH_DELAY = 300

function zoomLevel(zoom: PdfZoom): ZoomLevel {
  switch (zoom) {
    case "fit-page":
      return ZoomMode.FitPage
    case "fit-width":
      return ZoomMode.FitWidth
    case "automatic":
      return ZoomMode.Automatic
    default:
      return zoom
  }
}

function useEngine() {
  const [state, setState] = React.useState<{ engine?: PdfEngine; error?: string }>({})
  React.useEffect(() => {
    let gone = false
    loadPdfEngine().then(
      (engine) => !gone && setState({ engine }),
      (e: unknown) => !gone && setState({ error: e instanceof Error ? e.message : String(e) })
    )
    return () => {
      gone = true
    }
  }, [])
  return state
}

function nameOf(fileName: string | undefined, src: string) {
  const withPdf = (name: string) => (name.toLowerCase().endsWith(".pdf") ? name : `${name}.pdf`)
  if (fileName?.trim()) return withPdf(fileName.trim())
  if (src.startsWith("data:") || src.startsWith("blob:") || !src.includes("/")) return "document.pdf"
  const last = src.split(/[?#]/)[0].split("/").pop() || "document.pdf"
  try {
    return withPdf(decodeURIComponent(last))
  } catch {
    return withPdf(last)
  }
}

function save(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = fileName
  a.rel = "noopener"
  document.body.append(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** The PDF as shown: turned (pdf-lib, loaded only then) when the viewer turned its pages. */
async function asShown(bytes: ArrayBuffer, rotation: Rotation): Promise<Blob> {
  if (!rotation) return new Blob([bytes], { type: "application/pdf" })
  const { PDFDocument, degrees } = await import("pdf-lib")
  const doc = await PDFDocument.load(bytes)
  for (const page of doc.getPages()) page.setRotation(degrees((page.getRotation().angle + rotation * 90) % 360))
  const out = await doc.save()
  return new Blob([out.slice().buffer as ArrayBuffer], { type: "application/pdf" })
}

type EmbedProps = PdfViewerProps

/** EmbedPDF with its plugins, made once per viewer; the PDF opened in it is src (or the one opened with Open). */
const PdfViewerEmbed = React.forwardRef<PdfViewerHandle, EmbedProps>(function PdfViewerEmbed(props, ref) {
  const { defaultZoom = "automatic", showToolbar = true, className, style } = props
  const { engine, error } = useEngine()
  const [plugins] = React.useState(() => [
    createPluginRegistration(DocumentManagerPluginPackage),
    createPluginRegistration(ViewportPluginPackage, { viewportGap: PAGE_GAP }),
    createPluginRegistration(ScrollPluginPackage, { defaultPageGap: PAGE_GAP, defaultBufferSize: 2 }),
    createPluginRegistration(RenderPluginPackage),
    createPluginRegistration(TilingPluginPackage, { tileSize: 768, overlapPx: 2.5, extraRings: 0 }),
    createPluginRegistration(InteractionManagerPluginPackage),
    createPluginRegistration(SelectionPluginPackage, { marquee: { enabled: false } }),
    createPluginRegistration(SearchPluginPackage, { showAllResults: true }),
    createPluginRegistration(ThumbnailPluginPackage, { ...THUMB, buffer: 3, autoScroll: true, scrollBehavior: "auto" }),
    createPluginRegistration(ZoomPluginPackage, { defaultZoomLevel: zoomLevel(defaultZoom), minZoom: ZOOMS[0], maxZoom: ZOOMS[ZOOMS.length - 1] }),
    createPluginRegistration(RotatePluginPackage),
  ])

  if (error || !engine) {
    return (
      <div className={cn("nt-pdf", className)} style={style}>
        {showToolbar && <div className="nt-pdf-toolbar" aria-hidden="true" />}
        <div className="nt-pdf-body">
          <div className="nt-pdf-state">{error ? <span role="alert">The PDF engine could not be loaded.</span> : <ProgressBar aria-label="Loading the PDF engine" style={{ width: 160 }} />}</div>
        </div>
      </div>
    )
  }
  return (
    <EmbedPDF engine={engine} plugins={plugins}>
      <Loader {...props} viewerRef={ref} />
    </EmbedPDF>
  )
})

type LoaderProps = EmbedProps & { viewerRef: React.ForwardedRef<PdfViewerHandle> }

/** Opens the PDF (read here into bytes, whatever src is), closes the one before; draws the viewer once it is open. */
function Loader(props: LoaderProps) {
  const { src, onDocumentLoadSuccess, onDocumentLoadError, onPdfUpload } = props
  const { provides: manager } = useDocumentManagerCapability()
  const { activeDocumentId, activeDocument } = useActiveDocument()
  const [uploaded, setUploaded] = React.useState<{ for?: string; url: string; name: string }>()
  const [failed, setFailed] = React.useState<string>()
  const bytes = React.useRef<ArrayBuffer | null>(null)
  const latest = React.useRef({ onDocumentLoadSuccess, onDocumentLoadError })
  latest.current = { onDocumentLoadSuccess, onDocumentLoadError }

  // an opened file is shown until src changes
  const shown = uploaded && uploaded.for === src ? uploaded : undefined
  const file = shown?.url ?? src ?? ""
  const fileName = shown?.name ?? props.fileName

  React.useEffect(() => () => {
    if (uploaded) URL.revokeObjectURL(uploaded.url)
  }, [uploaded])

  React.useEffect(() => {
    if (!manager || !file) return
    let gone = false
    setFailed(undefined)
    const before = manager.getOpenDocuments().map((d) => d.id)
    const fail = (message: string) => {
      if (gone) return
      setFailed(message)
      latest.current.onDocumentLoadError?.(message)
    }
    readPdf(file).then(
      (content) => {
        if (gone) return
        // the engine takes the buffer over (it is moved to the worker): a copy is kept for Download
        bytes.current = content.slice(0)
        manager.openDocumentBuffer({ buffer: content, name: nameOf(fileName, file), autoActivate: true }).wait(
          (opened) =>
            opened.task.wait(
              (doc) => {
                if (gone) return
                latest.current.onDocumentLoadSuccess?.(doc.pageCount)
                for (const id of before) manager.closeDocument(id).wait(() => undefined, () => undefined)
              },
              (e) => fail(e.reason?.message ?? "This PDF could not be opened.")
            ),
          (e) => fail(e.reason?.message ?? "This PDF could not be opened.")
        )
      },
      (e: unknown) => fail(e instanceof Error ? e.message : String(e))
    )
    return () => {
      gone = true
    }
  }, [manager, file]) // eslint-disable-line react-hooks/exhaustive-deps

  const open = React.useCallback(
    (picked: File) => {
      setUploaded({ for: src, url: URL.createObjectURL(picked), name: picked.name })
      onPdfUpload?.(picked)
    },
    [src, onPdfUpload]
  )

  const doc = activeDocument?.status === "loaded" ? (activeDocument.document ?? null) : null
  const state = !file ? "empty" : failed || activeDocument?.status === "error" ? "error" : !activeDocumentId || !doc ? "loading" : "ready"
  if (state !== "ready" || !activeDocumentId || !doc) {
    return <Frame {...props} onOpen={open} state={state === "ready" ? "loading" : state} message={failed} />
  }
  return <Viewer key={activeDocumentId} {...props} fileName={nameOf(fileName, file)} documentId={activeDocumentId} doc={doc} bytes={bytes} onOpen={open} />
}

/** The viewer's frame without a PDF: its toolbar (only Open works) and what is happening instead. */
function Frame({ className, style, showToolbar = true, showUpload = true, state, message, onOpen, "aria-label": label }: EmbedProps & { state: "empty" | "error" | "loading"; message?: string; onOpen: (file: File) => void }) {
  return (
    <div className={cn("nt-pdf", className)} style={style} role="region" aria-label={label ?? "PDF"} aria-busy={state === "loading" || undefined}>
      {showToolbar && (
        <div className="nt-pdf-toolbar" role="toolbar" aria-label="PDF">
          <span className="nt-pdf-grow" />
          {showUpload && <OpenButton onOpen={onOpen} />}
        </div>
      )}
      <div className="nt-pdf-body">
        <div className="nt-pdf-state">
          {state === "loading" && <ProgressBar aria-label="Opening the PDF" style={{ width: 160 }} />}
          {state === "error" && <span role="alert">{message ? `This PDF could not be opened: ${message}` : "This PDF could not be opened."}</span>}
          {state === "empty" && (
            <div className="nt-pdf-empty">
              <strong>No PDF</strong>
              <span>{showUpload ? "Open one to see it here." : "There is no PDF to show."}</span>
              {showUpload && <OpenButton onOpen={onOpen} label="Open a PDF…" />}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function IconButton({ label, children, ...props }: React.ComponentProps<typeof Button> & { label: string }) {
  return (
    <Button variant="subtle" className="nt-pdf-icon-button" aria-label={label} title={label} {...props}>
      {children}
    </Button>
  )
}

function OpenButton({ onOpen, label }: { onOpen: (file: File) => void; label?: string }) {
  const input = React.useRef<HTMLInputElement>(null)
  return (
    <>
      <input
        ref={input}
        type="file"
        accept="application/pdf,.pdf"
        hidden
        tabIndex={-1}
        onChange={(event) => {
          const picked = event.target.files?.[0]
          if (picked) onOpen(picked)
          event.currentTarget.value = ""
        }}
      />
      {label ? (
        <Button onClick={() => input.current?.click()}>{label}</Button>
      ) : (
        <IconButton label="Open a PDF" onClick={() => input.current?.click()}>
          <UploadIcon />
        </IconButton>
      )}
    </>
  )
}

type ViewerProps = EmbedProps & {
  viewerRef: React.ForwardedRef<PdfViewerHandle>
  documentId: string
  doc: PdfDocumentObject
  fileName: string
  bytes: React.RefObject<ArrayBuffer | null>
  onOpen: (file: File) => void
}

function Viewer(props: ViewerProps) {
  const {
    viewerRef,
    documentId,
    doc,
    fileName,
    bytes,
    onOpen,
    defaultZoom = "automatic",
    showToolbar = true,
    showDownload = true,
    showRotateControls = true,
    showUpload = true,
    defaultShowThumbnails = false,
    initialSearchQuery,
    toolbarActions,
    pageClassName,
    renderPageOverlay,
    onActivePageChange,
    onDownload,
    onPagePointerDown,
    onPagePointerMove,
    onPagePointerUp,
    onPagePointerCancel,
    className,
    style,
    "aria-label": label,
  } = props
  const { state: scrollState, provides: scroll } = useScroll(documentId)
  const { state: zoomState, provides: zoom } = useZoom(documentId)
  const { rotation, provides: rotate } = useRotate(documentId)
  const { provides: thumbnails } = useThumbnailCapability()
  const [thumbs, setThumbs] = React.useState(defaultShowThumbnails)
  const [saving, setSaving] = React.useState(false)
  const viewport = React.useRef<HTMLDivElement | null>(null)
  const search = React.useRef<HTMLInputElement>(null)
  const page = scrollState.currentPage
  const pages = doc.pageCount
  const scale = zoomState.currentZoomLevel

  React.useEffect(() => {
    if (page > 0) onActivePageChange?.(page)
  }, [page, onActivePageChange])

  // A numeric zoom does not lift the viewport's gate by itself (only the fitted ones do): asked for once here.
  const zoomed = React.useRef(false)
  React.useEffect(() => {
    if (!zoom || zoomed.current) return
    zoomed.current = true
    zoom.requestZoom(zoomLevel(defaultZoom))
  }, [zoom, defaultZoom])

  // the thumbnails, opened, show the page in view
  React.useEffect(() => {
    if (!thumbs || !thumbnails || page < 1) return
    const frame = requestAnimationFrame(() => thumbnails.forDocument(documentId).scrollToThumb(page - 1))
    return () => cancelAnimationFrame(frame)
  }, [thumbs]) // eslint-disable-line react-hooks/exhaustive-deps

  const goTo = React.useCallback(
    (n: number, behavior: "auto" | "smooth" = "auto") => scroll?.scrollToPage({ pageNumber: Math.min(Math.max(1, n), pages), behavior }),
    [scroll, pages]
  )

  React.useImperativeHandle(
    viewerRef,
    () => ({
      scrollToPage: (n, options) => goTo(n, options?.behavior),
      scrollToPageArea: (n, area, options) => {
        const size = doc.pages[n - 1]?.size
        scroll?.scrollToPage({
          pageNumber: n,
          ...(size ? { pageCoordinates: { x: ((area.left ?? 0) / 100) * size.width, y: (area.top / 100) * size.height }, alignY: 25 } : {}),
          behavior: options?.behavior ?? "auto",
        })
      },
      getViewportElement: () => viewport.current,
    }),
    [doc, scroll, goTo]
  )

  const download = async () => {
    const original = bytes.current
    if (!original || saving) return
    setSaving(true)
    try {
      const blob = await asShown(original.slice(0), rotation)
      const name = rotation ? fileName.replace(/\.pdf$/i, "-rotated.pdf") : fileName
      if (onDownload) onDownload(blob, name)
      else save(blob, name)
    } catch (e) {
      console.error("PdfViewer download:", e)
    } finally {
      setSaving(false)
    }
  }

  const stepZoom = (by: 1 | -1) => {
    const next = by > 0 ? ZOOMS.find((z) => z > scale + 0.001) : [...ZOOMS].reverse().find((z) => z < scale - 0.001)
    if (next) zoom?.requestZoom(next)
  }

  const level = zoomState.zoomLevel
  const zoomValue = String(level)
  const zoomOptions = [
    { value: ZoomMode.Automatic, label: "Automatic" },
    { value: ZoomMode.FitWidth, label: "Fit width" },
    { value: ZoomMode.FitPage, label: "Fit page" },
    ...(typeof level === "number" && !ZOOMS.includes(level) ? [{ value: String(level), label: `${Math.round(level * 100)}%` }] : []),
    ...ZOOMS.map((z) => ({ value: String(z), label: `${Math.round(z * 100)}%` })),
  ]

  const keys = (event: React.KeyboardEvent) => {
    const mod = event.ctrlKey || event.metaKey
    if (mod && event.key.toLowerCase() === "f" && search.current) {
      event.preventDefault()
      search.current.focus()
      search.current.select()
    } else if (mod && (event.key === "=" || event.key === "+")) {
      event.preventDefault()
      stepZoom(1)
    } else if (mod && event.key === "-") {
      event.preventDefault()
      stepZoom(-1)
    }
  }

  const renderPage = (layout: PageLayout) => {
    const n = layout.pageNumber
    const size = doc.pages[layout.pageIndex]?.size ?? { width: layout.width / scale, height: layout.height / scale }
    return (
      <Rotate documentId={documentId} pageIndex={layout.pageIndex}>
        <PagePointerProvider
          documentId={documentId}
          pageIndex={layout.pageIndex}
          data-page={n}
          className={cn("nt-pdf-page", pageClassName?.(n))}
          onPointerDown={onPagePointerDown && ((e: React.PointerEvent<HTMLDivElement>) => onPagePointerDown(e, n))}
          onPointerMove={onPagePointerMove && ((e: React.PointerEvent<HTMLDivElement>) => onPagePointerMove(e, n))}
          onPointerUp={onPagePointerUp && ((e: React.PointerEvent<HTMLDivElement>) => onPagePointerUp(e, n))}
          onPointerCancel={onPagePointerCancel && ((e: React.PointerEvent<HTMLDivElement>) => onPagePointerCancel(e, n))}
        >
          <RenderLayer documentId={documentId} pageIndex={layout.pageIndex} scale={Math.min(scale, 1)} dpr={1} className="nt-pdf-layer nt-pdf-base" />
          <TilingLayer documentId={documentId} pageIndex={layout.pageIndex} className="nt-pdf-layer" />
          <SearchLayer documentId={documentId} pageIndex={layout.pageIndex} className="nt-pdf-layer" highlightColor="rgba(253, 224, 71, 0.45)" activeHighlightColor="rgba(249, 115, 22, 0.55)" />
          <SelectionLayer documentId={documentId} pageIndex={layout.pageIndex} textStyle={{ background: "color-mix(in srgb, var(--nt-accent) 32%, transparent)" }} />
          {renderPageOverlay && (
            <div className="nt-pdf-overlay">
              {renderPageOverlay({ pageNumber: n, pageWidth: size.width, pageHeight: size.height, scale, rotation: rotation * 90 })}
            </div>
          )}
        </PagePointerProvider>
      </Rotate>
    )
  }

  return (
    <div className={cn("nt-pdf", className)} style={style} role="region" aria-label={label ?? fileName} onKeyDown={keys}>
      {showToolbar && (
        <div className="nt-pdf-toolbar" role="toolbar" aria-label="PDF">
          <IconButton label={thumbs ? "Hide the pages" : "Show the pages"} aria-pressed={thumbs} onClick={() => setThumbs((v) => !v)}>
            <PagesIcon />
          </IconButton>
          <PageNumber page={page} pages={pages} onPage={goTo} />
          <span className="nt-pdf-separator" role="separator" aria-orientation="vertical" />
          <IconButton label="Zoom out" disabled={scale <= ZOOMS[0] + 0.001} onClick={() => stepZoom(-1)}>
            <ZoomOutIcon />
          </IconButton>
          <Select aria-label="Zoom" className="nt-pdf-zoom" value={zoomValue} options={zoomOptions} onChange={(e) => {
            const v = e.target.value
            zoom?.requestZoom(Number.isNaN(Number(v)) ? (v as ZoomMode) : Number(v))
          }} />
          <IconButton label="Zoom in" disabled={scale >= ZOOMS[ZOOMS.length - 1] - 0.001} onClick={() => stepZoom(1)}>
            <ZoomInIcon />
          </IconButton>
          {showRotateControls && (
            <>
              <span className="nt-pdf-separator" role="separator" aria-orientation="vertical" />
              <IconButton label="Rotate left" onClick={() => rotate?.rotateBackward()}>
                <RotateLeftIcon />
              </IconButton>
              <IconButton label="Rotate right" onClick={() => rotate?.rotateForward()}>
                <RotateRightIcon />
              </IconButton>
            </>
          )}
          <span className="nt-pdf-grow" />
          <SearchControl documentId={documentId} inputRef={search} initial={initialSearchQuery} />
          {toolbarActions && (
            <>
              <span className="nt-pdf-separator" role="separator" aria-orientation="vertical" />
              {toolbarActions}
            </>
          )}
          {(showDownload || showUpload) && <span className="nt-pdf-separator" role="separator" aria-orientation="vertical" />}
          {showDownload && (
            <IconButton label={saving ? "Preparing the download" : "Download"} disabled={saving} onClick={download}>
              <DownloadIcon />
            </IconButton>
          )}
          {showUpload && <OpenButton onOpen={onOpen} />}
        </div>
      )}
      <div className="nt-pdf-body">
        {thumbs && <Thumbnails documentId={documentId} page={page} pages={pages} rotation={rotation} onPage={goTo} />}
        <Viewport documentId={documentId} className="nt-pdf-viewport">
          <ViewportBridge into={viewport} />
          <CopyShortcut documentId={documentId} />
          <GlobalPointerProvider documentId={documentId}>
            <Scroller documentId={documentId} renderPage={renderPage} />
          </GlobalPointerProvider>
          <CopyToClipboard />
        </Viewport>
      </div>
    </div>
  )
}

/** Keeps the scrolling element for the handle's getViewportElement. */
function ViewportBridge({ into }: { into: React.RefObject<HTMLDivElement | null> }) {
  const element = useViewportElement()
  React.useEffect(() => {
    into.current = element?.current ?? null
  })
  return null
}

/** Ctrl+C / ⌘C (and the Copy command) copy the text selected in the PDF, unless a field has the focus. */
function CopyShortcut({ documentId }: { documentId: string }) {
  const { provides: selection } = useSelectionCapability()
  React.useEffect(() => {
    if (!selection) return
    const inField = (target: EventTarget | null) =>
      target instanceof HTMLElement && (target.isContentEditable || target.closest("input, textarea, select, [contenteditable='true']") != null)
    const copy = (event: Event) => {
      if (inField(event.target) || !selection.getState(documentId).selection) return
      event.preventDefault()
      selection.copyToClipboard(documentId)
    }
    const keys = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() === "c" && (event.ctrlKey || event.metaKey)) copy(event)
    }
    document.addEventListener("copy", copy)
    document.addEventListener("keydown", keys)
    return () => {
      document.removeEventListener("copy", copy)
      document.removeEventListener("keydown", keys)
    }
  }, [documentId, selection])
  return null
}

/** "Page [n] of N": the number can be typed (Enter goes there, Escape leaves it). */
function PageNumber({ page, pages, onPage }: { page: number; pages: number; onPage: (n: number) => void }) {
  const [draft, setDraft] = React.useState<string>()
  const shown = draft ?? String(Math.max(page, 1))
  const go = () => {
    const n = Number(draft)
    if (draft !== undefined && Number.isInteger(n)) onPage(n)
    setDraft(undefined)
  }
  return (
    <span className="nt-pdf-page-number">
      <input
        className="nt-input nt-pdf-page-input"
        aria-label={`Page, of ${pages}`}
        inputMode="numeric"
        value={shown}
        onFocus={(e) => e.currentTarget.select()}
        onChange={(e) => setDraft(e.target.value.replace(/\D/g, ""))}
        onBlur={go}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault()
            go()
          } else if (e.key === "Escape") {
            setDraft(undefined)
            e.currentTarget.blur()
          }
        }}
      />
      <span aria-hidden="true">of {pages}</span>
    </span>
  )
}

/** The search box: matches marked as it is typed; Enter goes to the next, Shift+Enter the one before. */
function SearchControl({ documentId, inputRef, initial }: { documentId: string; inputRef: React.RefObject<HTMLInputElement | null>; initial?: string }) {
  const { state, provides } = useSearch(documentId)
  const { provides: scroll } = useScroll(documentId)
  const [draft, setDraft] = React.useState(initial ?? "")
  const [searching, setSearching] = React.useState(false)
  const run = React.useRef(0)
  const latest = React.useRef({ provides, scroll })
  latest.current = { provides, scroll }

  const show = React.useCallback((result: { pageIndex: number; rects: { origin: { x: number; y: number } }[] } | undefined) => {
    const target = latest.current.scroll
    if (!result || !target) return
    const rect = result.rects[0]
    target.scrollToPage({ pageNumber: result.pageIndex + 1, ...(rect ? { pageCoordinates: { x: rect.origin.x, y: rect.origin.y }, alignY: 30 } : {}), behavior: "auto" })
  }, [])

  React.useEffect(() => {
    const searcher = provides
    if (!searcher) return
    const query = draft.trim()
    const id = ++run.current
    if (!query) {
      searcher.stopSearch()
      setSearching(false)
      return
    }
    setSearching(true)
    const timer = window.setTimeout(() => {
      searcher.startSearch()
      searcher.searchAllPages(query).wait(
        (result) => {
          if (run.current !== id) return
          setSearching(false)
          if (result.results[0]) {
            searcher.goToResult(0)
            show(result.results[0])
          }
        },
        () => run.current === id && setSearching(false)
      )
    }, SEARCH_DELAY)
    return () => window.clearTimeout(timer)
  }, [draft, provides, show])

  const step = (by: 1 | -1) => {
    if (!provides || !state.total) return
    const index = by > 0 ? provides.nextResult() : provides.previousResult()
    show(state.results[index])
  }

  const status = !draft.trim() ? "" : searching ? "Searching…" : state.total ? `${state.activeResultIndex + 1} of ${state.total}` : "No matches"
  return (
    <span className="nt-pdf-search">
      <SearchField
        ref={inputRef}
        aria-label="Search the PDF"
        placeholder="Search"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault()
            step(e.shiftKey ? -1 : 1)
          }
        }}
      />
      {status && (
        <span className="nt-pdf-search-status" aria-live="polite">
          {status}
        </span>
      )}
      {state.total > 0 && draft.trim() && (
        <>
          <IconButton label="Previous match" onClick={() => step(-1)}>
            <ChevronUpIcon />
          </IconButton>
          <IconButton label="Next match" onClick={() => step(1)}>
            <ChevronDownIcon />
          </IconButton>
        </>
      )}
    </span>
  )
}

/** The page thumbnails, only those near the view drawn; a click goes to the page, arrow keys move between them. */
function Thumbnails({ documentId, page, pages, rotation, onPage }: { documentId: string; page: number; pages: number; rotation: Rotation; onPage: (n: number) => void }) {
  const list = React.useId()
  const quarter = rotation % 2 === 1
  return (
    <ThumbnailsPane
      documentId={documentId}
      className="nt-pdf-thumbs"
      role="listbox"
      aria-label="Pages"
      tabIndex={0}
      aria-activedescendant={page > 0 ? `${list}-${page}` : undefined}
      onKeyDown={(e: React.KeyboardEvent<HTMLDivElement>) => {
        const next = e.key === "ArrowDown" ? page + 1 : e.key === "ArrowUp" ? page - 1 : e.key === "Home" ? 1 : e.key === "End" ? pages : null
        if (next === null) return
        e.preventDefault()
        onPage(next)
      }}
    >
      {(meta: ThumbMeta) => {
        const n = meta.pageIndex + 1
        const pad = meta.padding ?? 0
        const fit = quarter ? Math.min(meta.width / meta.height, meta.height / meta.width) : 1
        return (
          <div
            key={meta.pageIndex}
            id={`${list}-${n}`}
            role="option"
            aria-selected={n === page}
            aria-label={`Page ${n}`}
            className="nt-pdf-thumb"
            style={{ top: meta.top, height: meta.wrapperHeight }}
            onClick={() => onPage(n)}
          >
            <span className="nt-pdf-thumb-image" style={{ width: meta.width + pad * 2, height: meta.height + pad * 2, padding: pad }}>
              <ThumbImg
                documentId={documentId}
                meta={meta}
                style={{ width: meta.width, height: meta.height, transform: rotation ? `rotate(${rotation * 90}deg) scale(${fit})` : undefined }}
              />
            </span>
            <span className="nt-pdf-thumb-label" style={{ height: meta.labelHeight }}>
              {n}
            </span>
          </div>
        )
      }}
    </ThumbnailsPane>
  )
}

export { PdfViewerEmbed }
