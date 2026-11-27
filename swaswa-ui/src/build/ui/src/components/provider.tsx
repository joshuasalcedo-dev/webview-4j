import * as React from "react"

import "../styles/native.css"
import "../styles/navigation.css"
import "../styles/sidebar-parts.css"
import "../styles/dialog.css"
import "../styles/files.css"
import "../styles/text.css"
import "../styles/inputs.css"
import "../styles/scroll.css"
import { cn } from "../lib/utils"
import { useAppearance } from "../lib/appearance"
import { detectPlatform, type Platform, type Theme } from "../lib/platform"
import { growSelection } from "../lib/smart-select"
import { ContextMenuHost } from "./context-menu"
import { DialogHost } from "./dialog"
import { createSounds, type SoundFiles, SoundContext } from "./sound"

type Native = { platform: Platform; theme: Theme }

/** What the pointer says while something runs: "progress" (the app still answers) or "wait" (it does not). */
type Busy = "progress" | "wait"

type BusyApi = <T>(work: Promise<T>, kind?: Busy) => Promise<T>

const NativeContext = React.createContext<Native | null>(null)
const BusyContext = React.createContext<BusyApi | null>(null)

const DARK = "(prefers-color-scheme: dark)"

function subscribeToScheme(onChange: () => void) {
  const query = window.matchMedia(DARK)
  query.addEventListener("change", onChange)
  return () => query.removeEventListener("change", onChange)
}

function useSystemTheme(): Theme {
  return React.useSyncExternalStore(
    subscribeToScheme,
    () => (window.matchMedia(DARK).matches ? "dark" : "light"),
    () => "light"
  )
}

type NativeProviderProps = React.ComponentProps<"div"> & {
  /** Which system to look like ("auto": the one this runs on). Left out: the app's appearance (useAppearance). */
  platform?: Platform | "auto"
  /** Light or dark ("system" follows the system, and changes when it does). Left out: the app's appearance. */
  theme?: Theme | "system"
  /** Another accent colour than the system's default blue, e.g. a brand colour. Left out: the app's appearance. */
  accent?: string
  /**
   * The pointer while the app works: true or "progress" for the arrow with a spinner (it still answers),
   * "wait" for the spinner alone (it does not). useBusy() sets it around a promise instead.
   */
  busy?: boolean | Busy
  /** Keep the webview's own right-click menu (Back, Reload, Inspect) where no component has one: for debugging. */
  browserMenu?: boolean
  /**
   * The app's sounds (useSound(), and the dialogs' own): on by default, false for none (e.g. a "Sounds"
   * setting turned off), or the app's own files for some of them ({ message: "/sounds/ping.ogg" }).
   */
  sounds?: boolean | SoundFiles
}

/**
 * Where ui-native's controls get their look and behaviour: everything inside looks like Windows 11 on
 * Windows and like macOS on a Mac, light or dark as the system is. A right-click shows the system's kind of
 * menu (the edit menu on text, a component's own menu, or nothing), never the webview's; the pointer is the
 * arrow, the I-beam over text, and the busy pointer while the app works. Put it around the app; it is a div
 * with the window's background colour.
 */
/**
 * Whether the person last used the keyboard or a pointer, on the page's root element (data-nt-input). The
 * keyboard focus ring is drawn only after keyboard use: when a component moves the focus for the person
 * (a panel opening, a menu closing), the browser would otherwise draw the ring after a mouse click too.
 */
let inputWatchers = 0
const keyboard = (event: KeyboardEvent) => {
  // Modifier keys alone (Ctrl in Ctrl+click) are not keyboard use.
  if (!["Control", "Meta", "Shift", "Alt"].includes(event.key))
    document.documentElement.dataset.ntInput = "keyboard"
}
const pointer = () => {
  document.documentElement.dataset.ntInput = "pointer"
}
function watchInput() {
  if (inputWatchers++ === 0) {
    document.addEventListener("keydown", keyboard, true)
    document.addEventListener("pointerdown", pointer, true)
  }
  return () => {
    if (--inputWatchers === 0) {
      document.removeEventListener("keydown", keyboard, true)
      document.removeEventListener("pointerdown", pointer, true)
    }
  }
}

function NativeProvider({
  platform: forcedPlatform,
  theme: forcedTheme,
  accent: forcedAccent,
  busy,
  browserMenu = false,
  sounds = true,
  onMouseDownCapture,
  className,
  style,
  children,
  ...props
}: NativeProviderProps) {
  const systemTheme = useSystemTheme()
  // The app's appearance (one place for all its windows), unless this provider is told otherwise.
  const [appearance] = useAppearance()
  const platform = forcedPlatform ?? appearance.platform
  const theme = forcedTheme ?? appearance.theme
  const accent = forcedAccent ?? appearance.accent
  React.useEffect(() => watchInput(), [])
  const resolved: Native = React.useMemo(
    () => ({
      platform: platform === "auto" ? detectPlatform() : platform,
      theme: theme === "system" ? systemTheme : theme,
    }),
    [platform, theme, systemTheme]
  )

  const soundApi = React.useMemo(
    () =>
      createSounds(
        resolved.platform,
        sounds !== false,
        typeof sounds === "object" ? sounds : undefined
      ),
    [resolved.platform, sounds]
  )

  // How many pieces of work useBusy() is waiting for, of each kind.
  const [running, setRunning] = React.useState({ progress: 0, wait: 0 })
  const during = React.useCallback<BusyApi>(async (work, kind = "progress") => {
    setRunning((count) => ({ ...count, [kind]: count[kind] + 1 }))
    try {
      return await work
    } finally {
      setRunning((count) => ({ ...count, [kind]: count[kind] - 1 }))
    }
  }, [])
  const pointer: Busy | undefined =
    busy === "wait" || running.wait > 0
      ? "wait"
      : busy || running.progress > 0
        ? "progress"
        : undefined

  return (
    <NativeContext.Provider value={resolved}>
      <BusyContext.Provider value={during}>
        <SoundContext.Provider value={soundApi}>
          <div
            data-platform={resolved.platform}
            data-theme={resolved.theme}
            data-busy={pointer}
            className={cn("nt-root", className)}
            onMouseDownCapture={(event) => {
              // Each click after a double-click grows the selection: sentence, paragraph, everything.
              growSelection(event.nativeEvent)
              onMouseDownCapture?.(event)
            }}
            style={
              accent
                ? ({ ...style, "--nt-accent": accent } as React.CSSProperties)
                : style
            }
            {...props}
          >
            <ContextMenuHost
              platform={resolved.platform}
              browserMenu={browserMenu}
            >
              <DialogHost>{children}</DialogHost>
            </ContextMenuHost>
          </div>
        </SoundContext.Provider>
      </BusyContext.Provider>
    </NativeContext.Provider>
  )
}

/** The platform and theme the nearest NativeProvider resolved (outside one: this system, light). */
function useNative(): Native {
  return (
    React.useContext(NativeContext) ?? {
      platform: detectPlatform(),
      theme: "light",
    }
  )
}

/**
 * Shows the busy pointer while a promise runs: const busy = useBusy(); await busy(send(message)).
 * "progress" (the default) is the arrow with a spinner, for work the app goes on answering during;
 * "wait" is the spinner alone. Outside a NativeProvider it just waits.
 */
function useBusy(): BusyApi {
  return React.useContext(BusyContext) ?? ((work) => work)
}

export { NativeContext, NativeProvider, useBusy, useNative }
export type { Busy, NativeProviderProps, Platform, Theme }
