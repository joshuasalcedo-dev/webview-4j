package io.joshuasalcedo.swaswa.ffi;

/**
 * A page's request to Java over the bridge ({@code window.__webview4j.request('GET', '/users/42')}, or a fetch of
 * {@code <base>api/...}): which view it came from (set by the native side, never by the page), the method, the path,
 * the query as the page wrote it, and the body. A {@link PageRequestHandler} answers it ({@link App#onPageRequest}).
 *
 * @param view     the window whose page asked
 * @param method   GET, POST, PUT, PATCH or DELETE
 * @param path     the path, from {@code /}: {@code /users/42}
 * @param rawQuery the query after {@code ?}, still encoded; empty for none
 * @param body     the body as text (JSON, usually); empty for none
 */
public record PageRequest(View view, HttpMethod method, String path, String rawQuery, String body) {}
