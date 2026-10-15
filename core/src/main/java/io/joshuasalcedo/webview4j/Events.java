package io.joshuasalcedo.webview4j;

import java.util.Locale;
import java.util.Map;

/** Turns the native side's messages (PROTOCOL.md §2.2) into {@link WebViewEvent}s. */
final class Events {

    private Events() {}

    /** A native log record, from {@code app.log}. */
    static LogRecord nativeLog(Map<String, Object> json) {
        return new LogRecord(LogRecord.Source.NATIVE, null, constant(LogRecord.Level.class, json, "level"),
                text(json, "target"), text(json, "message"));
    }

    /**
     * A report from view {@code view}, from {@code view.<view>.<kind>}; null for a kind this
     * version does not know.
     */
    @SuppressWarnings("unchecked")
    static WebViewEvent fromView(String view, String kind, Map<String, Object> json) {
        return switch (kind) {
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
            case "log" -> new LogRecord(LogRecord.Source.PAGE, view, constant(LogRecord.Level.class, json, "level"),
                    null, text(json, "message"));
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
