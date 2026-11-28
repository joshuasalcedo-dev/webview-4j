// Files the PDF components import as URLs (Vite's ?url): the bundler copies them into the build and gives their URL.
declare module "*.wasm?url" {
  const url: string
  export default url
}

declare module "*.pdf?url" {
  const url: string
  export default url
}
