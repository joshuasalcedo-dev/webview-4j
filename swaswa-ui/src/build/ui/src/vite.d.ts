// The part of Vite's import.meta that ui-native uses (the app's bundler is Vite).
interface ImportMeta {
  glob<T>(
    pattern: string,
    options?: { query?: string; import?: string; eager?: false },
  ): Record<string, () => Promise<T>>
}
