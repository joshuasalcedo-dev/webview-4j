package io.joshuasalcedo.swaswa.ffi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Whether the app runs in development or in production, and so where its data goes.
 *
 * <p>Set with {@code -Dswaswa.mode=dev} or {@code prod} (or the {@code SWASWA_MODE} environment variable). Unset, it is
 * {@link #DEV} when the app runs from a build's class folders ({@code target/classes}, {@code target/test-classes},
 * Gradle's {@code build/classes}, an IDE's {@code out/production}), {@link #PROD} when from jars (as installed).
 *
 * <p>In development an app's data (its browser profile, its stores, its windows' places) goes in its build folder,
 * {@code <target>/swaswa-dev/<app>}: a clean build takes it away, and runs never fill the user's own data folder
 * ({@code %LOCALAPPDATA%}). In production it goes in the system's place for an app's data.
 */
public enum AppMode {
    DEV, PROD;

    private static final Set<String> BUILD_FOLDERS = Set.of("target", "build", "out");
    private static volatile AppMode current;
    private static volatile Optional<Path> buildDirectory;

    /** The mode this app runs in: set, or found from where its classes are (once, then kept). */
    public static AppMode current() {
        AppMode m = current;
        if (m == null) {
            String set = System.getProperty("swaswa.mode", System.getenv("SWASWA_MODE"));
            if (set != null && !set.isBlank()) {
                m = switch (set.strip().toLowerCase(Locale.ROOT)) {
                    case "dev", "development" -> DEV;
                    case "prod", "production" -> PROD;
                    default -> throw new IllegalArgumentException("swaswa.mode is dev or prod, not " + set);
                };
            } else {
                m = buildDirectory().isPresent() ? DEV : PROD;
            }
            current = m;
        }
        return m;
    }

    public static boolean isDev() {
        return current() == DEV;
    }

    /**
     * The build folder the app runs from ({@code .../target}), if it runs from one: the first class folder on its class
     * path that is in a {@code target}, {@code build} or {@code out} folder.
     */
    public static Optional<Path> buildDirectory() {
        Optional<Path> b = buildDirectory;
        if (b == null) {
            b = find();
            buildDirectory = b;
        }
        return b;
    }

    /**
     * Where an app named {@code app} keeps its data in development: {@code <build>/swaswa-dev/<app>}, or the working
     * folder's {@code target/swaswa-dev/<app>} when dev was set but the app runs from no build folder.
     */
    static Path devDirectory(String app) {
        Path build = buildDirectory().orElseGet(() -> Path.of("target").toAbsolutePath());
        return build.resolve("swaswa-dev").resolve(app);
    }

    private static Optional<Path> find() {
        try {
            // the class path's folders (a test run's too: surefire's own jar names them in its manifest)
            Enumeration<URL> roots = ClassLoader.getSystemClassLoader().getResources("");
            while (roots.hasMoreElements()) {
                Optional<Path> b = buildOf(roots.nextElement());
                if (b.isPresent()) return b;
            }
            for (String entry : (System.getProperty("java.class.path", "") + java.io.File.pathSeparator
                    + System.getProperty("jdk.module.path", "")).split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) continue;
                Path p = Path.of(entry).toAbsolutePath();
                if (Files.isDirectory(p)) {
                    Optional<Path> b = buildOf(p);
                    if (b.isPresent()) return b;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // no class path to read: as installed
        }
        return Optional.empty();
    }

    private static Optional<Path> buildOf(URL url) {
        if (!"file".equals(url.getProtocol())) return Optional.empty();
        try {
            return buildOf(Path.of(url.toURI()));
        } catch (URISyntaxException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** {@code .../target/classes} is in {@code .../target}; a folder in no build folder is in none. */
    static Optional<Path> buildOf(Path folder) {
        for (Path p = folder.toAbsolutePath().normalize(); p != null && p.getFileName() != null; p = p.getParent()) {
            if (BUILD_FOLDERS.contains(p.getFileName().toString()) && p.getParent() != null
                    && (Files.exists(p.getParent().resolve("pom.xml")) || Files.exists(p.getParent().resolve("build.gradle"))
                    || Files.exists(p.getParent().resolve("build.gradle.kts")) || p.getFileName().toString().equals("out"))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
