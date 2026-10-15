package io.joshuasalcedo.webview4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A one-connection WebSocket server (RFC 6455, text frames only), enough for the page to talk to. */
final class TestWebSocketServer implements AutoCloseable {

    private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    private final CountDownLatch connected = new CountDownLatch(1);
    final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private volatile Socket socket;

    TestWebSocketServer() throws IOException {
        Thread.ofVirtual().name("test-websocket-server").start(this::serve);
    }

    String url() {
        return "ws://127.0.0.1:" + server.getLocalPort() + "/";
    }

    void awaitConnection() throws InterruptedException {
        if (!connected.await(20, TimeUnit.SECONDS)) throw new AssertionError("the page never connected");
    }

    synchronized void send(String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > 125) throw new IllegalArgumentException("short frames only");
        OutputStream out = socket.getOutputStream();
        out.write(0x81);                 // FIN + text
        out.write(payload.length);       // unmasked, server to client
        out.write(payload);
        out.flush();
    }

    private void serve() {
        try (Socket client = server.accept()) {
            socket = client;
            InputStream in = client.getInputStream();
            String key = null;
            for (String line; !(line = line(in)).isEmpty(); ) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) key = line.substring(18).trim();
            }
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            OutputStream out = client.getOutputStream();
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            connected.countDown();
            while (true) {
                int first = in.read();
                if (first < 0 || (first & 0x0F) == 0x8) return;   // gone, or a close frame
                int second = in.read();
                long length = second & 0x7F;
                if (length == 126) length = (in.read() << 8) | in.read();
                else if (length == 127) throw new IOException("long frames are not supported here");
                byte[] mask = in.readNBytes(4);                   // client frames are always masked
                byte[] payload = in.readNBytes((int) length);
                for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                if ((first & 0x0F) == 0x1) received.add(new String(payload, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            if (!server.isClosed()) e.printStackTrace();
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int b; (b = in.read()) != -1 && b != '\n'; ) if (b != '\r') line.write(b);
        return line.toString(StandardCharsets.US_ASCII);
    }

    @Override
    public void close() throws IOException {
        server.close();
        if (socket != null) socket.close();
    }
}
