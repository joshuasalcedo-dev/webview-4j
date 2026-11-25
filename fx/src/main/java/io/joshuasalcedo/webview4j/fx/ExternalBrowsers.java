package io.joshuasalcedo.webview4j.fx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The other browsers on this computer, to open a page in one of them: the system's default, and Chrome, Edge,
 * Firefox, Brave, Opera or Vivaldi where installed. Only http(s) addresses, each given to the browser as one argument
 * (no shell in between).
 */
public final class ExternalBrowsers {

    /** One installed browser. */
    public record Browser(String name, Path executable) {

        /** Opens {@code url} in it, in a new tab or window as it does. */
        public void open(String url) {
            start(List.of(executable.toString(), checked(url)));
        }
    }

    private ExternalBrowsers() {}

    /** The browsers found in their usual places, in this order: Chrome, Edge, Firefox, Brave, Opera, Vivaldi. */
    public static List<Browser> installed() {
        if (!windows()) return List.of();
        String programs = System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files");
        String programs86 = System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)");
        String local = System.getenv().getOrDefault("LOCALAPPDATA", "");
        List<Browser> found = new ArrayList<>();
        find(found, "Google Chrome", programs + "\\Google\\Chrome\\Application\\chrome.exe",
                programs86 + "\\Google\\Chrome\\Application\\chrome.exe", local + "\\Google\\Chrome\\Application\\chrome.exe");
        find(found, "Microsoft Edge", programs86 + "\\Microsoft\\Edge\\Application\\msedge.exe",
                programs + "\\Microsoft\\Edge\\Application\\msedge.exe");
        find(found, "Firefox", programs + "\\Mozilla Firefox\\firefox.exe", programs86 + "\\Mozilla Firefox\\firefox.exe");
        find(found, "Brave", programs + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                local + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
        find(found, "Opera", local + "\\Programs\\Opera\\opera.exe", programs + "\\Opera\\opera.exe");
        find(found, "Vivaldi", local + "\\Vivaldi\\Application\\vivaldi.exe", programs + "\\Vivaldi\\Application\\vivaldi.exe");
        return List.copyOf(found);
    }

    /** Opens {@code url} in the system's default browser. */
    public static void openInDefault(String url) {
        String checked = checked(url);
        if (windows()) start(List.of("rundll32", "url.dll,FileProtocolHandler", checked));
        else if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) start(List.of("open", checked));
        else start(List.of("xdg-open", checked));
    }

    private static void find(List<Browser> found, String name, String... places) {
        for (String place : places) {
            if (place.isEmpty() || place.startsWith("\\")) continue;
            Path path = Path.of(place);
            if (Files.isRegularFile(path)) {
                found.add(new Browser(name, path));
                return;
            }
        }
    }

    /** An http(s) address in one piece, or refused: never a flag or a command for the browser. */
    static String checked(String url) {
        Objects.requireNonNull(url, "url");
        String trimmed = url.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))
                || trimmed.chars().anyMatch(c -> c <= ' ' || c == '"')) {
            throw new IllegalArgumentException("only an http(s) address opens in another browser, not " + url);
        }
        return trimmed;
    }

    private static void start(List<String> command) {
        try {
            new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw new UncheckedIOException("could not start " + command.getFirst(), e);
        }
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
