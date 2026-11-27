// The workbook and CSV views' own glyphs: 16px strokes in currentColor, as lib/icons.tsx draws them.
const props = {
  viewBox: "0 0 16 16",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.3,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const

export const ZoomOut = () => (
  <svg {...props}>
    <circle cx="8" cy="8" r="6" />
    <path d="M5.5 8h5" />
  </svg>
)

export const ZoomIn = () => (
  <svg {...props}>
    <circle cx="8" cy="8" r="6" />
    <path d="M5.5 8h5M8 5.5v5" />
  </svg>
)

export const Download = () => (
  <svg {...props}>
    <path d="M8 2v8.5M4.5 7 8 10.5 11.5 7M2.5 13.5h11" />
  </svg>
)

export const Save = () => (
  <svg {...props}>
    <path d="M2.5 3.5a1 1 0 0 1 1-1h7.3l2.7 2.7v7.3a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1Z" />
    <path d="M5 2.5v3h5v-3M5 13.5v-4h6v4" />
  </svg>
)

export const Undo = () => (
  <svg {...props}>
    <path d="M5.5 3 2.5 6l3 3" />
    <path d="M2.5 6h7a4 4 0 0 1 0 8H7" />
  </svg>
)

export const Redo = () => (
  <svg {...props}>
    <path d="m10.5 3 3 3-3 3" />
    <path d="M13.5 6h-7a4 4 0 0 0 0 8H9" />
  </svg>
)

export const Merge = () => (
  <svg {...props}>
    <rect x="1.5" y="3" width="13" height="10" rx="1.5" />
    <path d="M4 8h3M5.5 6.5 7 8 5.5 9.5M12 8H9M10.5 6.5 9 8l1.5 1.5" />
  </svg>
)

export const Bold = () => (
  <svg {...props} strokeWidth={1.6}>
    <path d="M4.5 2.5h4.2a2.6 2.6 0 0 1 0 5.2H4.5ZM4.5 7.7h5a2.9 2.9 0 0 1 0 5.8h-5Z" />
  </svg>
)

export const Italic = () => (
  <svg {...props}>
    <path d="M6.5 2.5h6M3.5 13.5h6M9.5 2.5l-3 11" />
  </svg>
)

export const ChevronUp = () => (
  <svg {...props}>
    <path d="m4 10 4-4 4 4" />
  </svg>
)

export const ChevronDown = () => (
  <svg {...props}>
    <path d="m4 6 4 4 4-4" />
  </svg>
)

export const SortMenu = () => (
  <svg {...props}>
    <path d="m4.5 6.5 3.5 3.5 3.5-3.5" />
  </svg>
)

export const Formula = () => (
  <svg {...props}>
    <path d="M10.5 2.5c-1.6 0-2.2.9-2.5 2.5L6.6 12c-.3 1.3-.9 1.5-2.1 1.5M5 6.5h5M9.5 9.5l3 3M12.5 9.5l-3 3" />
  </svg>
)
