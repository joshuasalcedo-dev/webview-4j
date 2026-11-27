// The app's appearance, in one place: which system it looks like, light or dark, and its accent colour. Every
// NativeProvider follows it unless told otherwise, so all of an app's windows change together, at once: the
// windows of one app share their storage, and each is told when another changes it.
import * as React from "react"

import type { Platform, Theme } from "./platform"

export type Appearance = {
  /** The look: "auto" is this computer's system (Windows 11 on Windows, macOS on a Mac). */
  platform: Platform | "auto"
  /** Light or dark: "system" follows the system's setting, and changes when it does. */
  theme: Theme | "system"
  /** Another accent colour than the system's blue (a CSS colour); left out, the system's own. */
  accent?: string
}

export const DEFAULT_APPEARANCE: Appearance = { platform: "auto", theme: "system" }

const KEY = "nt-appearance"
const here = new Set<() => void>()

function read(): Appearance {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? "null") as Partial<Appearance> | null
    return { ...DEFAULT_APPEARANCE, ...(saved ?? {}) }
  } catch {
    return DEFAULT_APPEARANCE
  }
}

// One object per stored value, so React sees a change only when there is one.
let cached = { raw: undefined as string | null | undefined, value: DEFAULT_APPEARANCE }
function current(): Appearance {
  let raw: string | null = null
  try {
    raw = localStorage.getItem(KEY)
  } catch {
    // Without storage the app keeps the defaults.
  }
  if (raw !== cached.raw) cached = { raw, value: read() }
  return cached.value
}

function subscribe(onChange: () => void) {
  here.add(onChange)
  // Another window of the app changed it.
  const other = (event: StorageEvent) => event.key === KEY && onChange()
  window.addEventListener("storage", other)
  return () => {
    here.delete(onChange)
    window.removeEventListener("storage", other)
  }
}

/** Changes the app's appearance: every window follows at once, and it is kept for next time. */
export function setAppearance(change: Partial<Appearance>) {
  const next = { ...current(), ...change }
  try {
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // Without storage the change lasts until the window closes; nothing else depends on it.
  }
  here.forEach((notify) => notify())
}

/** The app's appearance, and what changes it (Preferences › Appearance). */
export function useAppearance(): [Appearance, (change: Partial<Appearance>) => void] {
  const appearance = React.useSyncExternalStore(subscribe, current, () => DEFAULT_APPEARANCE)
  return [appearance, setAppearance]
}
