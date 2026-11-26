import { ProgressBar } from "../../../progress-bar"
import { Text } from "../../../text"

/** The running bar while a file loads. */
export function FileLoading({ label }: { label: string }) {
  return (
    <div className="nt-xlsx-state">
      <ProgressBar aria-label={label} className="nt-xlsx-progress" />
    </div>
  )
}

/** Why there is nothing to show. */
export function FileState({ title, message }: { title: string; message?: string }) {
  return (
    <div className="nt-xlsx-state" role={title ? "status" : undefined}>
      <Text variant="headline">{title}</Text>
      {message && <Text tone="secondary">{message}</Text>}
    </div>
  )
}
