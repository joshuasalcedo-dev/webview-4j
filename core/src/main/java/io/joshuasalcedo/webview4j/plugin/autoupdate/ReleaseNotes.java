package io.joshuasalcedo.webview4j.plugin.autoupdate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A CHANGELOG.md: each version with its date and its changes. The usual format: {@code ## <version> — <date>} (or
 * {@code ## [<version>] - <date>}), then the changes in Markdown. The build puts the app's own into its jar
 * ({@link #bundled()}), so What's new and the Changelog page need nothing else.
 */
public record ReleaseNotes(String markdown, List<Release> releases) {

    /** One version's entry. */
    public record Release(String version, String date, String body) {
        /** The entry as Markdown, with its heading. */
        public String markdown() {
            return "## " + version + (date == null || date.isBlank() ? "" : " — " + date) + "\n\n" + body + "\n";
        }
    }

    /** Where the build puts the app's changelog in its jar. */
    public static final String RESOURCE = "META-INF/webview4j/CHANGELOG.md";

    private static final Pattern HEADING = Pattern.compile("^##\\s+\\[?v?([0-9][^\\]\\s]*)\\]?\\s*(?:[—–-]\\s*(.+?))?\\s*$");

    public ReleaseNotes {
        releases = List.copyOf(releases);
    }

    public static ReleaseNotes parse(String markdown) {
        List<Release> out = new ArrayList<>();
        String version = null;
        String date = null;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\r?\\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                if (version != null) out.add(new Release(version, date, body.toString().strip()));
                version = m.group(1);
                date = m.group(2) == null ? null : m.group(2).strip();
                body.setLength(0);
            } else if (version != null) {
                body.append(line).append('\n');
            }
        }
        if (version != null) out.add(new Release(version, date, body.toString().strip()));
        return new ReleaseNotes(markdown, out);
    }

    /** The app's own changelog, put in its jar by the build; empty if it has none. */
    public static ReleaseNotes bundled() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader()
                : ReleaseNotes.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            return parse(in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Release> of(String version) {
        return releases.stream().filter(r -> r.version().equals(version)).findFirst();
    }

    /** The versions after {@code version} (What's new since it), up to and with {@code upTo}. */
    public List<Release> between(Version after, Version upTo) {
        return releases.stream()
                .filter(r -> Version.is(r.version()))
                .filter(r -> after == null || Version.of(r.version()).newerThan(after))
                .filter(r -> upTo == null || !Version.of(r.version()).newerThan(upTo))
                .toList();
    }
}
