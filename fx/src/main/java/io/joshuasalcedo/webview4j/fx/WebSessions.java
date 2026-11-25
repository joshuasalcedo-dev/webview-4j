package io.joshuasalcedo.webview4j.fx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import io.joshuasalcedo.webview4j.WindowOptions;

/**
 * Saved browser sessions on disk: each is a folder of its own that the webview keeps its profile in (cookies,
 * localStorage, IndexedDB, the cache, signed-in sites), and the address it was last on. Open one again and it is as
 * it was left; a new one starts empty.
 *
 * <pre>{@code
 * WebSessions sessions = WebSessions.in(Path.of(System.getenv("LOCALAPPDATA"), "MyApp", "sessions"));
 * WebSessions.Session work = sessions.create();                // or sessions.find("work")
 * WebViewPane web = new WebViewPane(app, work.options("https://en.wikipedia.org"));
 * web.locationProperty().addListener((p, before, url) -> work.remember(url));
 * }</pre>
 *
 * <p>One session is open in one webview at a time: WebView2 keeps a profile folder for the page using it.
 */
public final class WebSessions {

    private static final String LAST_URL = "last-url.txt";
    private static final String PROFILE = "profile";
    private static final DateTimeFormatter NAMES = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path root;

    private WebSessions(Path root) {
        this.root = root;
    }

    /** The sessions kept in this folder, which is made when needed. */
    public static WebSessions in(Path root) {
        return new WebSessions(Objects.requireNonNull(root, "root").toAbsolutePath());
    }

    public Path root() {
        return root;
    }

    /** Every saved session, the most recently used first. */
    public List<Session> list() {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> folders = Files.list(root)) {
            return folders.filter(Files::isDirectory).map(Session::new)
                    .sorted(Comparator.comparing(Session::lastUsed).reversed()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Session> find(String name) {
        Session session = new Session(root.resolve(checked(name)));
        return Files.isDirectory(session.directory()) ? Optional.of(session) : Optional.empty();
    }

    /** A new, empty session, named for now ({@code 2026-11-25_14-03-12}). */
    public Session create() {
        String name = LocalDateTime.now().format(NAMES);
        for (int n = 2; Files.exists(root.resolve(name)); n++) name = LocalDateTime.now().format(NAMES) + "-" + n;
        return create(name);
    }

    /** A new, empty session with this name, or the one already saved by it. */
    public Session create(String name) {
        Session session = new Session(root.resolve(checked(name)));
        try {
            Files.createDirectories(session.profile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return session;
    }

    private static String checked(String name) {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[A-Za-z0-9._-]{1,80}") || name.startsWith(".")) {
            throw new IllegalArgumentException("a session's name is letters, digits, '.', '_' and '-', not '" + name + "'");
        }
        return name;
    }

    /** One saved session: its folder, and the address it was last on. */
    public record Session(Path directory) {

        public String name() {
            return directory.getFileName().toString();
        }

        /** The webview's profile: give it to {@link WindowOptions#dataDirectory(Path)}. */
        public Path profile() {
            return directory.resolve(PROFILE);
        }

        /** Window options for this session, opening where it was left, or at {@code home} the first time. */
        public WindowOptions options(String home) {
            return WindowOptions.url(lastUrl().orElse(home)).dataDirectory(profile()).browse();
        }

        /** The address it was last on, saved by {@link #remember}. */
        public Optional<String> lastUrl() {
            try {
                Path file = directory.resolve(LAST_URL);
                if (!Files.exists(file)) return Optional.empty();
                String url = Files.readString(file, StandardCharsets.UTF_8).strip();
                return url.startsWith("http://") || url.startsWith("https://") ? Optional.of(url) : Optional.empty();
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        /** Saves the address it is on: it opens there next time. Only http(s) addresses are kept. */
        public void remember(String url) {
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
            try {
                Files.createDirectories(directory);
                Files.writeString(directory.resolve(LAST_URL), url, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** When it was last used: its last address's save, or its folder's. */
        public java.time.Instant lastUsed() {
            try {
                Path file = directory.resolve(LAST_URL);
                return Files.getLastModifiedTime(Files.exists(file) ? file : directory).toInstant();
            } catch (IOException e) {
                return java.time.Instant.EPOCH;
            }
        }

        /** How much the profile keeps on disk, in bytes. */
        public long size() {
            if (!Files.isDirectory(directory)) return 0;
            try (Stream<Path> files = Files.walk(directory)) {
                return files.filter(Files::isRegularFile).mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (IOException e) {
                        return 0;
                    }
                }).sum();
            } catch (IOException | UncheckedIOException e) {
                return 0;
            }
        }

        /** Deletes it from disk. Close its webview first: WebView2 holds the profile while a page uses it. */
        public void delete() {
            if (!Files.exists(directory)) return;
            try (Stream<Path> files = Files.walk(directory)) {
                List<Path> all = new ArrayList<>(files.toList());
                all.sort(Comparator.reverseOrder());
                for (Path file : all) Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
