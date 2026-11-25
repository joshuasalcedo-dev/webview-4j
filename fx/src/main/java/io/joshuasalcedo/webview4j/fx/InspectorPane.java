package io.joshuasalcedo.webview4j.fx;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.joshuasalcedo.webview4j.Capture;
import io.joshuasalcedo.webview4j.Click;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.LogRecord;
import io.joshuasalcedo.webview4j.NetworkEvent;
import io.joshuasalcedo.webview4j.PageLoad;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WebSocketFrame;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * What a {@link WebViewPane}'s page does, live, in JavaFX tables: its network (every fetch and XMLHttpRequest with
 * headers and bodies, and every picture, script, style and font it loads), its console, and the user's clicks and
 * WebSocket frames. The same rows are lists to read or listen to from Java: {@link #network()}, {@link #console()},
 * {@link #events()}.
 *
 * <pre>{@code
 * InspectorPane inspector = new InspectorPane();
 * inspector.attach(web);
 * inspector.network().addListener((ListChangeListener<InspectorPane.Request>) change -> ...);
 * }</pre>
 */
public class InspectorPane extends TabPane {

    /** Rows kept per list: the oldest go first. */
    public static final int LIMIT = 5_000;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ObservableList<Request> network = FXCollections.observableArrayList();
    private final ObservableList<LogRecord> console = FXCollections.observableArrayList();
    private final ObservableList<Entry> events = FXCollections.observableArrayList();
    /** Requests still waiting for their answer, by the page's id for them. */
    private final Map<String, Request> pending = new HashMap<>();
    private final TextArea detail = new TextArea();
    /** The page the console's scripts run in: the one attached last. */
    private View current;

    public InspectorPane() {
        getStyleClass().add("webview-inspector");
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getTabs().addAll(
                new Tab("Network", networkTab()),
                new Tab("Console", consoleTab()),
                new Tab("Events", table(events,
                        column("Time", 100, Entry::time),
                        column("Kind", 90, Entry::kind),
                        column("What", 600, Entry::text))));
    }

    /** Shows what this pane's page does, from now on (and after it reopens): switches its reports on. */
    public void attach(WebViewPane pane) {
        Objects.requireNonNull(pane, "pane").whenOpen(this::attach);
    }

    /** Shows what this view's page does, from now on: switches its reports on. */
    public void attach(View view) {
        Platform.runLater(() -> current = view);
        view.capture(reports());
        view.subscribe(NetworkEvent.class, e -> Platform.runLater(() -> network(view, e)));
        view.subscribe(LogRecord.class, e -> Platform.runLater(() -> add(console, e)));
        view.subscribe(Click.class, e -> Platform.runLater(() -> add(events, new Entry(now(), "click",
                e.tag() + " " + e.selector() + (e.text() == null || e.text().isBlank() ? "" : "  \"" + e.text().strip() + "\"")))));
        view.subscribe(WebSocketFrame.class, e -> Platform.runLater(() -> add(events, new Entry(now(), "websocket",
                e.direction() + " " + e.url() + "  " + e.data()))));
        view.subscribe(PageLoad.class, e -> Platform.runLater(() -> add(events, new Entry(now(), "page",
                e.state().name().toLowerCase() + " " + e.url()))));
    }

    /** The page's requests, a row each, updated as they answer. */
    public ObservableList<Request> network() { return network; }

    /** The page's console and uncaught errors. */
    public ObservableList<LogRecord> console() { return console; }

    /** Clicks, WebSocket frames and page loads. */
    public ObservableList<Entry> events() { return events; }

    public void clear() {
        network.clear();
        console.clear();
        events.clear();
        pending.clear();
        detail.clear();
    }

    // ---- the rows ----

    /** One request of the page's. Its status, time and answer fill in when it answers. */
    public static final class Request {
        private final String time;
        private final String kind;
        private final String method;
        private final String url;
        private final SimpleObjectProperty<NetworkEvent> last = new SimpleObjectProperty<>();
        private final StringProperty status = new SimpleStringProperty("…");
        private final StringProperty duration = new SimpleStringProperty("");
        private final StringProperty size = new SimpleStringProperty("");
        private NetworkEvent start;

        Request(String time, NetworkEvent event) {
            this.time = time;
            this.kind = event.kind() == null ? "" : event.kind();
            this.method = event.method() == null ? "GET" : event.method();
            this.url = event.url() == null ? "" : event.url();
            if (event.phase() == NetworkEvent.Phase.START) start = event;
            update(event);
        }

        void update(NetworkEvent event) {
            last.set(event);
            switch (event.phase()) {
                case START -> status.set("…");
                case ERROR -> status.set(event.error() == null ? "failed" : event.error());
                case END -> status.set(String.valueOf(event.status()));
                case RESOURCE -> status.set(event.status() == 0 ? "" : String.valueOf(event.status()));
            }
            if (event.phase() != NetworkEvent.Phase.START) {
                duration.set(event.durationMs() + " ms");
                size.set(Bytes.of(event.size()));
            }
        }

        public String time() { return time; }
        public String kind() { return kind; }
        public String method() { return method; }
        public String url() { return url; }
        /** The latest step: START, then END or ERROR; or a RESOURCE alone. */
        public NetworkEvent event() { return last.get(); }
        public ObservableValue<String> statusProperty() { return status; }
        public ObservableValue<String> durationProperty() { return duration; }
        public ObservableValue<String> sizeProperty() { return size; }

        /** Headers and bodies, as text. */
        public String describe() {
            NetworkEvent end = last.get();
            StringBuilder out = new StringBuilder(method).append(' ').append(url).append('\n');
            if (end != null && end.phase() != NetworkEvent.Phase.START) {
                out.append(status.get()).append(end.statusText() == null ? "" : " " + end.statusText())
                        .append("  ").append(duration.get()).append("  ").append(size.get())
                        .append(end.mimeType() == null ? "" : "  " + end.mimeType()).append('\n');
            }
            NetworkEvent request = start != null ? start : end;
            if (request != null) {
                headers(out, "Request headers", request.requestHeaders());
                body(out, "Request body", request.requestBody());
            }
            if (end != null) {
                headers(out, "Response headers", end.responseHeaders());
                body(out, "Response body", end.responseBody());
            }
            return out.toString();
        }

        private static void headers(StringBuilder out, String title, Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) return;
            out.append('\n').append(title).append('\n');
            headers.forEach((name, value) -> out.append("  ").append(name).append(": ").append(value).append('\n'));
        }

        private static void body(StringBuilder out, String title, String body) {
            if (body == null || body.isEmpty()) return;
            out.append('\n').append(title).append('\n').append(body).append('\n');
        }
    }

    /** A click, a WebSocket frame or a page load. */
    public record Entry(String time, String kind, String text) {}

    // ---- inside ----

    private void network(View view, NetworkEvent event) {
        String key = view.id() + "/" + event.id();
        switch (event.phase()) {
            case START -> {
                Request request = new Request(now(), event);
                pending.put(key, request);
                add(network, request);
            }
            case END, ERROR -> {
                Request request = pending.remove(key);
                if (request != null) request.update(event);
                else add(network, new Request(now(), event));
            }
            case RESOURCE -> add(network, new Request(event.startedAt() > 0 ? clock(event.startedAt()) : now(), event));
        }
    }

    /**
     * Runs JavaScript in the page, as a browser's console does: the script and its value (or its error) join the
     * console, beside the page's own {@code console.*} and uncaught errors. A promise's value is not awaited.
     */
    public CompletableFuture<Object> run(String script) {
        Objects.requireNonNull(script, "script");
        View view = current;
        if (view == null) return CompletableFuture.failedFuture(new IllegalStateException("no page is attached yet"));
        add(console, new LogRecord(LogRecord.Source.PAGE, view.id(), LogRecord.Level.INFO, "›", script.strip()));
        return view.eval(script).whenComplete((value, error) -> Platform.runLater(() -> add(console, error == null
                ? new LogRecord(LogRecord.Source.PAGE, view.id(), LogRecord.Level.INFO, "‹", Json.write(value))
                : new LogRecord(LogRecord.Source.PAGE, view.id(), LogRecord.Level.ERROR, "‹", message(error)))));
    }

    private BorderPane consoleTab() {
        TableView<LogRecord> table = table(console,
                column("Level", 70, r -> r.level().name()),
                column("", 30, r -> r.target() == null ? "" : r.target()),
                column("Message", 700, LogRecord::message));
        table.setRowFactory(t -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(LogRecord record, boolean empty) {
                super.updateItem(record, empty);
                setStyle(empty || record == null ? "" : switch (record.level()) {
                    case ERROR -> "-fx-background-color: #fef2f2;";
                    case WARN -> "-fx-background-color: #fffbeb;";
                    default -> "";
                });
            }
        });
        console.addListener((javafx.collections.ListChangeListener<LogRecord>) change -> {
            if (!console.isEmpty()) table.scrollTo(console.size() - 1);
        });

        TextArea script = new TextArea();
        script.setPromptText("JavaScript to run in the page, e.g. document.title   (Ctrl+Enter runs it)");
        script.setPrefRowCount(3);
        script.setStyle("-fx-font-family: 'Consolas', monospace;");
        HBox.setHgrow(script, Priority.ALWAYS);
        Runnable runIt = () -> {
            if (!script.getText().isBlank()) run(script.getText());
        };
        script.setOnKeyPressed(key -> {
            if (key.getCode() == javafx.scene.input.KeyCode.ENTER && key.isShortcutDown()) {
                runIt.run();
                key.consume();
            }
        });
        Button runButton = new Button("Run");
        runButton.setDefaultButton(false);
        runButton.setOnAction(e -> runIt.run());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> console.clear());
        javafx.scene.layout.VBox buttons = new javafx.scene.layout.VBox(6, runButton, clear);
        runButton.setMaxWidth(Double.MAX_VALUE);
        clear.setMaxWidth(Double.MAX_VALUE);
        HBox input = new HBox(6, script, buttons);
        input.setPadding(new Insets(6));

        BorderPane tab = new BorderPane(table);
        tab.setBottom(input);
        return tab;
    }

    private static String message(Throwable error) {
        Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private BorderPane networkTab() {
        TextField filter = new TextField();
        filter.setPromptText("Filter by address, method or kind");
        HBox.setHgrow(filter, Priority.ALWAYS);
        FilteredList<Request> shown = new FilteredList<>(network);
        filter.textProperty().addListener((p, before, text) -> shown.setPredicate(text == null || text.isBlank() ? null
                : r -> (r.method() + " " + r.url() + " " + r.kind()).toLowerCase().contains(text.toLowerCase())));
        Label count = new Label();
        count.textProperty().bind(javafx.beans.binding.Bindings.size(shown).asString("%d requests"));
        Button clear = new Button("Clear");
        clear.setOnAction(e -> clear());
        HBox bar = new HBox(8, filter, count, clear);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6));

        TableView<Request> table = table(shown,
                column("Time", 95, Request::time),
                column("Method", 70, Request::method),
                observed("Status", 70, Request::statusProperty),
                column("Kind", 80, Request::kind),
                column("Address", 520, Request::url),
                observed("Time taken", 90, Request::durationProperty),
                observed("Size", 80, Request::sizeProperty));
        table.getSelectionModel().selectedItemProperty().addListener((p, before, request) ->
                detail.setText(request == null ? "" : request.describe()));
        detail.setEditable(false);
        detail.setWrapText(true);
        detail.setPromptText("Pick a request to see its headers and bodies");
        detail.setStyle("-fx-font-family: 'Consolas', monospace;");
        SplitPane split = new SplitPane(table, detail);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.65);
        BorderPane tab = new BorderPane(split);
        tab.setTop(bar);
        return tab;
    }

    @SafeVarargs
    private static <T> TableView<T> table(ObservableList<T> rows, TableColumn<T, String>... columns) {
        TableView<T> table = new TableView<>(rows);
        table.getColumns().addAll(columns);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Nothing yet"));
        return table;
    }

    private static <T> TableColumn<T, String> column(String name, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(String.valueOf(value.apply(cell.getValue()))));
        return column;
    }

    private static <T> TableColumn<T, String> observed(String name, double width, Function<T, ObservableValue<String>> value) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> value.apply(cell.getValue()));
        return column;
    }

    private static <T> void add(ObservableList<T> list, T row) {
        if (list.size() >= LIMIT) list.remove(0, list.size() - LIMIT + 1);
        list.add(row);
    }

    private static String now() {
        return LocalTime.now().format(CLOCK);
    }

    private static String clock(long epochMillis) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(CLOCK);
    }


    /** The reports {@link #attach} switches on. */
    static Set<Capture> reports() {
        return EnumSet.of(Capture.NETWORK, Capture.CONSOLE, Capture.CLICKS, Capture.WEBSOCKET);
    }
}
