package io.joshuasalcedo.swaswa.ffi;

import java.util.Map;

/**
 * One step of one of the page's requests ({@link Capture#NETWORK}): a {@code fetch} or XMLHttpRequest as it
 * {@link Phase#START starts} and as it {@link Phase#END ends} or {@link Phase#ERROR fails} (the same {@code id}),
 * or another request the page made (a picture, a script, a style, a font: {@link Phase#RESOURCE}, its timing and
 * size only). Bodies are text up to 32 KB; binary ones say their size. What a step does not know is null or 0.
 *
 * @param id         numbers the page's requests: a START and its END or ERROR share it
 * @param kind       {@code fetch}, {@code xhr}, or for a resource the browser's initiator ({@code img}, {@code script},
 *                   {@code link}, {@code css}, {@code navigation}...)
 * @param startedAt  epoch milliseconds (START and RESOURCE)
 * @param durationMs from start to answer (END, ERROR, RESOURCE)
 * @param size       the response's size in bytes (END, RESOURCE)
 */
public record NetworkEvent(String view, Phase phase, long id, String kind, String method, String url, long startedAt,
                           int status, String statusText, long durationMs, long size, String mimeType, String error,
                           Map<String, String> requestHeaders, String requestBody,
                           Map<String, String> responseHeaders, String responseBody) implements WebViewEvent {

    public enum Phase { START, END, ERROR, RESOURCE }
}
