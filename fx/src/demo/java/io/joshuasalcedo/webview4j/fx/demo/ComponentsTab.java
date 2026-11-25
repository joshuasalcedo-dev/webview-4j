package io.joshuasalcedo.webview4j.fx.demo;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.fx.InspectorPane;
import io.joshuasalcedo.webview4j.fx.ScreenPane;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.components.PillColor;
import io.joshuasalcedo.webview4j.ui.components.Tone;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A page made only of Java: webview-4j's components (buttons, fields, switches, segmented choices, progress,
 * avatars, pills, money, lists, surfaces, code), shown inside JavaFX. On the left, JavaFX changes the screen's
 * state, and puts HTML of its own into the page.
 */
final class ComponentsTab extends SplitPane {

    /** The screen: its fields are its state, render() draws them. */
    static final class Showcase implements Screen {
        String name = "Ana";
        boolean notifications = true;
        boolean darkCards;
        String plan = "pro";
        double upload = 0.35;
        int clicks;
        long balance = 1_249_900;
        final List<String> people = new ArrayList<>(List.of("Ana Reyes", "Marco Silva", "Jules Moreau"));
        String said = "";

        @Override
        public Component<?> render() {
            return column(
                    title("Built in Java, shown in JavaFX"),
                    text("Every control here is a webview-4j component, written in Java: no HTML, no CSS, no JavaScript.")
                            .tone(Tone.SECONDARY),
                    row(
                            surface("Profile",
                                    row(avatar(name.isBlank() ? "?" : name).size(48),
                                            column(text(name.isBlank() ? "Nobody yet" : name),
                                                    pillGroup(pill(plan.toUpperCase()).color(PillColor.GREEN),
                                                            pill(notifications ? "Notified" : "Quiet").color(PillColor.ORANGE).outline()))).gap(12),
                                    textField("Name").value(name).onChange(v -> name = v).hint("Type: the avatar and the title follow"),
                                    toggle("Notifications").checked(notifications).onChange(v -> notifications = v),
                                    segmented("Plan", plans()).value(plan).onChange(v -> plan = v)),
                            surface("Account",
                                    text("Balance"),
                                    money(balance, "PHP"),
                                    progress("Upload", upload),
                                    row(button("Add ₱100").accent().onClick(() -> balance += 10_000),
                                            button("Upload more").onClick(() -> upload = Math.min(1, upload + 0.15)),
                                            button("Reset").subtle().onClick(() -> {
                                                upload = 0;
                                                balance = 0;
                                            })).gap(8))).gap(16).wrap(),
                    surface("People",
                            list("People", people.stream().<Component<?>>map(p -> listItem(p).key(p).leading(avatar(p))).toList()),
                            row(button("Add someone").onClick(() -> people.add("Person " + (people.size() + 1))),
                                    button("Remove the last").subtle().onClick(() -> {
                                        if (!people.isEmpty()) people.removeLast();
                                    })).gap(8)),
                    surface("Clicks",
                            row(button("Click me").accent().onClick(() -> clicks++),
                                    text("Clicked " + clicks + " times" + (said.isBlank() ? "" : " · " + said)).tone(Tone.SECONDARY)).gap(12)),
                    code("""
                            class Showcase implements Screen {
                                int clicks;
                                public Component<?> render() {
                                    return column(title("Built in Java"),
                                            button("Click me").accent().onClick(() -> clicks++));
                                }
                            }""", "java")).gap(16).padding(24).scroll();
        }

        private static Map<String, String> plans() {
            Map<String, String> plans = new LinkedHashMap<>();
            plans.put("free", "Free");
            plans.put("pro", "Pro");
            plans.put("team", "Team");
            return plans;
        }
    }

    ComponentsTab(App app) {
        Showcase showcase = new Showcase();
        ScreenPane pane = new ScreenPane(app, showcase);

        // JavaFX changing the Java screen's state: the page draws again with refresh().
        TextField name = new TextField("Grace Hopper");
        Button setName = fxButton("Set the name", () -> {
            showcase.name = name.getText();
            pane.refresh();
        });
        Button addPerson = fxButton("Add a person", () -> {
            showcase.people.add(name.getText());
            pane.refresh();
        });
        Button click = fxButton("Click 10 times", () -> {
            showcase.clicks += 10;
            showcase.said = "10 clicks from JavaFX";
            pane.refresh();
        });
        Button team = fxButton("Plan: Team", () -> {
            showcase.plan = "team";
            pane.refresh();
        });
        Button fill = fxButton("Upload: done", () -> {
            showcase.upload = 1;
            pane.refresh();
        });

        // HTML of JavaFX's own, put into the page: at the end of what the selector picks.
        TextField where = new TextField("body");
        TextArea html = new TextArea("""
                <div style="margin:24px;padding:16px 20px;border-radius:14px;color:white;
                            background:linear-gradient(135deg,#6366f1,#ec4899);font:600 15px system-ui">
                  Injected from JavaFX at %s
                </div>""".formatted(java.time.LocalTime.now().withNano(0)));
        html.setPrefRowCount(6);
        html.setWrapText(true);
        html.setStyle("-fx-font-family: 'Consolas', monospace;");
        Label injected = new Label();
        injected.setWrapText(true);
        Button append = fxButton("Inject (append)", () -> pane.dom().append(where.getText(), mark(html.getText()))
                .thenAccept(n -> Platform.runLater(() -> injected.setText("Added to " + n + " element(s)"))));
        Button prepend = fxButton("Inject (prepend)", () -> pane.dom().prepend(where.getText(), mark(html.getText()))
                .thenAccept(n -> Platform.runLater(() -> injected.setText("Added to " + n + " element(s)"))));
        Button removeInjected = fxButton("Remove what was injected", () -> pane.dom().remove("[data-injected]")
                .thenAccept(n -> Platform.runLater(() -> injected.setText("Removed " + n))));

        VBox controls = new VBox(8,
                heading("Change the Java screen"), name, setName, addPerson, click, team, fill,
                new Separator(),
                heading("Inject HTML"), new Label("Where (a CSS selector)"), where, html,
                new HBox(6, append, prepend), removeInjected, injected);
        controls.setPadding(new Insets(12));
        controls.setMinWidth(260);
        controls.setMaxWidth(340);
        VBox.setVgrow(html, Priority.SOMETIMES);

        InspectorPane inspector = new InspectorPane();
        inspector.attach(pane);
        inspector.getSelectionModel().select(1);
        SplitPane page = new SplitPane(pane, inspector);
        page.setOrientation(Orientation.VERTICAL);
        page.setDividerPositions(0.72);

        getItems().addAll(controls, page);
        setDividerPositions(0.24);
        SplitPane.setResizableWithParent(controls, false);
    }

    /** The injected HTML, wrapped so that "Remove what was injected" finds it. */
    private static String mark(String html) {
        return "<div data-injected>" + html + "</div>";
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        return label;
    }

    private static Button fxButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
    }
}
