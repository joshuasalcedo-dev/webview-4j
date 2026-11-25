package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.ComponentsTest.until;
import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.components.MediaPlayer;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The media player from Java: it reads the file's length, and a seek on its bar reaches Java with the time. */
class MediaPlayerTest {

    /** {@code seconds} of silence as a 16-bit mono WAV. */
    static byte[] silence(int seconds) {
        int rate = 8_000;
        int bytes = rate * seconds * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + bytes).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(bytes);
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        wav.writeBytes(header.array());
        wav.writeBytes(new byte[bytes]);
        return wav.toByteArray();
    }

    @Test
    void bytesBecomeADataUrlAndBadSettingsAreRefused() {
        Component<?> player = MediaPlayer.of(new byte[] {1, 2, 3}, "audio/wav");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> p = (java.util.Map<String, Object>) GeneratedComponentsTest.tree(player).get("p");
        assertEquals("data:audio/wav;base64,AQID", p.get("src"));
        assertEquals("audio", p.get("kind"));
        assertThrows(IllegalArgumentException.class, () -> MediaPlayer.of(new byte[0], "text/html"));
        assertThrows(IllegalArgumentException.class, () -> mediaPlayer("a.mp3").volume(2));
        assertThrows(IllegalArgumentException.class, () -> mediaPlayer("a.mp3").rate(10));
    }

    static final class Listening implements Screen {
        volatile Double seekedTo;

        @Override
        public Component<?> render() {
            return column(MediaPlayer.of(silence(3), "audio/wav").title("Silence").onSeek(at -> seekedTo = at),
                    text("seeked " + (seekedTo == null ? "no" : String.format("%.1f", seekedTo)))).padding(16);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void itReadsTheLengthAndASeekReachesJava() throws Exception {
        try (App app = App.start()) {
            Listening screen = new Listening();
            View view = Ui.open(app, screen, o -> o.headless(true).size(700, 300)).join().view();
            until(view, "0:03");   // the length, read from the file
            view.eval("""
                    (() => { const bar = document.querySelector('input[aria-label=Seek]');
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(bar, '1.5'); bar.dispatchEvent(new Event('input', {bubbles: true})); })()""").join();
            until(view, "seeked 1.5");
            assertEquals(1.5, screen.seekedTo, 0.05);
            assertEquals(true, view.eval("!!document.querySelector('button[aria-label=Play]')").join(), "a play button");
        }
    }
}
