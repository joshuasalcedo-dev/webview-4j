// The Word viewer and editor themselves, with @extend-ai/react-docx: loaded the first time a DocxViewer or a
// DocxEditor is shown (docx-viewer.tsx, docx-editor.tsx), after loadDocx() has readied the library's WebAssembly.
import {
  type DocModel,
  type DocxBorderPreset,
  type DocxContextMenuAction,
  type DocxContextMenuRenderProps,
  type DocxEditorController,
  DocxEditorViewer,
  type DocxPageThumbnailItem,
  type OoxmlPackage,
  parseDocx,
  serializeDocx,
  useDocxBorders,
  useDocxComments,
  useDocxEditor,
  useDocxLineSpacing,
  useDocxPageLayout,
  useDocxParagraphStyles,
  useDocxTrackChanges,
  useDocxViewerThumbnails,
} from "@extend-ai/react-docx"
import { useVirtualizer } from "@tanstack/react-virtual"
import * as React from "react"
import { createPortal } from "react-dom"

import { cn } from "../../../lib/utils"
import { Button } from "../../button"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import { useDialogs } from "../../dialog"
import { ProgressBar } from "../../progress-bar"
import { useNative } from "../../provider"
import { SearchField } from "../../search-field"
import { Select } from "../../select"
import { SidebarIcon } from "../../../lib/icons"
import { Toolbar, ToolbarButton, ToolbarSeparator, ToolbarSpacer } from "../../toolbar"
import { createDocxCommentCardRenderer, createDocxTrackedChangeCardRenderer, DocxAnnotationList } from "./docx-annotation-card"
import {
  DocumentViewerSidebar,
  DocumentViewerSidebarList,
  DocumentViewerSidebarSkeleton,
  useElementWidth,
  useInlineThumbnailSidebar,
} from "./document-viewer-sidebar"
import * as Icons from "./icons"
import { base64Of, DOCX_MIME, docxName, downloadBytes, pickDocx, pickFile, readDocx, toBase64, withDocxExtension } from "./load"
import { type DocxMatch, docxOutline, docxSections, searchDocx } from "./model"
import type { DocxEditorProps, DocxPanel, DocxViewerProps, DocxZoom } from "./types"
import "./docx.css"

const ZOOMS = [25, 50, 75, 100, 125, 150, 200, 300, 400]
const MIN_ZOOM = 10
const MAX_ZOOM = 400
/** The margin the library adds beside each page for comment and change cards. */
const GUTTER_PX = 300
/** Room around the page when it is fitted to the viewer. */
const FIT_PADDING = 48
const THUMB_WIDTH = 112
const THUMB_ROW = 190
const THUMB_PREFETCH = 4
const LOADING_DELAY_MS = 300
const FONTS = ["Calibri", "Cambria", "Arial", "Helvetica", "Georgia", "Times New Roman", "Segoe UI", "Verdana", "Courier New"]
const SIZES = [8, 9, 10, 10.5, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72]
const SPACINGS = [1, 1.15, 1.5, 2, 2.5, 3]
const COLORS: [string, string][] = [
  ["", "Automatic"], ["#C00000", "Dark red"], ["#FF0000", "Red"], ["#ED7D31", "Orange"], ["#BF9000", "Gold"],
  ["#00B050", "Green"], ["#0070C0", "Blue"], ["#002060", "Navy"], ["#7030A0", "Purple"], ["#7F7F7F", "Grey"],
]
const HIGHLIGHTS: [string, string][] = [
  ["", "No highlight"], ["yellow", "Yellow"], ["green", "Bright green"], ["cyan", "Turquoise"], ["magenta", "Pink"],
  ["red", "Red"], ["blue", "Blue"], ["lightGray", "Light grey"],
]
const BORDERS: [DocxBorderPreset, string][] = [
  ["bottom", "Bottom border"], ["top", "Top border"], ["left", "Left border"], ["right", "Right border"],
  ["none", "No border"], ["all", "All borders"], ["outside", "Outside borders"], ["inside", "Inside borders"],
  ["inside-horizontal", "Inside horizontal"], ["inside-vertical", "Inside vertical"], ["horizontal-line", "Horizontal line"],
]

const clampZoom = (value: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(value)))

function stepZoom(current: number, by: 1 | -1): number {
  if (by > 0) return ZOOMS.find((z) => z > current) ?? MAX_ZOOM
  return [...ZOOMS].reverse().find((z) => z < current) ?? MIN_ZOOM
}

/** The kit's dialogs, or null outside a NativeProvider (window.prompt then). */
function useDialogsOrNull() {
  try {
    return useDialogs()
  } catch {
    return null
  }
}

/** The page in view, kept outside React state so scrolling redraws only what shows it. */
type PageStore = {
  get: () => number
  set: (page: number) => void
  subscribe: (listener: () => void) => () => void
}

function createPageStore(): PageStore {
  let page = 1
  const listeners = new Set<() => void>()
  return {
    get: () => page,
    set: (next) => {
      const value = Math.max(1, Math.round(next || 1))
      if (value === page) return
      page = value
      listeners.forEach((listener) => listener())
    },
    subscribe: (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

const usePage = (store: PageStore) => React.useSyncExternalStore(store.subscribe, store.get, store.get)

/** Shows the spinner only when loading takes a moment. */
function useDelayed(on: boolean, ms: number) {
  const [shown, setShown] = React.useState(false)
  React.useEffect(() => {
    if (!on) return
    const id = window.setTimeout(() => setShown(true), ms)
    return () => {
      window.clearTimeout(id)
      setShown(false)
    }
  }, [on, ms])
  return on && shown
}

/** The library re-renders pages with a padding shorthand React warns about; the warning says nothing to the app. */
function useQuietPaddingWarning(on: boolean) {
  React.useEffect(() => {
    if (!on) return
    const original = console.error
    console.error = (...args: unknown[]) => {
      if (typeof args[0] === "string" && args[0].includes("a style property during rerender") && args.some((a) => String(a).includes("padding"))) return
      original(...args)
    }
    return () => {
      console.error = original
    }
  }, [on])
}

const sleepFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))

/** The page wrappers drawn now (offscreen pages are placeholders the library fills as they scroll in). */
const pageWrappers = (viewport: HTMLElement) =>
  Array.from(viewport.querySelectorAll<HTMLElement>('[data-docx-page-wrapper="true"][data-index]'))

/** A body node's drawn element: a paragraph's, or a table's. */
const nodeElement = (viewport: HTMLElement, nodeIndex: number) =>
  viewport.querySelector<HTMLElement>(`[data-docx-paragraph-node-index="${nodeIndex}"]`) ??
  viewport.querySelector<HTMLElement>(`[data-docx-table-index="${nodeIndex}"]`)

/** The body node a drawn paragraph belongs to (a table's, for a paragraph in a cell). */
function hostNodeIndex(host: Element): number | undefined {
  const own = host.getAttribute("data-docx-paragraph-node-index")
  if (own != null) return Number(own)
  const table = host.closest("[data-docx-table-index]")?.getAttribute("data-docx-table-index")
  return table != null ? Number(table) : undefined
}

/** Where `query` is in the drawn text: Ranges, the one at `current` (a node and its nth match there) apart. */
function findRanges(viewport: HTMLElement, query: string, current?: { nodeIndex: number; nth: number }) {
  const all: Range[] = []
  let chosen: Range | undefined
  const needle = query.trim().toLocaleLowerCase()
  if (!needle) return { all, chosen }
  const seen = new Map<number, number>()
  for (const host of viewport.querySelectorAll<HTMLElement>("[data-docx-paragraph-host]")) {
    const nodeIndex = hostNodeIndex(host)
    const texts: Text[] = []
    const walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT)
    for (let n = walker.nextNode(); n; n = walker.nextNode()) texts.push(n as Text)
    const whole = texts.map((t) => t.data).join("").toLocaleLowerCase()
    const starts: number[] = []
    let total = 0
    for (const t of texts) {
      starts.push(total)
      total += t.data.length
    }
    const at = (offset: number): [Text, number] => {
      let i = starts.length - 1
      while (i > 0 && starts[i] > offset) i--
      return [texts[i], offset - starts[i]]
    }
    for (let found = whole.indexOf(needle); found !== -1; found = whole.indexOf(needle, found + needle.length)) {
      const range = document.createRange()
      const [startNode, startOffset] = at(found)
      const [endNode, endOffset] = at(found + needle.length - 1)
      range.setStart(startNode, startOffset)
      range.setEnd(endNode, endOffset + 1)
      all.push(range)
      if (nodeIndex !== undefined) {
        const nth = seen.get(nodeIndex) ?? 0
        seen.set(nodeIndex, nth + 1)
        if (current && current.nodeIndex === nodeIndex && current.nth === nth) chosen = range
      }
    }
  }
  return { all, chosen }
}

type HighlightApi = { highlights: Map<string, unknown> }
const highlightApi = (): HighlightApi | undefined =>
  typeof CSS !== "undefined" && "highlights" in CSS && typeof Highlight !== "undefined" ? (CSS as unknown as HighlightApi) : undefined

/** Marks the matches (the CSS Custom Highlight API: the document's DOM is left as it is). */
function paintHighlights(all: Range[], chosen: Range | undefined) {
  const api = highlightApi()
  if (!api) return
  api.highlights.set("nt-docx-match", new Highlight(...all.filter((r) => r !== chosen)))
  if (chosen) api.highlights.set("nt-docx-match-current", new Highlight(chosen))
  else api.highlights.delete("nt-docx-match-current")
}

function clearHighlights() {
  const api = highlightApi()
  api?.highlights.delete("nt-docx-match")
  api?.highlights.delete("nt-docx-match-current")
}

type WorkspaceProps = DocxViewerProps & DocxEditorProps & { mode: "view" | "edit" }

/** The viewer (mode "view") or the editor (mode "edit"). */
function DocxWorkspace(props: WorkspaceProps) {
  const {
    mode,
    src,
    data,
    fileName,
    defaultZoom = "fit-width",
    zoom: zoomProp,
    onZoomChange,
    showToolbar = true,
    defaultPanel = null,
    showComments: initialComments = true,
    showTrackedChanges: initialChanges = mode === "view",
    showDownload = true,
    showUpload = true,
    onDownload,
    onUpload,
    onPageChange,
    onLoad,
    onError,
    toolbarActions,
    height,
    darkPages: darkProp,
    readOnly: readOnlyProp,
    onReadOnlyChange,
    showReadOnlyToggle = true,
    onSave,
    onChange,
    onDirtyChange,
    className,
    style,
    "aria-label": ariaLabel = "Word document",
    ...rest
  } = props
  const editing = mode === "edit"
  const { theme, platform } = useNative()
  const dialogs = useDialogsOrNull()

  // ---- read-only and dark pages ----
  const [lockedOwn, setLockedOwn] = React.useState(readOnlyProp ?? false)
  React.useEffect(() => {
    if (readOnlyProp !== undefined) setLockedOwn(readOnlyProp)
  }, [readOnlyProp])
  const locked = !editing || lockedOwn
  const setLocked = (value: boolean) => {
    setLockedOwn(value)
    onReadOnlyChange?.(value)
  }
  const [darkOwn, setDarkOwn] = React.useState<boolean>()
  React.useEffect(() => setDarkOwn(undefined), [theme])

  // ---- the document ----
  // A document opened here (Open), or a new blank one (New), in place of the one given until that changes.
  const [opened, setOpened] = React.useState<{ name: string; bytes?: ArrayBuffer; key: number; from: [unknown, unknown] } | null>(null)
  const active = opened && opened.from[0] === src && opened.from[1] === data ? opened : null
  const shownName = active?.name ?? docxName(fileName, src)
  const editor = useDocxEditor(
    React.useMemo(
      () => ({
        initialFileName: shownName,
        initialDocumentTheme: "light" as const,
        initialShowComments: initialComments,
        initialShowTrackedChanges: initialChanges,
      }),
      // read once, when the editor is made
      []
    )
  )
  const latest = React.useRef(editor)
  latest.current = editor
  const exportTransform = React.useRef<((model: DocModel) => DocModel) | undefined>(undefined)
  // The editor the viewer gets: the same, but the drafts it keeps (text typed and not yet committed) reach Save too.
  const viewerEditor = React.useMemo<DocxEditorController>(
    () => ({
      ...editor,
      registerPendingExportModelTransformer: (transform) => {
        exportTransform.current = transform
        editor.registerPendingExportModelTransformer(transform)
      },
    }),
    [editor]
  )
  const { layout } = useDocxPageLayout(editor)
  const { comments, showComments, setShowComments } = useDocxComments(editor)
  const { trackedChanges, showTrackedChanges, setShowTrackedChanges } = useDocxTrackChanges(editor)
  const hasDocument = Boolean(src || data || active) || editing
  const [loading, setLoading] = React.useState(Boolean(src || data))
  const [error, setError] = React.useState<string>()
  const [reportedPages, setReportedPages] = React.useState(0)
  const original = React.useRef<ArrayBuffer | undefined>(undefined)
  const basePackage = React.useRef<Promise<OoxmlPackage> | undefined>(undefined)
  /** The base64 of the bytes last given out (Save, Download): Java sending them back is no new document. */
  const given = React.useRef<string | undefined>(undefined)
  const queue = React.useRef<Promise<void>>(Promise.resolve())
  const onErrorRef = React.useRef(onError)
  onErrorRef.current = onError

  const pages = hasDocument && !loading && !error ? Math.max(1, reportedPages || editor.totalPages) : 0
  const disabled = !hasDocument || loading || Boolean(error)
  const commentsShown = showComments && comments.length > 0
  const changesShown = showTrackedChanges && trackedChanges.length > 0
  // the library makes the pages read-only while tracked changes are marked
  const readOnly = locked || changesShown
  const darkPages = readOnly && (darkOwn ?? darkProp ?? theme === "dark")
  // dark pages are the library's night reader: its dark document theme, read-only
  const { setDocumentTheme } = editor
  React.useEffect(() => setDocumentTheme(darkPages ? "dark" : "light"), [darkPages, setDocumentTheme])

  React.useEffect(() => {
    if (editor.importError) {
      setError(editor.importError.message)
      setLoading(false)
      onErrorRef.current?.(editor.importError.message)
    }
  }, [editor.importError])

  React.useEffect(() => {
    let current = true
    const echo = !active && base64Of(src)
    if (echo && echo === given.current) return
    const load = async () => {
      if (!current) return
      if (active && !active.bytes) {
        original.current = undefined
        basePackage.current = undefined
        given.current = undefined
        latest.current.newDocument()
        setLoading(false)
        setError(undefined)
        return
      }
      if (!active && !src && !data) {
        original.current = undefined
        basePackage.current = undefined
        setLoading(false)
        setError(undefined)
        return
      }
      setLoading(true)
      setError(undefined)
      setReportedPages(0)
      try {
        const bytes = active?.bytes ?? (await readDocx(src, data))
        if (!current || !bytes) return
        original.current = bytes
        basePackage.current = undefined
        given.current = undefined
        const name = withDocxExtension(active?.name ?? docxName(fileName, src))
        await latest.current.importDocxFile(new File([bytes.slice(0)], name, { type: DOCX_MIME }))
        if (current) setLoading(false)
      } catch (e) {
        if (!current) return
        const message = e instanceof Error ? e.message : String(e)
        setError(message)
        setLoading(false)
        onErrorRef.current?.(message)
      }
    }
    // Imports change the one editor: one at a time, the newest winning (a second one at once breaks the parse).
    queue.current = queue.current.then(load)
    return () => {
      current = false
    }
  }, [active, src, data, fileName])

  // ---- changes, for Save and onChange ----
  const baseline = React.useRef<DocModel | undefined>(undefined)
  const [dirty, setDirty] = React.useState(false)
  React.useEffect(() => {
    baseline.current = latest.current.model
    setDirty(false)
  }, [editor.documentLoadNonce])
  const onChangeRef = React.useRef(onChange)
  onChangeRef.current = onChange
  React.useEffect(() => {
    if (!editing || !baseline.current) return
    const changed = editor.model !== baseline.current
    setDirty(changed)
    if (!changed) return
    const id = window.setTimeout(() => onChangeRef.current?.(), 600)
    return () => window.clearTimeout(id)
  }, [editing, editor.model])
  const onDirtyRef = React.useRef(onDirtyChange)
  onDirtyRef.current = onDirtyChange
  const firstDirty = React.useRef(true)
  React.useEffect(() => {
    if (firstDirty.current) {
      firstDirty.current = false
      return
    }
    onDirtyRef.current?.(dirty)
  }, [dirty])

  React.useEffect(() => {
    if (!loading && !error && pages) onLoad?.({ fileName: shownName, pageCount: pages })
    // once per document shown
  }, [loading, error, editor.documentLoadNonce])

  /** The document as .docx bytes: the original (the viewer, nothing changed), else the model written out. */
  const bytesNow = React.useCallback(async (): Promise<Uint8Array> => {
    const ed = latest.current
    if (!editing && original.current) return new Uint8Array(original.current.slice(0))
    const model = exportTransform.current ? exportTransform.current(ed.model) : ed.model
    if (original.current && !basePackage.current) basePackage.current = parseDocx(original.current.slice(0))
    const pkg = basePackage.current ? await basePackage.current : undefined
    const out = new Uint8Array(await serializeDocx(model, pkg))
    baseline.current = ed.model
    setDirty(false)
    return out
  }, [editing])

  const [busy, setBusy] = React.useState(false)
  const give = React.useCallback(
    async (to: "save" | "download") => {
      if (busy || disabled) return
      setBusy(true)
      try {
        const bytes = await bytesNow()
        const name = withDocxExtension(shownName)
        const handler = to === "save" ? (onSave ?? onDownload) : onDownload
        if (handler) {
          given.current = toBase64(bytes)
          handler(bytes, name)
        } else downloadBytes(bytes, name)
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e)
        onErrorRef.current?.(message)
        if (dialogs) void dialogs.error({ title: "The document was not saved", message })
      } finally {
        setBusy(false)
      }
    },
    [busy, disabled, bytesNow, shownName, onSave, onDownload, dialogs]
  )

  const open = React.useCallback(async () => {
    const picked = await pickDocx()
    if (!picked) return
    setOpened({ name: picked.name, bytes: picked.bytes, key: Date.now(), from: [src, data] })
    onUpload?.(picked.name, new Uint8Array(picked.bytes.slice(0)))
  }, [src, data, onUpload])

  // ---- the scroller, zoom and pages ----
  const viewport = React.useRef<HTMLDivElement | null>(null)
  const [viewportElement, setViewportElement] = React.useState<HTMLDivElement | null>(null)
  const setViewport = React.useCallback((element: HTMLDivElement | null) => {
    viewport.current = element
    setViewportElement(element)
  }, [])
  const [size, setSize] = React.useState({ width: 0, height: 0 })
  React.useLayoutEffect(() => {
    if (!viewportElement) return
    const update = () => {
      const { clientWidth, clientHeight } = viewportElement
      if (clientWidth > 0) setSize({ width: clientWidth, height: clientHeight })
    }
    update()
    const observer = new ResizeObserver(update)
    observer.observe(viewportElement)
    return () => observer.disconnect()
  }, [viewportElement])
  const [zoomOwn, setZoomOwn] = React.useState<DocxZoom>(defaultZoom)
  const zoom = zoomProp ?? zoomOwn
  const setZoom = (next: DocxZoom) => {
    setZoomOwn(next)
    onZoomChange?.(next)
  }
  // the widest page decides (a landscape section is wider), so nothing scrolls sideways
  const widest = React.useMemo(
    () => Math.max(layout.pageWidthPx, ...docxSections(editor.model).map((section) => (section.widthIn ?? 0) * 96)),
    [layout.pageWidthPx, editor.model]
  )
  const pageWidth = widest + (commentsShown || changesShown ? GUTTER_PX : 0)
  const fitWidth = size.width ? clampZoom(((size.width - FIT_PADDING) / pageWidth) * 100) : 100
  const fitPage = size.height ? clampZoom(Math.min(fitWidth, ((size.height - FIT_PADDING) / layout.pageHeightPx) * 100)) : fitWidth
  const percent = zoom === "fit-width" ? fitWidth : zoom === "fit-page" ? fitPage : clampZoom(zoom)
  const scale = percent / 100

  const pageStore = React.useMemo(createPageStore, [])
  React.useEffect(() => {
    pageStore.set(1)
    viewport.current?.scrollTo({ top: 0, left: 0 })
  }, [editor.documentLoadNonce, pageStore])
  const onPageChangeRef = React.useRef(onPageChange)
  onPageChangeRef.current = onPageChange
  React.useEffect(
    () => pageStore.subscribe(() => onPageChangeRef.current?.(pageStore.get(), pages)),
    [pageStore, pages]
  )

  const trackPage = React.useCallback(() => {
    const el = viewport.current
    if (!el || !pages) return
    const box = el.getBoundingClientRect()
    const middle = box.top + box.height / 2
    let best = 1
    let distance = Number.POSITIVE_INFINITY
    for (const page of pageWrappers(el)) {
      const r = page.getBoundingClientRect()
      const d = r.top <= middle && r.bottom >= middle ? 0 : Math.min(Math.abs(r.top - middle), Math.abs(r.bottom - middle))
      if (d < distance) {
        distance = d
        best = Number(page.dataset.index) + 1
      }
    }
    pageStore.set(best)
  }, [pageStore, pages])

  React.useEffect(() => {
    const el = viewport.current
    if (!el || !pages) return
    let frame = 0
    const onScroll = () => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(trackPage)
    }
    frame = requestAnimationFrame(trackPage)
    el.addEventListener("scroll", onScroll, { passive: true })
    return () => {
      cancelAnimationFrame(frame)
      el.removeEventListener("scroll", onScroll)
    }
  }, [pages, trackPage])

  const stride = (layout.pageHeightPx + layout.viewportDefaults.pageGapPx) * scale
  const goToPage = React.useCallback(
    (page: number) => {
      const el = viewport.current
      if (!el) return
      const index = Math.min(Math.max(page, 1), Math.max(pages, 1)) - 1
      pageStore.set(index + 1)
      const wrapper = el.querySelector<HTMLElement>(`[data-docx-page-wrapper="true"][data-index="${index}"]`)
      const top = wrapper
        ? wrapper.getBoundingClientRect().top - el.getBoundingClientRect().top + el.scrollTop - 16
        : index * stride
      el.scrollTo({ top: Math.max(0, top) })
    },
    [pageStore, pages, stride]
  )

  /** Scrolls until the body node `nodeIndex` is drawn, then to it (or to `range` in it). */
  const reveal = React.useCallback(
    async (nodeIndex: number, range?: () => Range | undefined) => {
      const el = viewport.current
      if (!el) return
      const count = Math.max(1, latest.current.model.nodes.length)
      for (let attempt = 0; attempt < 10; attempt++) {
        const target = nodeElement(el, nodeIndex)
        if (target) {
          const rect = range?.()?.getBoundingClientRect() ?? target.getBoundingClientRect()
          const box = el.getBoundingClientRect()
          if (rect.top < box.top + 24 || rect.bottom > box.bottom - 24)
            el.scrollTo({ top: Math.max(0, el.scrollTop + rect.top - box.top - box.height / 3) })
          return
        }
        const drawn = pageWrappers(el)
          .map((page) => {
            const nodes = Array.from(page.querySelectorAll("[data-docx-paragraph-node-index]"), (n) =>
              Number(n.getAttribute("data-docx-paragraph-node-index"))
            )
            return { index: Number(page.dataset.index), min: Math.min(...nodes), max: Math.max(...nodes) }
          })
          .filter((p) => Number.isFinite(p.min))
        let page: number
        if (attempt === 0 || !drawn.length) page = Math.floor((nodeIndex / count) * Math.max(pages, 1))
        else if (nodeIndex < Math.min(...drawn.map((p) => p.min))) page = Math.min(...drawn.map((p) => p.index)) - 1
        else if (nodeIndex > Math.max(...drawn.map((p) => p.max))) page = Math.max(...drawn.map((p) => p.index)) + 1
        else page = (drawn.find((p) => nodeIndex <= p.max) ?? drawn[0]).index
        goToPage(Math.max(0, page) + 1)
        await sleepFrame()
      }
    },
    [goToPage, pages]
  )

  // ---- search ----
  const [query, setQuery] = React.useState("")
  const [matches, setMatches] = React.useState<DocxMatch[]>([])
  const [at, setAt] = React.useState(0)
  React.useEffect(() => {
    const id = window.setTimeout(() => {
      setMatches(searchDocx(editor.model, query))
      setAt(0)
    }, 150)
    return () => window.clearTimeout(id)
  }, [editor.model, query])
  const currentMatch = matches[at]
  const nth = React.useMemo(() => {
    if (!currentMatch) return 0
    let n = 0
    for (let i = at - 1; i >= 0 && matches[i].nodeIndex === currentMatch.nodeIndex; i--) n++
    return n
  }, [matches, at, currentMatch])
  const paint = React.useCallback(() => {
    const el = viewport.current
    if (!el || !query.trim()) {
      clearHighlights()
      return undefined
    }
    const { all, chosen } = findRanges(el, query, currentMatch ? { nodeIndex: currentMatch.nodeIndex, nth } : undefined)
    paintHighlights(all, chosen)
    return chosen
  }, [query, currentMatch, nth])
  React.useEffect(() => {
    const el = viewport.current
    if (!el) return
    paint()
    if (!query.trim()) return
    let frame = 0
    const observer = new MutationObserver(() => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => paint())
    })
    observer.observe(el, { childList: true, subtree: true, characterData: true })
    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
    }
  }, [paint, query, viewportElement, loading])
  React.useEffect(() => clearHighlights, [])
  const goToMatch = (index: number) => {
    if (!matches.length) return
    const next = (index + matches.length) % matches.length
    setAt(next)
    const match = matches[next]
    let n = 0
    for (let i = next - 1; i >= 0 && matches[i].nodeIndex === match.nodeIndex; i--) n++
    void reveal(match.nodeIndex, () => {
      const el = viewport.current
      return el ? findRanges(el, query, { nodeIndex: match.nodeIndex, nth: n }).chosen : undefined
    })
  }
  const searchRef = React.useRef<HTMLSpanElement>(null)
  const lastQuery = React.useRef("")

  // ---- panels ----
  const [panel, setPanel] = React.useState<DocxPanel>(defaultPanel ?? "pages")
  const [panelOpen, setPanelOpen] = React.useState(defaultPanel != null)
  const [shellRef, shellWidth] = useElementWidth<HTMLDivElement>()
  const inline = useInlineThumbnailSidebar(shellWidth)
  const [annotation, setAnnotation] = React.useState<string>()
  const outline = React.useMemo(() => docxOutline(editor.model), [editor.model])
  const sections = React.useMemo(() => docxSections(editor.model), [editor.model])
  const [nearNode, setNearNode] = React.useState<number>()

  const showPanel = (next: DocxPanel) => {
    if (panelOpen && panel === next) setPanelOpen(false)
    else {
      setPanel(next)
      setPanelOpen(true)
    }
  }

  // ---- keys: find, save, zoom ----
  const root = React.useRef<HTMLDivElement>(null)
  React.useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const el = root.current
      if (!el || !(event.ctrlKey || event.metaKey) || event.altKey) return
      if (!el.contains(document.activeElement) && document.activeElement !== document.body) return
      const key = event.key.toLowerCase()
      if (key === "f") {
        event.preventDefault()
        searchRef.current?.querySelector("input")?.focus()
      } else if (key === "s" && editing) {
        event.preventDefault()
        void give("save")
      } else if (key === "=" || key === "+") {
        event.preventDefault()
        setZoom(stepZoom(percent, 1))
      } else if (key === "-") {
        event.preventDefault()
        setZoom(stepZoom(percent, -1))
      } else if (key === "0") {
        event.preventDefault()
        setZoom("fit-width")
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  })

  useQuietPaddingWarning(!loading && !error)
  const spinner = useDelayed(loading, LOADING_DELAY_MS)

  const renderComment = React.useMemo(() => createDocxCommentCardRenderer(), [])
  const renderChange = React.useMemo(() => createDocxTrackedChangeCardRenderer(), [])
  const virtualization = React.useMemo(
    () => ({ enabled: true, overscan: 1, scrollElement: viewportElement, zoomScale: scale }),
    [viewportElement, scale]
  )

  const moreEntries = (): MenuEntry[] => [
    { label: "Comments in the margin", checked: showComments, disabled: disabled || !comments.length, onSelect: () => setShowComments(!showComments) },
    {
      label: editing ? "Show changes (read-only while shown)" : "Show changes",
      checked: showTrackedChanges,
      disabled: disabled || !trackedChanges.length,
      onSelect: () => setShowTrackedChanges(!showTrackedChanges),
    },
    ...(readOnly
      ? [{ label: "Dark pages", icon: <Icons.Moon />, checked: darkPages, disabled, onSelect: () => setDarkOwn(!darkPages) } satisfies MenuEntry]
      : []),
    "separator",
    { label: "Pages", checked: panelOpen && panel === "pages", onSelect: () => showPanel("pages") },
    { label: "Outline", checked: panelOpen && panel === "outline", onSelect: () => showPanel("outline") },
    { label: "Sections", checked: panelOpen && panel === "sections", onSelect: () => showPanel("sections") },
    { label: "Comments and changes", checked: panelOpen && panel === "comments", onSelect: () => showPanel("comments") },
    ...((showDownload || showUpload) ? ["separator" as const] : []),
    ...(showUpload ? [{ label: "Open…", onSelect: () => void open() }] : []),
    ...(showDownload ? [{ label: busy ? "Preparing…" : "Download", disabled: disabled || busy, onSelect: () => void give("download") }] : []),
  ]
  const more = useContextMenu(moreEntries)

  const zoomOptions = [
    { value: "fit-width", label: `Fit width${zoom === "fit-width" ? ` (${percent}%)` : ""}` },
    { value: "fit-page", label: `Whole page${zoom === "fit-page" ? ` (${percent}%)` : ""}` },
    ...(!ZOOMS.includes(percent) && typeof zoom === "number" ? [{ value: String(percent), label: `${percent}%` }] : []),
    ...ZOOMS.map((z) => ({ value: String(z), label: `${z}%` })),
  ]

  const toolbar = showToolbar && (
    <Toolbar aria-label={`${ariaLabel}: view`} className="nt-docx-toolbar">
      <ToolbarButton
        icon={<SidebarIcon />}
        label="Pages and outline"
        pressed={panelOpen}
        disabled={disabled}
        onClick={() => setPanelOpen(!panelOpen)}
      />
      {editing && (
        <>
          <ToolbarButton
            icon={<Icons.NewFile />}
            label="New document"
            onClick={() => setOpened({ name: "Document.docx", key: Date.now(), from: [src, data] })}
          />
          {showUpload && <ToolbarButton icon={<Icons.Open />} label="Open" onClick={() => void open()} />}
          <ToolbarButton icon={<Icons.Save />} label={dirty ? "Save (changed)" : "Save"} data-dirty={dirty || undefined} disabled={disabled || busy} onClick={() => void give("save")} />
          <ToolbarSeparator />
          <ToolbarButton icon={<Icons.Undo />} label="Undo" disabled={readOnly || !editor.canUndo} onClick={() => editor.undo()} />
          <ToolbarButton icon={<Icons.Redo />} label="Redo" disabled={readOnly || !editor.canRedo} onClick={() => editor.redo()} />
          <ToolbarSeparator />
        </>
      )}
      <PageControl store={pageStore} pages={pages} disabled={disabled} onGo={goToPage} />
      <ToolbarSpacer />
      <span ref={searchRef} className="nt-docx-search">
        <SearchField
          aria-label="Find in the document"
          placeholder="Find"
          value={query}
          disabled={disabled}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault()
              // the first Enter shows the first result, each next one the next (Shift: the one before)
              const fresh = query !== lastQuery.current
              lastQuery.current = query
              goToMatch(fresh ? at : event.shiftKey ? at - 1 : at + 1)
            }
          }}
        />
        {query.trim() && (
          <span className="nt-docx-search-count" aria-live="polite">
            {matches.length ? `${at + 1} of ${matches.length}` : "No results"}
          </span>
        )}
      </span>
      {query.trim() && matches.length > 0 && (
        <>
          <ToolbarButton icon={<Icons.ChevronUp />} label="Previous result" onClick={() => goToMatch(at - 1)} />
          <ToolbarButton icon={<Icons.ChevronDown />} label="Next result" onClick={() => goToMatch(at + 1)} />
        </>
      )}
      <ToolbarSeparator />
      <ToolbarButton icon={<Icons.ZoomOut />} label="Zoom out" disabled={disabled || percent <= MIN_ZOOM} onClick={() => setZoom(stepZoom(percent, -1))} />
      <Select
        aria-label="Zoom"
        className="nt-docx-zoom-select"
        disabled={disabled}
        value={typeof zoom === "number" ? String(percent) : zoom}
        options={zoomOptions}
        onChange={(event) => {
          const value = event.target.value
          setZoom(value === "fit-width" || value === "fit-page" ? value : Number(value))
        }}
      />
      <ToolbarButton icon={<Icons.ZoomIn />} label="Zoom in" disabled={disabled || percent >= MAX_ZOOM} onClick={() => setZoom(stepZoom(percent, 1))} />
      <ToolbarSeparator />
      <ToolbarButton
        icon={<Icons.CommentIcon />}
        label="Comments"
        pressed={showComments && comments.length > 0}
        disabled={disabled || !comments.length}
        onClick={() => setShowComments(!showComments)}
      />
      {editing && showReadOnlyToggle && (
        <ToolbarButton icon={<Icons.Lock />} label={locked ? "Allow editing" : "Read only"} pressed={locked} disabled={disabled} onClick={() => setLocked(!locked)} />
      )}
      {toolbarActions}
      <ToolbarButton icon={<Icons.More />} label="More" disabled={!hasDocument} onClick={(event) => more.onContextMenu(event)} />
    </Toolbar>
  )
  const panelBody = (() => {
    if (loading) return <DocumentViewerSidebarSkeleton />
    switch (panel) {
      case "pages":
        return (
          <ThumbnailList
            editor={viewerEditor}
            reportedPages={reportedPages}
            pages={pages}
            store={pageStore}
            open={panelOpen && panel === "pages" && !disabled}
            onSelect={(n) => {
              if (!inline) setPanelOpen(false)
              goToPage(n)
            }}
          />
        )
      case "outline":
        return (
          <div className="nt-docx-sidebar-scroll">
            <DocumentViewerSidebarList
              empty="No headings"
              active={nearNode !== undefined ? String(nearNode) : undefined}
              entries={outline.map((h) => ({ id: String(h.nodeIndex), label: h.text, level: Math.max(0, h.level - 1) }))}
              onSelect={(id) => {
                setNearNode(Number(id))
                if (!inline) setPanelOpen(false)
                void reveal(Number(id))
              }}
            />
          </div>
        )
      case "sections":
        return (
          <div className="nt-docx-sidebar-scroll">
            <DocumentViewerSidebarList
              entries={sections.map((s) => ({
                id: String(s.index),
                label: `${s.index + 1}. ${s.title}`,
                description: [
                  s.orientation === "landscape" ? "Landscape" : "Portrait",
                  s.widthIn && s.heightIn ? `${s.widthIn.toFixed(1)} × ${s.heightIn.toFixed(1)} in` : "",
                  s.headers || s.footers ? "header, footer" : "",
                ].filter(Boolean).join(" · "),
              }))}
              onSelect={(id) => {
                if (!inline) setPanelOpen(false)
                void reveal(sections[Number(id)]?.startNodeIndex ?? 0)
              }}
            />
          </div>
        )
      case "comments":
        return (
          <div className="nt-docx-sidebar-scroll">
            <DocxAnnotationList
              comments={comments}
              changes={trackedChanges}
              active={annotation}
              onSelect={(a) => {
                setAnnotation(a.type === "comment" ? a.comment.id : a.change.id)
                if (!inline) setPanelOpen(false)
                void reveal(a.type === "comment" ? a.comment.nodeIndex : a.change.nodeIndex)
              }}
            />
          </div>
        )
    }
  })()

  return (
    <div
      ref={root}
      className={cn("nt-docx", className)}
      data-mode={mode}
      data-dark-pages={darkPages || undefined}
      aria-label={ariaLabel}
      role="region"
      style={{ height: height ?? (style?.height != null || style?.flex != null ? undefined : 640), ...style }}
      {...rest}
    >
      {toolbar}
      {editing && showToolbar && <FormatBar editor={editor} disabled={disabled || readOnly} dialogs={dialogs} />}
      {busy && <ProgressBar aria-label="Preparing the document" className="nt-docx-busy" />}
      <div ref={shellRef} className="nt-docx-body">
        <DocumentViewerSidebar
          aria-label="Pages and outline"
          open={panelOpen && !disabled}
          inline={inline}
          width={panel === "pages" ? 168 : 232}
          tabs={[
            { value: "pages", label: "Pages", icon: <Icons.Pages /> },
            { value: "outline", label: "Outline", icon: <Icons.Outline /> },
            { value: "sections", label: "Sections", icon: <Icons.Sections /> },
            { value: "comments", label: "Comments", icon: <Icons.CommentIcon /> },
          ]}
          tab={panel}
          onTabChange={(value) => setPanel(value as DocxPanel)}
        >
          {panelBody}
        </DocumentViewerSidebar>
        <div
          ref={setViewport}
          className="nt-docx-scroll"
          tabIndex={0}
          aria-label={`${shownName}, ${pages ? `${pages} pages` : "loading"}`}
          onMouseDown={(event) => {
            const target = event.target as Element
            if (target.closest('a[href], button, input, select, textarea, [contenteditable="true"], [role="button"]')) return
            event.currentTarget.focus({ preventScroll: true })
          }}
        >
          {!hasDocument ? (
            <div className="nt-docx-empty">
              <div className="nt-docx-empty-card">
                <Icons.DocIcon />
                <div className="nt-docx-empty-title">Open a Word document to see it here</div>
                <div className="nt-docx-empty-text">Give the viewer a .docx (its src or data), or open one.</div>
                {showUpload && (
                  <Button variant="accent" onClick={() => void open()}>
                    Open a document…
                  </Button>
                )}
              </div>
            </div>
          ) : error ? (
            <div className="nt-docx-empty" role="alert">
              <div className="nt-docx-empty-card" data-error="">
                <div className="nt-docx-empty-title">This document can't be shown</div>
                <div className="nt-docx-empty-text">{error}</div>
                {showUpload && <Button onClick={() => void open()}>Open another…</Button>}
              </div>
            </div>
          ) : loading ? (
            <div className="nt-docx-empty" aria-busy="true">
              {spinner && <ProgressBar aria-label="Opening the document" className="nt-docx-loading-bar" />}
            </div>
          ) : (
            <div className="nt-docx-stage">
              <div className="nt-docx-zoom" style={{ zoom: scale }}>
                <DocxEditorViewer
                  editor={viewerEditor}
                  mode={readOnly ? "read-only" : "edit"}
                  showComments={commentsShown}
                  showTrackedChanges={changesShown}
                  renderCommentCard={renderComment}
                  renderTrackedChangeCard={renderChange}
                  renderContextMenu={editing ? (menu) => <DocxMenu {...menu} platform={platform} host={root.current} /> : undefined}
                  pageBackgroundColor={darkPages ? "#121212" : undefined}
                  pageGapBackgroundColor="transparent"
                  pageVirtualization={virtualization}
                  deferInitialPaginationPaint={false}
                  onPageCountChange={(count) => setReportedPages(Math.max(1, Math.round(count || 1)))}
                />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/** "Page 3 of 12": click the number to type one. */
function PageControl({ store, pages, disabled, onGo }: { store: PageStore; pages: number; disabled: boolean; onGo: (page: number) => void }) {
  const page = usePage(store)
  const shown = pages ? Math.min(page, pages) : 1
  const [draft, setDraft] = React.useState<string>()
  return (
    <span className="nt-docx-page">
      <span className="nt-docx-page-label">Page</span>
      <input
        className="nt-docx-page-input"
        aria-label="Page number"
        inputMode="numeric"
        disabled={disabled || !pages}
        value={draft ?? String(shown)}
        onFocus={(event) => {
          setDraft(String(shown))
          event.currentTarget.select()
        }}
        onBlur={() => setDraft(undefined)}
        onChange={(event) => {
          const value = event.target.value.replace(/[^0-9]/g, "")
          setDraft(value)
          const n = Number(value)
          if (value && Number.isInteger(n)) onGo(Math.min(Math.max(n, 1), Math.max(pages, 1)))
        }}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === "Escape") event.currentTarget.blur()
          else if (event.key === "ArrowUp") {
            event.preventDefault()
            onGo(Math.max(1, shown - 1))
          } else if (event.key === "ArrowDown") {
            event.preventDefault()
            onGo(Math.min(pages, shown + 1))
          }
        }}
      />
      <span className="nt-docx-page-label">of {pages || "–"}</span>
    </span>
  )
}

/** The page thumbnails: only those in view (and a few next to them) are drawn. */
function ThumbnailList({
  editor,
  reportedPages,
  pages,
  store,
  open,
  onSelect,
}: {
  editor: DocxEditorController
  reportedPages: number
  pages: number
  store: PageStore
  open: boolean
  onSelect: (page: number) => void
}) {
  const [window_, setWindow] = React.useState<{ visiblePageIndexes: number[]; prefetchPageIndexes: number[] }>({
    visiblePageIndexes: [],
    prefetchPageIndexes: [],
  })
  const thumbEditor = React.useMemo<DocxEditorController>(
    () => ({ ...editor, totalPages: Math.max(editor.totalPages, reportedPages) }),
    [editor, reportedPages]
  )
  const options = React.useMemo(
    () => ({
      disabled: !open,
      pixelRatio: 2,
      renderWindow: window_,
      resolution: { maxWidth: THUMB_WIDTH, maxHeight: THUMB_WIDTH * 1.42 },
    }),
    [open, window_]
  )
  const { thumbnails } = useDocxViewerThumbnails(thumbEditor, options)
  const page = usePage(store)
  const shown = React.useMemo(() => thumbnails.slice(0, pages || 0), [thumbnails, pages])
  const scroller = React.useRef<HTMLDivElement>(null)
  const id = React.useId()
  const virtualizer = useVirtualizer({
    count: shown.length,
    estimateSize: () => THUMB_ROW,
    getItemKey: (index) => shown[index]?.pageIndex ?? index,
    getScrollElement: () => scroller.current,
    overscan: 3,
  })
  const items = virtualizer.getVirtualItems()
  const signature = items.map((item) => item.index).join(",")

  React.useEffect(() => {
    const same = (a: number[], b: number[]) => a.length === b.length && a.every((v, i) => v === b[i])
    const set = (next: { visiblePageIndexes: number[]; prefetchPageIndexes: number[] }) =>
      setWindow((current) =>
        same(current.visiblePageIndexes, next.visiblePageIndexes) && same(current.prefetchPageIndexes, next.prefetchPageIndexes) ? current : next
      )
    if (!open || !shown.length) {
      set({ visiblePageIndexes: [], prefetchPageIndexes: [] })
      return
    }
    const visible = items.map((item) => shown[item.index]?.pageIndex).filter((i): i is number => i !== undefined)
    const first = items[0]?.index ?? 0
    const last = items[items.length - 1]?.index ?? first
    const prefetch: number[] = []
    for (let i = Math.max(0, first - THUMB_PREFETCH); i <= Math.min(shown.length - 1, last + THUMB_PREFETCH); i++) {
      const pageIndex = shown[i]?.pageIndex
      if (pageIndex !== undefined && !visible.includes(pageIndex)) prefetch.push(pageIndex)
    }
    set({ visiblePageIndexes: visible, prefetchPageIndexes: prefetch })
    // the window changes with the items in view (signature)
  }, [open, shown, signature])

  React.useEffect(() => {
    if (!open || page < 1 || !shown.length) return
    virtualizer.scrollToIndex(Math.min(page - 1, shown.length - 1), { align: "auto" })
  }, [open, page, shown.length, virtualizer])

  return (
    <div
      ref={scroller}
      className="nt-docx-sidebar-scroll nt-docx-thumbs"
      role="listbox"
      tabIndex={0}
      aria-label="Pages"
      aria-activedescendant={shown.length ? `${id}-page-${page}` : undefined}
      onKeyDown={(event) => {
        if (!pages) return
        const next =
          event.key === "ArrowDown" ? Math.min(pages, page + 1)
            : event.key === "ArrowUp" ? Math.max(1, page - 1)
              : event.key === "Home" ? 1
                : event.key === "End" ? pages
                  : undefined
        if (next === undefined) return
        event.preventDefault()
        onSelect(next)
      }}
    >
      <div style={{ position: "relative", height: virtualizer.getTotalSize() + 16 }}>
        {items.map((item) => {
          const thumb = shown[item.index]
          if (!thumb) return null
          return (
            <div
              key={item.key}
              ref={virtualizer.measureElement}
              data-index={item.index}
              className="nt-docx-thumb-row"
              style={{ transform: `translateY(${item.start + 8}px)` }}
            >
              <Thumbnail id={`${id}-page-${thumb.pageNumber}`} thumb={thumb} active={thumb.pageNumber === page} pages={pages} onSelect={onSelect} />
            </div>
          )
        })}
      </div>
    </div>
  )
}

function Thumbnail({ id, thumb, active, pages, onSelect }: { id: string; thumb: DocxPageThumbnailItem; active: boolean; pages: number; onSelect: (page: number) => void }) {
  return (
    <div
      id={id}
      role="option"
      aria-selected={active}
      aria-label={`Page ${thumb.pageNumber}`}
      aria-posinset={thumb.pageNumber}
      aria-setsize={pages}
      data-active={active || undefined}
      className="nt-docx-thumb"
      onClick={() => onSelect(thumb.pageNumber)}
    >
      <span className="nt-docx-thumb-page" data-status={thumb.status} style={{ aspectRatio: String(thumb.aspectRatio || 0.77) }}>
        <canvas ref={thumb.canvasRef} width={thumb.pixelWidthPx} height={thumb.pixelHeightPx} />
      </span>
      <span className="nt-docx-thumb-number">{thumb.pageNumber}</span>
    </div>
  )
}

/** The editor's second row: styles, fonts, emphasis, colours, alignment, lists, spacing, borders, links, tables, pictures. */
function FormatBar({ editor, disabled, dialogs }: { editor: DocxEditorController; disabled: boolean; dialogs: ReturnType<typeof useDialogsOrNull> }) {
  const { paragraphStyles, selectedParagraphStyleId, setParagraphStyle } = useDocxParagraphStyles(editor)
  const { lineSpacing, setLineSpacing } = useDocxLineSpacing(editor)
  const { borderContext, applyBorderPreset } = useDocxBorders(editor)
  const run = editor.selectedRunStyle ?? editor.pendingRunStyle
  const align = editor.selectedParagraph?.style?.align ?? "left"
  // keep the document's selection when a button is pressed (the library reads it on the click)
  const keep = (event: React.MouseEvent) => event.preventDefault()
  const tool = (label: string, icon: React.ReactNode, onClick: () => void, pressed?: boolean, off?: boolean) => (
    <ToolbarButton icon={icon} label={label} pressed={pressed} disabled={disabled || off} onMouseDown={keep} onClick={onClick} />
  )
  const fontSize = run?.fontSizePt
  const font = run?.fontFamily
  const styleOptions = paragraphStyles.map((s) => ({ value: s.id, label: s.name }))
  if (selectedParagraphStyleId && !styleOptions.some((o) => o.value === selectedParagraphStyleId))
    styleOptions.unshift({ value: selectedParagraphStyleId, label: selectedParagraphStyleId })
  const spacing = SPACINGS.find((s) => Math.abs(s - lineSpacing.multiple) < 0.01)

  const link = async () => {
    const current = editor.selectedLink ?? ""
    const value = dialogs
      ? await dialogs.prompt({ title: "Link", label: "Address", placeholder: "https://", value: current, confirm: "Apply" })
      : window.prompt("Link address", current)
    if (value === null) return
    editor.setLink(value.trim() || undefined)
  }

  return (
    <Toolbar aria-label="Formatting" className="nt-docx-toolbar nt-docx-format">
      <Select
        aria-label="Paragraph style"
        className="nt-docx-style-select"
        disabled={disabled || !styleOptions.length}
        value={selectedParagraphStyleId ?? ""}
        onChange={(event) => setParagraphStyle(event.target.value || undefined)}
        options={[{ value: "", label: "Style" }, ...styleOptions]}
      />
      <Select
        aria-label="Font"
        className="nt-docx-font-select"
        disabled={disabled}
        value={font && FONTS.includes(font) ? font : font ? font : ""}
        onChange={(event) => event.target.value && editor.setFontFamily(event.target.value)}
        options={[
          { value: "", label: "Font" },
          ...(font && !FONTS.includes(font) ? [{ value: font, label: font }] : []),
          ...FONTS.map((f) => ({ value: f, label: f })),
        ]}
      />
      <Select
        aria-label="Font size"
        className="nt-docx-size-select"
        disabled={disabled}
        value={fontSize ? String(fontSize) : ""}
        onChange={(event) => event.target.value && editor.setFontSize(Number(event.target.value))}
        options={[
          { value: "", label: "Size" },
          ...(fontSize && !SIZES.includes(fontSize) ? [{ value: String(fontSize), label: String(fontSize) }] : []),
          ...SIZES.map((s) => ({ value: String(s), label: String(s) })),
        ]}
      />
      <ToolbarSeparator />
      {tool("Bold", <Icons.Bold />, () => editor.toggleBold(), Boolean(run?.bold))}
      {tool("Italic", <Icons.Italic />, () => editor.toggleItalic(), Boolean(run?.italic))}
      {tool("Underline", <Icons.Underline />, () => editor.toggleUnderline(), Boolean(run?.underline))}
      {tool("Strikethrough", <Icons.Strike />, () => editor.toggleStrike(), Boolean(run?.strike))}
      {tool("Superscript", <Icons.Superscript />, () => editor.toggleSuperscript(), run?.verticalAlign === "superscript")}
      {tool("Subscript", <Icons.Subscript />, () => editor.toggleSubscript(), run?.verticalAlign === "subscript")}
      <ColorSelect label="Text colour" icon={<Icons.TextColor />} colors={COLORS} value={run?.color ?? ""} disabled={disabled} onPick={(c) => editor.setTextColor(c || undefined)} />
      <ColorSelect label="Highlight" icon={<Icons.Highlight />} colors={HIGHLIGHTS} value={run?.highlight ?? ""} disabled={disabled} onPick={(c) => editor.setHighlight(c || undefined)} />
      <ToolbarSeparator />
      {tool("Align left", <Icons.AlignLeft />, () => editor.setAlignment("left"), align === "left")}
      {tool("Center", <Icons.AlignCenter />, () => editor.setAlignment("center"), align === "center")}
      {tool("Align right", <Icons.AlignRight />, () => editor.setAlignment("right"), align === "right")}
      {tool("Justify", <Icons.AlignJustify />, () => editor.setAlignment("justify"), align === "justify")}
      <ToolbarSeparator />
      {tool("Bullets", <Icons.Bullets />, () => editor.toggleList("unordered"), editor.hasUnorderedList)}
      {tool("Numbering", <Icons.Numbers />, () => editor.toggleList("ordered"), editor.hasOrderedList)}
      {tool("Decrease indent", <Icons.Outdent />, () => void editor.adjustSelectedListDepth(-1), undefined, !(editor.hasOrderedList || editor.hasUnorderedList))}
      {tool("Increase indent", <Icons.Indent />, () => void editor.adjustSelectedListDepth(1), undefined, !(editor.hasOrderedList || editor.hasUnorderedList))}
      <Select
        aria-label="Line spacing"
        className="nt-docx-spacing-select"
        disabled={disabled}
        value={spacing ? String(spacing) : ""}
        onChange={(event) => event.target.value && setLineSpacing(Number(event.target.value))}
        options={[
          ...(spacing ? [] : [{ value: "", label: lineSpacing.multiple ? `${lineSpacing.multiple.toFixed(2)}` : "Spacing" }]),
          ...SPACINGS.map((s) => ({ value: String(s), label: `${s} lines` })),
        ]}
      />
      <Select
        aria-label={borderContext === "table" ? "Table borders" : "Paragraph borders"}
        className="nt-docx-border-select"
        disabled={disabled}
        value=""
        onChange={(event) => event.target.value && applyBorderPreset(event.target.value as DocxBorderPreset)}
        options={[{ value: "", label: "Borders" }, ...BORDERS.map(([value, label]) => ({ value, label }))]}
      />
      <ToolbarSeparator />
      {tool(editor.selectedLink ? "Edit link" : "Link", <Icons.LinkIcon />, () => void link(), Boolean(editor.selectedLink))}
      {tool("Insert table", <Icons.TableIcon />, () => editor.insertTable())}
      {tool("Insert picture", <Icons.ImageIcon />, () => {
        void pickFile("image/png,image/jpeg,image/gif,image/webp,image/bmp").then((file) => file && editor.insertImageFile(file))
      })}
    </Toolbar>
  )
}

/** A colour from a short list, as a Select whose face is the icon underlined in the colour. */
function ColorSelect({ label, icon, colors, value, disabled, onPick }: { label: string; icon: React.ReactNode; colors: [string, string][]; value: string; disabled: boolean; onPick: (color: string) => void }) {
  const known = colors.some(([c]) => c.toLowerCase() === value.toLowerCase())
  return (
    <span className="nt-docx-color" title={label} data-disabled={disabled || undefined}>
      <span className="nt-docx-color-face" aria-hidden="true" style={{ ["--nt-docx-swatch" as string]: value ? cssColor(value) : "transparent" }}>
        {icon}
      </span>
      <select
        aria-label={label}
        disabled={disabled}
        value={known ? colors.find(([c]) => c.toLowerCase() === value.toLowerCase())![0] : ""}
        onChange={(event) => onPick(event.target.value)}
      >
        {colors.map(([c, name]) => (
          <option key={c || "none"} value={c}>
            {name}
          </option>
        ))}
      </select>
    </span>
  )
}

const WORD_COLORS: Record<string, string> = { yellow: "#ffff00", green: "#00ff00", cyan: "#00ffff", magenta: "#ff00ff", red: "#ff0000", blue: "#0000ff", lightgray: "#d3d3d3" }
const cssColor = (value: string) => WORD_COLORS[value.toLowerCase()] ?? value

/** The editor's right-click menu (cut, copy, paste, table and picture commands), drawn as the kit's menus are. */
function DocxMenu({ actions, runAction, closeMenu, position, platform, host }: DocxContextMenuRenderProps & { platform: string; host: HTMLElement | null }) {
  const ref = React.useRef<HTMLDivElement>(null)
  const [activeIndex, setActive] = React.useState(-1)
  const [sub, setSub] = React.useState<string>()
  const flat = actions
  // Where it fits: by the pointer, flipped where the window ends (drawn outside the zoomed pages, at true size).
  React.useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const { width, height } = el.getBoundingClientRect()
    el.style.left = `${position.x + width > window.innerWidth - 4 ? Math.max(4, position.x - width) : position.x}px`
    el.style.top = `${position.y + height > window.innerHeight - 4 ? Math.max(4, position.y - height) : position.y}px`
    el.style.visibility = "visible"
    el.focus({ preventScroll: true })
  }, [position.x, position.y, sub])
  const choose = (action: DocxContextMenuAction) => {
    if (action.disabled) return
    if (action.children?.length) {
      setSub(sub === String(action.id) ? undefined : String(action.id))
      return
    }
    runAction(action.id)
    closeMenu()
  }
  const shortcut = (s: string | undefined) => (s && platform === "mac" ? s.replace(/Ctrl\+|Mod\+/g, "⌘").replace(/Shift\+/g, "⇧") : s?.replace(/Mod\+/g, "Ctrl+"))
  const enabled = flat.flatMap((a, i) => (a.disabled ? [] : [i]))
  const item = (action: DocxContextMenuAction, index: number, nested = false) => (
    <React.Fragment key={String(action.id)}>
      {action.separatorBefore && <div role="separator" className="nt-menu-separator" />}
      <div
        role={action.checked != null ? "menuitemcheckbox" : "menuitem"}
        aria-checked={action.checked}
        aria-disabled={action.disabled || undefined}
        aria-haspopup={action.children?.length ? "menu" : undefined}
        aria-expanded={action.children?.length ? sub === String(action.id) : undefined}
        className="nt-menu-item"
        data-destructive={action.destructive || undefined}
        data-active={(!nested && index === activeIndex) || undefined}
        onPointerEnter={() => !nested && setActive(action.disabled ? -1 : index)}
        // on the press, not the click: the library closes its menu on a press outside its own box, and this is
        // drawn outside it
        onPointerDown={(event) => {
          event.preventDefault()
          event.stopPropagation()
          choose(action)
        }}
      >
        <span className="nt-menu-gutter" aria-hidden="true">
          {action.checked ? (
            <svg viewBox="0 0 12 12" fill="none">
              <path d="M2.5 6.2 5 8.6l4.5-5.2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          ) : null}
        </span>
        <span className="nt-menu-label">{action.label}</span>
        {action.children?.length ? <span className="nt-menu-shortcut">›</span> : <span className="nt-menu-shortcut">{shortcut(action.shortcut)}</span>}
      </div>
      {action.children?.length && sub === String(action.id) ? (
        <div className="nt-docx-submenu" role="menu">{action.children.map((child, i) => item(child, i, true))}</div>
      ) : null}
    </React.Fragment>
  )
  const menu = (
    <div
      ref={ref}
      role="menu"
      tabIndex={-1}
      className="nt-menu nt-docx-menu"
      style={{ left: position.x, top: position.y }}
      data-gutter="true"
      onMouseDown={(event) => event.preventDefault()}
      onKeyDown={(event) => {
        const step = (by: number) => {
          if (!enabled.length) return
          const at = enabled.indexOf(activeIndex)
          setActive(enabled[at === -1 ? (by > 0 ? 0 : enabled.length - 1) : (at + by + enabled.length) % enabled.length])
        }
        if (event.key === "ArrowDown") step(1)
        else if (event.key === "ArrowUp") step(-1)
        else if (event.key === "Escape" || event.key === "Tab") closeMenu()
        else if ((event.key === "Enter" || event.key === " ") && flat[activeIndex]) choose(flat[activeIndex])
        else return
        event.preventDefault()
      }}
    >
      {flat.map((action, index) => item(action, index))}
    </div>
  )
  // in the kit's root (its platform and theme), outside the pages' zoom
  return createPortal(menu, host?.closest(".nt-root") ?? document.body)
}

export function DocxViewerImpl(props: DocxViewerProps) {
  return <DocxWorkspace {...props} mode="view" />
}

export function DocxEditorImpl(props: DocxEditorProps) {
  return <DocxWorkspace {...props} mode="edit" />
}
