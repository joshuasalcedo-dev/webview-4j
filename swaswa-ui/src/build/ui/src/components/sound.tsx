import * as React from "react"

import type { Platform } from "../lib/platform"

/**
 * The sounds an app plays at moments people should notice:
 * - error: something failed (with an error dialog);
 * - warning: something to be careful about (with a warning dialog);
 * - info: something to know (Windows plays one with a message box; a Mac does not);
 * - message: a message came in;
 * - sent: a message went out;
 * - notify: something finished or needs a look, while the app is in the background;
 * - done: a long piece of work finished;
 * - tap: a soft click, for a toggle or a drop.
 */
type SoundName =
  "error" | "warning" | "info" | "message" | "sent" | "notify" | "done" | "tap"

/** The app's own sound files instead of the built-in ones, by name (a URL each: "/sounds/ping.ogg"). */
type SoundFiles = Partial<Record<SoundName, string>>

type SoundApi = {
  /** Plays a sound now; nothing when sounds are off. */
  play: (name: SoundName) => void
  enabled: boolean
}

type Note = {
  /** Hz; two numbers glide from the first to the second. */
  frequency: number | [number, number]
  /** Seconds after the start. */
  at: number
  /** Seconds it sounds. */
  length: number
  /** 0 to 1. */
  volume?: number
  wave?: OscillatorType
}

// The built-in sounds are made here, not recorded: both systems' own sounds are theirs to ship, not ours.
// Windows' are low, soft and chime-like; a Mac's are short, bright clicks and pops.
const RECIPES: Record<Platform, Record<SoundName, Note[]>> = {
  windows: {
    error: [
      { frequency: 523, at: 0, length: 0.22, volume: 0.5 },
      { frequency: 392, at: 0.12, length: 0.4, volume: 0.5 },
    ],
    warning: [
      { frequency: 659, at: 0, length: 0.18, volume: 0.45 },
      { frequency: 587, at: 0.1, length: 0.35, volume: 0.45 },
    ],
    info: [
      { frequency: 784, at: 0, length: 0.2, volume: 0.35 },
      { frequency: 1047, at: 0.09, length: 0.35, volume: 0.3 },
    ],
    message: [
      { frequency: 880, at: 0, length: 0.14, volume: 0.35 },
      { frequency: 1175, at: 0.08, length: 0.28, volume: 0.3 },
    ],
    sent: [{ frequency: [600, 1100], at: 0, length: 0.12, volume: 0.22 }],
    notify: [
      { frequency: 659, at: 0, length: 0.16, volume: 0.35 },
      { frequency: 988, at: 0.1, length: 0.16, volume: 0.32 },
      { frequency: 1319, at: 0.2, length: 0.32, volume: 0.28 },
    ],
    done: [
      { frequency: 784, at: 0, length: 0.18, volume: 0.32 },
      { frequency: 988, at: 0.1, length: 0.18, volume: 0.3 },
      { frequency: 1175, at: 0.2, length: 0.4, volume: 0.3 },
    ],
    tap: [
      { frequency: 1400, at: 0, length: 0.03, volume: 0.15, wave: "triangle" },
    ],
  },
  mac: {
    error: [
      { frequency: 220, at: 0, length: 0.12, volume: 0.5, wave: "square" },
      { frequency: 196, at: 0.1, length: 0.16, volume: 0.45, wave: "square" },
    ],
    warning: [
      {
        frequency: [1200, 900],
        at: 0,
        length: 0.14,
        volume: 0.4,
        wave: "triangle",
      },
    ],
    info: [],
    message: [
      { frequency: 1568, at: 0, length: 0.08, volume: 0.3, wave: "triangle" },
      {
        frequency: 2093,
        at: 0.07,
        length: 0.18,
        volume: 0.25,
        wave: "triangle",
      },
    ],
    sent: [{ frequency: [400, 1600], at: 0, length: 0.16, volume: 0.25 }],
    notify: [{ frequency: 2637, at: 0, length: 0.5, volume: 0.25 }],
    done: [
      { frequency: 1319, at: 0, length: 0.1, volume: 0.28, wave: "triangle" },
      {
        frequency: 1976,
        at: 0.08,
        length: 0.3,
        volume: 0.25,
        wave: "triangle",
      },
    ],
    tap: [
      { frequency: 2200, at: 0, length: 0.02, volume: 0.15, wave: "triangle" },
    ],
  },
}

let context: AudioContext | null = null

function audio(): AudioContext | null {
  if (typeof window === "undefined" || !("AudioContext" in window)) return null
  context ??= new AudioContext()
  // A browser keeps sound off until the person has clicked or typed; afterwards this lets it play.
  if (context.state === "suspended") void context.resume()
  return context
}

function playNotes(notes: Note[]) {
  const ctx = audio()
  if (!ctx || notes.length === 0) return
  const start = ctx.currentTime + 0.01
  for (const note of notes) {
    const oscillator = ctx.createOscillator()
    const gain = ctx.createGain()
    oscillator.type = note.wave ?? "sine"
    const t = start + note.at
    if (Array.isArray(note.frequency)) {
      oscillator.frequency.setValueAtTime(note.frequency[0], t)
      oscillator.frequency.exponentialRampToValueAtTime(
        note.frequency[1],
        t + note.length
      )
    } else {
      oscillator.frequency.setValueAtTime(note.frequency, t)
    }
    // A quick rise and a soft fade, so nothing clicks.
    const peak = (note.volume ?? 0.3) * 0.25
    gain.gain.setValueAtTime(0, t)
    gain.gain.linearRampToValueAtTime(peak, t + 0.008)
    gain.gain.exponentialRampToValueAtTime(0.0001, t + note.length)
    oscillator.connect(gain).connect(ctx.destination)
    oscillator.start(t)
    oscillator.stop(t + note.length + 0.02)
  }
}

function playFile(url: string) {
  const sound = new Audio(url)
  sound.volume = 0.6
  // A file that can't play (blocked before a click, missing) is skipped: a sound is never worth an error.
  sound.play().catch(() => undefined)
}

function createSounds(
  platform: Platform,
  enabled: boolean,
  files: SoundFiles = {}
): SoundApi {
  return {
    enabled,
    play(name) {
      if (!enabled) return
      const file = files[name]
      if (file) playFile(file)
      else playNotes(RECIPES[platform][name])
    },
  }
}

const SoundContext = React.createContext<SoundApi>({
  play: () => undefined,
  enabled: false,
})

/**
 * Plays the app's sounds: const sound = useSound(); sound.play("message"). They follow the platform
 * (Windows' or a Mac's kind of sound) and are off when NativeProvider has sounds={false}. Dialogs play
 * theirs on their own (error, warning; info on Windows).
 */
function useSound(): SoundApi {
  return React.useContext(SoundContext)
}

export { createSounds, SoundContext, useSound }
export type { SoundApi, SoundFiles, SoundName }
