import { Dialog } from "../dialog"

/**
 * The sign-in ran out while the person was working: a dialog over the screen (what is on it stays), to sign in again
 * or to leave. Shown when the server says 401 and the token cannot be renewed.
 */
type SessionExpiredProps = {
  open: boolean
  /** Who was signed in: "You were signed in as Ana". */
  name?: string
  onSignIn?: () => void
  onSignOut?: () => void
  busy?: boolean
}

function SessionExpired({ open, name, onSignIn, onSignOut, busy }: SessionExpiredProps) {
  return (
    <Dialog
      open={open}
      title="Sign in again"
      kind="warning"
      message={`Your sign-in ran out${name ? ` (you were signed in as ${name})` : ""}. Sign in again to go on: what you were doing stays as it is.`}
      actions={[
        { label: busy ? "Signing in…" : "Sign in", primary: true, disabled: busy, onSelect: () => onSignIn?.() },
        { label: "Sign out", cancel: true, onSelect: () => onSignOut?.() },
      ]}
    />
  )
}

export { SessionExpired }
export type { SessionExpiredProps }
