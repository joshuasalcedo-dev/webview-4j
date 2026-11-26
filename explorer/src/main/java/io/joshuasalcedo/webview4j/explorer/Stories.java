package io.joshuasalcedo.webview4j.explorer;

import io.joshuasalcedo.webview4j.explorer.ApiIndex.Type;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The {@link Story}s in {@code io.joshuasalcedo.webview4j.explorer.stories}. While the explorer runs from its project,
 * they are compiled from their source folder, again each time a file there changes; otherwise they are the classes on
 * the class path. Also each story's code (the body of its {@code showcase}), and a new story's first source.
 */
final class Stories {

    static final String PACKAGE = "io.joshuasalcedo.webview4j.explorer.stories";
    private static final long RUN_SECONDS = 5;

    /** The stories found, and what failed to compile (null when all did). */
    record Loaded(List<Story> stories, String errors) {}

    /** What a showcase gave: a component, or what went wrong. */
    record Outcome(Object value, String error) {
        boolean ok() {
            return error == null;
        }
    }

    private final ClassLoader parent;
    private final Path sourceDir;
    private final Compiler compiler;

    Stories(ClassLoader parent, Path sourceDir) {
        this.parent = parent;
        this.sourceDir = sourceDir != null && Files.isDirectory(sourceDir) ? sourceDir : null;
        this.compiler = new Compiler(parent, PACKAGE);
    }

    /** The stories' source folder: {@code src/main/java/.../stories} of the explorer's project, if it is here. */
    static Path findSourceDir() {
        String relative = "src/main/java/" + PACKAGE.replace('.', '/');
        for (Path base : List.of(Path.of(""), Path.of("explorer"))) {
            Path dir = base.resolve(relative).toAbsolutePath();
            if (Files.isDirectory(dir)) return dir;
        }
        return null;
    }

    /** Whether the stories are compiled from their sources (and so follow their changes). */
    boolean live() {
        return sourceDir != null && compiler.available();
    }

    /** Something that changes when a story's source does: polled to compile them again. */
    long stamp() {
        if (sourceDir == null) return 0;
        try (Stream<Path> files = Files.list(sourceDir)) {
            return files.filter(f -> f.toString().endsWith(".java"))
                    .mapToLong(f -> f.toFile().lastModified() * 31 + f.getFileName().hashCode()).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Every story, sorted for its page: by {@link Story#order}, then title. */
    Loaded load() {
        if (live()) {
            Map<String, String> sources = sources();
            if (sources.isEmpty()) return new Loaded(List.of(), null);
            Compiler.Result result = compiler.compile(sources);
            if (!result.ok()) return new Loaded(fromClassPath(), result.errors());
            return new Loaded(instances(result.classNames(), result.loader()), null);
        }
        return new Loaded(fromClassPath(), null);
    }

    /** {@code story} with its showcase's body replaced by {@code body}: an edit in the explorer's Code tab. */
    Optional<Story> withBody(Story story, String body, StringBuilder errors) {
        String file = topLevel(story.getClass()) + ".java";
        Optional<String> source = source(story.getClass());
        if (source.isEmpty()) {
            errors.append("The source of ").append(file).append(" is not here to edit.");
            return Optional.empty();
        }
        Optional<int[]> at = showcaseBody(source.get(), story.getClass().getSimpleName());
        if (at.isEmpty()) {
            errors.append("No showcase(Args) method found in ").append(file).append('.');
            return Optional.empty();
        }
        String text = source.get();
        String edited = text.substring(0, at.get()[0]) + "\n" + body + "\n" + text.substring(at.get()[1]);
        Compiler.Result result = compiler.compile(Map.of(file, edited));
        if (!result.ok()) {
            errors.append(result.errors());
            return Optional.empty();
        }
        return instances(List.of(story.getClass().getName()), result.loader()).stream().findFirst();
    }

    /** The body of {@code story}'s showcase as written: what the Code tab shows. */
    Optional<String> code(Story story) {
        return source(story.getClass()).flatMap(text ->
                showcaseBody(text, story.getClass().getSimpleName()).map(at -> dedent(text.substring(at[0], at[1]))));
    }

    /** Runs {@code story}'s showcase with {@code args}, giving up after a few seconds. */
    static Outcome run(Story story, Args args) {
        FutureTask<Object> task = new FutureTask<>(() -> story.showcase(args));
        Thread.ofVirtual().name("story-" + story.getClass().getSimpleName()).start(task);
        try {
            return new Outcome(task.get(RUN_SECONDS, TimeUnit.SECONDS), null);
        } catch (TimeoutException e) {
            task.cancel(true);
            return new Outcome(null, "Still running after " + RUN_SECONDS + " s (an endless loop?).");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            StringBuilder out = new StringBuilder(cause.toString());
            for (StackTraceElement frame : cause.getStackTrace()) {
                if (frame.getClassName().startsWith("java.util.concurrent")) break;
                out.append("\n    at ").append(frame);
            }
            return new Outcome(null, out.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(null, "Interrupted.");
        }
    }

    /** Writes a first story for {@code type} ({@code <Name>Default.java}); its path, or empty without a source folder. */
    Optional<Path> create(Type type) {
        if (sourceDir == null) return Optional.empty();
        String name = type.name().replace(".", "") + "Default";
        Path file = sourceDir.resolve(name + ".java");
        if (Files.exists(file)) return Optional.of(file);
        String source = """
                package %s;

                import static io.joshuasalcedo.webview4j.ui.Ui.*;

                import %s;
                import io.joshuasalcedo.webview4j.explorer.Args;
                import io.joshuasalcedo.webview4j.explorer.Story;
                import io.joshuasalcedo.webview4j.ui.Component;

                public final class %s implements Story {

                    @Override
                    public Class<?> component() {
                        return %s.class;
                    }

                    @Override
                    public String title() {
                        return "Default";
                    }

                    @Override
                    public Component<?> showcase(Args args) {
                        return text("Make a %s here.");
                    }
                }
                """.formatted(PACKAGE, type.packageName() + "." + type.name().split("\\.")[0], name, type.name(), type.name());
        try {
            Files.writeString(file, source, StandardCharsets.UTF_8);
            return Optional.of(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- finding them ----

    private Map<String, String> sources() {
        Map<String, String> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(sourceDir)) {
            for (Path f : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                out.put(f.getFileName().toString(), Files.readString(f, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private List<Story> fromClassPath() {
        List<String> names = new ArrayList<>();
        String path = PACKAGE.replace('.', '/');
        try {
            Enumeration<URL> roots = parent.getResources(path);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if (root.getProtocol().equals("file")) {
                    try (Stream<Path> files = Files.list(Path.of(root.toURI()))) {
                        files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".class"))
                                .forEach(n -> names.add(PACKAGE + "." + n.substring(0, n.length() - 6).replace('/', '.')));
                    }
                } else if (root.openConnection() instanceof JarURLConnection jar) {
                    try (JarFile file = jar.getJarFile()) {
                        file.stream().map(e -> e.getName())
                                .filter(n -> n.startsWith(path + "/") && n.endsWith(".class") && n.indexOf('/', path.length() + 1) < 0)
                                .forEach(n -> names.add(n.substring(0, n.length() - 6).replace('/', '.')));
                    }
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return instances(names, parent);
    }

    private static List<Story> instances(List<String> classNames, ClassLoader loader) {
        List<Story> out = new ArrayList<>();
        for (String name : classNames) {
            try {
                Class<?> type = Class.forName(name, true, loader);
                if (!Story.class.isAssignableFrom(type) || type.isInterface() || Modifier.isAbstract(type.getModifiers())) continue;
                var constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                out.add((Story) constructor.newInstance());
            } catch (ReflectiveOperationException | LinkageError skipped) {
                // not a story the explorer can make (no constructor without parameters)
            }
        }
        out.sort(Comparator.comparingInt(Story::order).thenComparing(Story::title));
        return out;
    }

    // ---- its code ----

    private Optional<String> source(Class<?> story) {
        if (sourceDir == null) return Optional.empty();
        Path file = sourceDir.resolve(topLevel(story) + ".java");
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String topLevel(Class<?> type) {
        String name = type.getName().substring(type.getPackageName().length() + 1);
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static final Pattern SHOWCASE = Pattern.compile("\\bshowcase\\s*\\(\\s*(?:final\\s+)?Args\\s+\\w+\\s*\\)\\s*\\{");

    /** Where the body of the showcase of class {@code simpleName} is in {@code source}: from after its { to its }. */
    static Optional<int[]> showcaseBody(String source, String simpleName) {
        Matcher declared = Pattern.compile("\\b(class|record|enum)\\s+" + Pattern.quote(simpleName) + "\\b").matcher(source);
        int from = declared.find() ? declared.end() : 0;
        Matcher m = SHOWCASE.matcher(source);
        if (!m.find(from)) return Optional.empty();
        int open = m.end() - 1;
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return Optional.of(new int[] {open + 1, i});
        }
        return Optional.empty();
    }

    private static String dedent(String code) {
        String[] lines = code.replace("\r\n", "\n").split("\n", -1);
        int indent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) continue;
            int i = 0;
            while (i < line.length() && line.charAt(i) == ' ') i++;
            indent = Math.min(indent, i);
        }
        StringBuilder out = new StringBuilder();
        for (String line : lines) out.append(line.length() >= indent ? line.substring(indent) : line.strip()).append('\n');
        return out.toString().strip();
    }
}
