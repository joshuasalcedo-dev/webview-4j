package io.joshuasalcedo.webview4j.fx.demo;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.WindowOptions;
import javafx.application.ColorScheme;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.HBox;
import io.joshuasalcedo.webview4j.fx.BrowserPane;
import io.joshuasalcedo.webview4j.fx.Dom;
import io.joshuasalcedo.webview4j.fx.InspectorPane;
import io.joshuasalcedo.webview4j.fx.WebViewGrid;
import io.joshuasalcedo.webview4j.fx.WebViewPane;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * webview-4j-fx's demo: a modern webview (WebView2) inside a JavaFX app. Embedded: a page beside JavaFX buttons
 * that change it. Grid: four sites at once. Browser: the web, with its network, console and clicks below it.
 */
public final class FxDemo extends Application {

    private App app;

    @Override
    public void start(Stage stage) {
        app = App.start();

        TabPane tabs = new TabPane(
                new Tab("Java components + webview", new ComponentsTab(app)),
                new Tab("Embedded + webview", embedded()),
                new Tab("HTML + webview", new HtmlTab(app)),
                new Tab("Grid", grid()),
                new Tab("Browser", new BrowserPane(app, "https://developer.mozilla.org/en-US/")),
                new Tab("Sessions", new SessionsTab(app)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Options for every page in the app: light or dark (JavaFX's controls and the pages), and scrollbars.
        ChoiceBox<Theme> theme = new ChoiceBox<>(FXCollections.observableArrayList(Theme.SYSTEM, Theme.LIGHT, Theme.DARK));
        theme.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Theme t) { return t == null ? "" : t.name().charAt(0) + t.name().substring(1).toLowerCase(); }
            @Override public Theme fromString(String s) { return Theme.valueOf(s.toUpperCase()); }
        });
        theme.setValue(Theme.SYSTEM);
        CheckBox scrollbars = new CheckBox("Scrollbars");
        scrollbars.setSelected(true);
        HBox options = new HBox(8, new Label("Theme"), theme, scrollbars);
        options.setAlignment(Pos.CENTER_LEFT);
        options.setPadding(new Insets(6, 10, 6, 10));
        BorderPane root = new BorderPane(tabs);
        root.setTop(options);

        Scene scene = new Scene(root, 1280, 900);
        String dark = FxDemo.class.getResource("dark.css").toExternalForm();
        Runnable applyTheme = () -> {
            Theme chosen = theme.getValue();
            boolean isDark = chosen == Theme.DARK || (chosen == Theme.SYSTEM
                    && Platform.getPreferences().getColorScheme() == ColorScheme.DARK);
            scene.getStylesheets().remove(dark);
            if (isDark) scene.getStylesheets().add(dark);
            for (Node node : root.lookupAll(".webview-browser")) ((BrowserPane) node).setTheme(chosen);
            for (Node node : root.lookupAll(".webview-pane")) {
                WebViewPane pane = (WebViewPane) node;
                if (!pane.scrollbarsProperty().isBound()) pane.setTheme(chosen); // a browser's tabs follow it
            }
        };
        theme.valueProperty().addListener((p, before, now) -> applyTheme.run());
        Platform.getPreferences().colorSchemeProperty().addListener((p, before, now) -> applyTheme.run());
        scrollbars.selectedProperty().addListener((p, before, on) -> {
            for (Node node : root.lookupAll(".webview-browser")) ((BrowserPane) node).setScrollbars(on);
            for (Node node : root.lookupAll(".webview-pane")) {
                WebViewPane pane = (WebViewPane) node;
                if (!pane.scrollbarsProperty().isBound()) pane.setScrollbars(on);
            }
        });

        stage.setTitle("webview-4j-fx: a modern webview in JavaFX");
        stage.setScene(scene);
        applyTheme.run();
        stage.show();
    }

    @Override
    public void stop() {
        if (app != null) app.close();
        Platform.exit();
    }

    /** A page on the right, JavaFX buttons on the left that change its elements, and its console below. */
    private SplitPane embedded() {
        WebViewPane web = WebViewPane.html(app, PAGE);
        Dom dom = web.dom();
        Label engine = new Label("Engine: …");
        engine.setWrapText(true);
        Label result = new Label();
        result.setWrapText(true);

        TextField heading = new TextField("Changed from JavaFX");
        Button setHeading = button("Set the heading", () -> dom.setText("h1", heading.getText()));
        Button dark = button("Dark page", () -> {
            dom.setStyle("body", "background", "#18181b");
            dom.setStyle("body", "color", "#f4f4f5");
        });
        Button light = button("Light page", () -> {
            dom.setStyle("body", "background", "");
            dom.setStyle("body", "color", "");
        });
        Button highlight = button("Highlight the cards", () -> dom.toggleClass(".card", "picked"));
        Button addCard = button("Add a card", () -> dom.append("#cards",
                "<div class='card'><b>New card</b><p>Added by JavaFX at " + java.time.LocalTime.now().withNano(0) + "</p></div>"));
        Button removeLast = button("Remove the last card", () -> dom.remove("#cards .card:last-child"));
        Button type = button("Type in the field", () -> dom.setValue("#name", "Ana from JavaFX"));
        Button press = button("Click the page's button", () -> dom.click("#hello"));
        Button flashLinks = button("Flash the links", () -> dom.flash("a"));
        Button count = button("Count the cards", () -> dom.count(".card")
                .thenAccept(n -> Platform.runLater(() -> result.setText(n + " cards"))));

        InspectorPane inspector = new InspectorPane();
        inspector.attach(web);
        inspector.getSelectionModel().select(1); // the console: run JavaScript in the page from here
        web.whenOpen(view -> view.eval("navigator.userAgent").thenAccept(agent ->
                Platform.runLater(() -> engine.setText("Engine: " + engineOf(String.valueOf(agent))))));

        VBox controls = new VBox(8,
                new Label("Change the page from JavaFX"), engine, new Separator(),
                heading, setHeading, dark, light, highlight, addCard, removeLast, type, press, flashLinks, count, result);
        controls.setPadding(new Insets(12));
        controls.setMinWidth(230);
        controls.setMaxWidth(260);

        SplitPane page = new SplitPane(web, inspector);
        page.setOrientation(Orientation.VERTICAL);
        page.setDividerPositions(0.66);
        SplitPane split = new SplitPane(controls, page);
        split.setDividerPositions(0.2);
        SplitPane.setResizableWithParent(controls, false);
        return split;
    }

    private BorderPane grid() {
        WebViewGrid grid = new WebViewGrid(app, 2);
        grid.add("https://example.com");
        grid.add("https://en.wikipedia.org/wiki/WebView");
        grid.add("https://github.com/joshuasalcedo-dev/webview-4j");
        grid.add(WindowOptions.html(CLOCK));
        Button one = button("1 column", () -> grid.setColumns(1));
        Button two = button("2 columns", () -> grid.setColumns(2));
        Button four = button("4 columns", () -> grid.setColumns(4));
        Button add = button("Add example.com", () -> grid.add("https://example.com"));
        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(6, one, two, four, add);
        bar.setPadding(new Insets(8, 8, 0, 8));
        BorderPane pane = new BorderPane(grid);
        pane.setTop(bar);
        return pane;
    }

    /** "Edge 131 (WebView2, Chromium)" from the page's user agent. */
    static String engineOf(String agent) {
        java.util.regex.Matcher edge = java.util.regex.Pattern.compile("Edg/(\\d+)").matcher(agent);
        java.util.regex.Matcher chrome = java.util.regex.Pattern.compile("Chrome/(\\d+)").matcher(agent);
        if (edge.find()) return "Microsoft Edge " + edge.group(1) + " (WebView2, Chromium)";
        if (chrome.find()) return "Chromium " + chrome.group(1);
        return agent;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static final String PAGE = """
            <!doctype html>
            <html><head><meta charset="utf-8"><title>Embedded page</title>
            <style>
              body { font: 15px system-ui, sans-serif; margin: 24px; transition: background .3s, color .3s; }
              #cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
              .card { border: 1px solid #d4d4d8; border-radius: 12px; padding: 12px 14px; transition: transform .2s, box-shadow .2s; }
              .card.picked { transform: translateY(-3px); box-shadow: 0 8px 24px rgb(225 29 72 / .25); border-color: #e11d48; }
              .card p { margin: 6px 0 0; color: #71717a; }
              input, button { font: inherit; padding: 6px 10px; border-radius: 8px; border: 1px solid #d4d4d8; }
              :root { color-scheme: light dark; }
              @media (prefers-color-scheme: dark) {
                body { background: #18181b; color: #f4f4f5; }
                .card { border-color: #3f3f46; }
                a { color: #a5b4fc; }
              }
            </style></head>
            <body>
              <h1>A page inside JavaFX</h1>
              <p>WebView2, the engine of Microsoft Edge: <a href="https://developer.mozilla.org/">CSS grid</a>,
                 <a href="https://caniuse.com/">modern JavaScript</a>, everything Chromium does.</p>
              <div id="cards">
                <div class="card"><b>One</b><p>A card in a CSS grid.</p></div>
                <div class="card"><b>Two</b><p>JavaFX can change it.</p></div>
                <div class="card"><b>Three</b><p>Or remove it.</p></div>
              </div>
              <p><input id="name" placeholder="Your name"> <button id="hello">Say hello</button> <span id="said"></span></p>
              <script>
                document.getElementById('name').addEventListener('input', e => console.log('typed', e.target.value));
                document.getElementById('hello').addEventListener('click', () => {
                  const name = document.getElementById('name').value || 'nobody';
                  document.getElementById('said').textContent = 'Hello, ' + name + '!';
                  console.log('said hello to', name);
                  fetch('https://httpbin.org/get?from=webview4j-fx').then(r => r.json()).then(j => console.log('fetched', j.url));
                });
              </script>
            </body></html>
            """;

    private static final String CLOCK = """
            <!doctype html><html><head><meta charset="utf-8"><title>A live clock</title>
            <style>body{margin:0;height:100vh;display:grid;place-items:center;font:600 48px system-ui;
            background:linear-gradient(135deg,#6366f1,#ec4899);color:white}</style></head>
            <body><div id="t"></div><script>
            setInterval(() => document.getElementById('t').textContent = new Date().toLocaleTimeString(), 250);
            </script></body></html>
            """;
}
