package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What every collection (a table, a list, a grid, a tree) has: rows chosen, the query and its pages, and what it
 * shows when there is nothing, while it loads, and when loading failed.
 *
 * <p><b>The query.</b> Without {@link #onQueryChange}, the collection pages, sorts, searches and filters by itself.
 * With it, each change is a {@link Query} sent to Java: answer it (one page of rows) and give its {@link Paging}; the
 * screen shows again with them. {@link Query#apply} answers one in memory.
 *
 * <p><b>The states.</b> {@link #loading} shows grey rows the first time and a running bar after (the rows stay, so
 * nothing jumps); {@link #error} says what went wrong, with Try again ({@link #onRetry}); {@link #empty} says there is
 * nothing yet, with what to do ({@link #emptyAction}). When the search or the filters hide everything, it says "No
 * matches", with Clear filters.
 */
public abstract class CollectionView<C extends CollectionView<C>> extends Component<C> {

    private final Map<String, Object> empty = new LinkedHashMap<>();

    protected CollectionView(String type, String label) {
        super(type);
        prop("label", label);
    }

    // ---- choosing ----

    public C selected(List<String> ids) {
        return prop("selected", List.copyOf(ids));
    }

    public C onSelectionChange(Consumer<List<String>> action) {
        return on("onSelectionChange", v -> action.accept(Rows.texts(v)));
    }

    // ---- the query and its pages ----

    /** Rows per page; 0 for all of them. */
    public C pageSize(int size) {
        return prop("pageSize", size);
    }

    /** The query shown: its page, order, search and filters. */
    public C query(Query query) {
        return prop("query", query.write());
    }

    /** Where the rows given are: the page, its size, how many there are in all (on the server). */
    public C paging(Paging paging) {
        return prop("paging", paging.write());
    }

    /** The server answers the query: each change (a page, an order, a search, a filter) comes here. */
    public C onQueryChange(Consumer<Query> action) {
        return on("onQueryChange", v -> action.accept(Query.read(v)));
    }

    /** One page of rows and where it is: the rows, and {@link #paging}. */
    public C page(Query.Result<?> result) {
        rowsOf(result.rows());
        return paging(result.paging());
    }

    abstract C rowsOf(List<?> rows);

    // ---- the states ----

    /** Loading: grey rows the first time, a running bar over the rows after. */
    public C loading(boolean loading) {
        return prop("loading", loading);
    }

    /** Loading failed: what went wrong, said to a person. */
    public C error(String title, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("title", title);
        if (message != null) error.put("message", message);
        return prop("error", error);
    }

    /** Try again, under the error. */
    public C onRetry(Runnable action) {
        return on("onRetry", v -> action.run());
    }

    /** Said when there is nothing at all. */
    public C empty(String title) {
        return empty(title, null);
    }

    /** Said when there is nothing at all: a title and a line about it ("No orders yet", "Orders you take show here"). */
    public C empty(String title, String message) {
        empty.put("title", title);
        if (message != null) empty.put("message", message); else empty.remove("message");
        return prop("emptyState", empty);
    }

    /** What to do when there is nothing: a button under the empty state ("New order"). */
    public C emptyAction(String label, Runnable action) {
        empty.putIfAbsent("title", "Nothing here");
        empty.put("action", label);
        prop("emptyState", empty);
        return on("onEmptyAction", v -> action.run());
    }
}
