import * as React from "react"

import { cn } from "../lib/utils"

type ButtonProps = React.ComponentProps<"button"> & {
  /**
   * "default": a standard push button. "accent": the window's main action (Windows' accent button, macOS'
   * default button; one per window). "subtle": no fill until hovered (toolbars, icon buttons).
   */
  variant?: "default" | "accent" | "subtle"
}

/** A push button, as the system draws one. */
function Button({
  variant = "default",
  type = "button",
  className,
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      data-variant={variant}
      className={cn("nt-button", className)}
      {...props}
    />
  )
}

export { Button }
export type { ButtonProps }
