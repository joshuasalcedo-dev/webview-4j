import * as React from "react"

import { cn } from "../../../../lib/utils"
import { Button } from "../../../button"
import { SplitPane } from "../../../layout/layout"
import { SegmentedControl } from "../../../segmented-control"
import { ClearIcon, TypeIcon, UndoIcon } from "./icons"
import { PdfViewer, type PdfViewerHandle, type PdfViewerProps } from "./pdf-viewer"
import "./pdf.css"

/*
 * Values extracted from a PDF, each linked to where it is: a box over its page. Pointing at a value marks its box,
 * choosing one scrolls the PDF to it; choosing a box chooses its value. With expected values, it is a review: each
 * value next to what it should be, which can be corrected.
 */

type JsonPrimitive = string | number | boolean | null
type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue }

/** A box on a page, in percent of the page's width and height (0 to 100), from its top left corner, unrotated. */
type CitationArea = { left: number; top: number; width: number; height: number }

/** Where a value is: its page (1 for the first) and its box. */
type CitationLocation = { page: number; area: CitationArea }

/** What kind of value a field holds (as JSON Schema names them). */
type CitationFieldType = "string" | "number" | "integer" | "boolean" | "object" | "array"

/** One value taken from the PDF. */
type CitationField = {
  /** Its name in the data ("ending_balance"); a field inside an object is found as "parent.child". */
  key: string
  /** What people call it ("Ending balance"); the key if left out. */
  label?: string
  description?: string
  /** What kind of value it is; guessed from the value if left out. */
  type?: CitationFieldType
  /** What was taken from the PDF. */
  value: JsonValue
  /** What it should be (a review shows both and lets it be corrected). */
  expected?: JsonValue
  location?: CitationLocation
  /** An object's fields, each with its own box (else its value's entries are shown, without boxes). */
  fields?: CitationField[]
}

/** A box given as a polygon in the page's own units (as OCR and layout services give them), with the page's size. */
type CitationPolygon = { page: number; polygon: { x: number; y: number }[]; pageWidth: number; pageHeight: number }

/** The box around a polygon, in percent of its page. */
function citationLocation({ page, polygon, pageWidth, pageHeight }: CitationPolygon): CitationLocation | undefined {
  if (!polygon.length || !pageWidth || !pageHeight) return undefined
  const xs = polygon.map((p) => p.x)
  const ys = polygon.map((p) => p.y)
  const left = Math.min(...xs)
  const top = Math.min(...ys)
  return {
    page,
    area: {
      left: (left / pageWidth) * 100,
      top: (top / pageHeight) * 100,
      width: ((Math.max(...xs) - left) / pageWidth) * 100,
      height: ((Math.max(...ys) - top) / pageHeight) * 100,
    },
  }
}

const isObject = (v: JsonValue): v is { [key: string]: JsonValue } => typeof v === "object" && v !== null && !Array.isArray(v)

function typeOf(field: CitationField): CitationFieldType {
  if (field.type) return field.type
  const v = field.value ?? field.expected ?? null
  if (Array.isArray(v)) return "array"
  if (isObject(v) || field.fields) return "object"
  if (typeof v === "boolean") return "boolean"
  if (typeof v === "number") return Number.isInteger(v) ? "integer" : "number"
  return "string"
}

function shown(value: JsonValue | undefined): string {
  if (value === undefined) return ""
  if (value === null) return "NULL"
  if (typeof value === "boolean") return value ? "Yes" : "No"
  if (typeof value === "object") return JSON.stringify(value)
  return String(value)
}

const equal = (a: JsonValue | undefined, b: JsonValue | undefined) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null)

/** Every field, the ones inside objects too (keys "parent.child"), in order. */
function flatten(fields: CitationField[], parent = ""): CitationField[] {
  return fields.flatMap((f) => {
    const key = parent && !f.key.startsWith(parent + ".") ? `${parent}.${f.key}` : f.key
    const self = { ...f, key }
    return [self, ...(f.fields ? flatten(f.fields, key) : [])]
  })
}

/** The fields as one JSON object, of their values (or their expected values). */
function toJson(fields: CitationField[], which: "value" | "expected", edits: Record<string, JsonValue>, parent = ""): Record<string, JsonValue> {
  const out: Record<string, JsonValue> = {}
  for (const f of fields) {
    const key = parent && !f.key.startsWith(parent + ".") ? `${parent}.${f.key}` : f.key
    const name = key.slice(parent ? parent.length + 1 : 0)
    if (f.fields) out[name] = toJson(f.fields, which, edits, key)
    else out[name] = which === "expected" ? (key in edits ? edits[key] : (f.expected ?? f.value ?? null)) : (f.value ?? null)
  }
  return out
}

type CitationHighlightProps = {
  location: CitationLocation
  /** The one chosen or pointed at: drawn strong. */
  active?: boolean
  label?: string
  onClick?: () => void
}

/** A box over a page (inside a PdfViewer's renderPageOverlay): faint, or strong when active. */
function CitationHighlight({ location, active, label, onClick }: CitationHighlightProps) {
  const { area } = location
  return (
    <div
      className="nt-pdf-citation"
      data-active={active || undefined}
      data-clickable={onClick ? "" : undefined}
      style={{ left: `${area.left}%`, top: `${area.top}%`, width: `${area.width}%`, height: `${area.height}%` }}
      title={label}
      onClick={onClick}
      onPointerDown={onClick ? (e) => e.stopPropagation() : undefined}
    >
      {active && label && <span className="nt-pdf-citation-label">{label}</span>}
    </div>
  )
}

type BoundingBoxCitationsProps = {
  /** The PDF (as PdfViewer's src). */
  src?: string
  fileName?: string
  fields: CitationField[]
  /** The field chosen (its key); given, the choice is the caller's (with onActiveChange). */
  activeKey?: string
  onActiveChange?: (key: string | undefined) => void
  /** Each value next to its expected value, which can be corrected (a review). */
  showExpected?: boolean
  /** An expected value was corrected. */
  onExpectedChange?: (key: string, value: JsonValue) => void
  /** Every field's box shown faintly, not only the one pointed at (on unless false). */
  showAllBoxes?: boolean
  /** Where the divider is, 0 to 1 from the left (0.58 unless set). */
  dividerPosition?: number
  /** More for the PdfViewer (zoom, toolbar). */
  viewerProps?: Omit<PdfViewerProps, "src" | "fileName" | "renderPageOverlay">
  className?: string
  style?: React.CSSProperties
  "aria-label"?: string
}

/**
 * A PDF next to the values taken from it, each linked to its box on the page: pointing at a value marks its box,
 * choosing it scrolls the PDF there; a box clicked chooses its value. With expected values (showExpected), it is
 * a review: what was taken next to what it should be, which can be corrected; the JSON view shows them all.
 */
function BoundingBoxCitations({
  src,
  fileName,
  fields,
  activeKey: activeFromProps,
  onActiveChange,
  showExpected = false,
  onExpectedChange,
  showAllBoxes = true,
  dividerPosition = 0.58,
  viewerProps,
  className,
  style,
  "aria-label": label,
}: BoundingBoxCitationsProps) {
  const viewer = React.useRef<PdfViewerHandle>(null)
  const panel = React.useRef<HTMLDivElement>(null)
  const [ownActive, setOwnActive] = React.useState<string>()
  const [hover, setHover] = React.useState<string>()
  const [positions, setPositions] = React.useState([dividerPosition])
  const active = activeFromProps ?? ownActive
  const all = React.useMemo(() => flatten(fields), [fields])
  const byPage = React.useMemo(() => {
    const pages = new Map<number, CitationField[]>()
    for (const f of all) if (f.location) pages.set(f.location.page, [...(pages.get(f.location.page) ?? []), f])
    return pages
  }, [all])

  const choose = React.useCallback(
    (key: string | undefined, scroll: boolean) => {
      setOwnActive(key)
      onActiveChange?.(key)
      const location = all.find((f) => f.key === key)?.location
      if (scroll && location) viewer.current?.scrollToPageArea(location.page, location.area, { behavior: "smooth" })
    },
    [all, onActiveChange]
  )

  const fromBox = (key: string) => {
    choose(key, false)
    panel.current?.querySelector<HTMLElement>(`[data-field-key="${CSS.escape(key)}"]`)?.scrollIntoView({ block: "nearest", behavior: "smooth" })
  }

  const marked = hover ?? active
  const overlay = ({ pageNumber }: { pageNumber: number }) =>
    (byPage.get(pageNumber) ?? [])
      .filter((f) => showAllBoxes || f.key === marked)
      .map((f) => <CitationHighlight key={f.key} location={f.location!} active={f.key === marked} label={f.label ?? f.key} onClick={() => fromBox(f.key)} />)

  return (
    <SplitPane
      className={cn("nt-pdf-citations", className)}
      style={style}
      aria-label={label ?? "Citations"}
      dividerPositions={positions}
      onDividerPositionsChange={setPositions}
      minSize={240}
    >
      <PdfViewer {...viewerProps} ref={viewer} src={src} fileName={fileName} renderPageOverlay={overlay} />
      <CitationPanel
        ref={panel}
        fields={fields}
        activeKey={active}
        showExpected={showExpected}
        onExpectedChange={onExpectedChange}
        onFieldFocus={(key) => choose(key, true)}
        onFieldHover={setHover}
      />
    </SplitPane>
  )
}

type CitationPanelProps = {
  fields: CitationField[]
  activeKey?: string
  showExpected?: boolean
  onExpectedChange?: (key: string, value: JsonValue) => void
  /** A field was chosen (clicked, or given the focus). */
  onFieldFocus?: (key: string) => void
  /** The pointer is over a field (undefined: it left). */
  onFieldHover?: (key: string | undefined) => void
  className?: string
  style?: React.CSSProperties
}

/** The values, as cards (Fields) or as one JSON document (JSON). */
const CitationPanel = React.forwardRef<HTMLDivElement, CitationPanelProps>(function CitationPanel(
  { fields, activeKey, showExpected = false, onExpectedChange, onFieldFocus, onFieldHover, className, style },
  ref
) {
  const [view, setView] = React.useState<"fields" | "json">("fields")
  const [edits, setEdits] = React.useState<Record<string, JsonValue>>({})
  React.useEffect(() => setEdits({}), [fields])
  const edit = (key: string, value: JsonValue) => {
    setEdits((e) => ({ ...e, [key]: value }))
    onExpectedChange?.(key, value)
  }
  return (
    <div ref={ref} className={cn("nt-pdf-citation-panel", className)} style={style}>
      <div className="nt-pdf-toolbar">
        <SegmentedControl
          aria-label="Show"
          value={view}
          onValueChange={setView}
          segments={[
            { value: "fields", label: "Fields" },
            { value: "json", label: "JSON" },
          ]}
        />
      </div>
      {view === "fields" ? (
        <div className="nt-pdf-fields">
          {fields.map((f) => (
            <FieldCard key={f.key} field={f} parent="" activeKey={activeKey} showExpected={showExpected} edits={edits} onEdit={edit} onFocus={onFieldFocus} onHover={onFieldHover} />
          ))}
        </div>
      ) : (
        <div className="nt-pdf-json" data-columns={showExpected ? 2 : 1}>
          <section>
            {showExpected && <h4>Extracted</h4>}
            <pre>{JSON.stringify(toJson(fields, "value", edits), null, 2)}</pre>
          </section>
          {showExpected && (
            <section>
              <h4>Expected</h4>
              <pre>{JSON.stringify(toJson(fields, "expected", edits), null, 2)}</pre>
            </section>
          )}
        </div>
      )}
    </div>
  )
})

type FieldCardProps = {
  field: CitationField
  parent: string
  activeKey?: string
  showExpected: boolean
  edits: Record<string, JsonValue>
  onEdit: (key: string, value: JsonValue) => void
  onFocus?: (key: string) => void
  onHover?: (key: string | undefined) => void
}

function FieldCard({ field, parent, activeKey, showExpected, edits, onEdit, onFocus, onHover }: FieldCardProps) {
  const key = parent && !field.key.startsWith(parent + ".") ? `${parent}.${field.key}` : field.key
  const type = typeOf(field)
  const original = field.expected ?? field.value ?? null
  const expected = key in edits ? edits[key] : original
  const modified = showExpected && !field.fields && !equal(expected, original)
  const differs = showExpected && !field.fields && !equal(expected, field.value)
  const active = activeKey === key || activeKey?.startsWith(key + ".")
  return (
    <div
      className="nt-pdf-field"
      data-field-key={key}
      data-active={active || undefined}
      data-linked={field.location ? "" : undefined}
      tabIndex={0}
      // the card's own focus and clicks, and its fields', not those of the cards inside it
      onFocus={(e) => (e.target as HTMLElement).closest(".nt-pdf-field") === e.currentTarget && onFocus?.(key)}
      onClick={(e) => (e.target as HTMLElement).closest(".nt-pdf-field") === e.currentTarget && onFocus?.(key)}
      onPointerEnter={() => onHover?.(key)}
      onPointerLeave={() => onHover?.(undefined)}
    >
      <div className="nt-pdf-field-head">
        <span className="nt-pdf-field-names">
          <span className="nt-pdf-field-label">
            {field.label ?? field.key}
            {modified && <span className="nt-pdf-field-modified" title="Changed" />}
          </span>
          <span className="nt-pdf-field-key">{key}</span>
        </span>
        {showExpected && !field.fields && type !== "object" && type !== "array" && (
          <>
            {modified && (
              <Button variant="subtle" className="nt-pdf-icon-button" aria-label={`Undo ${key}`} title="Undo the change" onClick={() => onEdit(key, original)}>
                <UndoIcon />
              </Button>
            )}
            <Button variant="subtle" className="nt-pdf-icon-button" aria-label={`Set ${key} to null`} title="Set to NULL" onClick={() => onEdit(key, null)}>
              <ClearIcon />
            </Button>
          </>
        )}
        <span className="nt-pdf-field-type">
          <TypeIcon type={type} />
          {type}
        </span>
      </div>
      {field.description && <p className="nt-pdf-field-description">{field.description}</p>}
      {field.fields ? (
        <div className="nt-pdf-field-children">
          {field.fields.map((child) => (
            <FieldCard key={child.key} field={child} parent={key} activeKey={activeKey} showExpected={showExpected} edits={edits} onEdit={onEdit} onFocus={onFocus} onHover={onHover} />
          ))}
        </div>
      ) : (
        <div className="nt-pdf-field-values" data-columns={showExpected ? 2 : 1}>
          <div className="nt-pdf-field-value">
            {showExpected && <span className="nt-pdf-field-caption">Extracted</span>}
            <Value value={field.value} />
          </div>
          {showExpected && (
            <div className="nt-pdf-field-value" data-differs={differs || undefined}>
              <span className="nt-pdf-field-caption">Expected</span>
              {type === "object" || type === "array" ? <Value value={expected} /> : <ValueInput type={type} value={expected} label={`Expected ${field.label ?? key}`} onChange={(v) => onEdit(key, v)} />}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

/** A value as people read it: a list of objects as a table, an object as its entries. */
function Value({ value }: { value: JsonValue }) {
  if (Array.isArray(value)) {
    if (value.length && value.every(isObject)) {
      const columns = [...new Set(value.flatMap((row) => Object.keys(row as object)))]
      return (
        <div className="nt-pdf-table-scroll">
          <table className="nt-pdf-table">
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={c}>{c}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {value.map((row, i) => (
                <tr key={i}>
                  {columns.map((c) => (
                    <td key={c}>{shown((row as Record<string, JsonValue>)[c] ?? null)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )
    }
    return <span className="nt-pdf-value">{value.length ? value.map((v) => shown(v)).join(", ") : "None"}</span>
  }
  if (isObject(value)) {
    return (
      <dl className="nt-pdf-entries">
        {Object.entries(value).map(([k, v]) => (
          <React.Fragment key={k}>
            <dt>{k}</dt>
            <dd>{shown(v)}</dd>
          </React.Fragment>
        ))}
      </dl>
    )
  }
  return (
    <span className="nt-pdf-value" data-null={value === null || undefined}>
      {shown(value)}
    </span>
  )
}

/** An expected value typed in: text, a number, or yes / no. */
function ValueInput({ type, value, label, onChange }: { type: CitationFieldType; value: JsonValue; label: string; onChange: (value: JsonValue) => void }) {
  if (type === "boolean") {
    return (
      <span className="nt-select nt-pdf-value-input">
        <select aria-label={label} value={value === null ? "" : String(value)} onChange={(e) => onChange(e.target.value === "" ? null : e.target.value === "true")}>
          <option value="true">Yes</option>
          <option value="false">No</option>
          <option value="">NULL</option>
        </select>
      </span>
    )
  }
  const numeric = type === "number" || type === "integer"
  return (
    <input
      className="nt-input nt-pdf-value-input"
      aria-label={label}
      inputMode={numeric ? "decimal" : undefined}
      placeholder="NULL"
      value={value === null ? "" : String(value)}
      onChange={(e) => {
        const text = e.target.value
        if (!numeric) return onChange(text)
        if (text.trim() === "") return onChange(null)
        const n = Number(text)
        onChange(Number.isNaN(n) ? text : n)
      }}
    />
  )
}

export { BoundingBoxCitations, CitationHighlight, citationLocation, CitationPanel }
export type {
  BoundingBoxCitationsProps,
  CitationArea,
  CitationField,
  CitationFieldType,
  CitationHighlightProps,
  CitationLocation,
  CitationPanelProps,
  CitationPolygon,
  JsonValue,
}
