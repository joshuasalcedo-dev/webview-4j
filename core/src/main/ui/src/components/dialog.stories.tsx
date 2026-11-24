import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Button } from "./button"
import { Dialog, DialogDetails, type DialogProps, useDialogs } from "./dialog"
import { ProgressBar } from "./progress-bar"
import { TextField } from "./text-field"

/** Room for the dialog: it covers its window (the NativeProvider), as in an app. */
const tall = { minHeight: 360 }

/** Every kind of dialog, from useDialogs(); what was answered shows underneath. */
function TryThem() {
  const dialogs = useDialogs()
  const [answer, setAnswer] = React.useState("nothing yet")
  const said = (what: unknown) => setAnswer(JSON.stringify(what))
  return (
    <div
      style={{
        display: "flex",
        flexWrap: "wrap",
        gap: 8,
        alignContent: "flex-start",
        ...tall,
      }}
    >
      <Button
        onClick={async () =>
          said(
            await dialogs.message({
              title: "Message sent",
              message: "Ana Reyes will see it next time she opens the chat.",
            })
          )
        }
      >
        Message
      </Button>
      <Button
        onClick={async () =>
          said(
            await dialogs.confirm({
              title: "Delete this chat?",
              message:
                "The messages are deleted for you only. This can't be undone.",
              kind: "warning",
              confirm: "Delete",
              destructive: true,
            })
          )
        }
      >
        Confirm (delete)
      </Button>
      <Button
        onClick={async () =>
          said(
            await dialogs.choose({
              title: "Save the changes to this message?",
              message: "If you don't save, your changes are lost.",
              kind: "warning",
              choices: [
                { value: "save", label: "Save", primary: true },
                { value: "discard", label: "Don't save", destructive: true },
                { value: "cancel", label: "Cancel", cancel: true },
              ],
            })
          )
        }
      >
        Choose (save?)
      </Button>
      <Button
        onClick={async () =>
          said(
            await dialogs.error({
              title: "Couldn't send the message",
              message:
                "The server didn't answer. Check your internet connection, then try again.",
              details:
                "POST /chats/42/messages\n503 Service Unavailable\nrequest-id: 7f3c9a1e",
              retry: "Try again",
            })
          )
        }
      >
        Error
      </Button>
      <Button
        onClick={async () =>
          said(
            await dialogs.prompt({
              title: "Rename this list",
              label: "Name",
              value: "Favourites",
              confirm: "Rename",
              validate: (v) =>
                v.trim() === "" ? "Type a name for the list." : undefined,
            })
          )
        }
      >
        Prompt (input)
      </Button>
      <Button
        onClick={() => {
          let value = 0
          const handle = dialogs.progress({
            title: "Sending 12 photos",
            message: "0 of 12 sent",
            value,
            onCancel: () => {
              window.clearInterval(timer)
              handle.close()
              said("cancelled")
            },
          })
          const timer = window.setInterval(() => {
            value += 25
            handle.update({
              value,
              message: `${Math.round((value / 100) * 12)} of 12 sent`,
            })
            if (value >= 100) {
              window.clearInterval(timer)
              handle.close()
              said("sent")
            }
          }, 700)
        }}
      >
        Progress
      </Button>
      <span className="nt-secondary" style={{ flexBasis: "100%" }}>
        Answered: {answer}
      </span>
    </div>
  )
}

/**
 * One dialog as it looks, drawn in place (inline) so the two systems show side by side; the real one covers
 * the whole window (try "Try every kind").
 */
function Shown(props: Omit<DialogProps, "open">) {
  return (
    <div style={{ width: "100%", padding: 32, ...tall }}>
      <Dialog open inline {...props} />
    </div>
  )
}

const meta = {
  title: "Native/Dialog",
  component: TryThem,
  decorators: [bothPlatforms],
  parameters: { nativeChrome: true },
} satisfies Meta<typeof TryThem>
export default meta
type Story = StoryObj<typeof meta>

const nothing = () => {}

export const TryEveryKind: Story = {}

export const Message: Story = {
  render: () => (
    <Shown
      title="Message sent"
      message="Ana Reyes will see it next time she opens the chat."
      kind="info"
      actions={[
        { label: "OK", primary: true, cancel: true, onSelect: nothing },
      ]}
    />
  ),
}

export const Confirm: Story = {
  render: () => (
    <Shown
      title="Delete this chat?"
      message="The messages are deleted for you only. This can't be undone."
      kind="warning"
      actions={[
        {
          label: "Delete",
          primary: true,
          destructive: true,
          onSelect: nothing,
        },
        { label: "Cancel", cancel: true, onSelect: nothing },
      ]}
    />
  ),
}

export const ConfirmDark: Story = {
  ...Confirm,
  parameters: { nativeChrome: true, ...dark },
}

export const ThreeChoices: Story = {
  render: () => (
    <Shown
      title="Save the changes to this message?"
      message="If you don't save, your changes are lost."
      kind="warning"
      actions={[
        { label: "Save", primary: true, onSelect: nothing },
        { label: "Don't save", destructive: true, onSelect: nothing },
        { label: "Cancel", cancel: true, onSelect: nothing },
      ]}
    />
  ),
}

export const ErrorWithDetails: Story = {
  render: () => (
    <Shown
      title="Couldn't send the message"
      message="The server didn't answer. Check your internet connection, then try again."
      kind="error"
      actions={[
        { label: "Try again", primary: true, onSelect: nothing },
        { label: "OK", cancel: true, onSelect: nothing },
      ]}
    >
      <DialogDetails>
        {
          "POST /chats/42/messages\n503 Service Unavailable\nrequest-id: 7f3c9a1e"
        }
      </DialogDetails>
    </Shown>
  ),
}

export const ErrorDark: Story = {
  ...ErrorWithDetails,
  parameters: { nativeChrome: true, ...dark },
}

export const Input: Story = {
  render: () => (
    <Shown
      title="Rename this list"
      actions={[
        { label: "Rename", primary: true, onSelect: nothing },
        { label: "Cancel", cancel: true, onSelect: nothing },
      ]}
    >
      <TextField
        className="nt-dialog-field"
        label="Name"
        defaultValue="Favourites"
      />
    </Shown>
  ),
}

export const InputInvalid: Story = {
  render: () => (
    <Shown
      title="Rename this list"
      actions={[
        { label: "Rename", primary: true, disabled: true, onSelect: nothing },
        { label: "Cancel", cancel: true, onSelect: nothing },
      ]}
    >
      <TextField
        className="nt-dialog-field"
        label="Name"
        defaultValue=""
        error="Type a name for the list."
      />
    </Shown>
  ),
}

export const Progress: Story = {
  render: () => (
    <Shown
      title="Sending 12 photos"
      message="5 of 12 sent"
      actions={[{ label: "Cancel", cancel: true, onSelect: nothing }]}
    >
      <ProgressBar
        className="nt-dialog-progress"
        aria-label="Sending"
        value={42}
      />
    </Shown>
  ),
}

export const ProgressUnknown: Story = {
  render: () => (
    <Shown
      title="Connecting to the server"
      message="This can take a moment."
      actions={[]}
    >
      <ProgressBar className="nt-dialog-progress" aria-label="Connecting" />
    </Shown>
  ),
}
