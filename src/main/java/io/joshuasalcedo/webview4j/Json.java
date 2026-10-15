package io.joshuasalcedo.webview4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for the native boundary, so the library needs no dependency.
 * Reads objects as {@code Map<String, Object>} (in order), arrays as {@code List<Object>},
 * numbers as {@code Long} or {@code Double}, plus {@code String}, {@code Boolean} and null.
 */
final class Json {

    private Json() {}

    static Object parse(String text) {
        Reader reader = new Reader(text);
        reader.space();
        Object value = reader.value();
        reader.space();
        if (reader.at < text.length()) throw reader.error("unexpected text after the value");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String text) {
        if (parse(text) instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("not a JSON object: " + text);
    }

    /** Writes maps, iterables, strings, numbers, booleans, enums (by name) and null. */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case String s -> quote(s, out);
            case Boolean b -> out.append(b);
            case Double d when d.isNaN() || d.isInfinite() -> throw new IllegalArgumentException("JSON has no " + d);
            case Float f when f.isNaN() || f.isInfinite() -> throw new IllegalArgumentException("JSON has no " + f);
            case Number n -> out.append(n);
            case Enum<?> e -> quote(e.name(), out);
            case Map<?, ?> map -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    quote(String.valueOf(entry.getKey()), out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
            }
            case Iterable<?> items -> {
                out.append('[');
                boolean first = true;
                for (Object item : items) {
                    if (!first) out.append(',');
                    first = false;
                    write(item, out);
                }
                out.append(']');
            }
            default -> throw new IllegalArgumentException("cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    // Control characters, and U+2028/U+2029 so the text is also safe inside a script.
                    if (c < 0x20 || c == ' ' || c == ' ') out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Reader {
        private final String text;
        private int at;

        Reader(String text) {
            this.text = text;
        }

        Object value() {
            if (at >= text.length()) throw error("unexpected end");
            char c = text.charAt(at);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield number();
                    throw error("unexpected '" + c + "'");
                }
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            space();
            if (take('}')) return Collections.unmodifiableMap(map);
            do {
                space();
                if (!peek('"')) throw error("expected a key");
                String key = string();
                space();
                expect(':');
                space();
                map.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return Collections.unmodifiableMap(map);
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            at++;
            space();
            if (take(']')) return Collections.unmodifiableList(list);
            do {
                space();
                list.add(value());
                space();
            } while (take(','));
            expect(']');
            return Collections.unmodifiableList(list);
        }

        private String string() {
            at++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (at >= text.length()) throw error("unterminated string");
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (at >= text.length()) throw error("unterminated escape");
                char e = text.charAt(at++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw error("short \\u escape");
                        try {
                            out.append((char) Integer.parseInt(text, at, at + 4, 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad \\u escape");
                        }
                        at += 4;
                    }
                    default -> throw error("bad escape \\" + e);
                }
            }
        }

        private Object number() {
            int start = at;
            if (peek('-')) at++;
            boolean decimal = false;
            while (at < text.length()) {
                char c = text.charAt(at);
                if (c >= '0' && c <= '9') at++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    decimal = true;
                    at++;
                } else break;
            }
            String digits = text.substring(start, at);
            try {
                if (!decimal) {
                    try {
                        return Long.parseLong(digits);
                    } catch (NumberFormatException tooLong) {
                        return Double.parseDouble(digits);
                    }
                }
                return Double.parseDouble(digits);
            } catch (NumberFormatException ex) {
                throw error("bad number " + digits);
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, at)) throw error("unexpected text");
            at += word.length();
            return value;
        }

        void space() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }

        private boolean peek(char c) {
            return at < text.length() && text.charAt(at) == c;
        }

        private boolean take(char c) {
            if (!peek(c)) return false;
            at++;
            return true;
        }

        private void expect(char c) {
            if (!take(c)) throw error("expected '" + c + "'");
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("bad JSON at " + at + ": " + message);
        }
    }
}
