package io.joshuasalcedo.webview4j.plugin.devtools;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * The dev server's live reload: it watches the app's source code ({@code src/main/java}, {@code src/main/resources}),
 * compiles it with the JDK's compiler when it changes, and hands a new class loader with the fresh classes to
 * {@code onCompiled}: a {@link io.joshuasalcedo.webview4j.WebviewApplication} starts again from it, in the same JVM and
 * the same native event loop. A compile that fails is logged, and the app keeps running as it was.
 * <p>
 * Only while developing ({@code -Dwebview4j.dev=true}); {@code -Dwebview4j.dev.reload=false} turns it off.
 * {@code -Dwebview4j.dev.sources} and {@code -Dwebview4j.dev.resources} (paths, separated as the class path is) say
 * where the sources are when they are not in the working directory's {@code src/main/java} and {@code src/main/resources}.
 * Needs a JDK (its compiler), as development does.
 */
public final class LiveReload implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(LiveReload.class.getName());
    /** How often the sources are looked at. */
    static final long POLL_MILLIS = 300;
    /** A change is compiled once the files have been quiet this long (an editor saving several files). */
    static final long QUIET_MILLIS = 250;

    private final List<Path> sources;
    private final List<Path> resources;
    private final Path out;
    private final ClassLoader parent;
    private final Consumer<ClassLoader> onCompiled;
    private volatile Map<Path, Long> seen = Map.of();
    private volatile boolean closed;
    private Thread watcher;
    private int generation;
    /** The one watching now: the dev window's Reload app button reaches it. */
    private static volatile LiveReload running;

    LiveReload(List<Path> sources, List<Path> resources, Path out, ClassLoader parent, Consumer<ClassLoader> onCompiled) {
        this.sources = List.copyOf(sources);
        this.resources = List.copyOf(resources);
        this.out = Objects.requireNonNull(out, "out");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.onCompiled = Objects.requireNonNull(onCompiled, "onCompiled");
    }

    /** Whether live reload is on: while developing, unless {@code -Dwebview4j.dev.reload=false}. */
    public static boolean wanted() {
        return DevWindow.wanted(System.getProperty(DevWindow.PROPERTY), System.getenv("WEBVIEW4J_DEV"))
                && !"false".equalsIgnoreCase(System.getProperty("webview4j.dev.reload"));
    }

    /**
     * Compiles the app's sources now and, if they compile, gives {@code onCompiled} their class loader; then watches
     * them, and does so again after each change. Empty when there are no sources to watch, or no compiler.
     */
    public static java.util.Optional<LiveReload> start(ClassLoader parent, Consumer<ClassLoader> onCompiled) {
        Path here = Path.of("").toAbsolutePath();
        List<Path> sources = paths("webview4j.dev.sources", here.resolve("src/main/java"));
        List<Path> resources = paths("webview4j.dev.resources", here.resolve("src/main/resources"));
        if (sources.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "live reload: no sources in " + here.resolve("src/main/java") + " (set -Dwebview4j.dev.sources)");
            return java.util.Optional.empty();
        }
        if (ToolProvider.getSystemJavaCompiler() == null) {
            LOG.log(System.Logger.Level.WARNING, "live reload: this Java has no compiler (run on a JDK)");
            return java.util.Optional.empty();
        }
        Path out;
        try {
            out = Files.createTempDirectory("webview4j-reload");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LiveReload reload = new LiveReload(sources, resources, out, parent, onCompiled);
        running = reload;
        reload.watch();
        return java.util.Optional.of(reload);
    }

    private static List<Path> paths(String property, Path fallback) {
        String given = System.getProperty(property);
        List<Path> paths = new ArrayList<>();
        if (given != null && !given.isBlank()) {
            for (String p : given.split(java.io.File.pathSeparator)) if (!p.isBlank()) paths.add(Path.of(p.strip()).toAbsolutePath());
        } else {
            paths.add(fallback);
        }
        paths.removeIf(p -> !Files.isDirectory(p));
        return paths;
    }

    /** The first compile now, then the watching thread. */
    void watch() {
        seen = snapshot();
        reload();
        watcher = Thread.ofPlatform().daemon().name("webview4j-live-reload").start(() -> {
            while (!closed) {
                try {
                    Thread.sleep(POLL_MILLIS);
                    if (!changed()) continue;
                    // until the files are quiet
                    Map<Path, Long> last;
                    do {
                        last = snapshot();
                        Thread.sleep(QUIET_MILLIS);
                    } while (!last.equals(snapshot()) && !closed);
                    seen = last;
                    if (!closed) reload();
                } catch (InterruptedException e) {
                    return;
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.ERROR, "live reload failed", e);
                }
            }
        });
    }

    /** The live reload watching now, if any. */
    public static java.util.Optional<LiveReload> running() {
        return java.util.Optional.ofNullable(running);
    }

    /** Compiles and starts the app again now, changed or not (the dev window's Reload app); on its own thread. */
    public void reloadNow() {
        Thread.ofVirtual().name("webview4j-reload-now").start(() -> {
            seen = snapshot();
            reload();
        });
    }

    /** Whether a source or resource file was added, changed or removed since the last compile. */
    boolean changed() {
        return !snapshot().equals(seen);
    }

    /** Compiles into a new folder, and hands over its class loader; false when the sources do not compile. */
    synchronized boolean reload() {
        long started = System.nanoTime();
        Path classes = out.resolve("g" + (++generation));
        String errors = compile(classes);
        if (errors != null) {
            LOG.log(System.Logger.Level.WARNING, "live reload: the sources do not compile, the app stays as it was\n" + errors);
            return false;
        }
        copyResources(classes);
        ClassLoader loader = loader(classes, parent);
        LOG.log(System.Logger.Level.INFO, "live reload: compiled in " + (System.nanoTime() - started) / 1_000_000 + " ms, starting again");
        onCompiled.accept(loader);
        return true;
    }

    /** javac over every source file, into {@code classes}; its messages when it fails, else null. */
    String compile(Path classes) {
        List<String> files = new ArrayList<>();
        for (Path root : sources) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().equals("module-info.java"))
                        .forEach(p -> files.add(p.toString()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (files.isEmpty()) return "no .java files in " + sources;
        try {
            Files.createDirectories(classes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        String classPath = System.getProperty("java.class.path");
        List<String> args = new ArrayList<>(List.of("-d", classes.toString(), "-classpath", classPath,
                // @WebViewController's route tables come from core's processor, as in the real build
                "-processorpath", classPath, "-proc:full", "-parameters", "-nowarn", "-encoding", "UTF-8",
                "--release", Integer.toString(Runtime.version().feature())));
        args.addAll(files);
        StringWriter messages = new StringWriter();
        int code = javac.run(null, new java.io.PrintStream(java.io.OutputStream.nullOutputStream()),
                new java.io.PrintStream(new java.io.OutputStream() {
                    @Override public void write(int b) { messages.write(b); }
                }, true), args.toArray(String[]::new));
        return code == 0 ? null : messages.toString();
    }

    private void copyResources(Path classes) {
        for (Path root : resources) {
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path target = classes.resolve(root.relativize(file).toString());
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Every watched file and when it changed. */
    private Map<Path, Long> snapshot() {
        Map<Path, Long> files = new HashMap<>();
        for (Path root : concat(sources, resources)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    try {
                        files.put(p, Files.getLastModifiedTime(p).toMillis() ^ Files.size(p) << 1);
                    } catch (IOException e) {
                        // removed while walking: the next look sees it gone
                    }
                });
            } catch (IOException | UncheckedIOException e) {
                // a folder removed while walking: the same
            }
        }
        return files;
    }

    private static List<Path> concat(List<Path> a, List<Path> b) {
        List<Path> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    /**
     * A class loader that loads the classes compiled into {@code classes} itself (though the app's own build has
     * them too, further up): child first for those, the parent for the rest (webview-4j, the libraries).
     */
    static ClassLoader loader(Path classes, ClassLoader parent) {
        URL url;
        try {
            url = classes.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return new URLClassLoader("webview4j-reload", new URL[] {url}, parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null && findResource(name.replace('.', '/') + ".class") != null) c = findClass(name);
                    if (c == null) return super.loadClass(name, resolve);
                    if (resolve) resolveClass(c);
                    return c;
                }
            }

            @Override
            public URL getResource(String name) {
                URL own = findResource(name);
                return own != null ? own : super.getResource(name);
            }
        };
    }

    @Override
    public void close() {
        closed = true;
        if (running == this) running = null;
        if (watcher != null) watcher.interrupt();
        try (Stream<Path> walk = Files.walk(out)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // a temporary folder: the system clears it
        }
    }
}
