package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records as cards in a grid that fills the width: a picture (or the initials of the title), a title, a quieter
 * line, a pill, a footnote; chosen, opened, a right-click menu. The Java side of the kit's {@code DataGrid}.
 * <pre>{@code
 * DataGrid.of("Products", products, "name").image("photoUrl").subtitle("category").pill("status").footer("price")
 * }</pre>
 */
public final class DataGrid extends Component<DataGrid> {

    public DataGrid(String label, List<?> items, String title) {
        super("dataGrid");
        prop("label", label);
        prop("items", Rows.rows(items));
        prop("title", title);
    }

    public static DataGrid of(String label, List<?> items, String title) {
        return new DataGrid(label, items, title);
    }

    public DataGrid itemKey(String key) { return prop("itemKey", key); }
    public DataGrid subtitle(String field) { return prop("subtitle", field); }
    /** The field with a picture's address. */
    public DataGrid image(String field) { return prop("image", field); }
    public DataGrid pill(String field) { return prop("pill", field); }
    public DataGrid footer(String field) { return prop("footer", field); }
    /** The narrowest a card is, CSS pixels: as many fit in a row as the width allows. */
    public DataGrid minCardWidth(double width) { return prop("minWidth", width); }
    public DataGrid multiple() { return prop("multiple", true); }
    public DataGrid selected(List<String> ids) { return prop("selected", List.copyOf(ids)); }
    public DataGrid onSelectionChange(Consumer<List<String>> action) { return on("onSelectionChange", v -> action.accept(Rows.texts(v))); }
    public DataGrid onOpen(Consumer<String> action) { return on("onOpen", v -> action.accept(String.valueOf(v))); }
    public DataGrid menu(String label, Consumer<String> action) {
        return add(slot(new Element("menuItem").set("label", label).handle("onSelect", v -> action.accept(String.valueOf(v))), "menu"));
    }
    public DataGrid empty(String text) { return prop("empty", text); }
}
