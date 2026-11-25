package io.joshuasalcedo.webview4j.fx.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.fx.InspectorPane;
import io.joshuasalcedo.webview4j.fx.WebViewPane;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

/**
 * HTML on the left, the page it makes on the right: a ready-made page with Tailwind CSS and JavaScript to start
 * from. Edit it and the page follows (or Run, Ctrl+Enter). Below: JavaFX buttons that call the page's JavaScript,
 * and the page's console, where JavaScript can be run too.
 */
final class HtmlTab extends BorderPane {

    private final WebViewPane web;
    private final TextArea code = new TextArea();
    private final Label status = new Label();

    HtmlTab(App app) {
        String page = resource("tailwind.html");
        code.setText(page);
        code.setStyle("-fx-font-family: 'Cascadia Mono', 'Consolas', monospace; -fx-font-size: 12.5px;");
        code.setWrapText(false);
        web = WebViewPane.html(app, page);

        Button run = button("Run  (Ctrl+Enter)", this::run);
        Button reset = button("Reset", () -> {
            code.setText(page);
            run();
        });
        CheckBox live = new CheckBox("Live: run as I type");
        live.setSelected(true);
        PauseTransition typing = new PauseTransition(Duration.millis(700));
        typing.setOnFinished(e -> run());
        code.textProperty().addListener((p, before, now) -> {
            if (live.isSelected()) typing.playFromStart();
        });
        code.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.ENTER && key.isShortcutDown()) {
                run();
                key.consume();
            }
        });
        HBox codeBar = new HBox(8, new Label("HTML"), spacer(), live, reset, run);
        codeBar.setAlignment(Pos.CENTER_LEFT);
        codeBar.setPadding(new Insets(6, 8, 6, 8));
        BorderPane editor = new BorderPane(code);
        editor.setTop(codeBar);

        // JavaFX calling the page's JavaScript (window.demo in tailwind.html).
        TextField todo = new TextField("Buy milk, from JavaFX");
        HBox.setHgrow(todo, Priority.ALWAYS);
        HBox calls = new HBox(6,
                todo,
                button("Add the to-do", () -> call("demo.addTodo(" + Json.write(todo.getText()) + ")")),
                button("Dark", () -> call("demo.setDark(true)")),
                button("Light", () -> call("demo.setDark(false)")),
                button("+1", () -> call("demo.step(1)")),
                button("Toast", () -> call("demo.toast('Hello from JavaFX')")),
                button("Load users", () -> call("demo.loadUsers(); 'loading'")),
                button("Read its state", () -> call("demo.state()")));
        calls.setAlignment(Pos.CENTER_LEFT);
        calls.setPadding(new Insets(6, 8, 6, 8));
        status.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #52525b;");
        status.setPadding(new Insets(0, 8, 6, 8));

        InspectorPane inspector = new InspectorPane();
        inspector.attach(web);
        inspector.getSelectionModel().select(1); // the console

        BorderPane preview = new BorderPane(web);
        HBox previewBar = new HBox(8, new Label("Page"), spacer(), new Label("WebView2 (Chromium)"));
        previewBar.setPadding(new Insets(6, 8, 6, 8));
        preview.setTop(previewBar);

        SplitPane codeAndPage = new SplitPane(editor, preview);
        codeAndPage.setDividerPositions(0.42);
        BorderPane bottom = new BorderPane(inspector);
        bottom.setTop(new javafx.scene.layout.VBox(calls, status));
        SplitPane all = new SplitPane(codeAndPage, bottom);
        all.setOrientation(Orientation.VERTICAL);
        all.setDividerPositions(0.68);
        setCenter(all);
    }

    private void run() {
        web.loadHtml(code.getText());
        status.setText("Ran the HTML at " + java.time.LocalTime.now().withNano(0));
    }

    private void call(String script) {
        web.eval(script).whenComplete((value, error) -> Platform.runLater(() -> status.setText(error == null
                ? script + "  →  " + Json.write(value)
                : script + "  →  " + error.getMessage())));
    }

    private static String resource(String name) {
        try (InputStream in = HtmlTab.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException(name + " is not on the class path");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static javafx.scene.layout.Region spacer() {
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
