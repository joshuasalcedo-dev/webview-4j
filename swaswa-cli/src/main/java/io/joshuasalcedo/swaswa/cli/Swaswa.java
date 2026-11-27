package io.joshuasalcedo.swaswa.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

/**
 * The {@code swaswa} command.
 * <pre>
 * swaswa new &lt;app&gt; [--package com.example.app] [--group com.example]   a new app, in folder &lt;app&gt;
 * swaswa run [--watch]                                                 runs it, in development (again on each change)
 * swaswa build [--type app-image|exe|msi|dmg|pkg|deb|rpm]              its jar, its libraries and its installer
 * swaswa version
 * </pre>
 * {@code run} and {@code build} work in an app's folder (where its pom.xml is), with Maven ({@code mvn}) on the PATH.
 */
public final class Swaswa {

    private final PrintStream out;
    private final Path here;

    Swaswa(PrintStream out, Path here) {
        this.out = out;
        this.here = here;
    }

    public static void main(String[] args) {
        int code;
        try {
            code = new Swaswa(System.out, Path.of("").toAbsolutePath()).run(List.of(args));
        } catch (Failure f) {
            System.err.println("swaswa: " + f.getMessage());
            code = 1;
        }
        System.exit(code);
    }

    /** What went wrong, said to the person (no stack trace). */
    static final class Failure extends RuntimeException {
        Failure(String message) {
            super(message);
        }
    }

    int run(List<String> args) {
        if (args.isEmpty()) {
            help();
            return 0;
        }
        List<String> rest = args.subList(1, args.size());
        return switch (args.getFirst()) {
            case "new" -> create(rest);
            case "run" -> runApp(rest);
            case "build" -> build(rest);
            case "version", "--version", "-v" -> {
                out.println("swaswa " + version());
                yield 0;
            }
            case "help", "--help", "-h" -> {
                help();
                yield 0;
            }
            default -> throw new Failure("no command " + args.getFirst() + " (swaswa help)");
        };
    }

    private void help() {
        out.println("""
                swaswa %s: new, run and build swaswa apps

                  swaswa new <app> [--package com.example.app] [--group com.example]
                                   a new app in folder <app>: its pom.xml and its class
                  swaswa run [--watch]
                                   runs the app here, in development (its data stays in target/);
                                   --watch: runs it again when its sources change
                  swaswa build [--type <type>]
                                   its jar (target/<app>.jar, libraries in target/lib), then with jpackage
                                   its installer in target/installer: app-image (a folder, the default),
                                   exe or msi (Windows, needs WiX), dmg or pkg (macOS), deb or rpm (Linux)
                  swaswa version""".formatted(version()));
    }

    static String version() {
        try (InputStream in = Swaswa.class.getResourceAsStream("version.properties")) {
            Properties p = new Properties();
            if (in != null) p.load(in);
            return p.getProperty("version", "dev");
        } catch (IOException e) {
            return "dev";
        }
    }

    // ---- new ----

    int create(List<String> args) {
        Options o = Options.of(args, "--package", "--group");
        if (o.positional.size() != 1) throw new Failure("swaswa new <app>: one name, as its folder (my-notes)");
        String app = o.positional.getFirst();
        if (!app.matches("[A-Za-z][A-Za-z0-9._-]*")) throw new Failure(app + ": a name of letters, digits, '.', '_' or '-', from a letter");
        Path dir = here.resolve(app);
        if (Files.exists(dir)) throw new Failure(dir + " is there already");

        String className = className(app);
        String group = o.get("--group", "com.example");
        String pkg = o.get("--package", group + "." + app.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
        if (!pkg.matches("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*")) throw new Failure(pkg + " is not a package name");
        Map<String, String> values = Map.of(
                "name", title(app),
                "artifactId", app.toLowerCase(Locale.ROOT),
                "groupId", group,
                "package", pkg,
                "className", className,
                "mainClass", pkg + "." + className,
                "swaswaVersion", version());

        write(dir.resolve("pom.xml"), template("pom.xml", values));
        write(dir.resolve("src/main/java/" + pkg.replace('.', '/') + "/" + className + ".java"), template("App.java", values));
        write(dir.resolve(".gitignore"), template("gitignore", values));
        out.println("made " + dir);
        out.println();
        out.println("  cd " + app);
        out.println("  swaswa run");
        return 0;
    }

    /** my-notes → MyNotes. */
    static String className(String app) {
        StringBuilder b = new StringBuilder();
        for (String part : app.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String name = b.toString();
        return name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0)) ? "App" + name : name;
    }

    /** my-notes → My Notes. */
    static String title(String app) {
        StringBuilder b = new StringBuilder();
        for (String part : app.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    static String template(String name, Map<String, String> values) {
        String text;
        try (InputStream in = Swaswa.class.getResourceAsStream("template/" + name)) {
            if (in == null) throw new IllegalStateException("no template " + name);
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Matcher m = Pattern.compile("\\{\\{(\\w+)}}").matcher(text);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null) throw new IllegalStateException("template " + name + ": no value for " + m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(v));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- run ----

    int runApp(List<String> args) {
        Options o = Options.of(args);
        Project project = Project.at(here);
        if (!o.has("--watch")) return maven(project.dir, "-q", "compile", "exec:exec@run");

        // --watch: compiled and run again when a source changes
        try (WatchService watch = here.getFileSystem().newWatchService()) {
            watchTree(project.dir.resolve("src"), watch);
            project.dir.register(watch, StandardWatchEventKinds.ENTRY_MODIFY);   // the pom too: its folder
            while (true) {
                if (maven(project.dir, "-q", "compile") != 0) {
                    out.println("swaswa: it does not compile; waiting for a change");
                    waitForChange(watch);
                    continue;
                }
                Process app = start(project.dir, "-q", "exec:exec@run");
                boolean changed = false;
                while (app.isAlive() && !changed) changed = changedWithin(watch, 500);
                if (!changed) return app.exitValue();   // the person closed it: done
                out.println("swaswa: changed, running again");
                stop(app);
                watchTree(project.dir.resolve("src"), watch);   // new folders too
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    private static void watchTree(Path root, WatchService watch) throws IOException {
        if (!Files.isDirectory(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watch, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void waitForChange(WatchService watch) throws InterruptedException {
        while (!changedWithin(watch, 1000)) {
            // waiting
        }
    }

    /** A source changed within {@code millis} (the events of one save taken together). */
    private static boolean changedWithin(WatchService watch, long millis) throws InterruptedException {
        WatchKey key = watch.poll(millis, TimeUnit.MILLISECONDS);
        if (key == null) return false;
        boolean relevant = false;
        do {
            for (var event : key.pollEvents()) {
                Object context = event.context();
                String name = context == null ? "" : context.toString();
                if (!name.endsWith("~") && !name.startsWith(".")) relevant = true;
            }
            key.reset();
            key = watch.poll(300, TimeUnit.MILLISECONDS);   // the rest of the same save
        } while (key != null);
        return relevant;
    }

    /** Stops the app this command started (its whole process tree: Maven, then the app's JVM). */
    private static void stop(Process app) throws InterruptedException {
        app.descendants().forEach(ProcessHandle::destroy);
        app.destroy();
        if (!app.waitFor(10, TimeUnit.SECONDS)) {
            app.descendants().forEach(ProcessHandle::destroyForcibly);
            app.destroyForcibly();
        }
    }

    // ---- build ----

    int build(List<String> args) {
        Options o = Options.of(args, "--type");
        Project project = Project.at(here);
        int built = maven(project.dir, "-q", "package");
        if (built != 0) return built;

        Path target = project.dir.resolve("target");
        Path jar = target.resolve(project.artifactId + ".jar");
        if (!Files.isRegularFile(jar)) throw new Failure("no " + jar + " after the build (is <finalName> changed?)");
        out.println("jar: " + jar + " (its libraries in " + target.resolve("lib") + ")");

        // jpackage's input: the jar and its libraries, alone
        Path input = target.resolve("jpackage-input");
        Path installers = target.resolve("installer");
        deleteTree(input);
        deleteTree(installers.resolve(project.name));
        copyTree(target.resolve("lib"), input.resolve("lib"));
        copy(jar, input.resolve(jar.getFileName()));

        String type = o.get("--type", "app-image");
        List<String> jp = new ArrayList<>(List.of(
                "--type", type,
                "--name", project.name,
                "--app-version", appVersion(project.version),
                "--input", input.toString(),
                "--main-jar", jar.getFileName().toString(),
                "--main-class", project.mainClass,
                "--dest", installers.toString(),
                "--java-options", "--enable-native-access=ALL-UNNAMED"));
        if (isWindows() && !type.equals("app-image")) jp.addAll(List.of("--win-menu", "--win-shortcut", "--win-dir-chooser"));
        ToolProvider jpackage = ToolProvider.findFirst("jpackage")
                .orElseThrow(() -> new Failure("no jpackage in this Java (a JDK has it, a JRE does not)"));
        out.println("jpackage --type " + type + " ...");
        int code = jpackage.run(out, System.err, jp.toArray(String[]::new));
        if (code != 0) {
            if (isWindows() && (type.equals("exe") || type.equals("msi")))
                out.println("swaswa: exe and msi need the WiX Toolset on the PATH (https://wixtoolset.org)");
            return code;
        }
        out.println("installer: " + installers);
        return 0;
    }

    /** jpackage's version: numbers only (1.0.0-SNAPSHOT → 1.0.0). */
    static String appVersion(String version) {
        Matcher m = Pattern.compile("^(\\d+(?:\\.\\d+){0,2})").matcher(version);
        return m.find() ? m.group(1) : "1.0.0";
    }

    // ---- the app's project ----

    /** What {@code run} and {@code build} need of the app: from its pom.xml. */
    record Project(Path dir, String artifactId, String version, String name, String mainClass) {
        static Project at(Path from) {
            Path dir = from;
            while (dir != null && !Files.isRegularFile(dir.resolve("pom.xml"))) dir = dir.getParent();
            if (dir == null) throw new Failure("no pom.xml here or above: run it in an app's folder (swaswa new <app>)");
            String pom;
            try {
                pom = Files.readString(dir.resolve("pom.xml"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            String own = pom.replaceAll("(?s)<parent>.*?</parent>", "")
                    .replaceAll("(?s)<dependencies>.*?</dependencies>", "")
                    .replaceAll("(?s)<build>.*?</build>", "")
                    .replaceAll("(?s)<!--.*?-->", "");
            String artifactId = tag(own, "artifactId");
            String mainClass = tag(own, "swaswa.mainClass");
            if (artifactId == null) throw new Failure(dir.resolve("pom.xml") + " has no artifactId");
            if (mainClass == null) throw new Failure(dir.resolve("pom.xml") + " has no <swaswa.mainClass> property: the app's class");
            String version = tag(own, "version");
            String name = tag(own, "name");
            return new Project(dir, artifactId, version == null ? "1.0.0" : version,
                    name == null ? title(artifactId) : name.replaceAll("[^A-Za-z0-9 ._-]", ""), mainClass);
        }

        private static String tag(String xml, String name) {
            Matcher m = Pattern.compile("<" + Pattern.quote(name) + ">\\s*([^<]+?)\\s*</" + Pattern.quote(name) + ">").matcher(xml);
            return m.find() ? m.group(1) : null;
        }
    }

    // ---- Maven ----

    private int maven(Path dir, String... args) {
        try {
            return start(dir, args).waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    private Process start(Path dir, String... args) {
        List<String> command = new ArrayList<>();
        command.add(mavenCommand(dir));
        command.add("-B");
        command.addAll(List.of(args));
        try {
            return new ProcessBuilder(command).directory(dir.toFile()).inheritIO().start();
        } catch (IOException e) {
            throw new Failure("Maven did not start (" + e.getMessage() + "): is mvn on the PATH?");
        }
    }

    /** The app's own Maven wrapper if it has one, else mvn. */
    private static String mavenCommand(Path dir) {
        Path wrapper = dir.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
        if (Files.isRegularFile(wrapper)) return wrapper.toString();
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    // ---- files ----

    private static void copy(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyTree(Path from, Path to) {
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> files = Files.walk(from)) {
            files.filter(Files::isRegularFile).forEach(f -> copy(f, to.resolve(from.relativize(f).toString())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) Files.delete(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- arguments ----

    /** A command's arguments: its names, its flags, its options' values. */
    record Options(List<String> positional, Map<String, String> values) {
        static Options of(List<String> args, String... withValue) {
            List<String> positional = new ArrayList<>();
            Map<String, String> values = new java.util.LinkedHashMap<>();
            List<String> valued = List.of(withValue);
            for (int i = 0; i < args.size(); i++) {
                String a = args.get(i);
                if (a.startsWith("--") && a.contains("=")) {
                    values.put(a.substring(0, a.indexOf('=')), a.substring(a.indexOf('=') + 1));
                } else if (valued.contains(a)) {
                    if (i + 1 >= args.size()) throw new Failure(a + " needs a value");
                    values.put(a, args.get(++i));
                } else if (a.startsWith("--")) {
                    values.put(a, "true");
                } else {
                    positional.add(a);
                }
            }
            return new Options(positional, values);
        }

        boolean has(String name) {
            return values.containsKey(name);
        }

        String get(String name, String otherwise) {
            return values.getOrDefault(name, otherwise);
        }
    }
}
