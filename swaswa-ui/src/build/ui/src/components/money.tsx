import * as React from "react"

import { cn } from "../lib/utils"
import { NumberField, type NumberFieldProps } from "./text-inputs"

/**
 * Money is kept as a whole number of the currency's smallest unit (centavos, cents; yen have none), never as
 * a fraction: 0.1 + 0.2 is not 0.3 in floating point, but 10 + 20 centavos is always 30.
 */
type MinorUnits = number

/** What the person's language says about a currency: its decimal places, its symbol, and where it goes. */
function currencyInfo(currency: string, locale?: string) {
  const format = new Intl.NumberFormat(locale, { style: "currency", currency })
  const parts = format.formatToParts(1)
  const symbolAt = parts.findIndex((p) => p.type === "currency")
  const numberAt = parts.findIndex((p) => p.type === "integer")
  return {
    digits: format.resolvedOptions().maximumFractionDigits ?? 2,
    symbol: parts[symbolAt]?.value ?? currency,
    symbolFirst: symbolAt < numberAt,
  }
}

const toMajor = (minor: MinorUnits, digits: number) => minor / 10 ** digits
const toMinor = (major: number, digits: number): MinorUnits =>
  Math.round(major * 10 ** digits)

/** The name of a currency in the person's language ("Philippine Peso"), for pickers. */
function currencyName(currency: string, locale?: string): string {
  try {
    return (
      new Intl.DisplayNames(locale ? [locale] : undefined, {
        type: "currency",
      }).of(currency) ?? currency
    )
  } catch {
    return currency
  }
}

// --- Money: an amount shown as text ----------------------------------------------------------------------

type MoneyProps = Omit<React.HTMLAttributes<HTMLSpanElement>, "children"> & {
  /** In the currency's smallest unit (49900 is ₱499.00). */
  amount: MinorUnits
  /** The ISO 4217 code: "PHP", "USD", "EUR", "JPY". */
  currency: string
  /** "symbol" ₱499.00 (the default), "code" PHP 499.00, "name" 499.00 Philippine pesos. */
  display?: "symbol" | "narrowSymbol" | "code" | "name"
  /** Below zero: "minus" −₱5.00 (the default) or "accounting" (₱5.00), as in statements. */
  negative?: "minus" | "accounting"
  /** A plus on amounts above zero too (+₱5.00), for changes and transactions. */
  signed?: boolean
  /** Short for big amounts: ₱1.2K, ₱3.4M. */
  compact?: boolean
  /** Drop ".00" when the amount is whole (₱499). */
  trimZeros?: boolean
  /** Amounts below zero in the error colour, above zero in the success colour (with `signed`). */
  colour?: boolean
  locale?: string
}

/**
 * An amount of money written the way the person's language writes it: the currency's symbol where it belongs
 * (₱499.00, 499,00 €), its own decimal places (¥4,990 has none), digits that line up in columns. Selectable.
 */
function Money({
  amount,
  currency,
  display = "symbol",
  negative = "minus",
  signed = false,
  compact = false,
  trimZeros = false,
  colour = false,
  locale,
  className,
  ...props
}: MoneyProps) {
  const text = React.useMemo(() => {
    const { digits } = currencyInfo(currency, locale)
    const major = toMajor(amount, digits)
    const whole = trimZeros && Number.isInteger(major)
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency,
      currencyDisplay: display,
      currencySign: negative === "accounting" ? "accounting" : "standard",
      signDisplay: signed ? "exceptZero" : "auto",
      notation: compact ? "compact" : "standard",
      ...(whole ? { minimumFractionDigits: 0, maximumFractionDigits: 0 } : {}),
    }).format(major)
  }, [amount, currency, display, negative, signed, compact, trimZeros, locale])
  return (
    <span
      className={cn("nt-money nt-selectable", className)}
      data-negative={amount < 0 || undefined}
      data-positive={(signed && amount > 0) || undefined}
      data-colour={colour || undefined}
      {...props}
    >
      {text}
    </span>
  )
}

// --- MoneyField: an amount typed in --------------------------------------------------------------------------

type MoneyFieldProps = Omit<
  NumberFieldProps,
  | "value"
  | "defaultValue"
  | "onValueChange"
  | "min"
  | "max"
  | "step"
  | "format"
  | "fractionDigits"
  | "before"
  | "after"
> & {
  /** In the currency's smallest unit (49900 is ₱499.00); null when empty. */
  value?: MinorUnits | null
  defaultValue?: MinorUnits | null
  onValueChange?: (amount: MinorUnits | null) => void
  /** The ISO 4217 code: "PHP", "USD". */
  currency: string
  /** Offers these currencies in a picker at the end of the box. */
  currencies?: string[]
  /**
   * Another currency was picked. The amount as typed stays (₱1,234.50 becomes ¥1,235 or $1,234.50, not
   * ¥123,450): `amount` is it in the new currency's smallest unit; set both.
   */
  onCurrencyChange?: (currency: string, amount: MinorUnits | null) => void
  /** Limits, in the smallest unit too. Below zero is not allowed unless min says so. */
  min?: MinorUnits
  max?: MinorUnits
  /** How much ↑ ↓ change it, in the smallest unit (100 = one peso by default). */
  step?: MinorUnits
  locale?: string
}

/**
 * A box for an amount of money: the currency's symbol inside, where the person's language puts it, the
 * currency's own decimal places (rounded when more are typed), thousands marks once it is left, and pasted
 * amounts like "₱1,234.50" read correctly. The value is a whole number of centavos/cents, never a fraction.
 * With `currencies`, a picker at the end changes the currency (the amount stays as typed).
 */
function MoneyField({
  value,
  defaultValue = null,
  onValueChange,
  currency,
  currencies,
  onCurrencyChange,
  min = 0,
  max,
  step,
  locale,
  stepper = false,
  disabled,
  ...props
}: MoneyFieldProps) {
  const info = React.useMemo(
    () => currencyInfo(currency, locale),
    [currency, locale]
  )
  const unit = 10 ** info.digits
  const controlled = value !== undefined
  const [own, setOwn] = React.useState<MinorUnits | null>(defaultValue)
  const current = controlled ? value : own

  const picker =
    currencies && currencies.length > 0 ? (
      <select
        className="nt-input-unit"
        aria-label="Currency"
        value={currency}
        disabled={disabled}
        onChange={(event) => {
          const next = event.target.value
          // The same amount as typed, counted in the new currency's smallest unit.
          const amount =
            current === null
              ? null
              : toMinor(
                  toMajor(current, info.digits),
                  currencyInfo(next, locale).digits
                )
          if (!controlled) setOwn(amount)
          onCurrencyChange?.(next, amount)
        }}
      >
        {currencies.map((code) => (
          <option key={code} value={code} title={currencyName(code, locale)}>
            {code}
          </option>
        ))}
      </select>
    ) : null

  const symbol = <span aria-hidden="true">{info.symbol}</span>
  return (
    <NumberField
      {...props}
      disabled={disabled}
      stepper={stepper}
      value={current === null ? null : current / unit}
      onValueChange={(major) => {
        const minor = major === null ? null : toMinor(major, info.digits)
        if (!controlled) setOwn(minor)
        onValueChange?.(minor)
      }}
      min={min / unit}
      max={max === undefined ? undefined : max / unit}
      step={(step ?? unit) / unit}
      fractionDigits={info.digits}
      format={{
        minimumFractionDigits: info.digits,
        maximumFractionDigits: info.digits,
      }}
      before={info.symbolFirst ? symbol : undefined}
      after={
        !info.symbolFirst || picker ? (
          <>
            {!info.symbolFirst && symbol}
            {picker}
          </>
        ) : undefined
      }
    />
  )
}

export { currencyInfo, currencyName, Money, MoneyField }
export type { MinorUnits, MoneyFieldProps, MoneyProps }
