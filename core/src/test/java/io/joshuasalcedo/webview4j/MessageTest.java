package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The protocol's conformance fixtures (protocol/fixtures), which Rust runs too. */
class MessageTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixture(String name) throws IOException {
        Path directory = Path.of(System.getProperty("webview4j.fixtures", "../protocol/fixtures"));
        return (Map<String, Object>) Json.parse(Files.readString(directory.resolve(name)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cases(Map<String, Object> fixture, String key) {
        return (List<Map<String, Object>>) fixture.get(key);
    }

    private static byte[] bytes(Map<String, Object> c, String text, String base64) {
        if (c.get(text) instanceof String s) return s.getBytes(StandardCharsets.UTF_8);
        return Base64.getDecoder().decode((String) c.get(base64));
    }

    @SuppressWarnings("unchecked")
    private static List<Message.Header> pairs(Object value) {
        List<Message.Header> headers = new ArrayList<>();
        for (Object pair : (List<Object>) value) {
            List<Object> p = (List<Object>) pair;
            headers.add(new Message.Header((String) p.get(0), (String) p.get(1)));
        }
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void decodesEveryFixtureFrame() throws Exception {
        for (Map<String, Object> c : cases(fixture("frames.json"), "decode")) {
            Message message = Message.decode(bytes(c, "frame", "frameBase64"));
            Map<String, Object> expect = (Map<String, Object>) c.get("expect");
            assertEquals(expect.get("subject"), message.subject(), (String) c.get("name"));
            assertEquals(pairs(expect.get("headers")), message.headers(), (String) c.get("name"));
            assertArrayEquals(bytes(expect, "payload", "payloadBase64"), message.payload(), (String) c.get("name"));
        }
    }

    @Test
    void encodesEveryFixtureMessageExactly() throws Exception {
        for (Map<String, Object> c : cases(fixture("frames.json"), "encode")) {
            Message message = new Message((String) c.get("subject"), pairs(c.get("headers")),
                    ((String) c.get("payload")).getBytes(StandardCharsets.UTF_8));
            assertEquals(c.get("frame"), new String(message.encode(), StandardCharsets.UTF_8), (String) c.get("name"));
            Message back = Message.decode(message.encode());
            assertEquals(message.subject(), back.subject());
            assertEquals(message.headers(), back.headers());
            assertArrayEquals(message.payload(), back.payload());
        }
    }

    @Test
    void refusesEveryFixtureErrorWithItsKind() throws Exception {
        for (Map<String, Object> c : cases(fixture("frames.json"), "errors")) {
            Message.FrameException error = assertThrows(Message.FrameException.class,
                    () -> Message.decode(((String) c.get("frame")).getBytes(StandardCharsets.UTF_8)), (String) c.get("name"));
            assertEquals(c.get("error"), error.kind(), (String) c.get("name"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void subjectsAndHeaderBlocksMatchTheFixtures() throws Exception {
        Map<String, Object> subjects = fixture("subjects.json");
        for (Object subject : (List<Object>) subjects.get("valid")) assertTrue(Message.validSubject((String) subject), (String) subject);
        for (Object subject : (List<Object>) subjects.get("invalid")) assertFalse(Message.validSubject((String) subject), (String) subject);

        Map<String, Object> blocks = fixture("headers.json");
        for (Map<String, Object> c : cases(blocks, "valid")) {
            assertEquals(pairs(c.get("headers")),
                    Message.headers(((String) c.get("block")).getBytes(StandardCharsets.UTF_8)), (String) c.get("block"));
        }
        for (Object block : (List<Object>) blocks.get("invalid")) {
            Message.FrameException error = assertThrows(Message.FrameException.class,
                    () -> Message.headers(((String) block).getBytes(StandardCharsets.UTF_8)), (String) block);
            assertEquals("bad-header", error.kind());
        }
    }

    @Test
    void headersAreFoundWithoutRegardToCase() {
        Message message = Message.of("_inbox.1").with("Status", "404").with("status", "500");
        assertEquals("404", message.header("STATUS"), "the first value wins");
        assertNull(message.header("Reply-To"));
    }
}
