// Icons by name, so Java can ask for one ("home", "table"): 16px strokes in currentColor, drawn here (no icon
// library). An unknown name draws nothing; one or two characters (an emoji, initials) are drawn as text.
import * as React from "react"

const props = {
  viewBox: "0 0 16 16",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.3,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const

const paths: Record<string, React.ReactNode> = {
  home: <path d="M2.5 7 8 2.5 13.5 7v6a.5.5 0 0 1-.5.5h-3v-4h-4v4H3a.5.5 0 0 1-.5-.5Z" />,
  search: <><circle cx="7" cy="7" r="4.5" /><path d="m10.5 10.5 3 3" /></>,
  settings: <><circle cx="8" cy="8" r="2" /><path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M3.4 12.6l1.4-1.4M11.2 4.8l1.4-1.4" /></>,
  cursor: <path d="m3 2.5 9.5 4.2-4 1.3-1.3 4Z" />,
  form: <><rect x="2.5" y="2.5" width="11" height="11" rx="1.5" /><path d="M5 5.5h6M5 8h6M5 10.5h3" /></>,
  text: <path d="M3 3.5h10M8 3.5v9.5M6 13h4" />,
  layout: <><rect x="2" y="2.5" width="12" height="11" rx="1.5" /><path d="M2 6h12M6.5 6v7.5" /></>,
  columns: <><rect x="2" y="2.5" width="12" height="11" rx="1.5" /><path d="M8 2.5v11" /></>,
  file: <><path d="M4 1.5h5l3 3v10H4Z" /><path d="M9 1.5v3h3" /></>,
  folder: <path d="M1.5 4a1 1 0 0 1 1-1h3.5l1.5 1.5h6a1 1 0 0 1 1 1V12a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1Z" />,
  code: <path d="M5.5 4.5 2 8l3.5 3.5M10.5 4.5 14 8l-3.5 3.5" />,
  table: <><rect x="2" y="2.5" width="12" height="11" rx="1.5" /><path d="M2 6.2h12M2 9.8h12M6.5 6.2v7.3" /></>,
  list: <path d="M5.5 4h8M5.5 8h8M5.5 12h8M2.5 4h.01M2.5 8h.01M2.5 12h.01" />,
  chart: <path d="M2.5 13.5h11M4.5 11V8M8 11V4.5M11.5 11V6.5" />,
  money: <><rect x="1.5" y="4" width="13" height="8" rx="1.5" /><circle cx="8" cy="8" r="1.8" /></>,
  windows: <><rect x="1.5" y="3.5" width="9" height="8" rx="1" /><path d="M5.5 3.5V2h9v8h-4" /></>,
  window: <><rect x="1.5" y="2.5" width="13" height="11" rx="1.5" /><path d="M1.5 5.5h13" /></>,
  lock: <><rect x="3" y="7" width="10" height="7" rx="1.5" /><path d="M5 7V5a3 3 0 0 1 6 0v2" /></>,
  user: <><circle cx="8" cy="5.5" r="2.8" /><path d="M2.5 14a5.5 5.5 0 0 1 11 0" /></>,
  users: <><circle cx="6" cy="5.5" r="2.5" /><path d="M1.5 13.5a4.5 4.5 0 0 1 9 0M10.5 3.2a2.5 2.5 0 0 1 0 4.8M12 9.5a4.5 4.5 0 0 1 2.5 4" /></>,
  bell: <path d="M4 11.5V7a4 4 0 0 1 8 0v4.5l1 1H3Zm2.5 2h3" />,
  mail: <><rect x="1.5" y="3" width="13" height="10" rx="1.5" /><path d="m2 4 6 5 6-5" /></>,
  chat: <path d="M2.5 3.5h11v7h-7l-3 2.5v-2.5h-1Z" />,
  image: <><rect x="2" y="2.5" width="12" height="11" rx="1.5" /><circle cx="5.5" cy="6" r="1.2" /><path d="m2.5 12 3.5-3.5 2.5 2.5 2-2 3 3" /></>,
  palette: <><path d="M8 1.5a6.5 6.5 0 1 0 0 13c1 0 1.2-.8.8-1.5-.5-.8 0-1.8 1-1.8h1.7A2.5 2.5 0 0 0 14.5 8.7 6.6 6.6 0 0 0 8 1.5Z" /><circle cx="5" cy="7" r=".6" /><circle cx="8" cy="4.8" r=".6" /><circle cx="11" cy="7" r=".6" /></>,
  package: <><path d="M8 1.5 14 4.5v7L8 14.5 2 11.5v-7Z" /><path d="M2 4.5 8 7.5l6-3M8 7.5v7" /></>,
  layers: <path d="M8 2 14 5 8 8 2 5Zm-6 6 6 3 6-3M2 11l6 3 6-3" />,
  grid: <><rect x="2" y="2" width="5" height="5" rx="1" /><rect x="9" y="2" width="5" height="5" rx="1" /><rect x="2" y="9" width="5" height="5" rx="1" /><rect x="9" y="9" width="5" height="5" rx="1" /></>,
  sparkles: <path d="M6.5 2.5 7.6 6l3.4 1-3.4 1.1-1.1 3.4-1-3.4L2 7l3.5-1Zm6 6.5.5 1.5 1.5.5-1.5.5-.5 1.5-.5-1.5L10.5 11l1.5-.5Z" />,
  star: <path d="m8 1.8 1.9 3.9 4.3.6-3.1 3 .7 4.3L8 11.6l-3.8 2 .7-4.3-3.1-3 4.3-.6Z" />,
  bookmark: <path d="M4 1.5h8v13l-4-3-4 3Z" />,
  calendar: <><rect x="2" y="3" width="12" height="11" rx="1.5" /><path d="M2 6.5h12M5 1.5v3M11 1.5v3" /></>,
  clock: <><circle cx="8" cy="8" r="6.2" /><path d="M8 4.5V8l2.5 1.5" /></>,
  globe: <><circle cx="8" cy="8" r="6.2" /><path d="M1.8 8h12.4M8 1.8c2 2 2 10.4 0 12.4M8 1.8c-2 2-2 10.4 0 12.4" /></>,
  download: <path d="M8 2v8.5M4.5 7 8 10.5 11.5 7M2.5 13.5h11" />,
  upload: <path d="M8 10.5V2M4.5 5.5 8 2l3.5 3.5M2.5 13.5h11" />,
  plus: <path d="M8 3v10M3 8h10" />,
  check: <path d="m3 8.5 3 3 7-7" />,
  info: <><circle cx="8" cy="8" r="6.2" /><path d="M8 7.2V11M8 5h.01" /></>,
  help: <><circle cx="8" cy="8" r="6.2" /><path d="M6.2 6.2a1.9 1.9 0 0 1 3.6.6c0 1.3-1.8 1.6-1.8 2.7M8 11.3h.01" /></>,
  logout: <path d="M6 2.5H3.5a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1H6M10 11l3-3-3-3M13 8H6" />,
  terminal: <><rect x="1.5" y="2.5" width="13" height="11" rx="1.5" /><path d="m4.5 6 2 2-2 2M8.5 10.5h3" /></>,
  play: <path d="M4.5 2.5v11l9-5.5Z" />,
  video: <><rect x="1.5" y="4" width="9" height="8" rx="1.5" /><path d="m10.5 7 4-2.5v7l-4-2.5" /></>,
  bolt: <path d="M9 1.5 3 9h4.5L7 14.5 13 7H8.5Z" />,
  gauge: <><path d="M2 11.5a6 6 0 1 1 12 0" /><path d="m8 11.5 3-4" /></>,
  heart: <path d="M8 13.5S2 10 2 6a3 3 0 0 1 6-1 3 3 0 0 1 6 1c0 4-6 7.5-6 7.5Z" />,
  tag: <><path d="M2 2h5.5L14 8.5 8.5 14 2 7.5Z" /><circle cx="5" cy="5" r=".8" /></>,
  shield: <path d="M8 1.5 13.5 3.5V8c0 3-2.5 5.3-5.5 6.5C5 13.3 2.5 11 2.5 8V3.5Z" />,
  key: <><circle cx="5" cy="10.5" r="3" /><path d="m7.2 8.3 6.3-6.3M11 4.5l1.8 1.8" /></>,
  book: <path d="M2.5 2.5h4a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 0-1.5-1.5h-4ZM13.5 2.5h-4A1.5 1.5 0 0 0 8 4v10a1.5 1.5 0 0 1 1.5-1.5h4Z" />,
  more: <path d="M3.5 8h.01M8 8h.01M12.5 8h.01" />,
}

/** The names Java can give ({@code icon("table")}). */
const iconNames = Object.keys(paths)

/** The icon called {@code name}; one or two characters (an emoji, initials) are drawn as they are. */
function NamedIcon({ name }: { name: string }) {
  const path = paths[name]
  if (path) return <svg {...props}>{path}</svg>
  if ([...name].length <= 2) return <span className="nt-named-icon-text">{name}</span>
  return null
}

export { iconNames, NamedIcon }
