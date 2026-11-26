package io.joshuasalcedo.swaswa.ffi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            case "resized" -> new Resized(view, decimal(json, "width"), decimal(json, "height"));
            case "moved" -> new Moved(view, decimal(json, "x"), decimal(json, "y"));
            case "focused" -> new Focused(view, Boolean.TRUE.equals(json.get("focused")));
            case "window-state" -> new WindowState(view, Boolean.TRUE.equals(json.get("minimized")),
                    Boolean.TRUE.equals(json.get("maximized")), Boolean.TRUE.equals(json.get("fullscreen")));
            case "scale" -> new ScaleChanged(view, decimal(json, "scale"));
            case "system-theme" -> new SystemThemeChanged(view, "dark".equals(text(json, "theme")) ? Theme.DARK : Theme.LIGHT);
            case "shortcut" -> new Shortcut(view, text(json, "shortcut"));
            case "new-window" -> new NewWindowRequested(view, text(json, "url"));
            case "download" -> new Download(view, constant(Download.State.class, json, "state"), text(json, "url"),
                    text(json, "path") == null ? null : Path.of(text(json, "path")));
            case "page-load" -> new PageLoad(view, constant(PageLoad.State.class, json, "state"), text(json, "url"));
            case "title-changed" -> new TitleChanged(view, text(json, "title"));
            case "cdp" -> new DevToolsEvent(view, text(json, "event"),
                    json.get("params") instanceof Map<?, ?> params ? (Map<String, Object>) params : Map.of());
            case "file-drop" -> {
                List<Path> paths = new ArrayList<>();
                if (json.get("paths") instanceof List<?> list) {
                    for (Object path : list) if (path instanceof String s) paths.add(Path.of(s));
                }
                yield new FileDrop(view, constant(FileDrop.Kind.class, json, "kind"), paths, decimal(json, "x"), decimal(json, "y"));
            }
            case "log" -> new LogRecord(LogRecord.Source.PAGE, view, constant(LogRecord.Level.class, json, "level"),
                    null, text(json, "message"));
            case "network" -> new NetworkEvent(view, constant(NetworkEvent.Phase.class, json, "phase"), number(json, "id"),
                    text(json, "kind"), text(json, "method"), text(json, "url"), number(json, "startedAt"),
                    (int) number(json, "status"), text(json, "statusText"), number(json, "durationMs"), number(json, "size"),
                    text(json, "mimeType"), text(json, "error"), strings(json, "requestHeaders"), text(json, "requestBody"),
                    strings(json, "responseHeaders"), text(json, "responseBody"));
            default -> null;
        };
    }

    /** An object of strings (headers), in order; empty when absent. */
    static Map<String, String> strings(Map<String, Object> json, String key) {
        if (!(json.get(key) instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> out = new java.util.LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        return java.util.Collections.unmodifiableMap(out);
    }

    static String text(Map<String, Object> json, String key) {
        return json.get(key) instanceof String s ? s : null;
    }

    static long number(Map<String, Object> json, String key) {
        return json.get(key) instanceof Number n ? n.longValue() : 0;
    }

    static double decimal(Map<String, Object> json, String key) {
        return json.get(key) instanceof Number n ? n.doubleValue() : 0;
    }

    private static <E extends Enum<E>> E constant(Class<E> type, Map<String, Object> json, String key) {
        String value = text(json, key);
        if (value == null) throw new IllegalArgumentException("no " + key + " in " + json);
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
