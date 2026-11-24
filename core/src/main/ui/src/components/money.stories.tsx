import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Money, MoneyField } from "./money"
import { Quantity, QuantityField, type Unit } from "./quantity"
import { Text } from "./text"

/** Amounts typed in: the value underneath is what the app gets (a whole number of centavos/cents). */
function Fields({ invalid = false }: { invalid?: boolean }) {
  const [price, setPrice] = React.useState<number | null>(49900)
  const [currency, setCurrency] = React.useState("PHP")
  const [tip, setTip] = React.useState<number | null>(150000)
  const [weight, setWeight] = React.useState<number | null>(2.5)
  const [weightUnit, setWeightUnit] = React.useState<Unit>("kilogram")
  const [length, setLength] = React.useState<number | null>(170)
  const [lengthUnit, setLengthUnit] = React.useState<Unit>("centimeter")
  const [discount, setDiscount] = React.useState<number | null>(15)
  return (
    <div style={{ display: "grid", gap: 14, width: "100%", maxWidth: 340 }}>
      <MoneyField
        label="Price"
        currency={currency}
        currencies={["PHP", "USD", "EUR", "JPY", "GBP"]}
        onCurrencyChange={(next, amount) => {
          setCurrency(next)
          setPrice(amount)
        }}
        value={price}
        onValueChange={setPrice}
        max={invalid ? 100000 : undefined}
        hint={`The app gets: ${price ?? "nothing"} (${currency}, in its smallest unit)`}
        error={invalid ? "The price can't be more than ₱1,000.00." : undefined}
      />
      <MoneyField
        label="Tip (yen: no decimals)"
        currency="JPY"
        value={tip}
        onValueChange={setTip}
        stepper
      />
      <QuantityField
        label="Weight"
        unit={weightUnit}
        units={["kilogram", "gram", "pound", "ounce"]}
        value={weight}
        onValueChange={setWeight}
        onUnitChange={(unit, value) => {
          setWeightUnit(unit)
          setWeight(value)
        }}
        min={0}
        step={0.1}
        hint="Pick pounds: the amount is converted."
      />
      <QuantityField
        label="Height"
        unit={lengthUnit}
        units={["centimeter", "meter", "inch", "foot"]}
        value={length}
        onValueChange={setLength}
        onUnitChange={(unit, value) => {
          setLengthUnit(unit)
          setLength(value)
        }}
        min={0}
      />
      <QuantityField
        label="Discount"
        unit="percent"
        value={discount}
        onValueChange={setDiscount}
        min={0}
        max={100}
        step={5}
      />
      <QuantityField
        label="Upload limit"
        unit="megabyte"
        defaultValue={100}
        display="long"
        min={1}
        stepper={false}
      />
    </div>
  )
}

/** Amounts shown as text: each currency its own way, lined up in a column. */
function Shown() {
  const rows: [string, React.ReactNode][] = [
    ["Price", <Money amount={49900} currency="PHP" />],
    ["Whole amount, no .00", <Money amount={49900} currency="PHP" trimZeros />],
    ["Dollars, code", <Money amount={1999} currency="USD" display="code" />],
    ["Euros, name", <Money amount={1250} currency="EUR" display="name" />],
    ["Yen (no decimals)", <Money amount={4990} currency="JPY" />],
    ["Refund (minus)", <Money amount={-50000} currency="PHP" colour />],
    [
      "Refund (accounting)",
      <Money amount={-50000} currency="PHP" negative="accounting" />,
    ],
    [
      "Change this month",
      <Money amount={125000} currency="PHP" signed colour />,
    ],
    ["Revenue (compact)", <Money amount={123456789} currency="PHP" compact />],
    ["Weight", <Quantity value={2.5} unit="kilogram" />],
    ["Same, in pounds", <Quantity value={2.5} unit="kilogram" as="pound" />],
    ["Temperature", <Quantity value={37} unit="celsius" />],
    ["In °F", <Quantity value={37} unit="celsius" as="fahrenheit" />],
    ["Download", <Quantity value={1.2} unit="gigabyte" />],
    ["Call", <Quantity value={95} unit="minute" display="long" />],
    ["Distance", <Quantity value={12.4} unit="kilometer" />],
    ["Speed", <Quantity value={60} unit="kilometer-per-hour" />],
  ]
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "auto auto",
        gap: "4px 24px",
        justifyContent: "start",
      }}
    >
      {rows.map(([label, value]) => (
        <React.Fragment key={label}>
          <Text tone="secondary" selectable={false}>
            {label}
          </Text>
          <span style={{ textAlign: "right" }}>{value}</span>
        </React.Fragment>
      ))}
    </div>
  )
}

const meta = {
  title: "Native/MoneyAndUnits",
  component: Fields,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Fields>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeStretch: true, ...dark } }

export const TypedIn: Story = {}
export const TypedInDark: Story = darkToo
export const WithErrors: Story = { args: { invalid: true } }
export const ShownAsText: Story = { render: () => <Shown /> }
export const ShownAsTextDark: Story = { render: () => <Shown />, ...darkToo }
export const Disabled: Story = {
  render: () => (
    <div style={{ display: "grid", gap: 14, width: "100%", maxWidth: 340 }}>
      <MoneyField
        label="Price"
        currency="PHP"
        defaultValue={49900}
        currencies={["PHP", "USD"]}
        disabled
      />
      <QuantityField
        label="Weight"
        unit="kilogram"
        units={["kilogram", "pound"]}
        value={2}
        disabled
      />
    </div>
  ),
}
