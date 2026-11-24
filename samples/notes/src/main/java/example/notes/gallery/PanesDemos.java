package example.notes.gallery;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.layout.AnchorPane;
import io.joshuasalcedo.webview4j.ui.components.layout.BorderPane;
import io.joshuasalcedo.webview4j.ui.components.layout.CardPane;
import io.joshuasalcedo.webview4j.ui.components.layout.FlowPane;
import io.joshuasalcedo.webview4j.ui.components.layout.GridPane;
import io.joshuasalcedo.webview4j.ui.components.layout.GridPane.ColumnConstraints;
import io.joshuasalcedo.webview4j.ui.components.layout.HBox;
import io.joshuasalcedo.webview4j.ui.components.layout.HPos;
import io.joshuasalcedo.webview4j.ui.components.layout.Insets;
import io.joshuasalcedo.webview4j.ui.components.layout.Pos;
import io.joshuasalcedo.webview4j.ui.components.layout.ScrollPane;
import io.joshuasalcedo.webview4j.ui.components.layout.SplitPane;
import io.joshuasalcedo.webview4j.ui.components.layout.StackPane;
import io.joshuasalcedo.webview4j.ui.components.layout.TilePane;
import io.joshuasalcedo.webview4j.ui.components.layout.VBox;
import io.joshuasalcedo.webview4j.ui.components.layout.VPos;
import java.util.ArrayList;
import java.util.List;

/** The gallery's pages: the layout panes, named as JavaFX's (HBox, VBox, GridPane...), and Swing's CardLayout. */
final class PanesDemos implements Demos {

    private String name = "";
    private String email = "";
    private String saved = "";
    private double[] split = {0.3};
    private String step = "account";
    private int slide;
    private int open;
    private Pos badge = Pos.TOP_RIGHT;
    private final List<String> tags = new ArrayList<>(List.of("java", "desktop", "react", "rust", "webview", "ffm"));

    /** A dashed box with a word in it: what the panes arrange. */
    private static Component<?> tile(String word) {
        return surface(null, text(word));
    }

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Panes", "HBox and VBox", () -> new VBox(12,
                        new HBox(8, button("Back"), HBox.hgrow(text("Title, taking the room left")), button("Next")),
                        new HBox(8, tile("One"), tile("Two"), tile("Three")).alignment(Pos.CENTER),
                        new VBox(4, tile("Under"), tile("each"), tile("other")).padding(new Insets(0, 0, 0, 24))),
                """
                new HBox(8, button("Back"), HBox.hgrow(text("Title")), button("Next"))
                new HBox(8, a, b, c).alignment(Pos.CENTER)
                new VBox(4, a, b, c).padding(new Insets(0, 0, 0, 24))
                // or with the factories: hbox(8, a, b), vbox(4, a, b)""",
                HBox.class, VBox.class);

        gallery.page("Panes", "GridPane", () -> new VBox(12,
                        new GridPane().hgap(12).vgap(8)
                                .columns(ColumnConstraints.fit().halignment(HPos.RIGHT), ColumnConstraints.grow())
                                .addRow(0, text("Name"), textField("Name").value(name).onChange(v -> name = v))
                                .addRow(1, text("Email"), textField("Email").value(email).onChange(v -> email = v))
                                .add(button("Save").accent().onClick(() -> saved = name + " <" + email + ">"), 1, 2, 1, 1, HPos.RIGHT, VPos.CENTER),
                        text(saved.isEmpty() ? "Fill it in and save" : "Saved " + saved).tone(Tone.SECONDARY)),
                """
                new GridPane().hgap(12).vgap(8)
                        .columns(ColumnConstraints.fit().halignment(HPos.RIGHT), ColumnConstraints.grow())
                        .addRow(0, text("Name"), textField("Name"))
                        .addRow(1, text("Email"), textField("Email"))
                        .add(button("Save").accent(), 1, 2, 1, 1, HPos.RIGHT, VPos.CENTER)""",
                GridPane.class);

        gallery.page("Panes", "BorderPane", () -> new BorderPane(tile("Center: the room left"))
                        .top(tile("Top")).left(tile("Left")).right(tile("Right")).bottom(tile("Bottom"))
                        .spacing(8).height(220),
                """
                new BorderPane(editor).top(toolbar).left(outline).right(inspector).bottom(status)""",
                BorderPane.class);

        gallery.page("Panes", "StackPane", () -> new VBox(12,
                        new StackPane(text("Picture"))
                                .add(pill("3").color(PillColor.RED).filled(), badge, new Insets(6))
                                .width(200).height(120),
                        segmented("Badge", Demos.ordered("TOP_LEFT", "Top left", "TOP_RIGHT", "Top right", "BOTTOM_RIGHT", "Bottom right", "CENTER", "Center"))
                                .value(badge.name()).onChange(v -> badge = Pos.valueOf(v))),
                """
                new StackPane(picture).add(pill("3"), Pos.TOP_RIGHT, new Insets(6))""",
                StackPane.class);

        gallery.page("Panes", "SplitPane", () -> new VBox(8,
                        new SplitPane(tile("Navigator"),
                                new SplitPane(tile("Editor"), tile("Console")).orientation(SplitPane.Orientation.VERTICAL))
                                .dividerPositions(split).onDividerPositionsChange(p -> split = p).height(240),
                        text("Divider at " + Math.round(split[0] * 100) + "%: drag it, or focus it and use the arrow keys").tone(Tone.SECONDARY)),
                """
                new SplitPane(navigator, new SplitPane(editor, console).orientation(Orientation.VERTICAL))
                        .dividerPositions(split).onDividerPositionsChange(p -> split = p)""",
                SplitPane.class);

        gallery.page("Panes", "FlowPane", () -> new VBox(12,
                        new FlowPane(6, 6, tags.stream().map(t -> (Component<?>) pill(t)).toArray(Component<?>[]::new)),
                        new HBox(8, button("Add a tag").onClick(() -> tags.add("tag " + (tags.size() + 1))),
                                button("Remove one").onClick(() -> { if (!tags.isEmpty()) tags.removeLast(); }))),
                """
                new FlowPane(6, 6, tags.stream().map(t -> pill(t)).toArray(Component[]::new))""",
                FlowPane.class);

        gallery.page("Panes", "TilePane", () -> new TilePane(
                        tile("Orders"), tile("Customers"), tile("Products"), tile("Invoices"), tile("Reports"), tile("Settings"), tile("Help"))
                        .tileWidth(140).hgap(8).vgap(8),
                """
                new TilePane(cards).tileWidth(140).hgap(8).vgap(8)   // as many in a row as fit""",
                TilePane.class);

        gallery.page("Panes", "AnchorPane", () -> new AnchorPane()
                        .add(tile("Top left"), AnchorPane.Anchors.topLeft(8, 8))
                        .add(tile("Top right"), AnchorPane.Anchors.topRight(8, 8))
                        .add(tile("Pinned left and right: stretched"), new AnchorPane.Anchors(null, 8.0, 8.0, 8.0))
                        .height(180),
                """
                new AnchorPane()
                        .add(logo, Anchors.topLeft(8, 8))
                        .add(close, Anchors.topRight(8, 8))
                        .add(footer, new Anchors(null, 8.0, 8.0, 8.0))   // right, bottom, left""",
                AnchorPane.class);

        gallery.page("Panes", "ScrollPane", () -> new ScrollPane(new VBox(4,
                        java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(i -> (Component<?>) text("Line " + i)).toArray(Component<?>[]::new)))
                        .padding(8).height(160),
                """
                new ScrollPane(longContent).height(160)   // fitToWidth unless told otherwise""",
                ScrollPane.class);

        gallery.page("Panes", "CardPane", () -> new VBox(12,
                        new CardPane()
                                .card("account", tile("1. Your account"))
                                .card("plan", tile("2. Your plan"))
                                .card("done", tile("3. All set"))
                                .show(step).transition(CardPane.Transition.SLIDE),
                        new HBox(8,
                                button("Account").onClick(() -> step = "account"),
                                button("Plan").onClick(() -> step = "plan"),
                                button("Done").accent().onClick(() -> step = "done"))),
                """
                new CardPane().card("account", account).card("plan", plan).card("done", done)
                        .show(step)   // Swing's CardLayout: one at a time, the others kept""",
                CardPane.class);

        gallery.page("Panes", "Carousel", () -> new VBox(8,
                        new Carousel("Pictures",
                                stackPage("Welcome", PillColor.BLUE), stackPage("Your files", PillColor.GREEN), stackPage("Share", PillColor.ORANGE))
                                .index(slide).onIndexChange(i -> slide = i).interval(4000).height(180),
                        text("Page " + (slide + 1) + " of 3").tone(Tone.SECONDARY)),
                """
                new Carousel("Pictures", page1, page2, page3).interval(4000).onIndexChange(i -> slide = i)""",
                Carousel.class);

        gallery.page("Panes", "TitledPane and Accordion", () -> new VBox(12,
                        new TitledPane("Advanced", textField("Proxy"), textField("Timeout, seconds")).expanded(false),
                        new Accordion()
                                .pane("General", text("Name, language, start at login"))
                                .pane("Privacy", text("Who sees what you share"))
                                .pane("About", text("webview-4j 0.2.0"))
                                .expanded(open).onExpandedChange(i -> open = i),
                        text(open < 0 ? "All closed" : "Open: " + List.of("General", "Privacy", "About").get(open)).tone(Tone.SECONDARY)),
                """
                new TitledPane("Advanced", proxy, timeout).expanded(false)
                new Accordion().pane("General", general).pane("Privacy", privacy)
                        .expanded(open).onExpandedChange(i -> open = i)""",
                TitledPane.class, Accordion.class);
    }

    private static Component<?> stackPage(String title, PillColor color) {
        return new StackPane(text(title).variant(TextVariant.TITLE)).add(pill(color.name().toLowerCase()).color(color), Pos.BOTTOM_LEFT, new Insets(12));
    }
}
