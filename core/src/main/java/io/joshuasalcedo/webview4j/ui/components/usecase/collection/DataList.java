package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import io.joshuasalcedo.webview4j.ui.components.Element;
import java.util.List;
import java.util.function.Consumer;

/**
 * A list of records, as the system's list: each item a title, a quieter line, an avatar, a pill, something after
 * it; a search box; items chosen, opened, a right-click menu; pages, or more loaded as the end comes near. The Java
 * side of the kit's {@code DataList}. The fields shown are named, so any record fits.
 * <pre>{@code
 * DataList.of("People", people, "name").subtitle("email").avatar("name").pill("role").trailing("lastSeen")
 *         .search().onOpen(id -> page.navigate("/people/" + id))
 * }</pre>
 */
public final class DataList extends CollectionView<DataList> {

    /** A list of {@code items} (records or maps), each titled by its {@code title} field. */
    public DataList(String label, List<?> items, String title) {
        super("dataList", label);
        prop("items", Rows.rows(items));
        prop("title", title);
    }

    public static DataList of(String label, List<?> items, String title) {
        return new DataList(label, items, title);
    }

    /** The field that tells items apart: "id" unless set. */
    public DataList itemKey(String key) { return prop("itemKey", key); }
    /** The field shown on a second, quieter line. */
    public DataList subtitle(String field) { return prop("subtitle", field); }
    /** The field whose initials (a name) or picture make the avatar before each item. */
    public DataList avatar(String field) { return prop("avatar", field); }
    /** The field shown as a pill. */
    public DataList pill(String field) { return prop("pill", field); }
    /** The field shown after the item: a time, a count. */
    public DataList trailing(String field) { return prop("trailing", field); }
    /** A search box: the items whose shown fields have the text. */
    public DataList search() { return prop("search", true); }
    /** Several items chosen, with Ctrl/⌘. */
    public DataList multiple() { return prop("multiple", true); }
    /** An item opened (double-click, Enter): its id. */
    public DataList onOpen(Consumer<String> action) { return on("onOpen", v -> action.accept(String.valueOf(v))); }
    /** An entry of every item's right-click menu: it gets the item's id. Call again for each. */
    public DataList menu(String label, Consumer<String> action) {
        return add(slot(new Element("menuItem").set("label", label).handle("onSelect", v -> action.accept(String.valueOf(v))), "menu"));
    }
    /** More to load as the end comes near: {@link #onLoadMore} adds them, the screen shows again. */
    public DataList hasMore(boolean more) { return prop("hasMore", more); }
    public DataList onLoadMore(Runnable action) { return on("onLoadMore", v -> action.run()); }

    /** The items shown (one page of them, with {@link #paging}, when the server answers the query). */
    public DataList items(List<?> items) {
        return prop("items", Rows.rows(items));
    }

    @Override
    DataList rowsOf(List<?> rows) {
        return items(rows);
    }
}
