/** The systems ui-native looks like. Linux and anything else get the Windows look. */
export type Platform = "windows" | "mac"

/** Light or dark, as resolved (a NativeProvider's "system" becomes one of these). */
export type Theme = "light" | "dark"

/** The system this runs on, from what the browser (or the Tauri webview) says about it. */
export function detectPlatform(): Platform {
  if (typeof navigator === "undefined") return "windows"
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } }
  const said = (nav.userAgentData?.platform || nav.platform || nav.userAgent || "").toLowerCase()
  return said.includes("mac") || said.includes("iphone") || said.includes("ipad") ? "mac" : "windows"
}
