import * as React from "react"

import { cn } from "../lib/utils"
import "./media-player.css"

type MediaPlayerProps = Omit<React.ComponentProps<"div">, "onPlay" | "onPause" | "onEnded" | "onError" | "children"> & {
  /** The sound or video: an app file ("app/intro.mp4"), a data: or blob: URL, or an address. */
  src: string
  /** "video" shows the picture; "audio" only the controls. Left out, it goes by the file's extension. */
  kind?: "audio" | "video"
  /** What it is, for the controls and screen readers ("Intro", "Voicemail from Ana"). */
  title?: string
  /** A picture shown before a video plays. */
  poster?: string
  /** Captions or subtitles (a WebVTT file), shown on the video. */
  captions?: string
  /** The captions' language ("en"); English unless set. */
  captionsLanguage?: string
  autoPlay?: boolean
  loop?: boolean
  /** Starts with the sound off (a video that plays by itself must). */
  muted?: boolean
  /** 0 to 1 (1 unless set). */
  volume?: number
  /** Playback speed, 0.25 to 4 (1 unless set). */
  rate?: number
  /** Shows the speed button (on unless false). */
  showRate?: boolean
  onPlay?: () => void
  onPause?: () => void
  /** It played to the end. */
  onEnded?: () => void
  /** The time, in seconds, when the person seeks (not as it plays, which would be many times a second). */
  onSeek?: (seconds: number) => void
  /** It could not be played: why. */
  onError?: (message: string) => void
}

const VIDEO = /\.(mp4|m4v|webm|ogv|mov)(\?|#|$)/i
const RATES = [0.5, 0.75, 1, 1.25, 1.5, 2]

/** 75 -> "1:15", 3725 -> "1:02:05". */
function clock(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00"
  const s = Math.floor(seconds % 60)
  const m = Math.floor(seconds / 60) % 60
  const h = Math.floor(seconds / 3600)
  const two = (n: number) => String(n).padStart(2, "0")
  return h > 0 ? `${h}:${two(m)}:${two(s)}` : `${m}:${two(s)}`
}

const Icon = {
  play: <path d="M5 3.5v9l7.5-4.5z" fill="currentColor" stroke="none" />,
  pause: <path d="M5 3.5v9M11 3.5v9" strokeWidth={2.2} />,
  volume: <><path d="M2.5 6h2.5l3.5-3v10l-3.5-3H2.5z" fill="currentColor" stroke="none" /><path d="M11 5.5a3.5 3.5 0 0 1 0 5M12.8 3.8a6 6 0 0 1 0 8.4" /></>,
  muted: <><path d="M2.5 6h2.5l3.5-3v10l-3.5-3H2.5z" fill="currentColor" stroke="none" /><path d="m11 6 3.5 4M14.5 6 11 10" /></>,
  full: <path d="M2.5 6V2.5H6M10 2.5h3.5V6M13.5 10v3.5H10M6 13.5H2.5V10" />,
}

function Glyph({ children }: { children: React.ReactNode }) {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      {children}
    </svg>
  )
}

/**
 * A sound or a video with the system's kind of controls: play and pause, a bar to seek on, the time, the volume
 * and mute, the speed, and full screen for a video. Keys while it has the focus: Space or K plays and pauses, ← and
 * → go back and on 5 s, ↑ and ↓ change the volume, M mutes, F goes full screen.
 */
function MediaPlayer({
  src,
  kind,
  title,
  poster,
  captions,
  captionsLanguage = "en",
  autoPlay,
  loop,
  muted: startMuted,
  volume: volumeProp = 1,
  rate: rateProp = 1,
  showRate = true,
  onPlay,
  onPause,
  onEnded,
  onSeek,
  onError,
  className,
  ...props
}: MediaPlayerProps) {
  const video = (kind ?? (VIDEO.test(src) ? "video" : "audio")) === "video"
  const media = React.useRef<HTMLVideoElement & HTMLAudioElement>(null)
  const root = React.useRef<HTMLDivElement>(null)
  const [playing, setPlaying] = React.useState(false)
  const [at, setAt] = React.useState(0)
  const [duration, setDuration] = React.useState(0)
  const [volume, setVolume] = React.useState(volumeProp)
  const [muted, setMuted] = React.useState(!!startMuted)
  const [rate, setRate] = React.useState(rateProp)
  const [error, setError] = React.useState<string>()

  React.useEffect(() => setVolume(volumeProp), [volumeProp])
  React.useEffect(() => setRate(rateProp), [rateProp])
  React.useEffect(() => setMuted(!!startMuted), [startMuted])
  React.useEffect(() => {
    const m = media.current
    if (!m) return
    m.volume = Math.min(1, Math.max(0, volume))
    m.muted = muted
    m.playbackRate = Math.min(4, Math.max(0.25, rate))
  }, [volume, muted, rate])
  React.useEffect(() => setError(undefined), [src])

  const toggle = () => {
    const m = media.current
    if (!m) return
    if (m.paused) void m.play().catch((e: Error) => fail(e.message))
    else m.pause()
  }
  const seek = (seconds: number) => {
    const m = media.current
    if (!m || !Number.isFinite(m.duration)) return
    m.currentTime = Math.min(m.duration, Math.max(0, seconds))
    setAt(m.currentTime)
    onSeek?.(m.currentTime)
  }
  const fail = (message: string) => {
    setError(message)
    setPlaying(false)
    onError?.(message)
  }
  const fullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void root.current?.requestFullscreen?.()
  }

  const onKey = (e: React.KeyboardEvent) => {
    if ((e.target as HTMLElement).tagName === "INPUT" && e.key !== " ") return
    const handled = (() => {
      switch (e.key) {
        case " ":
        case "k":
          toggle()
          return true
        case "ArrowLeft":
          seek(at - 5)
          return true
        case "ArrowRight":
          seek(at + 5)
          return true
        case "ArrowUp":
          setVolume((v) => Math.min(1, v + 0.1))
          setMuted(false)
          return true
        case "ArrowDown":
          setVolume((v) => Math.max(0, v - 0.1))
          return true
        case "m":
          setMuted((m) => !m)
          return true
        case "f":
          if (video) fullscreen()
          return video
        default:
          return false
      }
    })()
    if (handled) e.preventDefault()
  }

  const Media = video ? "video" : "audio"
  const label = title ?? (video ? "Video" : "Sound")
  return (
    <div
      ref={root}
      role="group"
      aria-label={label}
      tabIndex={0}
      onKeyDown={onKey}
      data-kind={video ? "video" : "audio"}
      data-playing={playing ? "" : undefined}
      className={cn("nt-media", className)}
      {...props}
    >
      <Media
        ref={media}
        src={src}
        poster={video ? poster : undefined}
        autoPlay={autoPlay}
        loop={loop}
        muted={muted}
        preload="metadata"
        playsInline
        className="nt-media-element"
        onClick={video ? toggle : undefined}
        onPlay={() => {
          setPlaying(true)
          onPlay?.()
        }}
        onPause={() => {
          setPlaying(false)
          onPause?.()
        }}
        onEnded={() => {
          setPlaying(false)
          onEnded?.()
        }}
        onTimeUpdate={(e) => setAt(e.currentTarget.currentTime)}
        onLoadedMetadata={(e) => setDuration(e.currentTarget.duration)}
        onDurationChange={(e) => setDuration(e.currentTarget.duration)}
        onError={(e) => fail(e.currentTarget.error?.message || "This file cannot be played here.")}
      >
        {video && captions && <track kind="captions" src={captions} srcLang={captionsLanguage} label={captionsLanguage} default />}
      </Media>
      {error && (
        <div role="alert" className="nt-media-error">
          {error}
        </div>
      )}
      <div className="nt-media-controls">
        <button type="button" className="nt-media-button nt-media-play" aria-label={playing ? "Pause" : "Play"} onClick={toggle}>
          <Glyph>{playing ? Icon.pause : Icon.play}</Glyph>
        </button>
        <span className="nt-media-time" aria-live="off">
          {clock(at)}
        </span>
        <input
          type="range"
          className="nt-media-seek"
          aria-label="Seek"
          min={0}
          max={Number.isFinite(duration) && duration > 0 ? duration : 0}
          step="any"
          value={at}
          aria-valuetext={`${clock(at)} of ${clock(duration)}`}
          style={{ "--nt-media-done": `${duration > 0 ? (at / duration) * 100 : 0}%` } as React.CSSProperties}
          onChange={(e) => seek(Number(e.currentTarget.value))}
        />
        <span className="nt-media-time">{clock(duration)}</span>
        <button type="button" className="nt-media-button" aria-label={muted ? "Unmute" : "Mute"} onClick={() => setMuted((m) => !m)}>
          <Glyph>{muted || volume === 0 ? Icon.muted : Icon.volume}</Glyph>
        </button>
        <input
          type="range"
          className="nt-media-volume"
          aria-label="Volume"
          min={0}
          max={1}
          step={0.05}
          value={muted ? 0 : volume}
          style={{ "--nt-media-done": `${(muted ? 0 : volume) * 100}%` } as React.CSSProperties}
          onChange={(e) => {
            setVolume(Number(e.currentTarget.value))
            setMuted(false)
          }}
        />
        {showRate && (
          <button
            type="button"
            className="nt-media-button nt-media-rate"
            aria-label={`Speed ${rate}×`}
            title="Speed"
            onClick={() => setRate((r) => RATES[(RATES.indexOf(r) + 1) % RATES.length] ?? 1)}
          >
            {rate}×
          </button>
        )}
        {video && (
          <button type="button" className="nt-media-button" aria-label="Full screen" onClick={fullscreen}>
            <Glyph>{Icon.full}</Glyph>
          </button>
        )}
      </div>
    </div>
  )
}

export { clock, MediaPlayer }
export type { MediaPlayerProps }
