package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

/** What the dev logger sends its page, for each kind of event: protocol/fixtures/dev-logger.json, which Rust takes. */
class DevLoggerTest {

    record User(long id, String name) {}

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fixture() throws IOException {
        Path directory = Path.of(System.getProperty("webview4j.fixtures", "../protocol/fixtures"));
        Map<String, Object> f = (Map<String, Object>) Json.parse(Files.readString(directory.resolve("dev-logger.json")));
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object c : (List<Object>) f.get("cases")) entries.add((Map<String, Object>) ((Map<String, Object>) c).get("entry"));
        return entries;
    }

    /** As JSON goes: numbers compared as numbers (120 and 120.0 are the same), "time" left out. */
    private static Object normal(Object value) {
        return switch (value) {
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> { if (!"time".equals(k)) out.put(String.valueOf(k), normal(v)); });
                yield out;
            }
            case List<?> list -> list.stream().map(DevLoggerTest::normal).toList();
            case Number n -> n.doubleValue();
            case null, default -> value;
        };
    }

    private static Object asSent(Map<String, Object> entry) {
        return normal(Json.parse(Json.write(entry)));
    }

    @Test
    void entriesAreWhatTheFixtureSays() throws IOException {
        java.util.logging.LogRecord jul = new java.util.logging.LogRecord(Level.INFO, "saved {0} todos");
        jul.setParameters(new Object[] {3});
        jul.setLoggerName("app.todos");
        Map<String, Object> headers = Map.of(
                "requestHeaders", Map.of("content-type", "application/json"),
                "responseHeaders", Map.of("content-type", "application/json"));

        List<Map<String, Object>> made = List.of(
                DevLogger.entry(new LogRecord(LogRecord.Source.PAGE, "v1", LogRecord.Level.WARN, null, "slow")),
                DevLogger.entry(new LogRecord(LogRecord.Source.NATIVE, null, LogRecord.Level.ERROR, "webview4j::runtime", "boom")),
                DevLogger.entry(jul),
                DevLogger.entry(new ApiCall("v1", "POST", "/api/todos", 500, 120, "{\"title\":\"milk\"}", "{\"error\":\"locked\"}", headers)),
                DevLogger.entry(new ApiCall("v1", "GET", "https://rates.example/today", 0, 3000, null, null, Map.of())),
                DevLogger.entry(new PageCall("v1", "users.find", Map.of("id", 42), new User(42, "Ana"), null, 3.5)),
                DevLogger.entry(new PageCall("v1", "GET /users/9", null, null, "no user 9", 1.25)),
                DevLogger.log(1, "WARN", "com.example.Orders", "retrying order 7"),
                DevLogger.javaCall(1, "GET", "https://api.example.com/rates", 200, 84.5, Map.of("accept", "application/json"), null,
                        Map.of("content-type", "application/json"), "{\"usd\":56.1}", null),
                DevLogger.javaCall(1, "POST", "https://api.example.com/pay", 0, 2.0, Map.of(), "{\"amount\":10}", Map.of(), null,
                        "Connection refused"));

        List<Map<String, Object>> expected = fixture();
        assertEquals(expected.size(), made.size());
        for (int i = 0; i < made.size(); i++) {
            assertEquals(normal(expected.get(i)), asSent(made.get(i)), "case " + i);
            assertTrue(made.get(i).get("time") instanceof Number, "case " + i + " has a time");
        }
    }

    @Test
    void longBodiesAreCutAndOddOnesWrittenAsText() {
        String big = "x".repeat(DevLogger.MAX_BODY + 10);
        Map<String, Object> call = DevLogger.entry(new ApiCall("v1", "GET", "/big", 200, 5, null, big, Map.of()));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) call.get("response")).get("body");
        assertEquals(DevLogger.MAX_BODY + 1, body.length(), "cut, with … at the end");
        assertTrue(body.endsWith("…"));

        // not JSON (an object Json cannot write): its text
        Object odd = new Object() { @Override public String toString() { return "an odd thing"; } };
        Map<String, Object> answered = DevLogger.entry(new PageCall("v1", "odd", null, odd, null, 1));
        @SuppressWarnings("unchecked")
        Object result = ((Map<String, Object>) answered.get("response")).get("body");
        assertEquals("an odd thing", result);
    }

    @Test
    void javaLevelsBecomeTheLoggersLevels() {
        for (var c : List.of(Map.entry(Level.SEVERE, "error"), Map.entry(Level.WARNING, "warn"), Map.entry(Level.INFO, "info"),
                Map.entry(Level.CONFIG, "debug"), Map.entry(Level.FINE, "debug"), Map.entry(Level.FINEST, "trace"))) {
            java.util.logging.LogRecord r = new java.util.logging.LogRecord(c.getKey(), "m");
            r.setLoggerName("x");
            assertEquals(c.getValue(), DevLogger.entry(r).get("level"), c.getKey().getName());
        }
    }

    @Test
    void aMessageThatIsNotAPatternStaysAsItIs() {
        java.util.logging.LogRecord r = new java.util.logging.LogRecord(Level.INFO, "a {broken pattern");
        r.setParameters(new Object[] {1});
        r.setLoggerName("x");
        assertEquals("a {broken pattern", DevLogger.entry(r).get("message"));
    }
}
