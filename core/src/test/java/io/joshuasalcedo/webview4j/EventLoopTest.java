package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The protocol over FFM with nothing on top: frames into wv_send, frames out of wv_run's callback,
 * against a real, hidden window. Windows only for now: elsewhere the loop needs the main thread
 * (macOS) or a display (Linux CI). One test, since a process gets one event loop at a time.
 */
@EnabledOnOs(OS.WINDOWS)
class EventLoopTest {

    private final BlockingQueue<Message> received = new LinkedBlockingQueue<>();
    private final java.util.List<Message> seen = new java.util.ArrayList<>();
    private long inbox;

    /** Never leave a loop running for the next test class, whatever happened here. */
    @org.junit.jupiter.api.AfterEach
    void quit() {
        Native.send(Message.of("app.quit").encode());
    }

    @Test
    @Timeout(60)
    void framesInRepliesAndEventsOut() throws Exception {
        assertEquals(Native.NOT_RUNNING, Native.send(Message.of("app.list").encode()));

        Thread loop = Thread.ofPlatform().name("webview4j-event-loop").start(() -> Native.run(frame -> {
            try {
                received.add(Message.decode(frame));
            } catch (Message.FrameException e) {
                throw new AssertionError("the native side sent a bad frame: " + e.kind());
            }
        }));
        Message ready = next("app.ready");
        assertEquals("1", ready.header("Protocol"));
        assertThrows(IllegalStateException.class, () -> Native.run(frame -> {}), "a second loop must be refused");

        Message opened = request(Message.of("app.open").withJson(java.util.Map.of("html", "<p>hi</p>", "headless", true)));
        assertEquals("200", opened.header("Status"));
        String view = (String) ((java.util.Map<?, ?>) opened.json()).get("view");

        Message evaluated = request(Message.of("view." + view + ".eval").withText("1 + 1", "text/javascript"));
        assertEquals("200", evaluated.header("Status"));
        assertEquals(2L, evaluated.json());

        Message titled = request(Message.of("view." + view + ".title").withText("Renamed", "text/plain"));
        assertEquals("200", titled.header("Status"));
        assertTrue(request(Message.of("app.list")).text().contains("\"title\":\"Renamed\""));

        // errors are replies with a status, never a dropped message
        assertEquals("400", request(Message.of("view." + view + ".size").withJson(java.util.Map.of("width", 1))).header("Status"));
        assertEquals("400", request(Message.of("view." + view + ".size").withJson(java.util.Map.of("width", 1, "height", 1, "depth", 1))).header("Status"),
                "unknown keys are refused");
        assertEquals("404", request(Message.of("view.wv-404.show")).header("Status"));
        Message unknown = request(Message.of("view." + view + ".teleport"));
        assertEquals("404", unknown.header("Status"));
        assertEquals("text/plain", unknown.header("Content-Type"));
        assertEquals(Native.MALFORMED, Native.send("MSG view..show 2 0\r\n\r\n".getBytes()), "malformed frames are refused on the spot");

        assertEquals("200", request(Message.of("view." + view + ".close")).header("Status"));
        next("view." + view + ".closed");

        assertEquals("200", request(Message.of("app.quit")).header("Status"));
        loop.join(SECONDS.toMillis(10));
        assertFalse(loop.isAlive(), "run must return after app.quit");
        assertEquals(Native.NOT_RUNNING, Native.send(Message.of("app.list").encode()));
    }

    /** Sends with a fresh Reply-To and returns the reply. */
    private Message request(Message message) throws InterruptedException {
        String replyTo = "_inbox." + (++inbox);
        assertEquals(Native.SENT, Native.send(message.with("Reply-To", replyTo).encode()));
        return next(replyTo);
    }

    /** The first message to {@code subject}; messages to other subjects stay for later calls. */
    private Message next(String subject) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            for (java.util.Iterator<Message> it = seen.iterator(); it.hasNext(); ) {
                Message message = it.next();
                if (message.subject().equals(subject)) {
                    it.remove();
                    return message;
                }
            }
            Message message = received.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (message != null) seen.add(message);
        }
        throw new AssertionError("nothing came to " + subject);
    }
}
