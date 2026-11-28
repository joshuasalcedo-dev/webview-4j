import * as React from "react"

import { cn } from "../lib/utils"
import { NumberField, type NumberFieldProps } from "./text-inputs"

/**
 * Units of measure, by the names the browsers know (so they are written in the person's language: "kg",
 * "Kilogramm", "公斤"), grouped by what they measure. Within a group they convert into each other.
 */
const UNITS = {
  length: {
    millimeter: 0.001,
    centimeter: 0.01,
    meter: 1,
    kilometer: 1000,
    inch: 0.0254,
    foot: 0.3048,
    yard: 0.9144,
    mile: 1609.344,
  },
  mass: {
    gram: 1,
    kilogram: 1000,
    ounce: 28.349523125,
    pound: 453.59237,
    stone: 6350.29318,
  },
  volume: {
    milliliter: 0.001,
    liter: 1,
    "fluid-ounce": 0.0295735295625,
    gallon: 3.785411784,
  },
  duration: {
    millisecond: 0.001,
    second: 1,
    minute: 60,
    hour: 3600,
    day: 86400,
    week: 604800,
  },
  data: {
    byte: 1,
    kilobyte: 1e3,
    megabyte: 1e6,
    gigabyte: 1e9,
    terabyte: 1e12,
  },
  speed: {
    "meter-per-second": 1,
    "kilometer-per-hour": 1 / 3.6,
    "mile-per-hour": 0.44704,
  },
  temperature: { celsius: 1, fahrenheit: 1 },
  share: { percent: 1 },
} as const

type UnitGroup = keyof typeof UNITS
type Unit = { [G in UnitGroup]: keyof (typeof UNITS)[G] }[UnitGroup]

function groupOf(unit: Unit): UnitGroup {
  for (const group of Object.keys(UNITS) as UnitGroup[])
    if (unit in UNITS[group]) return group
  throw new Error(
    `"${unit}" is not a unit ui-native knows. Use one of: ${Object.values(UNITS).flatMap(Object.keys).join(", ")}.`
  )
}

/**
 * The same amount in another unit of the same kind (2 kilogram → 4.409 pound). Units of different kinds
 * (kilograms to metres) can't be converted; that is a mistake in the calling code, so it says so.
 */
function convertUnit(value: number, from: Unit, to: Unit): number {
  if (from === to) return value
  const group = groupOf(from)
  if (groupOf(to) !== group)
    throw new Error(
      `A ${from} can't be turned into a ${to}: they measure different things.`
    )
  if (group === "temperature")
    return from === "celsius" ? (value * 9) / 5 + 32 : ((value - 32) * 5) / 9
  const factors = UNITS[group] as Record<string, number>
  return (value * factors[from]) / factors[to]
}

/** How a unit is written after a number: "kg" (short), "kilograms" (long), "kg" or "°" (narrow). */
function unitLabel(
  unit: Unit,
  display: "short" | "long" | "narrow" = "short",
  locale?: string,
  value = 2
): string {
  const parts = new Intl.NumberFormat(locale, {
    style: "unit",
    unit,
    unitDisplay: display,
  }).formatToParts(value)
  return parts
    .filter((p) => p.type === "unit")
    .map((p) => p.value)
    .join("")
    .trim()
}

// --- Quantity: an amount with its unit, as text -----------------------------------------------------------

type QuantityProps = Omit<React.HTMLAttributes<HTMLSpanElement>, "children"> & {
  value: number
  unit: Unit
  /** "short" 2.5 kg (the default), "long" 2.5 kilograms, "narrow" 2.5kg. */
  display?: "short" | "long" | "narrow"
  /** Shown in this unit instead (converted). */
  as?: Unit
  /** Most decimal places shown (2 by default). */
  digits?: number
  /** Short for big numbers: 1.2K km. */
  compact?: boolean
  locale?: string
}

/** An amount with its unit, written the way the person's language writes it (2.5 kg, 37 °C, 1.2 GB). */
function Quantity({
  value,
  unit,
  display = "short",
  as,
  digits = 2,
  compact = false,
  locale,
  className,
  ...props
}: QuantityProps) {
  const shown = as ? convertUnit(value, unit, as) : value
  const text = new Intl.NumberFormat(locale, {
    style: "unit",
    unit: as ?? unit,
    unitDisplay: display,
    maximumFractionDigits: digits,
    notation: compact ? "compact" : "standard",
  }).format(shown)
  return (
    <span className={cn("nt-quantity nt-selectable", className)} {...props}>
      {text}
    </span>
  )
}

// --- QuantityField: an amount with its unit, typed in ------------------------------------------------------

type QuantityFieldProps = Omit<
  NumberFieldProps,
  "after" | "format" | "fractionDigits" | "onValueChange"
> & {
  /** The unit the value is in. */
  unit: Unit
  /** Offers these units (of the same kind) in a picker at the end of the box. */
  units?: Unit[]
  /** The value changed (in `unit`). */
  onValueChange?: (value: number | null) => void
  /**
   * Another unit was picked. With `convert` (the default) the amount is turned into it (2 kg → 4.41 lb) and
   * comes along; otherwise the amount stays as typed.
   */
  onUnitChange?: (unit: Unit, value: number | null) => void
  convert?: boolean
  /** "short" kg (the default), "long" kilograms, "narrow". */
  display?: "short" | "long" | "narrow"
  /** Decimal places kept (2 by default). */
  digits?: number
  locale?: string
}

/**
 * A box for an amount with a unit of measure: the unit written after the number in the person's language,
 * or a picker of units that converts the amount when another is chosen. ↑ ↓, limits and the person's own
 * number format as in NumberField.
 */
function QuantityField({
  unit,
  units,
  value,
  onValueChange,
  onUnitChange,
  convert = true,
  display = "short",
  digits = 2,
  locale,
  disabled,
  ...props
}: QuantityFieldProps) {
  const after =
    units && units.length > 1 ? (
      <select
        className="nt-input-unit"
        aria-label="Unit"
        value={unit}
        disabled={disabled}
        onChange={(event) => {
          const next = event.target.value as Unit
          const amount = value ?? null
          const moved =
            convert && amount !== null
              ? Number(convertUnit(amount, unit, next).toFixed(digits))
              : amount
          onUnitChange?.(next, moved)
        }}
      >
        {units.map((u) => (
          <option key={u} value={u} title={unitLabel(u, "long", locale)}>
            {unitLabel(u, display, locale)}
          </option>
        ))}
      </select>
    ) : (
      <span aria-label={unitLabel(unit, "long", locale)}>
        {unitLabel(unit, display, locale)}
      </span>
    )
  return (
    <NumberField
      {...props}
      value={value}
      disabled={disabled}
      onValueChange={onValueChange}
      fractionDigits={digits}
      after={after}
    />
  )
}

export { convertUnit, Quantity, QuantityField, unitLabel, UNITS }
export type { QuantityFieldProps, QuantityProps, Unit, UnitGroup }
