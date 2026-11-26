import * as React from "react"

import { Button } from "../../button"
import "./collection.css"

/**
 * A map of keys to values, as a property grid (Windows' Properties, a Mac's inspector): each key with its value;
 * maps and lists inside unfold, as in a JSON viewer; values edited in place (a double-click), entries added and
 * removed. Paths name a value inside: "address.city", "tags.0".
 */
type DataMapProps = {
  "aria-label": string
  /** The map: values are text, numbers, yes/no, null, lists and maps. */
  value: Record<string, unknown>
  /** Values (not maps or lists) edited in place: the path and what was typed, as JSON when it reads as JSON. */
  onEdit?: (path: string, value: unknown) => void
  /** An entry added at the top level. */
  onAdd?: (key: string, value: unknown) => void
  /** An entry removed (any depth). */
  onRemove?: (path: string) => void
  /** How deep the map is open at first (1: the top level). */
  depth?: number
  className?: string
  style?: React.CSSProperties
}

/** Typed text as a value: JSON when it reads as JSON (numbers, true, null, "quoted"), otherwise the text. */
function read(text: string): unknown {
  const t = text.trim()
  if (t === "") return ""
  try {
    return JSON.parse(t)
  } catch {
    return text
  }
}

function DataMap({ value, onEdit, onAdd, onRemove, depth = 1, className, style, ...props }: DataMapProps) {
  const [newKey, setNewKey] = React.useState("")
  const [newValue, setNewValue] = React.useState("")
  return (
    <div className={["nt-datamap", className].filter(Boolean).join(" ")} style={style} role="tree" aria-label={props["aria-label"]}>
      <Entries value={value} path="" level={0} open={depth} onEdit={onEdit} onRemove={onRemove} />
      {Object.keys(value).length === 0 && <div className="nt-datatable-note">Empty.</div>}
      {onAdd && (
        <form
          className="nt-datamap-add"
          onSubmit={(e) => {
            e.preventDefault()
            if (!newKey.trim()) return
            onAdd(newKey.trim(), read(newValue))
            setNewKey("")
            setNewValue("")
          }}
        >
          <input className="nt-input" aria-label="Key" placeholder="Key" value={newKey} onChange={(e) => setNewKey(e.target.value)} />
          <input className="nt-input" aria-label="Value" placeholder="Value" value={newValue} onChange={(e) => setNewValue(e.target.value)} />
          <Button type="submit" disabled={!newKey.trim()}>
            Add
          </Button>
        </form>
      )}
    </div>
  )
}

function Entries({
  value,
  path,
  level,
  open,
  onEdit,
  onRemove,
}: {
  value: Record<string, unknown> | unknown[]
  path: string
  level: number
  open: number
  onEdit?: (path: string, value: unknown) => void
  onRemove?: (path: string) => void
}) {
  const entries = Array.isArray(value) ? value.map((v, i) => [String(i), v] as const) : Object.entries(value)
  return (
    <>
      {entries.map(([key, v]) => (
        <Entry key={key} name={key} value={v} path={path ? `${path}.${key}` : key} level={level} open={open} onEdit={onEdit} onRemove={onRemove} />
      ))}
    </>
  )
}

function Entry({
  name,
  value,
  path,
  level,
  open,
  onEdit,
  onRemove,
}: {
  name: string
  value: unknown
  path: string
  level: number
  open: number
  onEdit?: (path: string, value: unknown) => void
  onRemove?: (path: string) => void
}) {
  const nested = value != null && typeof value === "object"
  const [isOpen, setOpen] = React.useState(level < open - 1)
  const [editing, setEditing] = React.useState(false)
  const count = nested ? (Array.isArray(value) ? value.length : Object.keys(value as object).length) : 0
  return (
    <>
      <div className="nt-datamap-row" role="treeitem" aria-level={level + 1} aria-expanded={nested ? isOpen : undefined} style={{ paddingInlineStart: level * 16 }}>
        <span className="nt-datamap-key">
          {nested ? (
            <button type="button" className="nt-datatree-toggle" aria-label={isOpen ? "Close" : "Open"} onClick={() => setOpen(!isOpen)}>
              {isOpen ? "▾" : "▸"}
            </button>
          ) : (
            <span className="nt-datatree-toggle" aria-hidden="true" />
          )}
          {name}
        </span>
        <span
          className="nt-datamap-value"
          data-kind={value === null ? "null" : Array.isArray(value) ? "list" : typeof value}
          onDoubleClick={() => !nested && onEdit && setEditing(true)}
        >
          {editing ? (
            <input
              className="nt-input"
              autoFocus
              defaultValue={typeof value === "string" ? value : JSON.stringify(value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  onEdit?.(path, read((e.target as HTMLInputElement).value))
                  setEditing(false)
                }
                if (e.key === "Escape") setEditing(false)
              }}
              onBlur={(e) => {
                onEdit?.(path, read(e.target.value))
                setEditing(false)
              }}
            />
          ) : nested ? (
            <span className="nt-datamap-summary">{Array.isArray(value) ? `${count} items` : `${count} entries`}</span>
          ) : value === null ? (
            "null"
          ) : typeof value === "string" ? (
            value
          ) : (
            String(value)
          )}
        </span>
        {onRemove && (
          <button type="button" className="nt-datamap-remove" aria-label={`Remove ${name}`} onClick={() => onRemove(path)}>
            ×
          </button>
        )}
      </div>
      {nested && isOpen && (
        <Entries value={value as Record<string, unknown>} path={path} level={level + 1} open={open} onEdit={onEdit} onRemove={onRemove} />
      )}
    </>
  )
}

export { DataMap }
export type { DataMapProps }
