import * as React from "react"

import { cn } from "../lib/utils"

type SelectOption = {
  value: string
  label: React.ReactNode
  disabled?: boolean
}

type SelectProps = React.ComponentProps<"select"> & {
  /** The choices; or give <option>s as children. */
  options?: SelectOption[]
}

/**
 * A dropdown: Windows' ComboBox, macOS' pop-up button. The list that opens is the system's own (a native
 * <select>), so it looks and behaves exactly like the system's, keyboard included.
 */
function Select({ options, className, children, ...props }: SelectProps) {
  return (
    <span className={cn("nt-select", className)}>
      <select {...props}>
        {options?.map((option) => (
          <option
            key={option.value}
            value={option.value}
            disabled={option.disabled}
          >
            {option.label}
          </option>
        ))}
        {children}
      </select>
      <svg
        className="nt-select-icon"
        data-for="windows"
        viewBox="0 0 12 12"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="m2.5 4.5 3.5 3.5 3.5-3.5"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <span className="nt-select-icon" data-for="mac" aria-hidden="true">
        <svg viewBox="0 0 9 11" fill="none">
          <path
            d="m2 4 2.5-2.5L7 4M2 7l2.5 2.5L7 7"
            stroke="currentColor"
            strokeWidth="1.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
    </span>
  )
}

export { Select }
export type { SelectOption, SelectProps }
