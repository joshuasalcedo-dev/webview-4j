package io.joshuasalcedo.webview4j.ui;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** The server's side of one WebSocket (RFC 6455): text messages in and out, pings answered, a size limit. */
final class WebSocket implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int maxMessage;
    private volatile boolean closed;

    WebSocket(Socket socket, InputStream in, int maxMessage) throws IOException {
        this.socket = socket;
        this.in = in;
        this.out = socket.getOutputStream();
        this.maxMessage = maxMessage;
    }

    /** The handshake's answer to the client's key. */
    static String accept(String key) {
        try {
            byte[] sha = MessageDigest.getInstance("SHA-1").digest((key.trim() + GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(sha);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The next text message; null when the client closed. Pings are answered, pongs ignored. */
    String read() throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        while (true) {
            int b0 = in.read();
            if (b0 < 0) return null;
            int b1 = need(in.read());
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long length = b1 & 0x7F;
            if (length == 126) length = (need(in.read()) << 8) | need(in.read());
            else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) length = (length << 8) | need(in.read());
            }
            if (!masked) throw new IOException("a client's frames are masked");
            if (length > maxMessage || message.size() + length > maxMessage) throw new IOException("a message over " + maxMessage + " bytes");
            byte[] mask = in.readNBytes(4);
            byte[] payload = in.readNBytes((int) length);
            if (payload.length != length || mask.length != 4) throw new EOFException();
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
            switch (opcode) {
                case 0x8 -> {
                    send(0x8, new byte[0]);
                    return null;
                }
                case 0x9 -> send(0xA, payload);
                case 0xA -> { }
                case 0x1, 0x0 -> {
                    message.write(payload);
                    if (fin) return message.toString(StandardCharsets.UTF_8);
                }
                default -> throw new IOException("a frame of opcode " + opcode);
            }
        }
    }

    void send(String text) throws IOException {
        send(0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void send(int opcode, byte[] payload) throws IOException {
        if (closed && opcode != 0x8) throw new IOException("closed");
        ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 10);
        frame.write(0x80 | opcode);
        if (payload.length < 126) frame.write(payload.length);
        else if (payload.length < 65536) {
            frame.write(126);
            frame.write(payload.length >>> 8);
            frame.write(payload.length & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) frame.write((int) ((long) payload.length >>> (8 * i)) & 0xFF);
        }
        frame.write(payload);
        out.write(frame.toByteArray());
        out.flush();
    }

    private static int need(int b) throws EOFException {
        if (b < 0) throw new EOFException();
        return b;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            send(0x8, new byte[0]);
        } catch (IOException e) {
            // already gone
        }
        try {
            socket.close();
        } catch (IOException e) {
            // already gone
        }
    }
}
