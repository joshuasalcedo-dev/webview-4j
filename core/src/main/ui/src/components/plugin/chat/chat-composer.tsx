import * as React from "react"

import { cn } from "../../../lib/utils"
import { Composer, type ComposerProps } from "../../composer"
import {
  type ComposerToolContext,
  defaultComposerTools,
} from "../../composer-toolbar"
import { useChatEvent } from "./chat-provider"
import { type ChatCapability, useChatCapabilities } from "./capabilities"
import "./chat.css"
import { BlockIcon, CloseSmallIcon, EditIcon, ReplyIcon } from "./icons"

type ChatComposerProps = ComposerProps & {
  /** Answering a message: a bar above the box with who and what (× or Escape stops). */
  replyingTo?: { name: string; text: string }
  onCancelReply?: () => void
  /** Changing one's own message: the box holds its text, a bar says so (× or Escape stops). */
  editing?: { id: string; text: string }
  onCancelEdit?: () => void
}

/** Which capabilities each built-in tool needs (any one missing hides it). */
const TOOL_NEEDS: Record<string, ChatCapability[]> = {
  attach: ["attach", "canUpload"],
  voice: ["voice", "canAddMedia"],
  gif: ["canAddMedia"],
  mention: ["mention"],
  format: ["canFormatText"],
  tip: ["sendTip"],
}

/**
 * The Composer with what a chat adds: "Replying to Ana: …" or "Editing message" above it (× or Escape stops),
 * and the chat's rules (ChatProvider): when nothing can be sent here (read-only, blocked, not allowed) a quiet
 * notice with the reason takes its place; tools the rules don't allow are not there; files are checked
 * against the size and kinds allowed; Send says why when it is off.
 */
function ChatComposer({
  replyingTo,
  onCancelReply,
  editing,
  onCancelEdit,
  placeholder,
  className,
  onKeyDownCapture,
  tools,
  sendDisabledReason,
  ...props
}: ChatComposerProps) {
  const rules = useChatCapabilities()
  const cancel = editing ? onCancelEdit : replyingTo ? onCancelReply : undefined
  const box = React.useRef<HTMLDivElement>(null)
  const api = React.useRef<ComposerToolContext | null>(null)
  // "Thank Ana" and the like: text put in from elsewhere in the chat.
  useChatEvent("composer.insert", (event) => {
    api.current?.insert(event.text)
    api.current?.focus()
  })
  // Choosing Reply puts the caret in the box, ready to write.
  React.useEffect(() => {
    if (replyingTo)
      box.current?.querySelector<HTMLTextAreaElement>("textarea")?.focus()
  }, [replyingTo])

  if (rules.readOnlyReason) {
    return (
      <div className={cn("nt-chatcomposer", className)}>
        <div className="nt-composer-notice" role="status">
          <BlockIcon />
          <span>{rules.readOnlyReason}</span>
        </div>
      </div>
    )
  }

  // No tools given: the config's, or the usual set (an empty list means none).
  const chosenTools =
    tools ??
    rules.composer?.tools ??
    defaultComposerTools(rules.composer?.toolOptions)
  const allowedTools = chosenTools.map((tool) => {
    const needs = TOOL_NEEDS[tool.id] ?? []
    return needs.some((n) => !rules.can(n)) ? { ...tool, hidden: true } : tool
  })
  const filesAllowed = rules.can("attach") && rules.can("canUpload")

  return (
    <div
      ref={box}
      className={cn("nt-chatcomposer", className)}
      onKeyDownCapture={(event) => {
        if (
          event.key === "Escape" &&
          cancel &&
          !event.nativeEvent.isComposing
        ) {
          event.preventDefault()
          event.stopPropagation()
          cancel()
        }
        onKeyDownCapture?.(event)
      }}
    >
      {(editing || replyingTo) && (
        <div className="nt-composer-context" role="status">
          <span className="nt-composer-context-icon">
            {editing ? <EditIcon /> : <ReplyIcon />}
          </span>
          <span className="nt-composer-context-text">
            <span className="nt-composer-context-title">
              {editing ? "Editing message" : `Replying to ${replyingTo?.name}`}
            </span>
            <span className="nt-composer-context-quote">
              {editing ? editing.text : replyingTo?.text}
            </span>
          </span>
          <button
            type="button"
            className="nt-composer-button"
            aria-label={editing ? "Stop editing" : "Stop replying"}
            title={`${editing ? "Stop editing" : "Stop replying"} (Esc)`}
            onClick={cancel}
          >
            <CloseSmallIcon />
          </button>
        </div>
      )}
      <Composer
        maxFileBytes={rules.capabilities.maxUploadBytes}
        accept={rules.capabilities.acceptedFileTypes}
        {...props}
        // A new box for each message edited, so it starts with that message's text.
        key={editing ? `edit-${editing.id}` : "new"}
        defaultText={editing?.text}
        autoFocus={!!editing}
        placeholder={editing ? "Edit the message" : placeholder}
        attachments={editing || !filesAllowed ? false : props.attachments}
        tools={allowedTools}
        apiRef={api}
        sendDisabledReason={
          sendDisabledReason ?? rules.state.sendDisabledReason
        }
      />
    </div>
  )
}

export { ChatComposer }
export type { ChatComposerProps }
