package io.joshuasalcedo.webview4j;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A protocol message (PROTOCOL.md §1): a subject, headers, and a payload nobody in the middle
 * reads. {@link #decode} checks a frame in the order of §1.2, so it names the same error as every
 * other implementation; {@code protocol/fixtures} holds the cases they all run.
 */
record Message(String subject, List<Header> headers, byte[] payload) {

    static final int MAX_SUBJECT = 256;
    static final long MAX_HEADERS = 64 * 1024;
    static final long MAX_PAYLOAD = 64 * 1024 * 1024;
    private static final int MAX_LINE = 300;

    record Header(String name, String value) {}

    /** Why a frame was refused; {@link #kind} is the name the fixtures use. */
    static final class FrameException extends Exception {
        private final String kind;

        FrameException(String kind) {
            super(kind);
            this.kind = kind;
        }

        String kind() {
            return kind;
        }
    }

    Message {
        Objects.requireNonNull(subject, "subject");
        headers = List.copyOf(headers);
        Objects.requireNonNull(payload, "payload");
    }

    static Message of(String subject) {
        return new Message(subject, List.of(), new byte[0]);
    }

    Message with(String name, String value) {
        List<Header> more = new ArrayList<>(headers);
        more.add(new Header(name, value));
        return new Message(subject, more, payload);
    }

    Message withPayload(byte[] bytes, String contentType) {
        return new Message(subject, headers, bytes).with("Content-Type", contentType);
    }

    Message withText(String text, String contentType) {
        return withPayload(text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    Message withJson(Object value) {
        return withText(Json.write(value), "application/json");
    }

    /** The first value of header {@code name}, compared without regard to case; null if absent. */
    String header(String name) {
        for (Header header : headers) {
            if (header.name().equalsIgnoreCase(name)) return header.value();
        }
        return null;
    }

    String text() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** The payload as JSON values; null when empty. */
    Object json() {
        return payload.length == 0 ? null : Json.parse(text());
    }

    byte[] encode() {
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        for (Header header : headers) block.writeBytes((header.name() + ": " + header.value() + "\r\n").getBytes(StandardCharsets.UTF_8));
        block.writeBytes(new byte[] {'\r', '\n'});
        byte[] line = ("MSG " + subject + " " + block.size() + " " + payload.length + "\r\n").getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream frame = new ByteArrayOutputStream(line.length + block.size() + payload.length);
        frame.writeBytes(line);
        frame.writeBytes(block.toByteArray());
        frame.writeBytes(payload);
        return frame.toByteArray();
    }

    static Message decode(byte[] frame) throws FrameException {
        // 1. the line
        int end = -1;
        for (int i = 0; i + 1 < frame.length && i <= MAX_LINE; i++) {
            if (frame[i] == '\r' && frame[i + 1] == '\n') {
                end = i;
                break;
            }
        }
        if (end < 0) throw new FrameException("bad-line");
        for (int i = 0; i < end; i++) {
            if (frame[i] < 0x20 || frame[i] > 0x7e) throw new FrameException("bad-line");
        }
        String[] parts = new String(frame, 0, end, StandardCharsets.US_ASCII).split(" ", -1);
        if (parts.length != 4 || !parts[0].equals("MSG") || parts[1].isEmpty() || parts[2].isEmpty() || parts[3].isEmpty()) {
            throw new FrameException("bad-line");
        }
        // 2. the subject
        String subject = parts[1];
        if (subject.length() > MAX_SUBJECT) throw new FrameException("too-large");
        if (!validSubject(subject)) throw new FrameException("bad-subject");
        // 3. the lengths
        long headerLength = length(parts[2]);
        long payloadLength = length(parts[3]);
        if (headerLength < 2) throw new FrameException("bad-length");
        // 4. the limits, from the line alone
        if (headerLength > MAX_HEADERS || payloadLength > MAX_PAYLOAD) throw new FrameException("too-large");
        // 5. exactly that many bytes follow
        long rest = frame.length - end - 2L;
        if (rest < headerLength + payloadLength) throw new FrameException("truncated");
        if (rest > headerLength + payloadLength) throw new FrameException("bad-length");
        // 6. the header block
        int blockStart = end + 2;
        int payloadStart = blockStart + (int) headerLength;
        return new Message(subject, headers(Arrays.copyOfRange(frame, blockStart, payloadStart)),
                Arrays.copyOfRange(frame, payloadStart, frame.length));
    }

    private static long length(String digits) throws FrameException {
        boolean plain = digits.chars().allMatch(c -> c >= '0' && c <= '9')
                && (digits.equals("0") || digits.charAt(0) != '0');
        if (!plain) throw new FrameException("bad-length");
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException tooBig) {
            throw new FrameException("bad-length");
        }
    }

    /** A header block: {@code Name: value\r\n} lines, then an empty line (PROTOCOL.md §3). */
    static List<Header> headers(byte[] block) throws FrameException {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(block)).toString();
        } catch (CharacterCodingException e) {
            throw new FrameException("bad-header");
        }
        if (!text.endsWith("\r\n")) throw new FrameException("bad-header");
        String lines = text.substring(0, text.length() - 2);
        if (!lines.isEmpty() && !lines.endsWith("\r\n")) throw new FrameException("bad-header");
        List<Header> headers = new ArrayList<>();
        if (lines.isEmpty()) return headers;
        for (String line : lines.substring(0, lines.length() - 2).split("\r\n", -1)) {
            if (line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) throw new FrameException("bad-header");
            int colon = line.indexOf(':');
            if (colon <= 0) throw new FrameException("bad-header");
            String name = line.substring(0, colon);
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (!(asciiAlphanumeric(c) || c == '-')) throw new FrameException("bad-header");
            }
            headers.add(new Header(name, trim(line.substring(colon + 1))));
        }
        return headers;
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == ' ' || value.charAt(start) == '\t')) start++;
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\t')) end--;
        return value.substring(start, end);
    }

    /** Tokens of {@code A-Z a-z 0-9 - _} separated by single dots, at most 256 bytes (PROTOCOL.md §2). */
    static boolean validSubject(String subject) {
        if (subject.getBytes(StandardCharsets.UTF_8).length > MAX_SUBJECT) return false;
        for (String token : subject.split("\\.", -1)) {
            if (!validToken(token)) return false;
        }
        return true;
    }

    static boolean validToken(String token) {
        if (token.isEmpty()) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(asciiAlphanumeric(c) || c == '-' || c == '_')) return false;
        }
        return true;
    }

    private static boolean asciiAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    @Override
    public String toString() {
        return "Message[" + subject + ", " + headers + ", " + payload.length + " bytes]";
    }
}
