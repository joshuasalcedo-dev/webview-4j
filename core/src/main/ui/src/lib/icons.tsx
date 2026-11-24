// The small glyphs the components draw themselves (no icon library needed): strokes in currentColor.
const props = { fill: "none", stroke: "currentColor", strokeLinecap: "round", strokeLinejoin: "round", "aria-hidden": true } as const

export const ChevronLeft = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.6} {...props}>
    <path d="M10 3 5 8l5 5" />
  </svg>
)

export const ChevronRight = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.6} {...props}>
    <path d="m6 3 5 5-5 5" />
  </svg>
)

export const ArrowLeft = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.3} {...props}>
    <path d="M14 8H2.5M7 3 2 8l5 5" />
  </svg>
)

export const Hamburger = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
    <path d="M2 4h12M2 8h12M2 12h12" />
  </svg>
)

export const SidebarIcon = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.2} {...props}>
    <rect x="1.5" y="2.5" width="13" height="11" rx="2" />
    <path d="M6 2.5v11M3.3 5h1M3.3 7h1M3.3 9h1" />
  </svg>
)

export const Close = () => (
  <svg viewBox="0 0 10 10" strokeWidth={1.2} {...props}>
    <path d="m1.5 1.5 7 7M8.5 1.5l-7 7" />
  </svg>
)

export const Plus = () => (
  <svg viewBox="0 0 10 10" strokeWidth={1.2} {...props}>
    <path d="M5 1v8M1 5h8" />
  </svg>
)

export const Magnifier = () => (
  <svg viewBox="0 0 16 16" strokeWidth={1.4} {...props}>
    <circle cx="7" cy="7" r="4.5" />
    <path d="m10.5 10.5 3.5 3.5" />
  </svg>
)
