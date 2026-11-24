import { createRoot } from "react-dom/client"
import { StrictMode, useState } from "react"
import { NativeProvider } from "@webview4j/ui/components/provider"
import type { Platform } from "@webview4j/ui/lib/platform"
import { App } from "./App"

export type Look = Platform | "auto"

function Root() {
  // The look: "auto" is the system the app runs on (Windows 11 or macOS); the theme follows the window's
  // (Java sets it, see AppearanceController), so NativeProvider stays on "system".
  const [look, setLook] = useState<Look>("auto")
  return (
    <NativeProvider platform={look} theme="system" style={{ height: "100%" }}>
      <App look={look} onLookChange={setLook} />
    </NativeProvider>
  )
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
