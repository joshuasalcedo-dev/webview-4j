import type { DataRow } from "./data-table"

/**
 * A row's field as text for a list or a grid: money (its smallest unit, with `<field>Currency` beside it, as Java
 * sends it) in the person's way of writing money, anything else as it is.
 */
function fieldText(item: DataRow, key?: string): string | undefined {
  if (!key || item[key] == null) return undefined
  const value = item[key]
  const currency = item[`${key}Currency`]
  if (typeof value === "number" && typeof currency === "string") {
    try {
      const format = new Intl.NumberFormat(undefined, { style: "currency", currency })
      const digits = format.resolvedOptions().maximumFractionDigits ?? 2
      return format.format(value / 10 ** digits)
    } catch {
      return `${value} ${currency}`
    }
  }
  return String(value)
}

export { fieldText }
