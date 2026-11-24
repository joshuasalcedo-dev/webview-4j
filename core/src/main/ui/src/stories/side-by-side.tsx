import type { Decorator } from "@storybook/react-vite"
import * as React from "react"

import { NativeProvider } from "../components/provider"
import type { Platform, Theme } from "../lib/platform"

const NAMES: Record<Platform, string> = { windows: "Windows 11", mac: "macOS" }

/** The same controls as each system draws them, next to each other. For the stories. */
export function SideBySide({
  theme,
  stretch = false,
  chrome = false,
  children,
}: {
  theme: Theme
  /** One full-width control per pane (a title bar, a progress bar), not a row of them. */
  stretch?: boolean
  /** A window's edge (title bar, toolbar, tabs, status bar): flush, no padding, at its own height. */
  chrome?: boolean
  children: React.ReactNode
}) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
        gap: 16,
      }}
    >
      {(["windows", "mac"] as const).map((platform) => (
        <div key={platform}>
          <div
            style={{
              marginBottom: 6,
              font: "600 11px system-ui, sans-serif",
              letterSpacing: 0.4,
              textTransform: "uppercase",
              color: "#8a8a8a",
            }}
          >
            {NAMES[platform]} · {theme}
          </div>
          <NativeProvider
            platform={platform}
            theme={theme}
            style={{
              display: "flex",
              flexDirection: stretch || chrome ? "column" : "row",
              flexWrap: "wrap",
              alignItems: stretch || chrome ? "stretch" : "center",
              gap: chrome ? 0 : 12,
              minHeight: chrome ? 0 : 72,
              padding: chrome ? 0 : 20,
              overflow: chrome ? "hidden" : undefined,
              borderRadius: 8,
            }}
          >
            {children}
          </NativeProvider>
        </div>
      ))}
    </div>
  )
}

/** A story decorator: the story on both systems; parameters.nativeTheme "dark" for the dark ones. */
export const bothPlatforms: Decorator = (Story, context) => (
  <SideBySide
    theme={context.parameters.nativeTheme ?? "light"}
    stretch={context.parameters.nativeStretch ?? false}
    chrome={context.parameters.nativeChrome ?? false}
  >
    <Story />
  </SideBySide>
)

export const dark = { nativeTheme: "dark" as const }
