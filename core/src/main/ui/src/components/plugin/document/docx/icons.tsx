// The Word components' own glyphs: 16px strokes in currentColor, as the kit's icons are (lib/icons.tsx).
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

const icon = (paths: React.ReactNode) => () => <svg {...props}>{paths}</svg>

export const ZoomIn = icon(<><circle cx="7" cy="7" r="4.5" /><path d="m10.5 10.5 3 3M5 7h4M7 5v4" /></>)
export const ZoomOut = icon(<><circle cx="7" cy="7" r="4.5" /><path d="m10.5 10.5 3 3M5 7h4" /></>)
export const FitWidth = icon(<><rect x="1.5" y="3" width="13" height="10" rx="1.5" /><path d="M4 8h8M5.5 6.5 4 8l1.5 1.5M10.5 6.5 12 8l-1.5 1.5" /></>)
export const FitPage = icon(<><rect x="4" y="1.5" width="8" height="13" rx="1.2" /><path d="M6 5h4M6 7.5h4M6 10h2.5" /></>)
export const Download = icon(<path d="M8 2v8M4.5 6.5 8 10l3.5-3.5M2.5 13.5h11" />)
export const Upload = icon(<path d="M8 10V2M4.5 5.5 8 2l3.5 3.5M2.5 13.5h11" />)
export const CommentIcon = icon(<path d="M2.5 3.5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1H7l-3 2.5V11h-.5a1 1 0 0 1-1-1Z" />)
export const ChangesIcon = icon(<><path d="M4 1.5h5l3 3v10H4Z" /><path d="M6 8h4M8 6v4M6 12h4" /></>)
export const Moon = icon(<path d="M13 9.5A5.5 5.5 0 0 1 6.5 3a5.5 5.5 0 1 0 6.5 6.5Z" />)
export const More = icon(<path d="M3.5 8h.01M8 8h.01M12.5 8h.01" />)
export const Pages = icon(<><rect x="2" y="1.5" width="5" height="6" rx=".8" /><rect x="9" y="1.5" width="5" height="6" rx=".8" /><rect x="2" y="9" width="5" height="5.5" rx=".8" /><rect x="9" y="9" width="5" height="5.5" rx=".8" /></>)
export const Outline = icon(<path d="M2.5 3.5h11M4.5 6.5h9M4.5 9.5h9M6.5 12.5h7" />)
export const Sections = icon(<><rect x="2.5" y="1.5" width="11" height="5.5" rx="1" /><rect x="2.5" y="9" width="11" height="5.5" rx="1" /></>)
export const Blocks = icon(<><rect x="2" y="2" width="12" height="3.5" rx="1" /><rect x="2" y="7" width="5.5" height="7" rx="1" /><rect x="9" y="7" width="5" height="7" rx="1" /></>)
export const Undo = icon(<path d="M5.5 3 2.5 6l3 3M2.5 6H10a3.5 3.5 0 0 1 0 7H7" />)
export const Redo = icon(<path d="M10.5 3l3 3-3 3M13.5 6H6a3.5 3.5 0 0 0 0 7h3" />)
export const Bold = icon(<path d="M4.5 2.5h4a2.75 2.75 0 0 1 0 5.5h-4Zm0 5.5h4.75a2.75 2.75 0 0 1 0 5.5H4.5Z" strokeWidth={1.6} />)
export const Italic = icon(<path d="M7 2.5h5M4 13.5h5M10 2.5l-4 11" />)
export const Underline = icon(<path d="M4.5 2.5V7a3.5 3.5 0 0 0 7 0V2.5M3.5 14h9" />)
export const Strike = icon(<path d="M2.5 8h11M11 4.5C10.5 3 9.4 2.5 8 2.5c-2 0-3.2 1-3.2 2.4 0 1.2.8 1.8 1.7 2.1M5 11.5c.5 1.4 1.6 2 3 2 2 0 3.3-1 3.3-2.5 0-.6-.2-1-.5-1.4" />)
export const Superscript = icon(<path d="m2.5 5 6 7M8.5 5l-6 7M11 2.5c.3-.6.9-1 1.6-1 .9 0 1.4.5 1.4 1.2 0 1.2-2.9 2.3-2.9 3.3H14" />)
export const Subscript = icon(<path d="m2.5 3 6 7M8.5 3l-6 7M11 10.5c.3-.6.9-1 1.6-1 .9 0 1.4.5 1.4 1.2 0 1.2-2.9 2.3-2.9 3.3H14" />)
export const Highlight = icon(<><path d="m9.5 2.5 4 4-6 6H3.5v-4Z" /><path d="M2 14.5h12" /></>)
export const TextColor = icon(<><path d="m4 11 4-8.5 4 8.5M5.4 8h5.2" /><path d="M2.5 14h11" strokeWidth={2} /></>)
export const AlignLeft = icon(<path d="M2.5 3.5h11M2.5 6.5h7M2.5 9.5h11M2.5 12.5h7" />)
export const AlignCenter = icon(<path d="M2.5 3.5h11M4.5 6.5h7M2.5 9.5h11M4.5 12.5h7" />)
export const AlignRight = icon(<path d="M2.5 3.5h11M6.5 6.5h7M2.5 9.5h11M6.5 12.5h7" />)
export const AlignJustify = icon(<path d="M2.5 3.5h11M2.5 6.5h11M2.5 9.5h11M2.5 12.5h11" />)
export const Bullets = icon(<path d="M6 4h7.5M6 8h7.5M6 12h7.5M2.75 4h.01M2.75 8h.01M2.75 12h.01" strokeWidth={1.6} />)
export const Numbers = icon(<path d="M6.5 4h7M6.5 8h7M6.5 12h7M2.5 2.5h1v3M2.3 6.8c.2-.4.6-.6 1-.6.5 0 .9.3.9.8 0 .7-1.9 1.3-1.9 2H4.4M2.4 11h1.8l-1 1.2c.6 0 1.1.3 1.1.9 0 .5-.4.9-1 .9-.4 0-.7-.2-.9-.5" />)
export const Indent = icon(<path d="M2.5 3h11M7 6.5h6.5M7 9.5h6.5M2.5 13h11M2.5 6l2 2-2 2" />)
export const Outdent = icon(<path d="M2.5 3h11M7 6.5h6.5M7 9.5h6.5M2.5 13h11M4.5 6l-2 2 2 2" />)
export const LinkIcon = icon(<path d="M7 9a3 3 0 0 0 4.2.3l2-2a3 3 0 0 0-4.2-4.3l-1 1M9 7a3 3 0 0 0-4.2-.3l-2 2A3 3 0 0 0 7 13l1-1" />)
export const TableIcon = icon(<><rect x="2" y="2.5" width="12" height="11" rx="1.5" /><path d="M2 6.2h12M2 9.8h12M6.5 6.2v7.3M10 6.2v7.3" /></>)
export const ImageIcon = icon(<><rect x="1.5" y="2.5" width="13" height="11" rx="1.5" /><circle cx="5.5" cy="6" r="1.3" /><path d="m1.5 12 4-3.5 3 2.5 2-1.5 4 3" /></>)
export const Lock = icon(<><rect x="3" y="7" width="10" height="7" rx="1.5" /><path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2" /></>)
export const Save = icon(<><path d="M2.5 3.5a1 1 0 0 1 1-1h7.5l2.5 2.5v7.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1Z" /><path d="M5 2.5v3.5h5V2.5M5 13.5v-4h6v4" /></>)
export const NewFile = icon(<><path d="M4 1.5h5l3 3v10H4Z" /><path d="M9 1.5v3h3M8 7.5v4M6 9.5h4" /></>)
export const Open = icon(<path d="M1.5 4a1 1 0 0 1 1-1h3.5l1.5 1.5h6a1 1 0 0 1 1 1V12a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1Z" />)
export const Borders = icon(<><rect x="2.5" y="2.5" width="11" height="11" rx=".5" strokeDasharray="1.5 1.5" /><path d="M2.5 13.5h11" strokeWidth={1.8} /></>)
export const ChevronUp = icon(<path d="m4 10 4-4 4 4" strokeWidth={1.6} />)
export const ChevronDown = icon(<path d="m4 6 4 4 4-4" strokeWidth={1.6} />)
export const DocIcon = icon(<><path d="M4 1.5h5l3 3v10H4Z" /><path d="M9 1.5v3h3M6 8h4M6 10.5h4" /></>)
export const Heading = icon(<path d="M3.5 2.5v11M10.5 2.5v11M3.5 8h7M13 7.5v6" />)
export const Paragraph = icon(<path d="M7 13.5v-11h6M10.5 2.5v11M7 2.5H6a3 3 0 0 0 0 6h1" />)
export const Figure = ImageIcon
export const HeaderIcon = icon(<><rect x="2.5" y="1.5" width="11" height="13" rx="1.2" /><path d="M5 4h6" strokeWidth={1.8} /></>)
export const FooterIcon = icon(<><rect x="2.5" y="1.5" width="11" height="13" rx="1.2" /><path d="M5 12h6" strokeWidth={1.8} /></>)
export const ListIcon = Bullets
