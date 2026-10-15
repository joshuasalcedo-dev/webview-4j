package io.joshuasalcedo.webview4j;

import java.util.Map;

/**
 * A {@code fetch} or {@code XMLHttpRequest} the page made, once answered ({@link Capture#API}).
 * Bodies and headers are clipped by the page script; {@code data} holds everything reported,
 * including the request and response headers and the page's cookies.
 */
public record ApiCall(String view, String method, String url, int status, long durationMs,
                      String requestBody, String responseBody, Map<String, Object> data) implements WebViewEvent {}
