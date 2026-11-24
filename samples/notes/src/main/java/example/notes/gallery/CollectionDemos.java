package example.notes.gallery;

import static example.notes.gallery.Demos.ordered;
import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.valueobject.financial.FiatCurrency;
import io.joshuasalcedo.valueobject.financial.Money;
import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.DataGrid;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.DataList;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.DataMap;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.DataTable;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.DataTree;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.Query;
import io.joshuasalcedo.webview4j.ui.components.usecase.mdi.Mdi;
import io.joshuasalcedo.webview4j.ui.components.usecase.mdi.MdiWindow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The gallery's pages: The collections (table, list, tree, map, grid) with their query, pages and states, and the MDI area. */
final class CollectionDemos implements Demos {

    enum Status { NEW, PACKED, SHIPPED, DELIVERED }

    record Order(String id, String customer, int items, Money total, LocalDate placed, Status status, boolean paid) {}

    record Person(String id, String name, String email, String role, String lastSeen) {}

    record Folder(String id, String name, int files, LocalDate modified, Boolean hasChildren, List<Folder> children) {}

    record Product(String id, String name, String category, String status, String price) {}

    private static final String[] CUSTOMERS = {"Ada Lovelace", "Grace Hopper", "Alan Turing", "Katherine Johnson",
            "Linus Torvalds", "Margaret Hamilton", "Dennis Ritchie", "Barbara Liskov", "Ken Thompson", "Radia Perlman"};

    /** The "server's" rows: a hundred orders, always the same. */
    private static final List<Order> ALL_ORDERS = generate(100);

    // ---- Data table ----
    private final List<Order> orders = new ArrayList<>(generate(14));
    private List<String> chosen = List.of();
    private String tableSaid = "Shift-click headers to sort by more than one column; double-click a customer to edit it.";

    // ---- Server query ----
    private Query query = Query.first(10);
    private int answered;

    // ---- States ----
    private String state = "rows";
    private boolean loadingOverRows;
    private int retries;

    // ---- Data list ----
    private String listSaid = "Double-click a person to open them.";

    // ---- Data tree ----
    private final Map<String, List<Folder>> loaded = new HashMap<>();
    private List<String> expanded = List.of("home");
    private String treeSaid = "Folders load their children when first opened.";

    // ---- Data map ----
    private final Map<String, Object> settings = initialSettings();
    private String mapSaid = "Edit a value, add or remove an entry.";

    // ---- Data grid ----
    private List<String> picked = List.of();
    private String gridSaid = "Click a card to choose it, Ctrl-click for more.";

    // ---- MDI ----
    private final Set<String> open = new LinkedHashSet<>(List.of("notes", "counter", "orders"));
    private int counter;

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Collections", "Data table", () -> column(
                DataTable.of("Orders", orders)
                        .header("id", "Order")
                        .filterable()
                        .editable("customer")
                        .onCellEdit(edit -> {
                            replace(edit.id(), o -> new Order(o.id(), edit.value(), o.items(), o.total(), o.placed(), o.status(), o.paid()));
                            tableSaid = "Order " + edit.id() + ": customer is now " + edit.value();
                        })
                        .selectable().selected(chosen).onSelectionChange(ids -> chosen = ids)
                        .action("Mark paid", ids -> {
                            ids.forEach(id -> replace(id, o -> new Order(o.id(), o.customer(), o.items(), o.total(), o.placed(), o.status(), true)));
                            tableSaid = "Marked " + ids.size() + " paid";
                        })
                        .action("Delete", ids -> {
                            orders.removeIf(o -> ids.contains(o.id()));
                            chosen = List.of();
                            tableSaid = "Deleted " + ids.size();
                        })
                        .rowMenu("Ship", id -> {
                            replace(id, o -> new Order(o.id(), o.customer(), o.items(), o.total(), o.placed(), Status.SHIPPED, o.paid()));
                            tableSaid = "Shipped " + id;
                        })
                        .rowMenu("Delete", id -> {
                            orders.removeIf(o -> o.id().equals(id));
                            tableSaid = "Deleted " + id;
                        })
                        .onRowOpen(id -> tableSaid = "Opened " + id)
                        .search().pageSize(8)
                        .height(460),
                text(chosen.size() + " chosen · " + tableSaid).tone(Tone.SECONDARY)),
                """
                record Order(String id, String customer, int items, Money total, LocalDate placed,
                             Status status, boolean paid) {}

                DataTable.of("Orders", orders)            // a column per component, typed from it
                        .filterable()                     // a filter under every column
                        .editable("customer").onCellEdit(edit -> rename(edit.id(), edit.value()))
                        .selectable().selected(chosen).onSelectionChange(ids -> chosen = ids)
                        .action("Mark paid", ids -> markPaid(ids))
                        .action("Delete", ids -> delete(ids))
                        .rowMenu("Ship", id -> ship(id))
                        .onRowOpen(id -> open(id))
                        .search().pageSize(8)
                        .height(460)""",
                DataTable.class);

        gallery.page("Collections", "Server query", () -> {
            Query.Result<Order> result = query.apply(ALL_ORDERS);
            return column(
                    DataTable.of("Orders on the server", result.rows().isEmpty() ? ALL_ORDERS.subList(0, 1) : result.rows())
                            .header("id", "Order")
                            .filterable("customer").filterable("total").filterable("placed")
                            .filterable("status", Status.class).filterable("paid")
                            .search()
                            .query(query).result(result)
                            .onQueryChange(q -> {
                                query = q;
                                answered++;
                            })
                            .height(500),
                    text("Answered " + answered + " queries · " + query + " · " + result.paging().total() + " match, page "
                            + result.paging().page() + " of " + result.paging().pages()).tone(Tone.SECONDARY));
        },
                """
                private Query query = Query.first(10);

                Query.Result<Order> result = query.apply(allOrders);   // the server: filter, search, sort, cut a page
                DataTable.of("Orders on the server", result.rows())
                        .filterable("customer").filterable("total").filterable("placed")
                        .filterable("status", Status.class).filterable("paid")
                        .search()
                        .query(query).result(result)              // the rows and their Paging
                        .onQueryChange(q -> query = q)          // each page, sort, search, filter comes here""",
                DataTable.class);

        gallery.page("Collections", "States", () -> {
            DataTable table = DataTable.of("Orders", ALL_ORDERS.subList(0, 8)).height(380);
            switch (state) {
                case "loading" -> table.rows(List.of()).loading(true);
                case "error" -> table.rows(List.of())
                        .error("The orders could not be loaded", "The server did not answer in time (" + retries + " tries so far).")
                        .onRetry(() -> {
                            retries++;
                            state = "rows";
                        });
                case "empty" -> table.rows(List.of())
                        .empty("No orders yet", "Orders people place show here.")
                        .emptyAction("New order", () -> state = "rows");
                default -> table.loading(loadingOverRows);
            }
            return column(
                    row(segmented("State", ordered("rows", "Rows", "loading", "Loading", "error", "Error", "empty", "Empty"))
                                    .value(state).onChange(v -> state = v),
                            toggle("Loading over the rows").checked(loadingOverRows).onChange(v -> loadingOverRows = v)
                                    .disabled(!state.equals("rows"))),
                    table);
        },
                """
                switch (state) {
                    case "loading" -> table.loading(true);         // grey rows the first time
                    case "error" -> table.error("The orders could not be loaded", "The server did not answer in time.")
                            .onRetry(() -> state = "rows");
                    case "empty" -> table.rows(List.of()).empty("No orders yet", "Orders people place show here.")
                            .emptyAction("New order", () -> state = "rows");
                    default -> table.loading(loadingOverRows);    // a running bar over the rows
                }""",
                DataTable.class, Segmented.class, Toggle.class);

        gallery.page("Collections", "Data list", () -> column(
                DataList.of("People", people(), "name")
                        .subtitle("email").avatar("name").pill("role").trailing("lastSeen")
                        .search().multiple()
                        .onOpen(id -> listSaid = "Opened " + id)
                        .menu("Message", id -> listSaid = "Messaging " + id)
                        .menu("Remove", id -> listSaid = "Removed " + id)
                        .height(420),
                text(listSaid).tone(Tone.SECONDARY)),
                """
                DataList.of("People", people, "name")
                        .subtitle("email").avatar("name").pill("role").trailing("lastSeen")
                        .search().multiple()
                        .onOpen(id -> open(id))
                        .menu("Message", id -> message(id))""",
                DataList.class);

        gallery.page("Collections", "Data tree", () -> column(
                DataTree.of("Files", List.of(folder("home", "home", 3, true), folder("srv", "srv", 1, true),
                                folder("etc", "etc", 42, false)).stream().map(this::withLoaded).toList())
                        .expanded(expanded).onExpandedChange(ids -> expanded = ids)
                        .onExpand(id -> {
                            loaded.put(id, childrenOf(id));
                            treeSaid = "Loaded the children of " + id;
                        })
                        .selectable()
                        .onRowOpen(id -> treeSaid = "Opened " + id)
                        .menu("Rename", id -> treeSaid = "Renaming " + id)
                        .height(420),
                text(treeSaid).tone(Tone.SECONDARY)),
                """
                record Folder(String id, String name, int files, LocalDate modified,
                              Boolean hasChildren, List<Folder> children) {}  // children null: not loaded yet

                DataTree.of("Files", roots)
                        .expanded(expanded).onExpandedChange(ids -> expanded = ids)
                        .onExpand(id -> loaded.put(id, server.childrenOf(id)))  // drawn again with them
                        .selectable()
                        .onRowOpen(id -> open(id))""",
                DataTree.class);

        gallery.page("Collections", "Data map", () -> column(
                DataMap.of("Settings", settings).open(2)
                        .onEdit(edit -> {
                            set(settings, edit.path(), edit.value());
                            mapSaid = edit.path() + " = " + edit.value();
                        })
                        .onAdd(entry -> {
                            settings.put(entry.key(), entry.value());
                            mapSaid = "Added " + entry.key();
                        })
                        .onRemove(path -> {
                            remove(settings, path);
                            mapSaid = "Removed " + path;
                        })
                        .height(420),
                text(mapSaid).tone(Tone.SECONDARY)),
                """
                DataMap.of("Settings", settings).open(2)
                        .onEdit(edit -> set(settings, edit.path(), edit.value()))   // "database.pool.size"
                        .onAdd(entry -> settings.put(entry.key(), entry.value()))
                        .onRemove(path -> remove(settings, path))""",
                DataMap.class);

        gallery.page("Collections", "Data grid", () -> column(
                DataGrid.of("Products", products(), "name")
                        .subtitle("category").pill("status").footer("price")
                        .minCardWidth(180).multiple()
                        .selected(picked).onSelectionChange(ids -> picked = ids)
                        .onOpen(id -> gridSaid = "Opened " + id)
                        .menu("Edit", id -> gridSaid = "Editing " + id)
                        .menu("Archive", id -> gridSaid = "Archived " + id)
                        .height(420),
                text(picked.size() + " chosen · " + gridSaid).tone(Tone.SECONDARY)),
                """
                DataGrid.of("Products", products, "name")
                        .subtitle("category").pill("status").footer("price")
                        .minCardWidth(180).multiple()
                        .selected(picked).onSelectionChange(ids -> picked = ids)
                        .onOpen(id -> open(id))
                        .menu("Edit", id -> edit(id))""",
                DataGrid.class);

        gallery.page("MDI", "Windows", () -> {
            List<MdiWindow> windows = new ArrayList<>();
            if (open.contains("notes")) windows.add(mdiWindow("notes", "Notes",
                    text("Drag a window by its title, resize it from a corner, minimize or maximize it."),
                    text("Closing one removes it here in Java; Reopen all brings them back.").tone(Tone.SECONDARY))
                    .bounds(16, 16, 320, 180).onClose(() -> open.remove("notes")));
            if (open.contains("counter")) windows.add(mdiWindow("counter", "Counter",
                    text("Clicked " + counter + " times"),
                    button("Click").accent().onClick(() -> counter++))
                    .bounds(360, 40, 260, 160).onClose(() -> open.remove("counter")));
            if (open.contains("orders")) windows.add(mdiWindow("orders", "Recent orders",
                    DataList.of("Recent orders", ALL_ORDERS.subList(0, 6), "customer").subtitle("placed").pill("status"))
                    .bounds(120, 220, 380, 240).onClose(() -> open.remove("orders")));
            return column(
                    row(button("Reopen all").onClick(() -> open.addAll(List.of("notes", "counter", "orders"))),
                            text(open.size() + " open").tone(Tone.SECONDARY)),
                    mdi("Workspace", windows.toArray(MdiWindow[]::new)).empty("Every window is closed").height(500)
                            .arrangeButtons()             // Cascade and Tile
                            .remember("gallery-workspace"));   // where the windows are, kept between runs
        },
                """
                mdi("Workspace",
                        mdiWindow("notes", "Notes", text("Drag a window by its title..."))
                                .bounds(16, 16, 320, 180).onClose(() -> open.remove("notes")),
                        mdiWindow("counter", "Counter", text("Clicked " + counter + " times"),
                                button("Click").accent().onClick(() -> counter++))
                                .bounds(360, 40, 260, 160),
                        mdiWindow("orders", "Recent orders", DataList.of("Recent orders", orders, "customer"))
                                .bounds(120, 220, 380, 240))
                        .empty("Every window is closed").height(500)
                        .arrangeButtons()                 // Cascade and Tile in the corner
                        .remember("workspace")            // opens as it was left, next run too
                // any component in a window: a table, a form, a GridPane, a SplitPane""",
                Mdi.class, MdiWindow.class, DataList.class, Button.class);
    }

    // ---- data ----

    private static List<Order> generate(int count) {
        List<Order> out = new ArrayList<>(count);
        Status[] statuses = Status.values();
        for (int i = 1; i <= count; i++) {
            BigDecimal amount = BigDecimal.valueOf((i * 7919L) % 50000 + 250, 2);
            out.add(new Order(String.format("A-%03d", i), CUSTOMERS[(i * 7) % CUSTOMERS.length], 1 + (i * 3) % 9,
                    Money.of(amount, i % 5 == 0 ? FiatCurrency.USD : FiatCurrency.PHP),
                    LocalDate.of(2026, 1, 1).plusDays((i * 37L) % 330), statuses[(i * 5) % statuses.length], i % 3 != 0));
        }
        return out;
    }

    private void replace(String id, java.util.function.UnaryOperator<Order> change) {
        orders.replaceAll(o -> o.id().equals(id) ? change.apply(o) : o);
    }

    private static List<Person> people() {
        String[] roles = {"Admin", "Editor", "Viewer"};
        String[] seen = {"now", "5 min", "1 h", "yesterday", "3 days"};
        List<Person> out = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS.length; i++) {
            String name = CUSTOMERS[i];
            out.add(new Person("p" + i, name, name.toLowerCase().replace(' ', '.') + "@example.com", roles[i % roles.length],
                    seen[i % seen.length]));
        }
        return out;
    }

    private static Folder folder(String id, String name, int files, boolean hasChildren) {
        return new Folder(id, name, files, LocalDate.of(2026, 11, 1).minusDays(id.length() * 3L), hasChildren, null);
    }

    /** A folder with the children loaded so far (null while not loaded: the tree asks for them). */
    private Folder withLoaded(Folder f) {
        List<Folder> kids = loaded.get(f.id());
        return kids == null ? f : new Folder(f.id(), f.name(), f.files(), f.modified(), f.hasChildren(),
                kids.stream().map(this::withLoaded).toList());
    }

    /** What the "server" says is inside a folder. */
    private static List<Folder> childrenOf(String id) {
        return switch (id) {
            case "home" -> List.of(folder("home/ada", "ada", 12, true), folder("home/grace", "grace", 7, true));
            case "srv" -> List.of(folder("srv/www", "www", 120, false), folder("srv/backups", "backups", 3, true));
            default -> id.split("/").length < 4
                    ? List.of(folder(id + "/docs", "docs", 9, true), folder(id + "/photos", "photos", 230, false))
                    : List.of(folder(id + "/old", "old", 1, false));
        };
    }

    private static List<Product> products() {
        return List.of(
                new Product("kb", "Mechanical keyboard", "Peripherals", "In stock", "₱4,990.00"),
                new Product("mouse", "Wireless mouse", "Peripherals", "In stock", "₱1,250.00"),
                new Product("monitor", "27\" 4K monitor", "Displays", "Low stock", "₱18,500.00"),
                new Product("dock", "USB-C dock", "Accessories", "Sold out", "₱6,200.00"),
                new Product("chair", "Ergonomic chair", "Furniture", "In stock", "₱14,000.00"),
                new Product("lamp", "Desk lamp", "Furniture", "Preorder", "₱2,100.00"),
                new Product("cam", "Webcam 1080p", "Peripherals", "In stock", "₱3,450.00"),
                new Product("hub", "Network switch", "Networking", "Low stock", "₱5,800.00"));
    }

    private static Map<String, Object> initialSettings() {
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("size", 10);
        pool.put("timeoutSeconds", 30);
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/notes");
        database.put("user", "notes");
        database.put("pool", pool);
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("width", 1200);
        window.put("height", 800);
        window.put("remember", true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "Notes");
        out.put("darkMode", false);
        out.put("database", database);
        out.put("window", window);
        out.put("tags", new ArrayList<>(List.of("desktop", "java", "react")));
        return out;
    }

    /** Sets the value at a path ("database.pool.size", "tags.0") in nested maps and lists. */
    @SuppressWarnings("unchecked")
    private static void set(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Object at = root;
        for (int i = 0; i < parts.length - 1 && at != null; i++) at = child(at, parts[i]);
        String last = parts[parts.length - 1];
        if (at instanceof Map<?, ?> m) ((Map<String, Object>) m).put(last, value);
        else if (at instanceof List<?> l && isIndex(last, l.size())) ((List<Object>) l).set(Integer.parseInt(last), value);
    }

    private static void remove(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object at = root;
        for (int i = 0; i < parts.length - 1 && at != null; i++) at = child(at, parts[i]);
        String last = parts[parts.length - 1];
        if (at instanceof Map<?, ?> m) m.remove(last);
        else if (at instanceof List<?> l && isIndex(last, l.size())) l.remove(Integer.parseInt(last));
    }

    private static Object child(Object at, String part) {
        if (at instanceof Map<?, ?> m) return m.get(part);
        if (at instanceof List<?> l && isIndex(part, l.size())) return l.get(Integer.parseInt(part));
        return null;
    }

    private static boolean isIndex(String part, int size) {
        try {
            int i = Integer.parseInt(part);
            return i >= 0 && i < size;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
