// A file's URL from Vite (`import url from "./file.wasm?url"`): the DOCX library's WebAssembly binary.
declare module "*?url" {
  const url: string
  export default url
}
