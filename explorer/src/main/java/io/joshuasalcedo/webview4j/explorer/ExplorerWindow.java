package io.joshuasalcedo.webview4j.explorer;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Closed;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.PageLoad;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.explorer.ApiIndex.Kind;
import io.joshuasalcedo.webview4j.explorer.ApiIndex.Member;
import io.joshuasalcedo.webview4j.explorer.ApiIndex.Type;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Window;
import io.joshuasalcedo.webview4j.ui.components.EmptyState;
import io.joshuasalcedo.webview4j.ui.components.Sidebar;
import io.joshuasalcedo.webview4j.ui.components.SidebarMenuButton;
import io.joshuasalcedo.webview4j.ui.components.TextVariant;
import io.joshuasalcedo.webview4j.ui.components.Tone;
import io.joshuasalcedo.webview4j.ui.components.layout.Box;
import io.joshuasalcedo.webview4j.ui.components.layout.ScrollPane;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The explorer: the components on the left (Components, then Plugins), a page per component made from its API, and on
 * it the {@link Story}s written for it, each a Preview with its controls (its {@link Variable}s) and its Code. Saving a
 * story's file compiles it and shows it again; editing its code here does too, without touching the file.
 */
final class ExplorerWindow extends Window {

    /** The sidebar's categories: package -> its title, in this order. Only components are listed. */
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    /** Which sidebar group each category is in: Components first, then Plugins. */
    private static final Map<String, String> GROUP = new LinkedHashMap<>();

    static {
        String p = "io.joshuasalcedo.webview4j.";
        category(p + "ui.components", "General", "Components");
        category(p + "ui.components.layout", "Layout", "Components");
        category(p + "ui.components.valueobject", "Fields", "Components");
        category(p + "plugin.authentication", "Authentication", "Plugins");
        category(p + "plugin.collection", "Collections", "Plugins");
        category(p + "plugin.document", "Documents", "Plugins");
        category(p + "plugin.autoupdate", "Updates", "Plugins");
        category(p + "plugin.mdi", "Windows", "Plugins");
    }

    private static void category(String pkg, String title, String group) {
        CATEGORIES.put(pkg, title);
        GROUP.put(title, group);
    }

    /** Not components to show: the building blocks the others are made of. */
    private static final List<String> HIDDEN = List.of("Element", "StyleSheet", "Html");

    /** A code edit runs once typing has stopped this long. */
    private static final long QUIET_MILLIS = 450;

    /** A story on the page: as written, as edited here, its variables' values, what it gave. */
    private static final class Shown {
        final Story written;
        Story story;
        final Args args;
        String code;
        final String writtenCode;
        Stories.Outcome outcome;
        String codeError;
        boolean codeTab;

        Shown(Story written, String code) {
            this.written = written;
            this.story = written;
            this.args = Args.of(written.variables());
            this.code = code;
            this.writtenCode = code;
        }
    }

    private final App app;
    private final ApiIndex index;
    private final Stories stories;
    private final List<Type> components;
    private final ScheduledExecutorService runner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "explorer-stories");
        t.setDaemon(true);
        return t;
    });
    private final Map<Shown, ScheduledFuture<?>> pending = new HashMap<>();

    private List<Story> all = List.of();
    private String loadErrors;
    private long stamp;
    private String query = "";
    private Type type;
    private List<Shown> shown = List.of();
    private volatile View docs;

    ExplorerWindow(App app) {
        super("webview-4j components");
        this.app = app;
        setSize(1440, 920);
        setMinimumSize(960, 600);
        ClassLoader loader = ExplorerWindow.class.getClassLoader();
        this.index = ApiIndex.load(loader).orElse(new ApiIndex("", "", List.of()));
        this.stories = new Stories(loader, Stories.findSourceDir());
        this.components = index.types().stream()
                .filter(t -> CATEGORIES.containsKey(t.packageName()))
                .filter(t -> index.isComponent(t.qualifiedName()) && !t.qualifiedName().equals(ApiIndex.COMPONENT))
                .filter(t -> !t.signature().contains("abstract ") && !HIDDEN.contains(t.name().split("\\.")[0]))
                .toList();
        reload();
        components.stream().filter(t -> t.name().equals("Button")).findFirst()
                .or(() -> components.stream().findFirst()).ifPresent(this::showPage);
        if (stories.live()) runner.scheduleWithFixedDelay(this::reloadIfChanged, 1, 1, TimeUnit.SECONDS);
    }

    // ---- stories ----

    private synchronized void reload() {
        stamp = stories.stamp();
        Stories.Loaded loaded = stories.load();
        all = loaded.stories();
        loadErrors = loaded.errors();
    }

    /** A story's file was saved: compile them again, and show the page again with them. */
    private void reloadIfChanged() {
        if (stories.stamp() == stamp) return;
        reload();
        Type t = type;
        if (t != null) showPage(t);
        repaint();
    }

    private List<Story> storiesOf(Type t) {
        return all.stream().filter(s -> s.component() != null && s.component().getName().equals(binaryName(t))).toList();
    }

    /** {@code pkg.Outer$Inner} for a nested type, as Class.getName() says it. */
    private static String binaryName(Type t) {
        return t.packageName() + "." + t.name().replace('.', '$');
    }

    private synchronized void showPage(Type picked) {
        type = picked;
        pending.values().forEach(f -> f.cancel(false));
        pending.clear();
        shown = storiesOf(picked).stream().map(s -> new Shown(s, stories.code(s).orElse(""))).toList();
        for (Shown s : shown) schedule(s, () -> show(s), 0);
        followInDocs();
    }

    private synchronized void schedule(Shown s, Runnable work, long delay) {
        ScheduledFuture<?> before = pending.get(s);
        if (before != null) before.cancel(false);
        pending.put(s, runner.schedule(work, delay, TimeUnit.MILLISECONDS));
    }

    /** Runs the story's showcase with its variables' values now. */
    private void show(Shown s) {
        Stories.Outcome outcome = Stories.run(s.story, s.args);
        synchronized (this) {
            s.outcome = outcome;
        }
        repaint();
    }

    /** The code was edited here: the story with that body, compiled, then shown. */
    private synchronized void edit(Shown s, String typed) {
        if (typed.equals(s.code)) return;
        s.code = typed;
        schedule(s, () -> {
            StringBuilder errors = new StringBuilder();
            Optional<Story> edited = stories.withBody(s.written, typed, errors);
            synchronized (this) {
                if (!typed.equals(s.code)) return;
                s.codeError = edited.isPresent() ? null : errors.toString();
                edited.ifPresent(story -> s.story = story);
            }
            if (edited.isPresent()) show(s);
            else repaint();
        }, QUIET_MILLIS);
    }

    private synchronized void reset(Shown s) {
        s.code = s.writtenCode;
        s.story = s.written;
        s.codeError = null;
        schedule(s, () -> show(s), 0);
    }

    private void set(Shown s, Variable<?> variable, Object value) {
        synchronized (this) {
            s.args.setUnchecked(variable, value);
        }
        schedule(s, () -> show(s), 0);
    }

    private void createStory() {
        Type t = type;
        if (t == null) return;
        stories.create(t);
        reloadIfChanged();
    }

    // ---- the pretty-docs window ----

    private void openDocs() {
        if (docs != null) {
            followInDocs();
            return;
        }
        WindowOptions options = WindowOptions.app("explorer-docs", ExplorerWindow.class.getClassLoader())
                .title("webview-4j API docs (pretty-docs)").size(1200, 860);
        app.open(options).thenAccept(view -> {
            docs = view;
            view.subscribe(PageLoad.class, load -> {
                if (load.state() == PageLoad.State.FINISHED) followInDocs();
            });
            view.subscribe(Closed.class, closed -> {
                docs = null;
                repaint();
            });
            repaint();
        });
    }

    private void followInDocs() {
        View open = docs;
        Type t = type;
        if (open == null || t == null) return;
        String route = "#/t/" + java.net.URLEncoder.encode(t.qualifiedName(), java.nio.charset.StandardCharsets.UTF_8);
        open.eval("location.hash = " + Json.write(route));
    }

    @Override
    protected WindowOptions options(WindowOptions options) {
        // the frame draws its own title bar
        return super.options(options).decorations(false);
    }

    // ---- drawing ----

    @Override
    public synchronized Component<?> render() {
        if (components.isEmpty()) {
            return frame(column(
                    title("No components"),
                    text("explorer/api-index.json is not on the class path. Start the explorer with just explorer: "
                            + "the build makes it from core's sources with pretty-docs.").tone(Tone.SECONDARY)).padding(24).gap(12));
        }
        return frame(page())
                .sidebar(componentList())
                .titleBar(titleBar("webview-4j components"))
                .toolbar(toolbar(type == null ? "" : type.name(),
                        spacer(),
                        toolbarButton(docs == null ? "Open docs" : "Show in docs").onClick(this::openDocs)))
                .statusBar(statusBar(
                        statusItem(components.size() + " components, " + all.size() + " stories"),
                        spacer(),
                        statusItem(stories.live() ? "Stories reload when saved" : "Stories from the class path")));
    }

    private Sidebar componentList() {
        String q = query.toLowerCase(Locale.ROOT).strip();
        // Components, then Plugins: each a group of categories, whose components sit indented under them.
        Map<String, List<SidebarMenuButton>> byCategory = new LinkedHashMap<>();
        CATEGORIES.values().forEach(c -> byCategory.put(c, new ArrayList<>()));
        for (Type t : components) {
            if (!q.isEmpty() && !t.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            int count = storiesOf(t).size();
            byCategory.get(CATEGORIES.get(t.packageName())).add(new SidebarMenuButton(t.name()).key(t.qualifiedName())
                    .badge(count).active(t == type).onClick(() -> showPage(t)));
        }
        Map<String, List<Component<?>>> byGroup = new LinkedHashMap<>();
        byCategory.forEach((category, items) -> {
            if (items.isEmpty()) return;
            byGroup.computeIfAbsent(GROUP.get(category), g -> new ArrayList<>())
                    .add(new SidebarMenuButton(category).key("category/" + category).open(true)
                            .items(items.toArray(SidebarMenuButton[]::new)));
        });
        List<Component<?>> groups = new ArrayList<>();
        byGroup.forEach((group, categories) -> groups.add(sidebarGroup(group, categories.toArray(Component<?>[]::new)).key(group)));
        return sidebar(groups.toArray(Component<?>[]::new))
                .header(sidebarInput("Search components").value(query).onChange(v -> {
                    synchronized (this) {
                        query = v;
                    }
                    repaint();
                }));
    }

    private Component<?> page() {
        if (type == null) return column(text("Pick a component."));
        List<Component<?>> parts = new ArrayList<>();
        parts.add(breadcrumb().crumb(GROUP.get(CATEGORIES.get(type.packageName()))).crumb(CATEGORIES.get(type.packageName())).crumb(type.name()));
        parts.add(text(type.name()).variant(TextVariant.LARGE_TITLE));
        String description = Javadocs.markdown(type.doc());
        if (!description.isEmpty()) parts.add(markdown(firstParagraph(description)));
        if (loadErrors != null) {
            parts.add(surface("A story does not compile", code(loadErrors, "plaintext")));
        }

        if (shown.isEmpty()) {
            parts.add(noStories());
        } else {
            for (int i = 0; i < shown.size(); i++) {
                Shown s = shown.get(i);
                if (i == 1) parts.add(text("Examples").variant(TextVariant.TITLE));
                if (i > 0) parts.add(text(s.written.title()).variant(TextVariant.HEADLINE));
                if (!s.written.description().isBlank()) parts.add(text(s.written.description()).tone(Tone.SECONDARY));
                parts.add(story(s, i));
            }
        }

        String rest = restOf(description);
        if (!rest.isEmpty()) {
            parts.add(text("Usage").variant(TextVariant.TITLE));
            parts.add(markdown(rest).headingOffset(2));
        }
        parts.add(text("API Reference").variant(TextVariant.TITLE));
        parts.add(markdown(apiTable()));
        // A scroll pane, not a scrolling column: a column's children shrink to fit it, and drew over each other.
        return new ScrollPane(column(parts).gap(16).padding(32).maxWidth(1024)).fitToWidth(true);
    }

    private Component<?> noStories() {
        String file = type.name().replace(".", "") + "Default.java";
        EmptyState empty = new EmptyState("No stories yet")
                .message("Write a Story for " + type.name() + " in " + Stories.PACKAGE + ": its showcase, its variables. "
                        + "It shows here as soon as it is saved.");
        return stories.live()
                ? column(empty, row(button("Create " + file).accent().onClick(this::createStory))).gap(12)
                : empty;
    }

    /** A story as the docs show one: Preview and Code tabs over a panel, the controls under the preview. */
    private Component<?> story(Shown s, int i) {
        Component<?> tabs = tabs("Story").key(type.qualifiedName() + "/tabs/" + i)
                .tab("preview", "Preview").tab("code", "Code")
                .value(s.codeTab ? "code" : "preview")
                .onChange(v -> {
                    synchronized (this) {
                        s.codeTab = v.equals("code");
                    }
                    repaint();
                });
        List<Component<?>> body = new ArrayList<>();
        if (s.codeTab) {
            long lines = Math.max(3, s.code.lines().count());
            body.add(codeEditor("Code").key(type.qualifiedName() + "/code/" + i).language("java").lineNumbers(true)
                    .value(s.code).onChange(typed -> edit(s, typed)).height(22 * lines + 40));
            body.add(row(text(s.codeError != null ? "Does not compile" : s.code.equals(s.writtenCode)
                                    ? "showcase(Args args) in " + s.written.getClass().getSimpleName() + ".java"
                                    : "Edited here (the file is unchanged)")
                            .tone(s.codeError != null ? Tone.ERROR : Tone.SECONDARY),
                    spacer(),
                    button("Reset").subtle().disabled(s.code.equals(s.writtenCode)).onClick(() -> reset(s))));
            if (s.codeError != null) body.add(code(s.codeError, "plaintext"));
        }
        body.add(preview(s));
        if (!s.written.variables().isEmpty()) body.add(controls(s));
        return column(tabs, surface(null, column(body).gap(12))).gap(8);
    }

    /** What the showcase gave, centred as a preview is. */
    private Component<?> preview(Shown s) {
        Component<?> inside;
        if (s.outcome == null) {
            inside = spinner("Running");
        } else if (!s.outcome.ok()) {
            inside = column(text("The showcase failed").tone(Tone.ERROR), code(s.outcome.error(), "plaintext")).gap(8);
        } else if (s.outcome.value() instanceof Component<?> component) {
            inside = component;
        } else if (s.outcome.value() instanceof Screen screen) {
            inside = screen.render();
        } else {
            inside = text(String.valueOf(s.outcome.value())).mono();
        }
        return column(inside).padding(32).minHeight(200).align(Box.Align.CENTER);
    }

    /** A control for each of the story's variables, made from its kind. */
    private Component<?> controls(Shown s) {
        List<Component<?>> fields = new ArrayList<>();
        for (Variable<?> v : s.written.variables()) {
            Object now = s.args.get(v);
            String key = type.qualifiedName() + "/" + s.written.getClass().getName() + "/" + v.name();
            fields.add(switch (v) {
                case Variable.Text t -> textField(t.name()).key(key).value((String) now).onChange(x -> set(s, t, x));
                case Variable.Flag f -> toggle(f.name()).key(key).checked((Boolean) now).onChange(x -> set(s, f, x));
                case Variable.Number n -> slider(n.name(), (Double) now).key(key).min(n.min()).max(n.max()).step(n.step())
                        .showValue().onChange((Double x) -> set(s, n, x));
                case Variable.Choice<?> c -> choice(s, c, (Enum<?>) now, key);
            });
        }
        return column(separator(), text("Controls").variant(TextVariant.HEADLINE), column(fields).gap(12)).gap(12);
    }

    private <E extends Enum<E>> Component<?> choice(Shown s, Variable.Choice<E> c, Enum<?> now, String key) {
        Map<String, String> options = new LinkedHashMap<>();
        for (E constant : c.type().getEnumConstants()) options.put(constant.name(), constant.name());
        return select(c.name(), options).key(key).value(now.name()).onChange(x -> set(s, c, Enum.valueOf(c.type(), x)));
    }

    /** The component's methods as a table: what each takes and does. */
    private String apiTable() {
        StringBuilder out = new StringBuilder("| Method | Description |\n|---|---|\n");
        for (Member m : type.members()) {
            if (m.kind() == Kind.FIELD) continue;
            String name = m.kind() == Kind.CONSTRUCTOR ? "new " + m.shortSignature() : m.shortSignature();
            out.append("| `").append(name.replace("|", "\\|")).append("` | ")
                    .append(Javadocs.summary(m.doc()).replace("|", "\\|")).append(" |\n");
        }
        return out.toString();
    }

    private static String firstParagraph(String markdown) {
        int end = markdown.indexOf("\n\n");
        return end < 0 ? markdown : markdown.substring(0, end);
    }

    private static String restOf(String markdown) {
        int end = markdown.indexOf("\n\n");
        return end < 0 ? "" : markdown.substring(end).strip();
    }
}
