import * as React from "react"

import { Button } from "../../button"
import { Checkbox } from "../../checkbox"
import { PasswordField } from "../../text-inputs"
import { TextField } from "../../text-field"
import "./authentication.css"

/**
 * Signing in: a name or email and a password, or a provider (Keycloak, Microsoft, Google: the system's browser opens
 * for it), with what went wrong said to the person, and a busy state while it is checked. Enter signs in.
 */
type Provider = { id: string; label: string }

type SignInFormProps = {
  /** "Sign in to Office". */
  title?: React.ReactNode
  subtitle?: React.ReactNode
  /** What the first field asks for: an email (the default) or a user name. */
  identifier?: "email" | "username"
  /** Passwords here (on unless only providers sign in). */
  password?: boolean
  providers?: Provider[]
  /** Remember me: offered when given. */
  remember?: boolean
  onRememberChange?: (remember: boolean) => void
  onSubmit?: (identifier: string, password: string) => void
  onProvider?: (id: string) => void
  onForgot?: () => void
  /** Being checked (or the browser is open for a provider). */
  busy?: boolean
  /** What went wrong, said to the person. */
  error?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

function SignInForm({
  title = "Sign in",
  subtitle,
  identifier = "email",
  password = true,
  providers = [],
  remember,
  onRememberChange,
  onSubmit,
  onProvider,
  onForgot,
  busy = false,
  error,
  className,
  style,
}: SignInFormProps) {
  const [who, setWho] = React.useState("")
  const [secret, setSecret] = React.useState("")
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!busy && who.trim() && secret) onSubmit?.(who.trim(), secret)
  }
  return (
    <form className={["nt-signin", className].filter(Boolean).join(" ")} style={style} onSubmit={submit} aria-busy={busy || undefined}>
      <div className="nt-signin-title">{title}</div>
      {subtitle && <div className="nt-signin-subtitle">{subtitle}</div>}
      {providers.length > 0 && (
        <div className="nt-signin-providers">
          {providers.map((p) => (
            <Button key={p.id} type="button" disabled={busy} onClick={() => onProvider?.(p.id)}>
              {p.label}
            </Button>
          ))}
        </div>
      )}
      {providers.length > 0 && password && <div className="nt-signin-or">or</div>}
      {password && (
        <>
          <TextField
            label={identifier === "email" ? "Email" : "User name"}
            type={identifier === "email" ? "email" : "text"}
            autoComplete={identifier === "email" ? "email" : "username"}
            value={who}
            onChange={(e) => setWho(e.target.value)}
            disabled={busy}
            autoFocus
          />
          <PasswordField label="Password" autoComplete="current-password" value={secret} onChange={(e) => setSecret(e.target.value)} disabled={busy} />
          <div className="nt-signin-row">
            {remember !== undefined && (
              <Checkbox label="Remember me" checked={remember} onChange={(e) => onRememberChange?.(e.target.checked)} disabled={busy} />
            )}
            <span style={{ flex: 1 }} />
            {onForgot && (
              <button type="button" className="nt-link nt-signin-forgot" onClick={onForgot}>
                Forgot password?
              </button>
            )}
          </div>
        </>
      )}
      {error && (
        <div className="nt-signin-error" role="alert">
          {error}
        </div>
      )}
      {password && (
        <Button variant="accent" type="submit" disabled={busy || !who.trim() || !secret}>
          {busy ? "Signing in…" : "Sign in"}
        </Button>
      )}
    </form>
  )
}

export { SignInForm }
export type { Provider, SignInFormProps }
