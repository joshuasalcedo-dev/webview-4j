package io.joshuasalcedo.swaswa.ffi;

/**
 * A process of the browser engine failed, behind {@code view} (Windows): a page's renderer crashed, ran out of
 * memory or stopped answering, or the engine's browser process itself went. Without an answer the page shows nothing,
 * so do one: a renderer ({@code renderer}, {@code frame-renderer}, {@code unresponsive}): {@link View#reload()};
 * the browser process ({@code browser}): close the app's windows and open them again; the others ({@code gpu},
 * {@code utility}) the engine starts again by itself.
 *
 * @param kind     {@code browser}, {@code renderer}, {@code frame-renderer}, {@code unresponsive}, {@code gpu},
 *                 {@code utility}, {@code other}
 * @param reason   {@code crashed}, {@code out-of-memory}, {@code terminated}, {@code launch-failed},
 *                 {@code profile-deleted}, {@code unexpected}, {@code unknown}
 * @param exitCode the process's exit code, when it exited
 */
public record EngineProcessFailed(String view, String kind, String reason, int exitCode) implements WebViewEvent {}
