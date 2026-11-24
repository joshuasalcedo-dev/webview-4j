// The chat's small glyphs, drawn here (no icon library): strokes in currentColor, 16 units square.
const line = {
  viewBox: "0 0 16 16",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.3,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const

export const ClockIcon = () => (
  <svg {...line}>
    <circle cx="8" cy="8" r="5.5" />
    <path d="M8 5v3l2 1.5" />
  </svg>
)

export const CheckIcon = () => (
  <svg {...line}>
    <path d="m3 8.5 3 3 7-7" />
  </svg>
)

export const DoubleCheckIcon = () => (
  <svg {...line}>
    <path d="m1.5 8.5 3 3 7-7M7.5 11.5l7-7" />
  </svg>
)

export const AlertIcon = () => (
  <svg {...line}>
    <circle cx="8" cy="8" r="6" />
    <path d="M8 4.8v3.8M8 11v.2" />
  </svg>
)

export const PinIcon = () => (
  <svg {...line}>
    <path d="M6 2.5h4M7 2.5l-.5 4-2 2h7l-2-2-.5-4M8 8.5v5" />
  </svg>
)

export const MutedIcon = () => (
  <svg {...line}>
    <path d="M4.5 6.5a3.5 3.5 0 0 1 5.6-2.8M11.5 7v2.5l1.5 2H5M6.8 13.5a1.4 1.4 0 0 0 2.4 0M2.5 2.5l11 11" />
  </svg>
)

export const PhotoIcon = () => (
  <svg {...line}>
    <rect x="2" y="3" width="12" height="10" rx="2" />
    <circle cx="6" cy="6.5" r="1.2" />
    <path d="m2.5 12 4-4 3 3 2-2 2.5 2.5" />
  </svg>
)

export const FileIconSmall = () => (
  <svg {...line}>
    <path d="M4 1.5h5l3.5 3.5v9.5H4zM9 1.5V5h3.5" />
  </svg>
)

export const MicIcon = () => (
  <svg {...line}>
    <rect x="6" y="1.5" width="4" height="8" rx="2" />
    <path d="M3.5 7.5a4.5 4.5 0 0 0 9 0M8 12v2.5" />
  </svg>
)

export const PhoneIcon = () => (
  <svg {...line}>
    <path d="M3 2.5h2.5l1.2 3-1.5 1a7 7 0 0 0 3.3 3.3l1-1.5 3 1.2V12a1.5 1.5 0 0 1-1.5 1.5A10 10 0 0 1 1.5 4 1.5 1.5 0 0 1 3 2.5z" />
  </svg>
)

export const VideoIcon = () => (
  <svg {...line}>
    <rect x="1.5" y="4" width="9" height="8" rx="1.5" />
    <path d="m10.5 7 4-2.5v7l-4-2.5" />
  </svg>
)

export const SearchIcon = () => (
  <svg {...line}>
    <circle cx="7" cy="7" r="4.5" />
    <path d="m10.5 10.5 3.5 3.5" />
  </svg>
)

export const InfoIcon = () => (
  <svg {...line}>
    <circle cx="8" cy="8" r="6.5" />
    <path d="M8 7.2v4M8 4.8v.1" />
  </svg>
)

export const ReplyIcon = () => (
  <svg {...line}>
    <path d="M6.5 4 2.5 8l4 4M2.5 8h7a4 4 0 0 1 4 4v1" />
  </svg>
)

export const ForwardIcon = () => (
  <svg {...line}>
    <path d="M9.5 4l4 4-4 4M13.5 8h-7a4 4 0 0 0-4 4v1" />
  </svg>
)

export const SmilePlusIcon = () => (
  <svg {...line}>
    <path d="M13.3 7.2A5.5 5.5 0 1 1 8.8 2.6" />
    <path d="M5.5 9.5a3 3 0 0 0 5 0M6 6.3v.1M10 6.3v.1M12.5 1.5v4M10.5 3.5h4" />
  </svg>
)

export const MoreIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
    <circle cx="3.5" cy="8" r="1.2" />
    <circle cx="8" cy="8" r="1.2" />
    <circle cx="12.5" cy="8" r="1.2" />
  </svg>
)

export const PlayIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
    <path d="M4.5 2.8v10.4L13 8z" />
  </svg>
)

export const PauseIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
    <rect x="4" y="3" width="3" height="10" rx="0.8" />
    <rect x="9" y="3" width="3" height="10" rx="0.8" />
  </svg>
)

export const ArrowDownIcon = () => (
  <svg {...line}>
    <path d="M8 2.5v11M3.5 9 8 13.5 12.5 9" />
  </svg>
)

export const EditIcon = () => (
  <svg {...line}>
    <path d="M11 2.5l2.5 2.5L6 12.5H3.5V10z" />
  </svg>
)

export const CloseSmallIcon = () => (
  <svg {...line}>
    <path d="m4 4 8 8M12 4l-8 8" />
  </svg>
)

export const ArchiveIcon = () => (
  <svg {...line}>
    <rect x="1.5" y="2.5" width="13" height="3" rx="0.5" />
    <path d="M2.5 5.5v7a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1v-7M6.5 8.5h3" />
  </svg>
)

export const MarkUnreadIcon = () => (
  <svg {...line}>
    <path d="M13.5 7.5V12a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 12V5A1.5 1.5 0 0 1 4 3.5h5.5M2.5 5l5.5 4 2.2-1.6" />
    <circle cx="12.5" cy="3.5" r="2" fill="currentColor" stroke="none" />
  </svg>
)

export const TrashIcon = () => (
  <svg {...line}>
    <path d="M2.5 4h11M6 4V2.5h4V4M4 4l.7 9.5h6.6L12 4M6.8 6.5v4.5M9.2 6.5v4.5" />
  </svg>
)

export const TagIcon = () => (
  <svg {...line}>
    <path d="M8.5 1.5h5v5l-7 7-5-5zM11 4v.1" />
  </svg>
)

export const BlockIcon = () => (
  <svg {...line}>
    <circle cx="8" cy="8" r="6" />
    <path d="m3.8 3.8 8.4 8.4" />
  </svg>
)

export const FlagIcon = () => (
  <svg {...line}>
    <path d="M3 14.5V2M3 2.5h8.5l-2 3 2 3H3" />
  </svg>
)

export const BellIcon = () => (
  <svg {...line}>
    <path d="M4.5 6.5a3.5 3.5 0 0 1 7 0v3l1.5 2H3l1.5-2zM6.8 13.5a1.4 1.4 0 0 0 2.4 0" />
  </svg>
)

export const EmojiIcon = () => (
  <svg {...line}>
    <circle cx="8" cy="8" r="6" />
    <path d="M5.5 9.5a3 3 0 0 0 5 0M6 6.3v.1M10 6.3v.1" />
  </svg>
)

export const LockIcon = () => (
  <svg {...line}>
    <rect x="3" y="7" width="10" height="7.5" rx="1.5" />
    <path d="M5 7V5a3 3 0 0 1 6 0v2" />
  </svg>
)

export const CoinIcon = () => (
  <svg {...line}>
    <circle cx="8" cy="8" r="6" />
    <path d="M9.8 5.8c-.4-.5-1-.8-1.8-.8-1 0-1.8.6-1.8 1.4S7 7.6 8 7.8s1.8.6 1.8 1.4S9 10.8 8 10.8c-.8 0-1.5-.3-1.9-.9M8 4v1M8 10.8V12" />
  </svg>
)
