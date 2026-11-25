// The spreadsheet engine's binary as a URL: the bundler (Vite) copies it next to the code and gives its address.
declare module "@extend-ai/react-xlsx/duke_sheets_wasm_bg.wasm?url" {
  const url: string
  export default url
}
