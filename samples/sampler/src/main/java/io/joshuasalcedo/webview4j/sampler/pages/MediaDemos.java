package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** The Sampler's pages: the media player, with a sound made here (no file to ship). */
final class MediaDemos implements Demos {

    private String said = "Play it, seek on the bar, change the volume or the speed (Space, ←, →, M also work).";

    /** A chord that fades out, as a 16-bit mono WAV: what the player plays. */
    static byte[] chime(double seconds) {
        int rate = 16_000;
        int samples = (int) (rate * seconds);
        ByteBuffer pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            double t = (double) i / rate;
            double fade = Math.exp(-2.5 * t / seconds) * Math.min(1, t * 50);
            double wave = Math.sin(2 * Math.PI * 523.25 * t) + 0.6 * Math.sin(2 * Math.PI * 659.25 * t) + 0.4 * Math.sin(2 * Math.PI * 783.99 * t);
            pcm.putShort((short) (wave / 2.0 * fade * 0.6 * Short.MAX_VALUE));
        }
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(samples * 2);
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        wav.writeBytes(header.array());
        wav.writeBytes(pcm.array());
        return wav.toByteArray();
    }

    private static final byte[] CHIME = chime(6);

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Content", "Media player", () -> column(
                        MediaPlayer.of(CHIME, "audio/wav").title("A chime")
                                .onPlay(() -> said = "Playing")
                                .onPause(() -> said = "Paused")
                                .onEnded(() -> said = "Played to the end")
                                .onSeek(at -> said = String.format("Went to %.1f s", at))
                                .maxWidth(520),
                        text(said).tone(Tone.SECONDARY)).gap(12),
                """
                new MediaPlayer("app/welcome.mp4").title("Welcome").captions("app/welcome.vtt")   // an app file: seeks without loading it all
                MediaPlayer.of(bytes, "audio/wav").title("A chime")   // bytes Java has
                        .onEnded(() -> heard = true).onSeek(at -> ...)""",
                MediaPlayer.class);
    }
}
