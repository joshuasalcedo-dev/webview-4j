package example.notes;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every component webview-4j's Java UI has, in a window written only in Java (no HTML, CSS or JavaScript): a
 * sidebar of the components, and for each one the component itself, working, and the Java that makes it.
 * Open it with {@code Ui.open(app, new GalleryScreen())}.
 */
public final class GalleryScreen implements Screen {

    /** One page of the gallery: its name, the component working, and the Java for it. */
    private record Page(String section, String name, Supplier<Component<?>> demo, String java) {}

    private final Map<String, Page> pages = new LinkedHashMap<>();
    private String selected = "Button";
    private Platform platform = Platform.AUTO;
    private Theme theme = Theme.SYSTEM;

    // What the demos change, so they work.
    private int clicks;
    private String name = "";
    private String notes = "";
    private boolean remember = true;
    private boolean sync;
    private String country = "ph";
    private String show = "all";
    private double progress = 40;
    private String chosen = "Ana Reyes";

    public GalleryScreen() {
        page("Actions", "Button", () -> column(
                row(button("Accent").accent().onClick(() -> clicks++),
                        button("Default").onClick(() -> clicks++),
                        button("Subtle").subtle().onClick(() -> clicks++),
                        button("Disabled").disabled(true)),
                text("Clicked " + clicks + " times").tone(Tone.SECONDARY)),
                """
                button("Accent").accent().onClick(() -> clicks++)
                button("Default").onClick(() -> clicks++)
                button("Subtle").subtle()
                button("Disabled").disabled(true)""");
        page("Actions", "Link", () -> column(
                link("webview-4j on GitHub", "https://github.com/joshuasalcedo-dev/webview-4j"),
                link("A link that runs Java", "#").onClick(() -> clicks++),
                text("Clicked " + clicks + " times").tone(Tone.SECONDARY)),
                """
                link("webview-4j on GitHub", "https://github.com/...")
                link("A link that runs Java", "#").onClick(() -> clicks++)""");

        page("Input", "Text field", () -> column(
                textField("Name").value(name).onChange(v -> name = v).hint("As people see it"),
                textField("Email").value("ada@").error("That is not an email address"),
                textField("Password").password().placeholder("At least 12 characters"),
                text(name.isBlank() ? "Type your name" : "Hello, " + name + "!")),
                """
                textField("Name").value(name).onChange(v -> name = v).hint("As people see it")
                textField("Email").value("ada@").error("That is not an email address")
                textField("Password").password()""");
        page("Input", "Text area", () -> column(
                textArea("Notes").value(notes).rows(5).onChange(v -> notes = v),
                text(notes.length() + " characters").tone(Tone.SECONDARY)),
                """
                textArea("Notes").value(notes).rows(5).onChange(v -> notes = v)""");
        page("Input", "Checkbox and switch", () -> column(
                checkbox("Remember me").checked(remember).onChange(v -> remember = v),
                toggle("Sync").checked(sync).onChange(v -> sync = v),
                text((remember ? "Remembered" : "Not remembered") + ", sync " + (sync ? "on" : "off")).tone(Tone.SECONDARY)),
                """
                checkbox("Remember me").checked(remember).onChange(v -> remember = v)
                toggle("Sync").checked(sync).onChange(v -> sync = v)""");
        page("Input", "Select", () -> column(
                select("Country", Map.of("ph", "Philippines", "jp", "Japan", "us", "United States"))
                        .value(country).onChange(v -> country = v).width(260),
                text("Chosen: " + country).tone(Tone.SECONDARY)),
                """
                select("Country", Map.of("ph", "Philippines", "jp", "Japan", "us", "United States"))
                        .value(country).onChange(v -> country = v)""");
        page("Input", "Segmented control", () -> column(
                segmented("Show", ordered("all", "All", "unread", "Unread", "starred", "Starred"))
                        .value(show).onChange(v -> show = v),
                text("Showing " + show).tone(Tone.SECONDARY)),
                """
                segmented("Show", Map.of("all", "All", "unread", "Unread", "starred", "Starred"))
                        .value(show).onChange(v -> show = v)""");

        page("Progress", "Progress bar", () -> column(
                progress("Upload", progress),
                row(button("Less").onClick(() -> progress = Math.max(0, progress - 10)),
                        button("More").onClick(() -> progress = Math.min(100, progress + 10)),
                        text((int) progress + "%")),
                progress("Waiting", 0).indeterminate()),
                """
                progress("Upload", progress)
                progress("Waiting", 0).indeterminate()""");
        page("Progress", "Spinner", () -> row(spinner("Loading"), spinner("Loading").size(32), text("Working…")),
                """
                spinner("Loading")
                spinner("Loading").size(32)""");

        page("Content", "Text", () -> column(
                text("Display").variant(TextVariant.DISPLAY),
                text("Title").variant(TextVariant.TITLE),
                text("Headline").variant(TextVariant.HEADLINE),
                text("Body text, as the system writes it."),
                text("Secondary").tone(Tone.SECONDARY),
                text("Error").tone(Tone.ERROR),
                text("monospaced()").mono()),
                """
                text("Display").variant(TextVariant.DISPLAY)
                text("Title").variant(TextVariant.TITLE)
                text("Secondary").tone(Tone.SECONDARY)
                text("monospaced()").mono()""");
        page("Content", "Avatar and pill", () -> column(
                row(avatar("Ana Reyes"), avatar("Marco Silva").size(40), avatar("Jules").size(48)),
                row(pill("Paid").color(PillColor.GREEN), pill("Pending").color(PillColor.ORANGE).outline(),
                        pill("Failed").color(PillColor.RED).filled())),
                """
                avatar("Ana Reyes")
                pill("Paid").color(PillColor.GREEN)
                pill("Failed").color(PillColor.RED).filled()""");
        page("Content", "Money", () -> column(
                money(49900, "PHP"), money(499000, "JPY"), money(-1250, "USD").accounting(), money(125000, "EUR").signed()),
                """
                money(49900, "PHP")              // ₱499.00
                money(-1250, "USD").accounting()  // ($12.50)
                money(125000, "EUR").signed()     // +€1,250.00""");
        page("Content", "List", () -> column(
                list("People", List.of("Ana Reyes", "Marco Silva", "Jules", "Kim Park").stream()
                        .map(person -> listItem(person).key(person)
                                .subtitle(person.equals(chosen) ? "Chosen" : "Click to choose")
                                .leading(avatar(person)).trailing("9:41")
                                .selected(person.equals(chosen))
                                .onSelect(() -> chosen = person)
                                .menu("Choose", () -> chosen = person))
                        .toList())),
                """
                list("People", people.stream()
                        .map(p -> listItem(p).key(p).leading(avatar(p))
                                .selected(p.equals(chosen)).onSelect(() -> chosen = p)
                                .menu("Choose", () -> chosen = p))
                        .toList())""");
        page("Content", "Surface", () -> surface("Account",
                textField("Display name").value(name).onChange(v -> name = v),
                toggle("Sync").checked(sync).onChange(v -> sync = v)),
                """
                surface("Account",
                        textField("Display name").value(name).onChange(v -> name = v),
                        toggle("Sync").checked(sync).onChange(v -> sync = v))""");
        page("Content", "Code", () -> code("""
                        App.run(app -> Ui.open(app, new GalleryScreen()));""", "java"),
                """
                code("App.run(app -> ...);", "java")""");

        page("Layout", "Row and column", () -> column(
                row(button("One"), button("Two"), button("Three")).gap(8),
                column(text("Under"), text("each"), text("other")).gap(4)),
                """
                row(button("One"), button("Two"), button("Three")).gap(8)
                column(text("Under"), text("each"), text("other")).gap(4)""");
        page("Layout", "Window frame", () -> text("""
                        This window is one: frame(body) with .titleBar(...), .toolbar(...), .statusBar(...),
                        and split(sidebar(...), content) for the side bar and the page."""),
                """
                frame(split(sidebar(...), content))
                        .titleBar(titleBar("Gallery"))
                        .toolbar(toolbar("Button", ...))
                        .statusBar(statusBar(statusItem("Ready").dot(DotState.ONLINE)))""");
    }

    private void page(String section, String name, Supplier<Component<?>> demo, String java) {
        pages.put(name, new Page(section, name, demo, java));
    }

    /** Pairs in the order given (Map.of has none). */
    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    @Override
    public Component<?> render() {
        Page page = pages.get(selected);

        // The side bar: one section per kind of component.
        Map<String, List<Component<?>>> sections = new LinkedHashMap<>();
        for (Page p : pages.values()) {
            sections.computeIfAbsent(p.section(), s -> new java.util.ArrayList<>())
                    .add(sidebarItem(p.name()).key(p.name()).selected(p.name().equals(selected)).onSelect(() -> selected = p.name()));
        }
        Sidebar sidebar = sidebar(sections.entrySet().stream()
                .map(s -> sidebarSection(s.getKey(), s.getValue().toArray(Component<?>[]::new)))
                .toArray(Component<?>[]::new));

        Component<?> content = column(
                text(page.name()).variant(TextVariant.LARGE_TITLE),
                surface("Try it", page.demo().get()),
                surface("The Java", code(page.java(), "java"))).padding(24).gap(16);

        return frame(split(sidebar, content))
                .platform(platform)
                .theme(theme)
                .titleBar(titleBar("Component gallery"))
                .toolbar(toolbar(page.name(),
                        spacer(),
                        segmented("Look", ordered("AUTO", "This system", "WINDOWS", "Windows 11", "MAC", "macOS"))
                                .value(platform.name()).onChange(v -> platform = Platform.valueOf(v)),
                        segmented("Theme", ordered("SYSTEM", "System", "LIGHT", "Light", "DARK", "Dark"))
                                .value(theme.name()).onChange(v -> theme = Theme.valueOf(v))))
                .statusBar(statusBar(
                        statusItem("Written in Java only").dot(DotState.ONLINE),
                        spacer(),
                        statusItem(pages.size() + " components")));
    }

    /** Opens the gallery on its own: {@code ./gradlew run -Pgallery}. */
    static void open(io.joshuasalcedo.webview4j.App app) {
        Ui.open(app, new GalleryScreen(), o -> o.title("Component gallery").size(1180, 780).minSize(760, 480).decorations(false));
    }
}
