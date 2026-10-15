package io.joshuasalcedo.webview4j;

import java.util.Locale;
import java.util.Map;

/** Turns the native side's event JSON into {@link WebViewEvent}s. */
final class Events {

    private Events() {}

    /** The event, or null for a type this version does not know (replies are handled elsewhere). */
    @SuppressWarnings("unchecked")
    static WebViewEvent from(Map<String, Object> json) {
        String view = text(json, "view");
        return switch (String.valueOf(json.get("type"))) {
            case "ready" -> new Ready();
            case "closed" -> new Closed(view);
            case "api-call" -> new ApiCall(view, text(json, "method"), text(json, "url"),
                    (int) number(json, "status"), number(json, "durationMs"),
                    text(json, "requestBody"), text(json, "responseBody"), json);
            case "click" -> new Click(view, text(json, "selector"), text(json, "tag"), text(json, "text"),
                    decimal(json, "x"), decimal(json, "y"), json);
            case "dom-event" -> {
                Map<String, Object> element = json.get("element") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map : Map.of();
                yield new DomEvent(view, text(json, "kind"), text(element, "selector"), element, json);
            }
            case "websocket" -> new WebSocketFrame(view, number(json, "socket"), text(json, "url"),
                    constant(WebSocketFrame.Direction.class, json, "direction"), text(json, "frame"));
            case "log" -> new LogRecord(constant(LogRecord.Source.class, json, "source"), view,
                    constant(LogRecord.Level.class, json, "level"), text(json, "target"), text(json, "message"));
            default -> null;
        };
    }

    static String text(Map<String, Object> json, String key) {
        return json.get(key) instanceof String s ? s : null;
    }

    static long number(Map<String, Object> json, String key) {
        return json.get(key) instanceof Number n ? n.longValue() : 0;
    }

    private static double decimal(Map<String, Object> json, String key) {
        return json.get(key) instanceof Number n ? n.doubleValue() : 0;
    }

    private static <E extends Enum<E>> E constant(Class<E> type, Map<String, Object> json, String key) {
        String value = text(json, key);
        if (value == null) throw new IllegalArgumentException("no " + key + " in " + json);
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
