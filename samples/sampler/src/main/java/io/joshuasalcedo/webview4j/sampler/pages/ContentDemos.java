package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.sampler.pages.Demos.ordered;
import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate.Changelog;
import io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate.UpdatePrompt;
import java.util.List;
import java.util.Map;

/** The Sampler's pages: What a page shows: text, avatars and pills, money and quantities, code, progress, lists, surfaces. */
final class ContentDemos implements Demos {

    private static final String CHANGELOG = """
            # Changelog

            ## 0.3.0 — 2026-12-01

            - Auto-update: the app finds, checks and installs its next version
            - A **changelog** in the app

            ## 0.2.0 — 2026-11-25

            - Layout panes named as JavaFX's

            ## 0.1.0 — 2026-10-01

            - The first version
            """;
    private String changelogShow = "all";
    private UpdatePrompt.Status updateStatus = UpdatePrompt.Status.AVAILABLE;

    private double progress = 40;
    private String chosen = "Ana Reyes";
    private String name = "";
    private boolean sync;
    private boolean imperial;
    private DotState connection = DotState.ONLINE;
    private final java.util.ArrayList<String> typers = new java.util.ArrayList<>(List.of("Ana"));
    private final java.util.ArrayList<String> tags = new java.util.ArrayList<>(
            List.of("Design", "Backend", "Urgent", "Q4", "Mobile"));
    private int maxTags = 3;

    private static final List<String> PEOPLE = List.of("Ana", "Marco", "Jules", "Kim");
    private static final List<String> ALL_TAGS = List.of("Design", "Backend", "Urgent", "Q4", "Mobile",
            "Billing", "Docs", "Security", "Beta", "Infra", "API");
    private static final PillColor[] TAG_COLORS = {
            PillColor.BLUE, PillColor.PURPLE, PillColor.RED, PillColor.TEAL, PillColor.GREEN,
            PillColor.ORANGE, PillColor.PINK, PillColor.YELLOW, PillColor.GREY};

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Progress", "Progress bar", () -> column(
                progress("Upload", progress),
                row(button("Less").onClick(() -> progress = Math.max(0, progress - 10)),
                        button("More").onClick(() -> progress = Math.min(100, progress + 10)),
                        text((int) progress + "%")),
                progress("Waiting", 0).indeterminate()),
                """
                progress("Upload", progress)
                progress("Waiting", 0).indeterminate()""",
                ProgressBar.class);
        gallery.page("Progress", "Spinner", () -> row(spinner("Loading"), spinner("Loading").size(32), text("Working…")),
                """
                spinner("Loading")
                spinner("Loading").size(32)""",
                Spinner.class);
        gallery.page("Content", "Text", () -> column(
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
                text("monospaced()").mono()""",
                Text.class);
        gallery.page("Content", "Avatar and pill", () -> column(
                row(avatar("Ana Reyes"), avatar("Marco Silva").size(40), avatar("Jules").size(48)),
                row(pill("Paid").color(PillColor.GREEN), pill("Pending").color(PillColor.ORANGE).outline(),
                        pill("Failed").color(PillColor.RED).filled())),
                """
                avatar("Ana Reyes")
                pill("Paid").color(PillColor.GREEN)
                pill("Failed").color(PillColor.RED).filled()""",
                Avatar.class, Pill.class);
        gallery.page("Content", "Money", () -> column(
                money(49900, "PHP"), money(499000, "JPY"), money(-1250, "USD").accounting(), money(125000, "EUR").signed()),
                """
                money(49900, "PHP")              // ₱499.00
                money(-1250, "USD").accounting()  // ($12.50)
                money(125000, "EUR").signed()     // +€1,250.00""",
                Money.class);
        gallery.page("Content", "List", () -> column(
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
                        .toList())""",
                ListView.class, ListItem.class);
        gallery.page("Content", "Surface", () -> surface("Account",
                textField("Display name").value(name).onChange(v -> name = v),
                toggle("Sync").checked(sync).onChange(v -> sync = v)),
                """
                surface("Account",
                        textField("Display name").value(name).onChange(v -> name = v),
                        toggle("Sync").checked(sync).onChange(v -> sync = v))""",
                Surface.class);
        gallery.page("Content", "Code", () -> code("""
                        App.run(app -> Ui.open(app, new SamplerScreen()));""", "java"),
                """
                code("App.run(app -> ...);", "java")""",
                CodeBlock.class);
        gallery.page("Content", "Inline code and paragraphs", () -> column(
                paragraph("Open a window with ", inlineCode("Ui.open(app, screen)"), " from your main method; it shows the screen ",
                        "and draws it again after each event."),
                paragraph("Set ", inlineCode("WEBVIEW4J_DEVTOOLS=1"), " to inspect the page, or read ",
                        link("the guide", "https://github.com/joshuasalcedo-dev/webview-4j"), "."),
                new Paragraph().text("Press ").with(inlineCode("Ctrl+K")).text(" to search. Settings live in ")
                        .with(inlineCode("~/.notes/config.toml")).text(", and ").strong("nothing").text(" is sent anywhere.")).gap(12),
                """
                paragraph("Open a window with ", inlineCode("Ui.open(app, screen)"), " from main.")
                new Paragraph().text("Press ").with(inlineCode("Ctrl+K")).text(" to search, ").strong("quickly").text(".")""",
                InlineCode.class, Paragraph.class);
        gallery.page("Content", "Markdown", () -> column(
                markdown("""
                        ## Release notes
                        Version **0.2.0** brings *layouts*, state kept on disk, and the web.

                        - Layout panes named as JavaFX's: `HBox`, `GridPane`, `SplitPane`
                        - [x] Tested in a real window
                        - [ ] Shipped

                        | Part | Package |
                        | --- | --- |
                        | Layouts | `ui.components.layout` |
                        | State | `ui.state` |

                        > Links open in your browser: [webview-4j](https://github.com/joshuasalcedo-dev/webview-4j)
                        """)),
                """
                markdown(\"""
                        ## Release notes
                        - Layout panes, named as JavaFX's
                        | Part | Package |
                        \""")   // links open in the browser; HTML in it is never run""",
                Markdown.class);
        gallery.page("Updates", "Changelog", () -> column(
                segmented("Show", Demos.ordered("all", "Every version", "since", "Since 0.1.0", "only", "0.3.0 only"))
                        .value(changelogShow).onChange(v -> changelogShow = v),
                switch (changelogShow) {
                    case "since" -> Changelog.of(CHANGELOG).since("0.1.0");
                    case "only" -> Changelog.of(CHANGELOG).only("0.3.0");
                    default -> Changelog.of(CHANGELOG);
                }),
                """
                Changelog.bundled()                     // the app's CHANGELOG.md, put in its jar by the build
                Changelog.bundled().since(lastRun)      // What's new
                Changelog.of(markdown).only("0.3.0")    // one version's notes""",
                Changelog.class);
        gallery.page("Updates", "Update prompt", () -> column(
                segmented("Status", Demos.ordered("AVAILABLE", "Available", "DOWNLOADING", "Downloading", "READY", "Ready", "FAILED", "Failed"))
                        .value(updateStatus.name()).onChange(v -> updateStatus = UpdatePrompt.Status.valueOf(v)),
                new UpdatePrompt("Notes", "0.3.0").current("0.2.0").notes("- Auto-update\n- A **changelog** in the app")
                        .status(updateStatus).progress(0.6).error("The connection dropped.")
                        .onUpdate(() -> updateStatus = UpdatePrompt.Status.DOWNLOADING)
                        .onInstall(() -> updateStatus = UpdatePrompt.Status.AVAILABLE)
                        .onLater(() -> updateStatus = UpdatePrompt.Status.AVAILABLE)
                        .onSkip(() -> updateStatus = UpdatePrompt.Status.AVAILABLE)),
                """
                AutoUpdate.start(app);   // What's new, updates every 6 hours, this prompt when one is there
                new UpdatePrompt("Notes", "0.3.0").current("0.2.0").notes(markdown)
                        .status(status).progress(p).onUpdate(...).onLater(...).onSkip(...)""",
                UpdatePrompt.class);
        gallery.page("Content", "Pill group", () -> column(
                pillGroup(tagPills()).max(maxTags),
                row(button("Add tag").onClick(() -> {
                            ALL_TAGS.stream().filter(t -> !tags.contains(t)).findFirst().ifPresent(tags::add);
                        }),
                        button("Remove tag").onClick(() -> {
                            if (!tags.isEmpty()) tags.removeLast();
                        }),
                        button("Show fewer").onClick(() -> maxTags = Math.max(1, maxTags - 1)),
                        button("Show more").onClick(() -> maxTags = Math.min(11, maxTags + 1)),
                        text(tags.size() + " tags, max " + maxTags)),
                text("fit(): as many as the room allows").tone(Tone.SECONDARY),
                pillGroup(tagPills()).fit()),
                """
                pillGroup(pill("Design").color(PillColor.BLUE), pill("Backend"), pill("Urgent").color(PillColor.RED),
                        pill("Q4"), pill("Mobile")).max(3)   // Design Backend Urgent +2
                pillGroup(pills).fit()                       // as many as fit""",
                PillGroup.class);
        gallery.page("Content", "Quantity", () -> column(
                row(button(imperial ? "Show metric" : "Show imperial").onClick(() -> imperial = !imperial),
                        text(imperial ? "Converted with as(...)" : "As stored").tone(Tone.SECONDARY)),
                row(text("Parcel weight:"), imperial
                        ? quantity(2.5, Unit.KILOGRAM).as(Unit.POUND).digits(2)
                        : quantity(2.5, Unit.KILOGRAM)),
                row(text("Distance:"), imperial
                        ? quantity(42.195, Unit.KILOMETER).as(Unit.MILE).digits(1)
                        : quantity(42.195, Unit.KILOMETER).digits(1)),
                row(text("Temperature:"), imperial
                        ? quantity(37, Unit.CELSIUS).as(Unit.FAHRENHEIT).digits(1)
                        : quantity(37, Unit.CELSIUS)),
                row(text("Speed limit:"), imperial
                        ? quantity(100, Unit.KILOMETER_PER_HOUR).as(Unit.MILE_PER_HOUR).digits(0)
                        : quantity(100, Unit.KILOMETER_PER_HOUR)),
                row(text("Download:"), quantity(1.2, Unit.GIGABYTE), text("of"), quantity(4500, Unit.MEGABYTE).as(Unit.GIGABYTE).digits(1)),
                row(text("Battery:"), quantity(87, Unit.PERCENT)),
                row(text("Display long / narrow:"), quantity(3, Unit.HOUR).display(Quantity.Display.LONG), quantity(250, Unit.MILLILITER).display(Quantity.Display.NARROW))),
                """
                quantity(2.5, Unit.KILOGRAM)                        // 2.5 kg
                quantity(2.5, Unit.KILOGRAM).as(Unit.POUND).digits(2) // 5.51 lb
                quantity(37, Unit.CELSIUS).as(Unit.FAHRENHEIT)       // 98.6 °F
                quantity(3, Unit.HOUR).display(Quantity.Display.LONG)   // 3 hours""",
                Quantity.class);
        gallery.page("Content", "Status dot", () -> column(
                row(statusDot(DotState.ONLINE), text("Online"), statusDot(DotState.CONNECTING), text("Connecting"),
                        statusDot(DotState.OFFLINE), text("Offline"), statusDot(DotState.ERROR), text("Error")),
                row(statusDot(connection), text("Sync server: " + connection.name().toLowerCase())),
                row(button("Connect").onClick(() -> connection = DotState.CONNECTING),
                        button("Connected").onClick(() -> connection = DotState.ONLINE),
                        button("Disconnect").onClick(() -> connection = DotState.OFFLINE),
                        button("Fail").onClick(() -> connection = DotState.ERROR))),
                """
                row(statusDot(DotState.ONLINE), text("Sync server"))
                statusDot(connected ? DotState.ONLINE : DotState.OFFLINE)""",
                StatusDot.class);
        gallery.page("Content", "Typing indicator", () -> column(
                typers.isEmpty() ? text("Nobody is typing.").tone(Tone.SECONDARY) : typing(typers.toArray(String[]::new)),
                row(button("Someone starts typing").onClick(() -> PEOPLE.stream()
                                .filter(p -> !typers.contains(p)).findFirst().ifPresent(typers::add)),
                        button("Someone stops").onClick(() -> {
                            if (!typers.isEmpty()) typers.removeFirst();
                        }),
                        text(typers.size() + " typing"))),
                """
                typing("Ana")                 // Ana is typing…
                typing("Ana", "Marco")        // Ana and Marco are typing…""",
                TypingIndicator.class);
    }

    private Pill[] tagPills() {
        Pill[] pills = new Pill[tags.size()];
        for (int i = 0; i < pills.length; i++) pills[i] = pill(tags.get(i)).color(TAG_COLORS[i % TAG_COLORS.length]);
        return pills;
    }
}
