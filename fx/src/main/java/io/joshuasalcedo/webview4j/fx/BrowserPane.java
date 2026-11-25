package io.joshuasalcedo.webview4j.fx;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Theme;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * A browser: tabs, an address bar, back, forward, reload and home, and for each tab the page's network, console and
 * clicks below it ({@link InspectorPane}, the Inspect button) and the engine's own developer tools (DevTools, or
 * right-click → Inspect). "Open in" hands the page to another browser on the computer. Links go to any website in
 * place ({@link WindowOptions#browse()}); no website can call the app.
 *
 * <p>Ctrl+T opens a tab, Ctrl+W closes one, Ctrl+L goes to the address bar, F5 reloads, F12 opens DevTools.
 */
public class BrowserPane extends BorderPane {

    private final App app;
    private final String home;
    private final Path profile;
    private final TabPane tabs = new TabPane();
    private final TextField address = new TextField();
    private final ToggleButton inspect = new ToggleButton("Inspect");
    private final ReadOnlyObjectWrapper<WebViewPane> current = new ReadOnlyObjectWrapper<>(this, "current");
    private final List<Consumer<WebViewPane>> onPage = new ArrayList<>();
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(this, "theme", Theme.SYSTEM);
    private final BooleanProperty scrollbars = new SimpleBooleanProperty(this, "scrollbars", true);

    /** A browser that keeps nothing between runs once closed... unless {@code profile} is given (see the other). */
    public BrowserPane(App app, String home) {
        this(app, home, null);
    }

    /**
     * A browser whose cookies, storage and signed-in sites are kept in {@code profile} (a folder; a
     * {@link WebSessions.Session#profile()}), shared by its tabs. Null: WebView2's default profile for the app.
     */
    public BrowserPane(App app, String home, Path profile) {
        this.app = Objects.requireNonNull(app, "app");
        this.home = Objects.requireNonNull(home, "home");
        this.profile = profile;
        getStyleClass().add("webview-browser");
        theme.addListener((p, before, now) -> pages().forEach(page -> page.setTheme(now)));

        Button back = button("←", "Back", () -> page().back());
        Button forward = button("→", "Forward", () -> page().forward());
        Button reload = button("⟳", "Reload (F5)", () -> page().reload());
        Button homeButton = button("⌂", "Home", () -> page().navigate(home));
        address.setPromptText("Search or type an address");
        address.setOnAction(e -> go(address.getText()));
        address.focusedProperty().addListener((p, before, focused) -> {
            if (focused) javafx.application.Platform.runLater(address::selectAll);
        });
        HBox.setHgrow(address, Priority.ALWAYS);
        ProgressIndicator loading = new ProgressIndicator();
        loading.setPrefSize(18, 18);
        Button newTab = button("+", "New tab (Ctrl+T)", () -> open(home));
        MenuButton openIn = openInMenu();
        Button devtools = button("DevTools", "The engine's developer tools (F12): Elements, Console, Network, Sources",
                () -> page().view().thenCompose(View::openDevTools));
        inspect.setTooltip(new Tooltip("The page's network, console and clicks, below it"));
        inspect.setSelected(true);
        inspect.selectedProperty().addListener((p, before, on) -> tabs.getTabs().forEach(this::showInspector));

        HBox bar = new HBox(6, back, forward, reload, homeButton, address, loading, newTab, openIn, devtools, inspect);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 8, 6, 8));
        bar.getStyleClass().add("webview-browser-bar");
        setTop(bar);

        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        setCenter(tabs);
        tabs.getSelectionModel().selectedItemProperty().addListener((p, before, tab) -> {
            WebViewPane page = tab == null ? null : ((BrowserTab) tab).page;
            current.set(page);
            address.setText(page == null ? "" : page.getLocation());
            loading.visibleProperty().unbind();
            if (page != null) loading.visibleProperty().bind(page.loadingProperty());
            else loading.setVisible(false);
        });
        tabs.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            if (tabs.getTabs().isEmpty()) open(home); // a browser always has a tab
        });

        sceneProperty().addListener((p, before, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN), () -> open(home));
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeTab);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), address::requestFocus);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), () -> page().reload());
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F12), () -> page().view().thenCompose(View::openDevTools));
        });
        open(home);
    }

    /** Opens {@code url} in a new tab, and shows it. */
    public WebViewPane open(String url) {
        WindowOptions options = WindowOptions.url(url).browse().devtools(true);
        if (profile != null) options.dataDirectory(profile);
        WebViewPane page = new WebViewPane(app, options);
        page.setTheme(theme.get());
        page.scrollbarsProperty().bind(scrollbars);
        BrowserTab tab = new BrowserTab(page);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        for (Consumer<WebViewPane> action : List.copyOf(onPage)) action.accept(tab.page);
        return tab.page;
    }

    /** Light or dark for every tab's page ({@code prefers-color-scheme}), and for the tabs opened later. */
    public ObjectProperty<Theme> themeProperty() { return theme; }
    public void setTheme(Theme value) { theme.set(value); }

    /** False hides every tab's scrollbars; the pages still scroll. */
    public BooleanProperty scrollbarsProperty() { return scrollbars; }
    public void setScrollbars(boolean on) { scrollbars.set(on); }

    /** Goes to this address in the tab shown; a bare name gets {@code https://}, words a search. */
    public void go(String text) {
        String url = Addresses.of(text);
        if (url != null) page().navigate(url);
    }

    /** The page in the tab shown. */
    public WebViewPane page() {
        return current.get();
    }

    public ReadOnlyObjectProperty<WebViewPane> pageProperty() {
        return current.getReadOnlyProperty();
    }

    /** Every tab's page, in order. */
    public List<WebViewPane> pages() {
        return tabs.getTabs().stream().map(tab -> ((BrowserTab) tab).page).toList();
    }

    /** Runs {@code action} for each page this browser opens from now on, and for those it has open already. */
    public void onPage(Consumer<WebViewPane> action) {
        onPage.add(Objects.requireNonNull(action, "action"));
        pages().forEach(action);
    }

    /** The inspector of the tab shown. */
    public InspectorPane inspector() {
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        return tab == null ? null : ((BrowserTab) tab).inspector;
    }

    /** Closes every tab's page: for good, unlike closing tabs (a new one opens). */
    public void close() {
        for (WebViewPane page : pages()) page.close();
    }


    private void closeTab() {
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        if (tab != null) {
            ((BrowserTab) tab).page.close();
            tabs.getTabs().remove(tab);
        }
    }

    private MenuButton openInMenu() {
        MenuButton menu = new MenuButton("Open in");
        menu.setTooltip(new Tooltip("Open this page in another browser"));
        menu.setOnShowing(e -> {
            menu.getItems().clear();
            MenuItem system = new MenuItem("The default browser");
            system.setOnAction(a -> ExternalBrowsers.openInDefault(page().getLocation()));
            menu.getItems().addAll(system, new SeparatorMenuItem());
            for (ExternalBrowsers.Browser browser : ExternalBrowsers.installed()) {
                MenuItem item = new MenuItem(browser.name());
                item.setOnAction(a -> browser.open(page().getLocation()));
                menu.getItems().add(item);
            }
            MenuItem copy = new MenuItem("Copy the address");
            copy.setOnAction(a -> {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(page().getLocation());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            });
            menu.getItems().addAll(new SeparatorMenuItem(), copy);
        });
        menu.getItems().add(new MenuItem("…")); // a menu with no items does not open
        return menu;
    }

    private void showInspector(Tab tab) {
        BrowserTab browserTab = (BrowserTab) tab;
        SplitPane split = browserTab.split;
        if (inspect.isSelected() && !split.getItems().contains(browserTab.inspector)) {
            split.getItems().add(browserTab.inspector);
            split.setDividerPositions(0.64);
        } else if (!inspect.isSelected()) {
            split.getItems().remove(browserTab.inspector);
        }
    }

    private static Button button(String text, String tip, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
        return button;
    }

    /** One tab: its page, and the page's inspector below it. */
    private final class BrowserTab extends Tab {
        final WebViewPane page;
        final InspectorPane inspector = new InspectorPane();
        final SplitPane split;

        BrowserTab(WebViewPane page) {
            this.page = page;
            inspector.attach(page);
            split = new SplitPane(page);
            split.setOrientation(Orientation.VERTICAL);
            setContent(split);
            showInspector(this);
            textProperty().bind(Bindings.createStringBinding(() -> {
                String title = page.getTitle().isBlank() ? page.getLocation() : page.getTitle();
                if (title.isBlank()) title = "New tab";
                return title.length() > 28 ? title.substring(0, 27) + "…" : title;
            }, page.titleProperty(), page.locationProperty()));
            setTooltip(new Tooltip());
            getTooltip().textProperty().bind(page.locationProperty());
            setOnClosed(e -> page.close());
            page.locationProperty().addListener((p, before, url) -> {
                if (page == page() && !address.isFocused()) address.setText(url);
            });
        }
    }
}
