package io.joshuasalcedo.webview4j.fx.demo;

import java.nio.file.Path;
import java.util.List;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Cookie;
import io.joshuasalcedo.webview4j.PageLoad;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.fx.BrowserPane;
import io.joshuasalcedo.webview4j.fx.WebSessions;
import io.joshuasalcedo.webview4j.fx.WebViewPane;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Saved sessions: each is a webview profile on disk (cookies, localStorage, signed-in sites) and the address it was
 * on. Make a new one, browse (Wikipedia, GitHub...), close it, open it again: the same page and the same cookies.
 */
final class SessionsTab extends SplitPane {

    private final App app;
    private final WebSessions sessions = WebSessions.in(Path.of(
            System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")), "webview4j-fx-demo", "sessions"));
    private final ListView<WebSessions.Session> list = new ListView<>();
    private final ObservableList<Cookie> cookies = FXCollections.observableArrayList();
    private final StackPane stage = new StackPane(new Label("Make a new session, or open a saved one"));
    private final Label status = new Label();
    private final Label visits = new Label();
    private BrowserPane browser;
    private WebSessions.Session open;

    SessionsTab(App app) {
        this.app = app;
        list.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(WebSessions.Session session, boolean empty) {
                super.updateItem(session, empty);
                setText(empty || session == null ? null : session.name() + (session.equals(open) ? "   (open)" : "")
                        + "\n" + session.lastUrl().orElse("never browsed"));
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) open(list.getSelectionModel().getSelectedItem());
        });

        Button create = button("New session", () -> open(sessions.create()));
        Button reopen = button("Open the picked one", () -> {
            WebSessions.Session picked = list.getSelectionModel().getSelectedItem();
            if (picked != null) open(picked);
        });
        Button close = button("Close it", this::close);
        Button delete = button("Delete the picked one", () -> {
            WebSessions.Session picked = list.getSelectionModel().getSelectedItem();
            if (picked == null) return;
            if (picked.equals(open)) close();
            picked.delete();
            refresh();
        });
        Button wikipedia = button("Wikipedia", () -> go("https://en.wikipedia.org/wiki/Web_browser"));
        Button github = button("GitHub", () -> go("https://github.com/explore"));
        Button mdn = button("MDN", () -> go("https://developer.mozilla.org/en-US/docs/Web/API/Window/localStorage"));
        Button example = button("example.com", () -> go("https://example.com"));
        Button showCookies = button("Show its cookies", this::loadCookies);

        Label where = new Label("Saved in " + sessions.root());
        where.setWrapText(true);
        where.setStyle("-fx-text-fill: #71717a; -fx-font-size: 11px;");
        VBox side = new VBox(8, new Label("Sessions"), list, create, reopen, close, delete, where);
        side.setPadding(new Insets(10));
        side.setMinWidth(240);
        side.setMaxWidth(320);

        HBox sites = new HBox(6, new Label("Go to:"), wikipedia, github, mdn, example, showCookies, status);
        sites.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        sites.setPadding(new Insets(8));

        TableView<Cookie> table = new TableView<>(cookies);
        table.getColumns().addAll(List.of(
                column("Domain", 190, Cookie::domain),
                column("Name", 180, Cookie::name),
                column("Value", 420, Cookie::value),
                column("Path", 70, Cookie::path),
                column("HttpOnly", 70, c -> c.httpOnly() ? "yes" : ""),
                column("Secure", 60, c -> c.secure() ? "yes" : "")));
        table.setPlaceholder(new Label("No cookies yet: browse somewhere, then Show its cookies"));
        BorderPane cookiePane = new BorderPane(table);
        cookiePane.setTop(visits);
        BorderPane.setMargin(visits, new Insets(6, 8, 6, 8));

        SplitPane page = new SplitPane(stage, cookiePane);
        page.setOrientation(Orientation.VERTICAL);
        page.setDividerPositions(0.66);
        BorderPane right = new BorderPane(page);
        right.setTop(sites);

        getItems().addAll(side, right);
        setDividerPositions(0.2);
        SplitPane.setResizableWithParent(side, false);
        refresh();
    }

    /** Opens this session's profile in a browser, at the address it was left on. */
    private void open(WebSessions.Session session) {
        close();
        open = session;
        browser = new BrowserPane(app, session.lastUrl().orElse("https://en.wikipedia.org/wiki/Main_Page"), session.profile());
        stage.getChildren().setAll(browser);
        status.setText("Opened " + session.name() + session.lastUrl().map(u -> ", where it was left").orElse(", a new one"));
        browser.onPage(page -> {
            page.locationProperty().addListener((p, before, url) -> session.remember(url));
            page.whenOpen(view -> view.subscribe(PageLoad.class, e -> {
                if (e.state() == PageLoad.State.FINISHED) Platform.runLater(() -> {
                    if (page == browser.page()) countVisit(view);
                    loadCookies();
                    refresh();
                });
            }));
        });
        refresh();
    }

    private void close() {
        if (browser == null) return;
        browser.close();
        browser = null;
        open = null;
        stage.getChildren().setAll(new Label("Closed. Open it again: its page and cookies come back from disk."));
        status.setText("Closed");
        cookies.clear();
        visits.setText("");
        refresh();
    }

    private void go(String url) {
        if (browser == null) open(sessions.create());
        browser.go(url);
    }

    /** localStorage too survives: the page counts how many times this session has loaded a page on this site. */
    private void countVisit(View view) {
        view.eval("(() => { const n = Number(localStorage.getItem('webview4j-visits') || 0) + 1;"
                + " localStorage.setItem('webview4j-visits', n); return location.host + ': visit ' + n + ' in this session (localStorage)'; })()")
                .thenAccept(text -> Platform.runLater(() -> visits.setText(String.valueOf(text))));
    }

    private void loadCookies() {
        if (browser == null) return;
        WebViewPane page = browser.page();
        page.view().thenCompose(view -> view.allCookies().exceptionallyCompose(e -> view.cookies(page.getLocation())))
                .thenAccept(found -> Platform.runLater(() -> {
                    cookies.setAll(found);
                    status.setText(found.size() + " cookies in " + (open == null ? "" : open.name()));
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> status.setText("Cookies: " + e.getMessage()));
                    return null;
                });
    }

    private void refresh() {
        WebSessions.Session picked = list.getSelectionModel().getSelectedItem();
        list.getItems().setAll(sessions.list());
        if (open != null) list.getSelectionModel().select(open);
        else if (picked != null) list.getSelectionModel().select(picked);
    }

    private static <T> TableColumn<T, String> column(String name, double width, java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(String.valueOf(value.apply(cell.getValue()))));
        return column;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
    }

}
