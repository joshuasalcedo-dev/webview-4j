import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { NativeProvider } from "./provider"
import {
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
} from "./value-fields"

/** Every value-object field, each checking its form as the person leaves it. */
function ValueFields({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [values, setValues] = React.useState<Record<string, string>>({ country: "PH", currency: "PHP", language: "en" })
  const set = (key: string) => (v: string) => setValues((all) => ({ ...all, [key]: v }))
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24, display: "grid", gap: 16, maxWidth: 520 }}>
      <EmailField label="Email" value={values.email} onValueChange={set("email")} />
      <PhoneField label="Phone" value={values.phone} onValueChange={set("phone")} />
      <UrlField label="Website" value={values.url} onValueChange={set("url")} />
      <DateField label="Birthday" value={values.date} onValueChange={set("date")} />
      <PercentField label="Discount" value={values.percent} onValueChange={set("percent")} />
      <IbanField label="IBAN" value={values.iban} onValueChange={set("iban")} />
      <CardNumberField label="Card" value={values.card} onValueChange={set("card")} />
      <CountrySelect label="Country" value={values.country} onValueChange={set("country")} />
      <CurrencySelect label="Currency" value={values.currency} onValueChange={set("currency")} />
      <LanguageSelect label="Language" value={values.language} onValueChange={set("language")} />
      <pre style={{ fontSize: 12 }}>{JSON.stringify(values, null, 2)}</pre>
    </NativeProvider>
  )
}

const meta = {
  title: "Native/Value fields",
  component: ValueFields,
  args: { platform: "windows", theme: "light" },
} satisfies Meta<typeof ValueFields>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
