package io.joshuasalcedo.webview4j;

import java.util.Map;

/**
 * A user interaction in the page ({@link Capture#DOM_EVENTS}). {@code kind} is one of
 * {@code click}, {@code doubleClick}, {@code hover}, {@code hoverEnd}, {@code input}, {@code change},
 * {@code focus}, {@code blur}, {@code keyDown}, {@code keyUp}, {@code contextMenu}. {@code element}
 * is a snapshot of the target: selector, tag, text, value (never a password's), attributes,
 * styles and bounds.
 */
public record DomEvent(String view, String kind, String selector, Map<String, Object> element,
                       Map<String, Object> data) implements WebViewEvent {}
