package io.joshuasalcedo.webview4j.devtools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Runs a project's JUnit tests with its own build ({@code gradlew test}, {@code mvnw test}, or {@code mvn test})
 * and reads the results as the build writes them (its JUnit XML reports), so they show while the tests run.
 * <pre>{@code
 * TestRunner run = TestRunner.start(Path.of("."), () -> System.out.println(run.progress().summary()));
 * }</pre>
 */
public final class TestRunner {

    public enum Status { PASSED, FAILED, SKIPPED }

    /** One test: its class, its name, how it went, how long it took, and why it failed. */
    public record TestResult(String className, String name, Status status, double seconds, String message) {}

    /**
     * Where the run is: whether it still runs, the build's exit code once it ended, its last lines of output, and
     * the tests finished so far.
     */
    public record Progress(boolean running, Integer exitCode, String command, List<String> output, List<TestResult> results) {

        public long count(Status status) {
            return results.stream().filter(r -> r.status() == status).count();
        }

        public String summary() {
            return count(Status.PASSED) + " passed, " + count(Status.FAILED) + " failed, " + count(Status.SKIPPED) + " skipped"
                    + (running ? " (running)" : exitCode == null ? "" : exitCode == 0 ? "" : " (the build ended with " + exitCode + ")");
        }
    }

    private static final int OUTPUT_LINES = 400;

    private final Path project;
    private final Runnable onChange;
    private final List<String> command;
    private final long started = System.currentTimeMillis();
    private final Deque<String> output = new ArrayDeque<>();
    private final Map<Path, Long> read = new LinkedHashMap<>();
    private final Map<String, TestResult> results = new LinkedHashMap<>();
    private volatile Process process;
    private volatile Integer exitCode;

    private TestRunner(Path project, Runnable onChange) {
        this.project = project.toAbsolutePath().normalize();
        this.onChange = onChange == null ? () -> {} : onChange;
        this.command = command(this.project);
    }

    /**
     * Starts the tests of the project in {@code project} (a folder with a Gradle wrapper, a Maven wrapper, or a
     * pom.xml). {@code onChange} runs, on another thread, each time there is more to show.
     *
     * @throws IllegalArgumentException if the folder has no Gradle or Maven build
     */
    public static TestRunner start(Path project, Runnable onChange) {
        TestRunner runner = new TestRunner(Objects.requireNonNull(project, "project"), onChange);
        runner.run();
        return runner;
    }

    /** The project's build tool and its test command; Gradle reruns the tests even when nothing changed. */
    static List<String> command(Path project) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (Files.exists(project.resolve("gradlew")) || Files.exists(project.resolve("gradlew.bat"))) {
            String gradlew = project.resolve(windows ? "gradlew.bat" : "gradlew").toString();
            return List.of(gradlew, "test", "--rerun", "--continue", "--console=plain");
        }
        if (Files.exists(project.resolve("pom.xml"))) {
            Path mvnw = project.resolve(windows ? "mvnw.cmd" : "mvnw");
            return List.of(Files.exists(mvnw) ? mvnw.toString() : windows ? "mvn.cmd" : "mvn", "test", "-Dsurefire.failIfNoSpecifiedTests=false");
        }
        throw new IllegalArgumentException("no Gradle wrapper or pom.xml in " + project);
    }

    private void run() {
        try {
            process = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new UncheckedIOException("could not run " + String.join(" ", command), e);
        }
        Thread.ofVirtual().name("webview4j-tests-output").start(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = lines.readLine()) != null; ) {
                    synchronized (this) {
                        output.addLast(line);
                        while (output.size() > OUTPUT_LINES) output.removeFirst();
                    }
                    onChange.run();
                }
            } catch (IOException ignored) {
                // the process ended
            }
        });
        Thread.ofVirtual().name("webview4j-tests-reports").start(() -> {
            try {
                while (process.isAlive()) {
                    if (readReports()) onChange.run();
                    Thread.sleep(700);
                }
                exitCode = process.waitFor();
                readReports();
                onChange.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** Stops the build. */
    public void stop() {
        Process p = process;
        if (p != null) p.descendants().forEach(ProcessHandle::destroy);
        if (p != null) p.destroy();
    }

    public synchronized Progress progress() {
        return new Progress(process != null && process.isAlive(), exitCode, String.join(" ", command),
                List.copyOf(output), List.copyOf(results.values()));
    }

    /** Reads the reports written (or rewritten) since the run started; true if there were new ones. */
    private boolean readReports() {
        List<Path> reports;
        try (Stream<Path> files = Files.walk(project, 8)) {
            reports = files.filter(p -> {
                        String name = p.getFileName().toString();
                        String where = p.toString().replace('\\', '/');
                        return name.startsWith("TEST-") && name.endsWith(".xml")
                                && (where.contains("/build/test-results/") || where.contains("/target/surefire-reports/"))
                                && !where.contains("/node_modules/");
                    })
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            return false;
        }
        boolean changed = false;
        for (Path report : reports) {
            long modified;
            try {
                modified = Files.getLastModifiedTime(report).toMillis();
            } catch (IOException e) {
                continue;
            }
            if (modified < started || Objects.equals(read.get(report), modified)) continue;
            List<TestResult> found = parse(report);
            if (found == null) continue;   // being written: next time
            synchronized (this) {
                for (TestResult r : found) results.put(r.className() + "#" + r.name(), r);
            }
            read.put(report, modified);
            changed = true;
        }
        return changed;
    }

    /** The tests in one JUnit XML report; null if it cannot be read (yet). */
    static List<TestResult> parse(Path report) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(report.toFile());
            NodeList cases = document.getElementsByTagName("testcase");
            List<TestResult> list = new ArrayList<>();
            for (int i = 0; i < cases.getLength(); i++) {
                Element test = (Element) cases.item(i);
                Status status = Status.PASSED;
                String message = null;
                for (String failure : List.of("failure", "error")) {
                    NodeList found = test.getElementsByTagName(failure);
                    if (found.getLength() > 0) {
                        status = Status.FAILED;
                        Element e = (Element) found.item(0);
                        message = e.getAttribute("message").isBlank() ? e.getTextContent().strip() : e.getAttribute("message");
                        break;
                    }
                }
                if (status == Status.PASSED && test.getElementsByTagName("skipped").getLength() > 0) status = Status.SKIPPED;
                double seconds = test.getAttribute("time").isBlank() ? 0 : Double.parseDouble(test.getAttribute("time"));
                list.add(new TestResult(test.getAttribute("classname"), test.getAttribute("name"), status, seconds, message));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }
}
