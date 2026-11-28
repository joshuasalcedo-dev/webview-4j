import * as React from "react"

import { cn } from "../lib/utils"

type AvatarProps = React.ComponentProps<"span"> & {
  /** Who it is: the initials come from it when there is no picture, and it is the picture's alt text. */
  name: string
  /** A picture; without one, the initials on the accent colour. */
  src?: string
  size?: number
}

function initials(name: string) {
  const words = name.trim().split(/\s+/).filter(Boolean)
  return (
    words.length > 1
      ? words[0][0] + words[words.length - 1][0]
      : name.slice(0, 2)
  ).toUpperCase()
}

/** A person's round picture, or their initials. */
function Avatar({
  name,
  src,
  size = 32,
  className,
  style,
  ...props
}: AvatarProps) {
  return (
    <span
      className={cn("nt-avatar", className)}
      style={{ width: size, height: size, ...style }}
      {...props}
    >
      {src ? (
        <img src={src} alt={name} />
      ) : (
        <span aria-label={name}>{initials(name)}</span>
      )}
    </span>
  )
}

export { Avatar }
export type { AvatarProps }
