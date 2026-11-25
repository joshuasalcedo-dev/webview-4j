package io.joshuasalcedo.webview4j.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.WindowOptions;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;

/**
 * Several webviews side by side, in equal cells, row after row: dashboards, a wall of sites, the same page at once
 * in several states. Each cell has the page's title above it.
 *
 * <pre>{@code
 * WebViewGrid grid = new WebViewGrid(app, 2);
 * grid.add("https://example.com");
 * grid.add(WindowOptions.html("<h1>Hi</h1>"));
 * }</pre>
 */
public class WebViewGrid extends GridPane {

    private final App app;
    private final List<WebViewPane> panes = new ArrayList<>();
    private int columns;

    public WebViewGrid(App app, int columns) {
        this.app = Objects.requireNonNull(app, "app");
        getStyleClass().add("webview-grid");
        setHgap(8);
        setVgap(8);
        setPadding(new Insets(8));
        setColumns(columns);
    }

    public WebViewPane add(String url) {
        return add(WindowOptions.url(url));
    }

    public WebViewPane add(WindowOptions options) {
        WebViewPane pane = new WebViewPane(app, options);
        panes.add(pane);
        layoutCells();
        return pane;
    }

    /** Takes the pane out and closes its page. */
    public void remove(WebViewPane pane) {
        if (panes.remove(pane)) {
            pane.close();
            layoutCells();
        }
    }

    public List<WebViewPane> panes() {
        return List.copyOf(panes);
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        if (columns < 1) throw new IllegalArgumentException("a grid has at least one column, not " + columns);
        this.columns = columns;
        layoutCells();
    }

    private void layoutCells() {
        getChildren().clear();
        getColumnConstraints().clear();
        getRowConstraints().clear();
        int rows = Math.max(1, (panes.size() + columns - 1) / columns);
        for (int c = 0; c < columns; c++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            column.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(column);
        }
        for (int r = 0; r < rows; r++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(100.0 / rows);
            row.setVgrow(Priority.ALWAYS);
            getRowConstraints().add(row);
        }
        for (int i = 0; i < panes.size(); i++) {
            add(cell(panes.get(i)), i % columns, i / columns);
        }
    }

    private static Node cell(WebViewPane pane) {
        Label title = new Label();
        title.textProperty().bind(Bindings.createStringBinding(
                () -> pane.getTitle().isBlank() ? pane.getLocation() : pane.getTitle(),
                pane.titleProperty(), pane.locationProperty()));
        title.setPadding(new Insets(0, 0, 4, 2));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #3f3f46;");
        // A node is reused in one parent only: the cell wraps the same pane each time the grid lays out again.
        if (pane.getParent() instanceof BorderPane old) old.setCenter(null);
        BorderPane cell = new BorderPane(pane);
        cell.setTop(title);
        cell.getStyleClass().add("webview-grid-cell");
        return cell;
    }
}
