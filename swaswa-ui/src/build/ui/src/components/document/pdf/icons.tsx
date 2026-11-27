// The PDF components' own glyphs, drawn as the kit's (lib/icons.tsx): 16px strokes in currentColor.
const props = { fill: "none", stroke: "currentColor", strokeLinecap: "round", strokeLinejoin: "round", "aria-hidden": true } as const

export const ZoomOutIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <circle cx="7" cy="7" r="4.8" />
    <path d="M4.8 7h4.4M10.6 10.6 14 14" />
  </svg>
)

export const ZoomInIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <circle cx="7" cy="7" r="4.8" />
    <path d="M4.8 7h4.4M7 4.8v4.4M10.6 10.6 14 14" />
  </svg>
)

export const RotateLeftIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <path d="M2.5 3v3.5H6" />
    <path d="M2.9 6.5A5.5 5.5 0 1 1 3.6 11" />
  </svg>
)

export const RotateRightIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <path d="M13.5 3v3.5H10" />
    <path d="M13.1 6.5A5.5 5.5 0 1 0 12.4 11" />
  </svg>
)

export const DownloadIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <path d="M8 2v8.5M4.5 7 8 10.5 11.5 7M2.5 13.5h11" />
  </svg>
)

export const UploadIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <path d="M8 10.5V2M4.5 5.5 8 2l3.5 3.5M2.5 13.5h11" />
  </svg>
)

export const PagesIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
    <rect x="1.5" y="2.5" width="13" height="11" rx="2" />
    <path d="M6 2.5v11M3 5h1.5v2H3zM3 9h1.5v2H3z" />
  </svg>
)

export const ChevronUpIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.5} {...props}>
    <path d="m3.5 10 4.5-4.5 4.5 4.5" />
  </svg>
)

export const ChevronDownIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.5} {...props}>
    <path d="m3.5 6 4.5 4.5L12.5 6" />
  </svg>
)

export const PlusIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.4} {...props}>
    <path d="M8 3v10M3 8h10" />
  </svg>
)

export const TrashIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
    <path d="M2.5 4h11M6 4V2.5h4V4M4 4l.7 9.5h6.6L12 4M6.8 6.5v4.5M9.2 6.5v4.5" />
  </svg>
)

export const GripIcon = () => (
  <svg viewBox="0 0 16 16" {...props} stroke="none" fill="currentColor">
    <circle cx="6" cy="4" r="1.1" />
    <circle cx="10" cy="4" r="1.1" />
    <circle cx="6" cy="8" r="1.1" />
    <circle cx="10" cy="8" r="1.1" />
    <circle cx="6" cy="12" r="1.1" />
    <circle cx="10" cy="12" r="1.1" />
  </svg>
)

export const UndoIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <path d="M5.5 3 2.5 6l3 3" />
    <path d="M2.5 6h7a4 4 0 0 1 0 8H6" />
  </svg>
)

export const ClearIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <circle cx="8" cy="8" r="5.8" />
    <path d="m5.8 5.8 4.4 4.4M10.2 5.8l-4.4 4.4" />
  </svg>
)

/** The glyph for a value's type: Aa text, # number, ✓ yes/no, {} object, [] list. */
export function TypeIcon({ type }: { type: string }) {
  switch (type) {
    case "number":
    case "integer":
      return (
        <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
          <path d="M6 2.5 5 13.5M11 2.5l-1 11M3 5.5h10.5M2.5 10.5H13" />
        </svg>
      )
    case "boolean":
      return (
        <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
          <rect x="2" y="2" width="12" height="12" rx="3" />
          <path d="m5 8.2 2 2 4-4.4" />
        </svg>
      )
    case "object":
      return (
        <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
          <path d="M5.5 2.5c-1.5 0-2 .7-2 2v1.5c0 1-.5 2-1.5 2 1 0 1.5 1 1.5 2v1.5c0 1.3.5 2 2 2M10.5 2.5c1.5 0 2 .7 2 2v1.5c0 1 .5 2 1.5 2-1 0-1.5 1-1.5 2v1.5c0 1.3-.5 2-2 2" />
        </svg>
      )
    case "array":
      return (
        <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
          <path d="M5.5 2.5h-2.5v11h2.5M10.5 2.5h2.5v11h-2.5" />
        </svg>
      )
    default:
      return (
        <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
          <path d="m1.5 12 3-8 3 8M2.6 9.2h3.8M12.5 7.5v4.5M12.5 9.5a2 2 0 1 1-2-2" />
        </svg>
      )
  }
}
