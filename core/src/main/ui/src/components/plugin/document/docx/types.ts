// The props of the Word viewer and editor (docx-viewer.tsx, docx-editor.tsx), here so the parts that load later
// (docx-impl.tsx) share them without loading the light ones.
import type * as React from "react"

/** How large the pages are: a percentage, the viewer's width, or a whole page in view. */
export type DocxZoom = number | "fit-width" | "fit-page"

/** A side panel of the viewer: page thumbnails, the outline, the sections, the comments and changes. */
export type DocxPanel = "pages" | "outline" | "sections" | "comments"

/** The document: a URL (a data: URL too, as Java sends one) or its bytes, and the name it is shown and saved as. */
export type DocxSource = {
  src?: string
  data?: ArrayBuffer | Uint8Array
  /** The name to show and to save as (the URL's last part, or "document.docx", if left out). */
  fileName?: string
}

export type DocxCommonProps = DocxSource &
  Omit<React.ComponentProps<"div">, "onChange" | "onError" | "children"> & {
    /** How large the pages are at first ("fit-width" unless set): 25 to 400, or "fit-width" / "fit-page". */
    defaultZoom?: DocxZoom
    /** Controlled zoom, with onZoomChange. */
    zoom?: DocxZoom
    onZoomChange?: (zoom: DocxZoom) => void
    /** The toolbar on top (on unless false). */
    showToolbar?: boolean
    /** The side panel open at first, or null (closed, the default). */
    defaultPanel?: DocxPanel | null
    /** Comments in the page's margin at first (on unless false). */
    showComments?: boolean
    /** Tracked changes marked in the text at first (on unless false). */
    showTrackedChanges?: boolean
    /** A Download item (on unless false). */
    showDownload?: boolean
    /** An Open item, to show another .docx (on unless false). */
    showUpload?: boolean
    /** Download gives the bytes here instead of saving a file (e.g. Java, which saves it where it wants). */
    onDownload?: (bytes: Uint8Array, fileName: string) => void
    /** A .docx was opened with Open: its name and bytes (it is shown either way). */
    onUpload?: (fileName: string, bytes: Uint8Array) => void
    /** The page in view changed (1 first), and how many there are. */
    onPageChange?: (page: number, pageCount: number) => void
    /** The document is shown: its name and its page count. */
    onLoad?: (info: { fileName: string; pageCount: number }) => void
    onError?: (message: string) => void
    /** More of the app's own buttons, at the toolbar's end. */
    toolbarActions?: React.ReactNode
    /** Its height (640 unless set, or sized by style's height or flex): a number of CSS pixels, or any CSS length. */
    height?: number | string
    /** What it shows, for screen readers ("Word document" unless set). */
    "aria-label"?: string
  }

export type DocxViewerProps = DocxCommonProps & {
  /** The pages drawn dark (the text light, pictures keeping their colours). Left out: dark in the dark theme. */
  darkPages?: boolean
}

export type DocxEditorProps = DocxCommonProps & {
  /** Nothing can be changed, as in the viewer; the toolbar's lock switches it (on unless showReadOnlyToggle false). */
  readOnly?: boolean
  onReadOnlyChange?: (readOnly: boolean) => void
  showReadOnlyToggle?: boolean
  /** Save (Ctrl+S, ⌘S on a Mac): the document as .docx bytes. Left out, Save downloads the file. */
  onSave?: (bytes: Uint8Array, fileName: string) => void
  /** The document was changed (a moment after the typing stops). */
  onChange?: () => void
  /** Whether it has changes not saved yet, each time that flips. */
  onDirtyChange?: (dirty: boolean) => void
}
