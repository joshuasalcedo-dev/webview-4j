import type { Decorator, Preview } from "@storybook/react-vite"

/** The ui-native stories: the page around a dark story is dark too (darker than any window, so the windows stand out). */
const nativeCanvas: Decorator = (Story, context) => {
  if (context.title.startsWith("Native/")) {
    const dark = (context.parameters.nativeTheme ?? context.args.theme) === "dark"
    document.body.style.background = dark ? "#141414" : ""
    document.body.style.colorScheme = dark ? "dark" : ""
  } else {
    document.body.style.background = ""
    document.body.style.colorScheme = ""
  }
  return Story()
}

const preview: Preview = {
  parameters: {
    layout: "padded",
    controls: { expanded: true },
  },
  decorators: [nativeCanvas],
}

export default preview
