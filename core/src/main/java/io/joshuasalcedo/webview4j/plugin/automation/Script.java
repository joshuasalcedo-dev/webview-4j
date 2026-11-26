package io.joshuasalcedo.webview4j.plugin.automation;

import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.Recording;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An automation, saved: its steps, recorded ({@link Automation#record()}) or written by hand, kept as a JSON file
 * people can read and change, and replayed in a window. Texts may hold variables, {@code ${NAME}}, filled at replay
 * from the given values or the environment: a recording writes a password as {@code ${PASSWORD}}, never the password.
 * <pre>{@code
 * Script tour = Script.load(Path.of("tour.automation.json"));
 * Script.Report report = tour.run(Automation.of(view), Map.of("PASSWORD", secret), Path.of("docs"));
 * Files.writeString(Path.of("SignInTest.java"), tour.toJava("SignInTest"));
 * }</pre>
 */
public record Script(String name, List<Step> steps) {

    public Script {
        Objects.requireNonNull(name, "name");
        steps = List.copyOf(steps);
    }

    /** One thing to do. */
    public sealed interface Step {
        record Click(Locator target) implements Step {}
        record DoubleClick(Locator target) implements Step {}
        record RightClick(Locator target) implements Step {}
        record Hover(Locator target) implements Step {}
        record Type(Locator target, String text) implements Step {}
        /** A key on what has the focus. */
        record Press(String key) implements Step {}
        record Select(Locator target, String option) implements Step {}
        record Check(Locator target, boolean checked) implements Step {}
        /** Until the page shows this text. */
        record WaitForText(String text) implements Step {}
        /** Until this is there. */
        record WaitFor(Locator target) implements Step {}
        /** This has that text, or the script fails. */
        record Expect(Locator target, String text) implements Step {}
        record Screenshot(String path) implements Step {}
        /** Starts recording the window into {@code path} (a GIF, MP4 or WebM, by its extension). */
        record Record(String path, int fps, double scale) implements Step {}
        record StopRecording() implements Step {}
        record Pause(long millis) implements Step {}
    }

    /** How a replay went: each step, how long it took, and the first failure (then nothing after it ran). */
    public record Report(String script, List<Done> steps, String failure) {
        public record Done(Step step, long millis, String error) {}

        public boolean ok() {
            return failure == null;
        }
    }

    public static Script of(String name, Step... steps) {
        return new Script(name, List.of(steps));
    }

    public Script with(Step step) {
        List<Step> all = new ArrayList<>(steps);
        all.add(step);
        return new Script(name, all);
    }

    // ---- replaying ----

    /** Replays every step in the window; paths in screenshot and record steps are under {@code base}. */
    public Report run(Automation ui, Map<String, String> variables, Path base) {
        List<Report.Done> done = new ArrayList<>();
        Recording recording = null;
        try {
            for (Step step : steps) {
                long start = System.nanoTime();
                try {
                    recording = runOne(ui, step, variables, base, recording);
                    done.add(new Report.Done(step, (System.nanoTime() - start) / 1_000_000, null));
                } catch (RuntimeException e) {
                    done.add(new Report.Done(step, (System.nanoTime() - start) / 1_000_000, e.getMessage()));
                    return new Report(name, done, "step " + done.size() + " (" + describe(step) + "): " + e.getMessage());
                }
            }
            return new Report(name, done, null);
        } finally {
            if (recording != null) recording.stop().join();
        }
    }

    public Report run(Automation ui) {
        return run(ui, Map.of(), Path.of("."));
    }

    private static Recording runOne(Automation ui, Step step, Map<String, String> vars, Path base, Recording recording) {
        switch (step) {
            case Step.Click s -> ui.find(s.target()).click();
            case Step.DoubleClick s -> ui.find(s.target()).doubleClick();
            case Step.RightClick s -> ui.find(s.target()).rightClick();
            case Step.Hover s -> ui.find(s.target()).hover();
            case Step.Type s -> ui.find(s.target()).type(fill(s.text(), vars));
            case Step.Press s -> ui.press(s.key());
            case Step.Select s -> ui.find(s.target()).select(fill(s.option(), vars));
            case Step.Check s -> {
                if (s.checked()) ui.find(s.target()).check();
                else ui.find(s.target()).uncheck();
            }
            case Step.WaitForText s -> ui.waitForText(fill(s.text(), vars));
            case Step.WaitFor s -> ui.find(s.target()).waitFor();
            case Step.Expect s -> ui.find(s.target()).expectText(fill(s.text(), vars));
            case Step.Screenshot s -> ui.screenshot(base.resolve(s.path()));
            case Step.Record s -> {
                if (recording != null) recording.stop().join();
                String ext = s.path().substring(s.path().lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                Recording.Options o = switch (ext) {
                    case "mp4" -> Recording.Options.mp4();
                    case "webm" -> Recording.Options.webm();
                    default -> Recording.Options.gif();
                };
                return ui.record(base.resolve(s.path()), o.fps(s.fps() > 0 ? s.fps() : o.fps()).scale(s.scale() > 0 ? s.scale() : 1.0));
            }
            case Step.StopRecording s -> {
                if (recording != null) recording.stop().join();
                return null;
            }
            case Step.Pause s -> ui.pause(Duration.ofMillis(s.millis()));
        }
        return recording;
    }

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)}");

    /** {@code ${NAME}} from the given values, else the environment; a missing one fails the step. */
    static String fill(String text, Map<String, String> vars) {
        if (text == null) return null;
        Matcher m = VARIABLE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = vars.getOrDefault(m.group(1), System.getenv(m.group(1)));
            if (v == null) throw new AutomationException("no value for ${" + m.group(1) + "}: give it to run(...), or set it in the environment", List.of());
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ---- saving ----

    public void save(Path file) {
        try {
            if (file.toAbsolutePath().getParent() != null) Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Script load(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The script as JSON: a step a line, to read and change by hand. */
    public String toJson() {
        StringBuilder out = new StringBuilder("{\n  \"name\": ").append(Json.write(name)).append(",\n  \"steps\": [");
        for (int i = 0; i < steps.size(); i++) {
            out.append(i == 0 ? "\n    " : ",\n    ").append(Json.write(write(steps.get(i))));
        }
        return out.append(steps.isEmpty() ? "]\n}\n" : "\n  ]\n}\n").toString();
    }

    public static Script parse(String json) {
        Map<String, Object> m = Json.object(json);
        List<Step> steps = new ArrayList<>();
        if (m.get("steps") instanceof List<?> list) for (Object o : list) if (o instanceof Map<?, ?> s) steps.add(read(s));
        return new Script(String.valueOf(m.getOrDefault("name", "automation")), steps);
    }

    static Map<String, Object> write(Step step) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (step) {
            case Step.Click s -> m.put("click", s.target().write());
            case Step.DoubleClick s -> m.put("doubleClick", s.target().write());
            case Step.RightClick s -> m.put("rightClick", s.target().write());
            case Step.Hover s -> m.put("hover", s.target().write());
            case Step.Type s -> {
                m.put("type", s.target().write());
                m.put("text", s.text());
            }
            case Step.Press s -> m.put("press", s.key());
            case Step.Select s -> {
                m.put("select", s.target().write());
                m.put("option", s.option());
            }
            case Step.Check s -> {
                m.put("check", s.target().write());
                m.put("checked", s.checked());
            }
            case Step.WaitForText s -> m.put("waitFor", Map.of("text", s.text()));
            case Step.WaitFor s -> m.put("waitFor", s.target().write());
            case Step.Expect s -> {
                m.put("expect", s.target().write());
                m.put("text", s.text());
            }
            case Step.Screenshot s -> m.put("screenshot", s.path());
            case Step.Record s -> {
                m.put("record", s.path());
                if (s.fps() > 0) m.put("fps", s.fps());
                if (s.scale() > 0 && s.scale() < 1) m.put("scale", s.scale());
            }
            case Step.StopRecording s -> m.put("stopRecording", true);
            case Step.Pause s -> m.put("pause", s.millis());
        }
        return m;
    }

    static Step read(Map<?, ?> m) {
        if (m.get("click") instanceof Map<?, ?> t) return new Step.Click(Locator.read(t));
        if (m.get("doubleClick") instanceof Map<?, ?> t) return new Step.DoubleClick(Locator.read(t));
        if (m.get("rightClick") instanceof Map<?, ?> t) return new Step.RightClick(Locator.read(t));
        if (m.get("hover") instanceof Map<?, ?> t) return new Step.Hover(Locator.read(t));
        if (m.get("type") instanceof Map<?, ?> t) return new Step.Type(Locator.read(t), String.valueOf(m.get("text")));
        if (m.get("press") != null) return new Step.Press(String.valueOf(m.get("press")));
        if (m.get("select") instanceof Map<?, ?> t) return new Step.Select(Locator.read(t), String.valueOf(m.get("option")));
        if (m.get("check") instanceof Map<?, ?> t) return new Step.Check(Locator.read(t), !Boolean.FALSE.equals(m.get("checked")));
        if (m.get("waitFor") instanceof Map<?, ?> t) {
            return t.size() == 1 && t.get("text") != null && !t.containsKey("role") ? new Step.WaitForText(String.valueOf(t.get("text"))) : new Step.WaitFor(Locator.read(t));
        }
        if (m.get("expect") instanceof Map<?, ?> t) return new Step.Expect(Locator.read(t), String.valueOf(m.get("text")));
        if (m.get("screenshot") != null) return new Step.Screenshot(String.valueOf(m.get("screenshot")));
        if (m.get("record") != null) {
            return new Step.Record(String.valueOf(m.get("record")), m.get("fps") instanceof Number n ? n.intValue() : 0,
                    m.get("scale") instanceof Number s ? s.doubleValue() : 0);
        }
        if (m.containsKey("stopRecording")) return new Step.StopRecording();
        if (m.get("pause") instanceof Number n) return new Step.Pause(n.longValue());
        throw new IllegalArgumentException("not a step: " + m);
    }

    static String describe(Step step) {
        return switch (step) {
            case Step.Click s -> "click " + s.target();
            case Step.DoubleClick s -> "double-click " + s.target();
            case Step.RightClick s -> "right-click " + s.target();
            case Step.Hover s -> "hover " + s.target();
            case Step.Type s -> "type into " + s.target();
            case Step.Press s -> "press " + s.key();
            case Step.Select s -> "select \"" + s.option() + "\" in " + s.target();
            case Step.Check s -> (s.checked() ? "check " : "uncheck ") + s.target();
            case Step.WaitForText s -> "wait for \"" + s.text() + "\"";
            case Step.WaitFor s -> "wait for " + s.target();
            case Step.Expect s -> "expect " + s.target() + " to have \"" + s.text() + "\"";
            case Step.Screenshot s -> "screenshot " + s.path();
            case Step.Record s -> "record " + s.path();
            case Step.StopRecording s -> "stop recording";
            case Step.Pause s -> "pause " + s.millis() + " ms";
        };
    }

    // ---- as Java ----

    /** The same steps as Java, a JUnit test of {@code className} to keep with the app's tests. */
    public String toJava(String className) {
        StringBuilder b = new StringBuilder();
        b.append("import io.joshuasalcedo.webview4j.App;\nimport io.joshuasalcedo.webview4j.View;\n")
                .append("import io.joshuasalcedo.webview4j.plugin.automation.Automation;\nimport java.nio.file.Path;\n")
                .append("import org.junit.jupiter.api.Test;\n\n")
                .append("/** ").append(name.replace("*/", "* /")).append(" (written from a recording). */\n")
                .append("class ").append(className).append(" {\n\n    @Test\n    void ").append(method(name)).append("() {\n")
                .append("        try (App app = App.start()) {\n")
                .append("            View view = openTheApp(app);   // the window the steps run in\n")
                .append("            Automation ui = Automation.of(view);\n");
        for (Step step : steps) b.append("            ").append(java(step)).append("\n");
        b.append("        }\n    }\n\n    View openTheApp(App app) {\n        throw new UnsupportedOperationException(\"open the app's window here\");\n    }\n}\n");
        return b.toString();
    }

    private static String java(Step step) {
        return switch (step) {
            case Step.Click s -> find(s.target()) + ".click();";
            case Step.DoubleClick s -> find(s.target()) + ".doubleClick();";
            case Step.RightClick s -> find(s.target()) + ".rightClick();";
            case Step.Hover s -> find(s.target()) + ".hover();";
            case Step.Type s -> find(s.target()) + ".type(" + javaText(s.text()) + ");";
            case Step.Press s -> "ui.press(" + Json.write(s.key()) + ");";
            case Step.Select s -> find(s.target()) + ".select(" + Json.write(s.option()) + ");";
            case Step.Check s -> find(s.target()) + (s.checked() ? ".check();" : ".uncheck();");
            case Step.WaitForText s -> "ui.waitForText(" + Json.write(s.text()) + ");";
            case Step.WaitFor s -> find(s.target()) + ".waitFor();";
            case Step.Expect s -> find(s.target()) + ".expectText(" + Json.write(s.text()) + ");";
            case Step.Screenshot s -> "ui.screenshot(Path.of(" + Json.write(s.path()) + "));";
            case Step.Record s -> "var recording = view.record(Path.of(" + Json.write(s.path()) + ")).join();";
            case Step.StopRecording s -> "recording.stop().join();";
            case Step.Pause s -> "ui.pause(java.time.Duration.ofMillis(" + s.millis() + "));";
        };
    }

    /** A variable becomes {@code System.getenv("NAME")}: a password is never written into the code. */
    private static String javaText(String text) {
        Matcher m = VARIABLE.matcher(text);
        if (m.matches()) return "System.getenv(" + Json.write(m.group(1)) + ")";
        return Json.write(text);
    }

    private static String find(Locator l) {
        String base = switch (l.kind()) {
            case ROLE -> switch (l.role()) {
                case "button", "link", "checkbox", "tab", "heading" -> "ui." + l.role() + "(" + Json.write(l.name()) + ")";
                case "menuitem" -> "ui.menuItem(" + Json.write(l.name()) + ")";
                default -> "ui.role(" + Json.write(l.role()) + ", " + Json.write(l.name()) + ")";
            };
            case LABEL -> "ui.field(" + Json.write(l.name()) + ")";
            case TEXT -> "ui.text(" + Json.write(l.name()) + ")";
            case TEST_ID -> "ui.testId(" + Json.write(l.name()) + ")";
            case CSS -> "ui.css(" + Json.write(l.css()) + ")";
        };
        return base + (l.exact() && l.kind() != Locator.Kind.TEST_ID && l.kind() != Locator.Kind.CSS ? ".exactly()" : "")
                + (l.nth() > 0 ? ".nth(" + l.nth() + ")" : "");
    }

    private static String method(String name) {
        String[] words = name.replaceAll("[^A-Za-z0-9 ]", " ").trim().split("\\s+");
        StringBuilder m = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            String w = words[i].toLowerCase(Locale.ROOT);
            m.append(i == 0 ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        String out = m.isEmpty() ? "replays" : m.toString();
        return Character.isDigit(out.charAt(0)) ? "replays" + out : out;
    }
}
