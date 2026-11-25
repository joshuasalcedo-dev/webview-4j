import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { NativeProvider } from "../../provider"
import { PasswordField } from "../../text-inputs"
import { AccountMenu } from "./account-menu"
import { CodeField } from "./code-field"
import { PasswordStrength } from "./password-strength"
import { SessionExpired } from "./session-expired"
import { SignInForm } from "./sign-in-form"

function Authentication({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [busy, setBusy] = React.useState(false)
  const [error, setError] = React.useState<string>()
  const [remember, setRemember] = React.useState(true)
  const [code, setCode] = React.useState("")
  const [password, setPassword] = React.useState("")
  const [expired, setExpired] = React.useState(false)
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24, display: "grid", gap: 32, gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))" }}>
      <SignInForm
        title="Sign in to Office"
        subtitle="With your work account."
        providers={[{ id: "keycloak", label: "Sign in with Keycloak" }, { id: "microsoft", label: "Sign in with Microsoft" }]}
        remember={remember}
        onRememberChange={setRemember}
        busy={busy}
        error={error}
        onForgot={() => setError("A link to reset it is on its way to your email.")}
        onSubmit={(_who, secret) => {
          setBusy(true)
          setError(undefined)
          setTimeout(() => {
            setBusy(false)
            if (secret !== "correct horse") setError("That email and password do not match.")
          }, 700)
        }}
        onProvider={() => setError("The browser opened for Keycloak (in the app).")}
      />
      <div style={{ display: "grid", gap: 16, alignContent: "start" }}>
        <div>Two-step sign-in: the code from your app</div>
        <CodeField value={code} onChange={setCode} onComplete={(c) => setError(`Code ${c} sent`)} />
        <PasswordField label="New password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <PasswordStrength password={password} />
        <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
          <AccountMenu name="Ana Reyes" email="ana@example.com" entries={[{ label: "Profile" }, { label: "Settings" }]} onSwitch={() => {}} onSignOut={() => setExpired(true)} />
          <AccountMenu compact name="Ana Reyes" onSignOut={() => {}} />
        </div>
      </div>
      <SessionExpired open={expired} name="Ana Reyes" onSignIn={() => setExpired(false)} onSignOut={() => setExpired(false)} />
    </NativeProvider>
  )
}

const meta = { title: "Native/Use cases/Authentication", component: Authentication, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof Authentication>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
