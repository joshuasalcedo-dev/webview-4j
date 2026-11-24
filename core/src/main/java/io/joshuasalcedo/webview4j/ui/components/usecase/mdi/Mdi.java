package io.joshuasalcedo.webview4j.ui.components.usecase.mdi;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.state.Codec;
import io.joshuasalcedo.webview4j.ui.state.Saved;
import java.util.function.Consumer;

/**
 * Windows inside the app's window (MDI): each moves, resizes, minimizes and maximizes there, and holds any component
 * (a form, a table, a split pane, a whole layout). Where the windows are is a {@link MdiLayout}: {@link #remember} keeps
 * it between runs on its own; {@link #onLayoutChange} and {@link #layout} let a screen keep it itself.
 * <pre>{@code
 * new Mdi("Workspace",
 *         new MdiWindow("orders", "Orders", DataTable.of("Orders", orders)),
 *         new MdiWindow("notes", "Notes", new BorderPane(editor).top(toolbar)))
 *     .remember("workspace").arrangeButtons()
 * }</pre>
 */
public final class Mdi extends Component<Mdi> {

    private Consumer<MdiLayout> listener;

    public Mdi(String label, MdiWindow... windows) {
        super("mdi");
        prop("label", label);
        add(windows);
    }

    public Mdi with(MdiWindow... windows) { return add(windows); }

    /** Shown when no window is open. */
    public Mdi empty(String text) { return prop("empty", text); }

    /** Cascade and Tile buttons in the area's corner. */
    public Mdi arrangeButtons() { return prop("arrange", true); }

    /** Puts the windows where {@code layout} says (when they open, and when it changes). */
    public Mdi layout(MdiLayout layout) {
        return prop("layout", layout.write());
    }

    /** Where the windows are, once a move or a resize settles (not for every pixel of a drag). */
    public Mdi onLayoutChange(Consumer<MdiLayout> action) {
        listener = listener == null ? action : listener.andThen(action);
        Consumer<MdiLayout> all = listener;
        return on("onLayoutChange", v -> all.accept(MdiLayout.read(v)));
    }

    /**
     * Keeps where the windows are between runs, under {@code name} in the session's saved state (on disk): they open
     * where they were left. In a screen's {@code render()}.
     */
    public Mdi remember(String name) {
        Saved<MdiLayout> saved = Saved.of("mdi." + name, Codec.of(MdiLayout.class), MdiLayout::empty);
        MdiLayout was = saved.get();
        if (!was.windows().isEmpty()) layout(was);
        return onLayoutChange(saved::set);
    }
}
