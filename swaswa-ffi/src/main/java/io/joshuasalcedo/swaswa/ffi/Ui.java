package io.joshuasalcedo.swaswa.ffi;

import java.util.function.Consumer;

/**
 * An immediate-mode UI, as egui and Dear ImGui are: the code that draws it runs every frame and builds it anew from
 * the app's own state; no widget is kept between frames. A widget's method draws it and says what the person did with
 * it: {@code if (ui.button("Save")) save();}, {@code volume = ui.slider("Volume", volume, 0, 100);}. Its labels are
 * also what tells widgets apart from frame to frame: two widgets of one kind in one place need two labels.
 *
 * <p>Given to the frame function of an {@link Immediate} implementation ({@link Egui}); valid only while it runs, on
 * the thread it runs on.
 */
public interface Ui {

    /** A large text: a section's title. */
    void heading(String text);

    /** A text. */
    void label(String text);

    /** A button: true on the frame it was clicked. */
    boolean button(String text);

    /** A checkbox: its state after this frame (the person may have changed {@code checked}). */
    boolean checkbox(String text, boolean checked);

    /** A slider from {@code min} to {@code max}: its value after this frame. */
    double slider(String text, double value, double min, double max);

    /**
     * A one-line text field holding {@code text}: its text after this frame (what the person typed into it).
     * {@code id} tells it apart from the others (and keeps its cursor, its selection, from frame to frame).
     */
    String textField(String id, String text);

    /** A text in a fixed-width font: code, a log's lines. */
    void monospace(String text);

    /**
     * A scroll area named {@code id}, at most {@code maxHeight} tall (0: the room left): what {@code content} draws in
     * it scrolls. With {@code stickToBottom}, it stays at its end as what is in it grows (a log), until the person
     * scrolls away; back at the end, it sticks again.
     */
    void scroll(String id, double maxHeight, boolean stickToBottom, Consumer<Ui> content);

    /** Draws the rows {@code from} (inclusive) to {@code to} (exclusive) of a {@link #rows} area: those in view. */
    @FunctionalInterface
    interface Rows {
        void draw(Ui ui, int from, int to);
    }

    /**
     * A scroll area of {@code count} rows, each a line of monospace text tall, of which only those in view are drawn:
     * {@code rows} is given their range, each frame. For a long, fast list (a log of a hundred thousand lines) at the
     * cost of the thirty in view. {@code stickToBottom} as {@link #scroll}.
     */
    void rows(String id, int count, double maxHeight, boolean stickToBottom, Rows rows);

    /**
     * A title bar of the window's own (for a window opened without the system's: {@link Immediate.Options#frameless}):
     * its title; drag it to move the window, double-click to maximize; minimize, maximize and close at its right.
     */
    void titleBar(String title);

    /**
     * A band {@code height} tall across the window that moves it when its empty parts are dragged (double-click:
     * maximize or restore): a title bar of the app's own design. What {@code content} draws in it (a logo, tabs, a
     * search field, buttons calling {@link #window()}) goes left to right and works as anywhere.
     */
    void dragRegion(double height, Consumer<Ui> content);

    /** The window this UI is drawn in, for buttons of the app's own (a title bar's). */
    Window window();

    /** What a frame's UI may do to its window. */
    interface Window {
        /** The system's move of the window, as a title bar's: call it on a press (the mouse button held). */
        void startDrag();

        void minimize();

        /** Maximized, or restored if it is. */
        void toggleMaximize();

        /** Closes the window once this frame is over. */
        void close();
    }

    /** A line between what is above and what is below. */
    void separator();

    /** Empty room, {@code points} logical pixels. */
    void space(double points);

    /** A row: what {@code row} draws goes left to right. */
    void horizontal(Consumer<Ui> row);

    /** A column: what {@code column} draws goes top to bottom (as in the frame itself). */
    void vertical(Consumer<Ui> column);
}
