package io.joshuasalcedo.webview4j;

import java.util.Map;

/**
 * A click in the page ({@link Capture#CLICKS}), on the nearest button, link or element with a role.
 * {@code data} holds everything reported: classes, role, aria label, href, data attributes.
 */
public record Click(String view, String selector, String tag, String text, double x, double y,
                    Map<String, Object> data) implements WebViewEvent {}
