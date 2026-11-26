import { Avatar } from "../../avatar"
import { type MenuEntry, useContextMenu } from "../../context-menu"
import "./authentication.css"

/**
 * Who is signed in, for a title bar or a side bar: an avatar (and the name, unless compact) that opens a menu with
 * the account's email, the app's entries (Profile, Settings), Switch account and Sign out.
 */
type AccountMenuProps = {
  name: string
  email?: string
  /** A picture's address; the initials of the name otherwise. */
  picture?: string
  /** Only the avatar (a title bar). */
  compact?: boolean
  /** The app's own entries, above Switch account and Sign out. */
  entries?: MenuEntry[]
  onSwitch?: () => void
  onSignOut?: () => void
  className?: string
}

function AccountMenu({ name, email, picture, compact = false, entries = [], onSwitch, onSignOut, className }: AccountMenuProps) {
  const menu = (): MenuEntry[] => {
    const all: MenuEntry[] = []
    if (email) all.push({ label: email, disabled: true }, "separator")
    all.push(...entries)
    if (entries.length) all.push("separator")
    if (onSwitch) all.push({ label: "Switch account…", onSelect: onSwitch })
    if (onSignOut) all.push({ label: "Sign out", onSelect: onSignOut })
    return all
  }
  const { onContextMenu } = useContextMenu(menu)
  return (
    <button
      type="button"
      className={["nt-account", className].filter(Boolean).join(" ")}
      aria-label={`Account: ${name}`}
      aria-haspopup="menu"
      onClick={(e) => onContextMenu(e)}
      onContextMenu={(e) => onContextMenu(e)}
    >
      {picture ? <img className="nt-account-picture" src={picture} alt="" /> : <Avatar name={name} size={compact ? 24 : 28} />}
      {!compact && <span className="nt-account-name">{name}</span>}
    </button>
  )
}

export { AccountMenu }
export type { AccountMenuProps }
