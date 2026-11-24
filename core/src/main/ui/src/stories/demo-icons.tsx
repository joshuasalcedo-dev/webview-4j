// Example 16px icons for the stories only (the apps bring their own).
const props = {
  viewBox: "0 0 16 16",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const

export const ChatIcon = () => (
  <svg {...props}>
    <path d="M2.5 3.5h11v7h-6l-3 2.5v-2.5h-2z" />
  </svg>
)

export const PeopleIcon = () => (
  <svg {...props}>
    <circle cx="6" cy="5.5" r="2.5" />
    <path d="M1.5 13.5c.5-2.5 2.3-3.8 4.5-3.8s4 1.3 4.5 3.8M10.5 3.2a2.4 2.4 0 0 1 0 4.6M12 9.9c1.3.5 2.2 1.7 2.5 3.6" />
  </svg>
)

export const StarIcon = () => (
  <svg {...props}>
    <path d="m8 2 1.8 3.8 4.2.5-3.1 2.9.8 4.1L8 11.3l-3.7 2 .8-4.1L2 6.3l4.2-.5z" />
  </svg>
)

export const ArchiveIcon = () => (
  <svg {...props}>
    <rect x="1.5" y="2.5" width="13" height="3" rx="0.5" />
    <path d="M2.5 5.5v7a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1v-7M6.5 8.5h3" />
  </svg>
)

export const GearIcon = () => (
  <svg {...props}>
    <circle cx="8" cy="8" r="2.2" />
    <path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M3.4 12.6l1.4-1.4M11.2 4.8l1.4-1.4" />
  </svg>
)

export const ComposeIcon = () => (
  <svg {...props}>
    <path d="M13.5 9v4a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1h4M11.5 1.8l2.7 2.7L8 10.7H5.3V8z" />
  </svg>
)

export const InfoIcon = () => (
  <svg {...props}>
    <circle cx="8" cy="8" r="6.5" />
    <path d="M8 7.2v4M8 4.8v.1" />
  </svg>
)

export const FolderIcon = () => (
  <svg {...props}>
    <path d="M1.5 4a1 1 0 0 1 1-1h3.5l1.5 1.5h6a1 1 0 0 1 1 1V12a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1z" />
  </svg>
)

/** An example app logo: an accent square with a chat bubble, as an app icon is drawn. */
export const AppLogo = () => (
  <svg viewBox="0 0 16 16" aria-hidden="true">
    <rect width="16" height="16" rx="3.5" fill="#0f6cbd" />
    <path d="M4 4.5h8v5H8l-2.5 2v-2H4z" fill="#ffffff" />
  </svg>
)
