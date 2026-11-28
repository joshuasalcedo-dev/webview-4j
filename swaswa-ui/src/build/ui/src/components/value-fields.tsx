import * as React from "react"

import { Select } from "./select"
import { TextField, type TextFieldProps } from "./text-field"
import { NumberField } from "./text-inputs"

/**
 * Fields for value objects: an email, a phone number, a web address, a date, a percentage, an IBAN, a card number,
 * a country, a currency, a language. Each is the system's control with the right keyboard and autofill, and it says
 * at once when what is typed cannot be one (a quick check of the form, on leaving the field). The app's own check
 * (a value object's parse, on the server or in Java) has the last word: give its message as `error`.
 *
 * They are controlled by text: `value` and `onValueChange(text)`; what a value object is made from.
 */

type ValueFieldProps = Omit<TextFieldProps, "value" | "onChange" | "type"> & {
  value?: string
  onValueChange?: (text: string) => void
}

/** A text field that checks its form when the person leaves it (not while they type: that would nag). */
function CheckedField({
  check,
  format,
  value,
  onValueChange,
  error,
  ...props
}: ValueFieldProps & {
  /** Why the text cannot be one; undefined when it can. */
  check: (text: string) => string | undefined
  /** How the text is written as it is typed (groups of digits, capitals). */
  format?: (text: string) => string
  type?: string
}) {
  const [text, setText] = React.useState(value ?? "")
  const [left, setLeft] = React.useState(false)
  React.useEffect(() => setText(value ?? ""), [value])
  const own = left && text.trim() !== "" ? check(text.trim()) : undefined
  return (
    <TextField
      {...props}
      value={text}
      error={error ?? own}
      onChange={(e) => {
        const next = format ? format(e.target.value) : e.target.value
        setText(next)
        onValueChange?.(next)
      }}
      onBlur={(e) => {
        setLeft(true)
        props.onBlur?.(e)
      }}
    />
  )
}

// ---- contact ----

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

/** An email address: the email keyboard, autofill, and "That is not an email address" when it cannot be one. */
function EmailField(props: ValueFieldProps) {
  return (
    <CheckedField
      type="email"
      inputMode="email"
      autoComplete="email"
      spellCheck={false}
      placeholder="name@example.com"
      check={(t) => (EMAIL.test(t) ? undefined : "That is not an email address")}
      {...props}
    />
  )
}

/** A phone number, with the country's code: the phone keyboard, and between 7 and 15 digits. */
function PhoneField(props: ValueFieldProps) {
  return (
    <CheckedField
      type="tel"
      inputMode="tel"
      autoComplete="tel"
      placeholder="+63 917 555 0142"
      format={(t) => t.replace(/[^\d+()\-\s.]/g, "")}
      check={(t) => {
        const digits = t.replace(/\D/g, "").length
        return digits < 7 ? "Too short for a phone number" : digits > 15 ? "Too long for a phone number" : undefined
      }}
      {...props}
    />
  )
}

/** A web address; one without "https://" gets it. */
function UrlField(props: ValueFieldProps) {
  return (
    <CheckedField
      type="url"
      inputMode="url"
      autoComplete="url"
      spellCheck={false}
      placeholder="https://example.com"
      check={(t) => {
        try {
          const url = new URL(/^[a-z][a-z0-9+.-]*:/i.test(t) ? t : `https://${t}`)
          return url.hostname.includes(".") || url.hostname === "localhost" ? undefined : "That is not a web address"
        } catch {
          return "That is not a web address"
        }
      }}
      {...props}
    />
  )
}

// ---- dates and numbers ----

/** A date: the system's date picker; its text is the ISO date (2026-11-25). */
function DateField({ value, onValueChange, ...props }: ValueFieldProps & { min?: string; max?: string }) {
  return <TextField {...props} type="date" value={value ?? ""} onChange={(e) => onValueChange?.(e.target.value)} />
}

/** A percentage: a number with "%" after it. Its text is "12.5%". */
function PercentField({
  value,
  onValueChange,
  label,
  hint,
  error,
  style,
  className,
}: ValueFieldProps) {
  const number = value != null && value.trim() !== "" ? Number(value.replace(/[%\s]/g, "")) : null
  return (
    <NumberField
      label={label}
      hint={hint}
      error={error}
      after="%"
      value={number != null && Number.isFinite(number) ? number : null}
      onValueChange={(n) => onValueChange?.(n == null ? "" : `${n}%`)}
      style={style}
      className={className}
    />
  )
}

// ---- banking ----

/** In groups of 4, as banks print them. */
const groups = (t: string) => t.replace(/\s+/g, "").replace(/(.{4})/g, "$1 ").trim()

/** An IBAN, in capitals and groups of 4 as it is typed, checked with its check digits (mod 97). */
function IbanField(props: ValueFieldProps) {
  return (
    <CheckedField
      autoComplete="off"
      spellCheck={false}
      placeholder="GB82 WEST 1234 5698 7654 32"
      format={(t) => groups(t.toUpperCase().replace(/[^A-Z0-9]/g, ""))}
      check={(t) => (ibanValid(t) ? undefined : "That is not an IBAN: check the letters and digits")}
      {...props}
    />
  )
}

function ibanValid(text: string) {
  const iban = text.replace(/\s/g, "")
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{10,30}$/.test(iban)) return false
  const moved = iban.slice(4) + iban.slice(0, 4)
  let rest = 0
  for (const ch of moved) {
    const n = ch >= "A" ? (ch.charCodeAt(0) - 55).toString() : ch
    for (const d of n) rest = (rest * 10 + Number(d)) % 97
  }
  return rest === 1
}

/** The card's brand from its first digits, as payment forms show it. */
function cardBrand(digits: string): string | undefined {
  if (/^4/.test(digits)) return "Visa"
  if (/^(5[1-5]|2[2-7])/.test(digits)) return "Mastercard"
  if (/^3[47]/.test(digits)) return "American Express"
  if (/^(6011|65|64[4-9])/.test(digits)) return "Discover"
  if (/^35/.test(digits)) return "JCB"
  return undefined
}

/** A card number: digits in groups of 4, the brand shown, checked with its check digit (Luhn). */
function CardNumberField({ hint, ...props }: ValueFieldProps) {
  const [brand, setBrand] = React.useState<string>()
  return (
    <CheckedField
      inputMode="numeric"
      autoComplete="cc-number"
      placeholder="4242 4242 4242 4242"
      hint={brand ?? hint}
      format={(t) => {
        const digits = t.replace(/\D/g, "").slice(0, 19)
        setBrand(cardBrand(digits))
        return groups(digits)
      }}
      check={(t) => (luhn(t.replace(/\D/g, "")) ? undefined : "That is not a card number: check the digits")}
      {...props}
    />
  )
}

function luhn(digits: string) {
  if (digits.length < 12) return false
  let sum = 0
  for (let i = 0; i < digits.length; i++) {
    let d = Number(digits[digits.length - 1 - i])
    if (i % 2 === 1) {
      d *= 2
      if (d > 9) d -= 9
    }
    sum += d
  }
  return sum % 10 === 0
}

// ---- lists to choose from ----

type Choice = { value: string; label: string }

type ChoiceFieldProps = {
  label?: React.ReactNode
  value?: string
  onValueChange?: (value: string) => void
  /** The choices; the field's own list when left out. */
  options?: Choice[]
  /** A "none" choice at the top: for a field that may stay empty. */
  optional?: boolean
  disabled?: boolean
  style?: React.CSSProperties
  className?: string
}

function ChoiceField({ label, value, onValueChange, options = [], optional, disabled, style, className }: ChoiceFieldProps) {
  const all = optional ? [{ value: "", label: "—" }, ...options] : options
  return (
    <label className={className} style={{ display: "grid", gap: 4, ...style }}>
      {label != null && <span className="nt-field-label">{label}</span>}
      <Select options={all} value={value ?? ""} disabled={disabled} onChange={(e) => onValueChange?.(e.target.value)} />
    </label>
  )
}

/** A flag from a country's two letters (PH: 🇵🇭). */
function flag(code: string) {
  return /^[A-Z]{2}$/.test(code) ? String.fromCodePoint(...[...code].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65)) : ""
}

const ISO_COUNTRIES =
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW".split(
    " "
  )

/** Names in the person's language, from the browser's own data. */
function displayNames(type: "region" | "currency" | "language") {
  try {
    return new Intl.DisplayNames(undefined, { type })
  } catch {
    return undefined
  }
}

/** A country: its flag and name, in the person's language, in alphabetical order. Its value is the two-letter code. */
function CountrySelect({ options, ...props }: ChoiceFieldProps) {
  const list = React.useMemo(() => {
    if (options) return options.map((o) => ({ value: o.value, label: `${flag(o.value)} ${o.label}`.trim() }))
    const names = displayNames("region")
    return ISO_COUNTRIES.map((code) => ({ value: code, label: `${flag(code)} ${names?.of(code) ?? code}` })).sort((a, b) =>
      a.label.slice(3).localeCompare(b.label.slice(3))
    )
  }, [options])
  return <ChoiceField {...props} options={list} />
}

/** A currency: its code and name. Its value is the ISO code (PHP). */
function CurrencySelect({ options, ...props }: ChoiceFieldProps) {
  const list = React.useMemo(() => {
    if (options) return options
    const names = displayNames("currency")
    const codes = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf?.("currency") ?? ["USD", "EUR", "PHP", "JPY", "GBP"]
    return codes.map((code) => ({ value: code, label: `${code} · ${names?.of(code) ?? code}` }))
  }, [options])
  return <ChoiceField {...props} options={list} />
}

const ISO_LANGUAGES = "ar bn cs da de el en es fa fi fil fr he hi hu id it ja ko ms nl no pl pt ro ru sv sw ta th tl tr uk ur vi zh".split(" ")

/** A language: its name in the person's language. Its value is the ISO 639 code (en). */
function LanguageSelect({ options, ...props }: ChoiceFieldProps) {
  const list = React.useMemo(() => {
    if (options) return options
    const names = displayNames("language")
    return ISO_LANGUAGES.map((code) => ({ value: code, label: names?.of(code) ?? code })).sort((a, b) => a.label.localeCompare(b.label))
  }, [options])
  return <ChoiceField {...props} options={list} />
}

export {
  CardNumberField,
  CountrySelect,
  CurrencySelect,
  DateField,
  EmailField,
  IbanField,
  LanguageSelect,
  PercentField,
  PhoneField,
  UrlField,
  cardBrand,
  flag,
  ibanValid,
  luhn,
}
export type { ChoiceFieldProps, ValueFieldProps }
