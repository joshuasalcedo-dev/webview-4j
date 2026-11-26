import * as React from "react"

import { Button } from "../../button"
import { ProgressBar } from "../../progress-bar"
import { Changelog } from "./changelog"
import "./autoupdate.css"

/**
 * A new version is there: what it is, what changed in it, and what to do. While it downloads, how far; once
 * downloaded and checked, Install and restart; if it failed, why, and Try again.
 */
type UpdateStatus = "available" | "downloading" | "ready" | "installing" | "failed"

type UpdatePromptProps = {
  /** The app's name, for "Notes 0.3.0 is available". */
  app: string
  version: string
  current?: string
  /** The new version's changelog section (Markdown), or a whole changelog with `only` this version. */
  notes?: string
  status?: UpdateStatus
  /** 0 to 1 while downloading. */
  progress?: number
  error?: string
  onUpdate?: () => void
  onInstall?: () => void
  onLater?: () => void
  onSkip?: () => void
  onLink?: (href: string) => void
  className?: string
  style?: React.CSSProperties
}

function UpdatePrompt({
  app,
  version,
  current,
  notes,
  status = "available",
  progress,
  error,
  onUpdate,
  onInstall,
  onLater,
  onSkip,
  onLink,
  className,
  style,
}: UpdatePromptProps) {
  return (
    <div className={["nt-update", className].filter(Boolean).join(" ")} style={style} role="region" aria-label="Update">
      <div className="nt-update-title">
        {status === "ready" ? `${app} ${version} is ready to install` : `${app} ${version} is available`}
      </div>
      {current && <div className="nt-update-subtitle">You have {current}.</div>}
      {notes && (
        <div className="nt-update-notes">
          <Changelog markdown={/^##\s/m.test(notes) ? notes : `## ${version}\n\n${notes}`} only={version} onLink={onLink} />
        </div>
      )}
      {status === "downloading" && <ProgressBar aria-label="Downloading" value={Math.round((progress ?? 0) * 100)} />}
      {status === "installing" && <ProgressBar aria-label="Starting the installer" />}
      {status === "failed" && <div className="nt-update-error" role="alert">{error ?? "The update could not be downloaded."}</div>}
      <div className="nt-update-actions">
        {status === "available" && (
          <>
            <Button variant="accent" onClick={onUpdate}>
              Update now
            </Button>
            <Button onClick={onLater}>Later</Button>
            {onSkip && (
              <Button variant="subtle" onClick={onSkip}>
                Skip this version
              </Button>
            )}
          </>
        )}
        {status === "ready" && (
          <>
            <Button variant="accent" onClick={onInstall}>
              Install and restart
            </Button>
            <Button onClick={onLater}>Later</Button>
          </>
        )}
        {status === "failed" && (
          <>
            <Button variant="accent" onClick={onUpdate}>
              Try again
            </Button>
            <Button onClick={onLater}>Later</Button>
          </>
        )}
      </div>
    </div>
  )
}

export { UpdatePrompt }
export type { UpdatePromptProps, UpdateStatus }
