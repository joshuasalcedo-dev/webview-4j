package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A sound or a video with the system's kind of controls: play and pause, a bar to seek on, the time, the volume and
 * mute, the speed, and full screen for a video. Its file is an app file ({@code "app/intro.mp4"}, which seeks
 * without loading all of it), bytes from Java ({@link #of}), or an address.
 * <pre>{@code
 * new MediaPlayer("app/welcome.mp4").title("Welcome").captions("app/welcome.vtt")
 * MediaPlayer.of(Files.readAllBytes(voicemail), "audio/mpeg").title("Voicemail from Ana").onEnded(() -> heard = true)
 * }</pre>
 */
public final class MediaPlayer extends Component<MediaPlayer> {

    /** Shows the picture, or only the controls; left out, it goes by the file's extension. */
    public enum Kind {
        AUDIO("audio"), VIDEO("video");

        final String key;

        Kind(String key) { this.key = key; }
    }

    public MediaPlayer(String src) {
        super("mediaPlayer");
        prop("src", Objects.requireNonNull(src, "src"));
    }

    /**
     * A player for bytes Java has (a recording, a file read from disk): sent with the screen as a data: URL, so best
     * for short ones; a long video is better as an app file.
     */
    public static MediaPlayer of(byte[] media, String contentType) {
        Objects.requireNonNull(media, "media");
        if (contentType == null || !contentType.matches("(audio|video)/[\\w.+-]+")) {
            throw new IllegalArgumentException("not a sound or video type: " + contentType);
        }
        MediaPlayer player = new MediaPlayer("data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(media));
        return player.kind(contentType.startsWith("video/") ? Kind.VIDEO : Kind.AUDIO);
    }

    public MediaPlayer kind(Kind kind) { return prop("kind", kind == null ? null : kind.key); }
    /** What it is, for the controls and screen readers. */
    public MediaPlayer title(String title) { return prop("title", title); }
    /** A picture shown before a video plays. */
    public MediaPlayer poster(String poster) { return prop("poster", poster); }
    /** Captions or subtitles, a WebVTT file ({@code .vtt}), in English. */
    public MediaPlayer captions(String vtt) { return prop("captions", vtt); }
    /** Captions in another language ({@code "fil"}, {@code "es"}). */
    public MediaPlayer captions(String vtt, String language) { return captions(vtt).prop("captionsLanguage", language); }
    public MediaPlayer autoPlay() { return prop("autoPlay", true); }
    public MediaPlayer loop() { return prop("loop", true); }
    /** Starts with the sound off (a video that plays by itself must). */
    public MediaPlayer muted() { return prop("muted", true); }

    /** 0 to 1. */
    public MediaPlayer volume(double volume) {
        if (volume < 0 || volume > 1) throw new IllegalArgumentException("volume is 0 to 1: " + volume);
        return prop("volume", volume);
    }

    /** Playback speed, 0.25 to 4. */
    public MediaPlayer rate(double rate) {
        if (rate < 0.25 || rate > 4) throw new IllegalArgumentException("rate is 0.25 to 4: " + rate);
        return prop("rate", rate);
    }

    /** Hides the speed button. */
    public MediaPlayer hideRate() { return prop("showRate", false); }

    public MediaPlayer onPlay(Runnable action) { return on("onPlay", v -> action.run()); }
    public MediaPlayer onPause(Runnable action) { return on("onPause", v -> action.run()); }
    /** It played to the end. */
    public MediaPlayer onEnded(Runnable action) { return on("onEnded", v -> action.run()); }
    /** The time, in seconds, when the person seeks (not as it plays). */
    public MediaPlayer onSeek(Consumer<Double> action) { return on("onSeek", v -> action.accept(Keys.number(v))); }
    /** It could not be played: why. */
    public MediaPlayer onError(Consumer<String> action) { return on("onError", v -> action.accept(Keys.text(v))); }
}
