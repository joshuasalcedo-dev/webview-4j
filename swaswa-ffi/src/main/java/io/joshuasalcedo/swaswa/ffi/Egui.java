package io.joshuasalcedo.swaswa.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * egui, the immediate-mode UI of Rust, drawn with the GPU (OpenGL) in windows of its own on the app's event loop: each
 * {@link Ui} call is a call into egui itself, nothing in between, the answer this very frame.
 *
 * <pre>{@code
 * ImmediateWindow w = Egui.on(app).open("Settings", 360, 240, ui -> {
 *     ui.heading("Volume");
 *     volume = ui.slider("Level", volume, 0, 100);
 *     if (ui.button("Reset")) volume = 50;
 * });
 * }</pre>
 */
public final class Egui implements Immediate {

    /** A window's frame function and what is waiting for its end. */
    private record Opened(Consumer<Ui> frame, CompletableFuture<Void> closed) {}

    private static final Map<Long, Opened> OPEN = new ConcurrentHashMap<>();
    private static volatile boolean hooked;
    private final App app;

    private Egui(App app) {
        this.app = app;
    }

    public static Egui on(App app) {
        Objects.requireNonNull(app, "app");
        hook();
        return new Egui(app);
    }

    @Override
    public ImmediateWindow open(Options options, Consumer<Ui> frame) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(frame, "frame");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("title", options.title());
        body.put("width", options.width());
        body.put("height", options.height());
        body.put("decorations", options.decorations());
        return opened(body, frame);
    }

    @Override
    public ImmediateWindow embed(View host, double x, double y, double width, double height, Consumer<Ui> frame) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(frame, "frame");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("a panel's size is positive");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("title", "egui panel");
        body.put("parent", host.id());
        body.put("x", x);
        body.put("y", y);
        body.put("width", width);
        body.put("height", height);
        ImmediateWindow panel = opened(body, frame);
        // it goes with its window (the system takes the child away with it): told here so whenClosed completes
        Subscription[] watch = new Subscription[1];
        watch[0] = app.subscribe(Closed.class, closed -> {
            if (closed.view().equals(host.id())) {
                panel.close();
                watch[0].close();
            }
        });
        panel.whenClosed().thenRun(() -> {
            if (watch[0] != null) watch[0].close();
        });
        return panel;
    }

    private ImmediateWindow opened(Map<String, Object> body, Consumer<Ui> frame) {
        Message reply = app.request(Message.of("app.egui.open").withJson(body)).join();
        @SuppressWarnings("unchecked")
        String id = Events.text((Map<String, Object>) reply.json(), "window");
        long number = Long.parseLong(id.substring(id.indexOf('-') + 1));
        CompletableFuture<Void> closed = new CompletableFuture<>();
        OPEN.put(number, new Opened(frame, closed));
        app.onEguiClosed(id, () -> {
            OPEN.remove(number);
            closed.complete(null);
        });
        return new Window(app, id, closed);
    }

    /** An egui window of the app's. */
    static final class Window implements ImmediateWindow {
        private final App app;
        private final String id;
        private final CompletableFuture<Void> closed;

        Window(App app, String id, CompletableFuture<Void> closed) {
            this.app = app;
            this.id = id;
            this.closed = closed;
        }

        @Override public String id() { return id; }

        @Override public void repaint() { app.request(Message.of("egui." + id + ".repaint")).exceptionally(gone -> null); }

        @Override public void close() { app.request(Message.of("egui." + id + ".close")).exceptionally(gone -> null).join(); }

        @Override public CompletableFuture<Void> whenClosed() { return closed; }

        @Override
        public void place(double x, double y, double width, double height) {
            app.request(Message.of("egui." + id + ".place").withJson(Map.of("x", x, "y", y, "width", Math.max(0, width), "height", Math.max(0, height))))
                    .exceptionally(gone -> null).join();
        }

        @Override
        public void setDecorations(boolean on) {
            app.request(Message.of("egui." + id + ".window").withJson(Map.of("decorations", on))).join();
        }

        /** Minimized, maximized, decorated: as the window is. */
        CompletableFuture<Map<?, ?>> state() {
            return app.request(Message.of("egui." + id + ".state")).thenApply(r -> (Map<?, ?>) r.json());
        }

        /** Input as if the person gave it (tests, tools): {kind: move|down|up|click, x, y}, {kind: text, text}, {kind: key, key}. */
        CompletableFuture<Void> input(List<Map<String, Object>> events) {
            return app.request(Message.of("egui." + id + ".input").withJson(Map.of("events", events))).thenApply(r -> null);
        }

        /** What the last frame drew, and how many frames there have been. */
        CompletableFuture<Map<?, ?>> drawn() {
            return app.request(Message.of("egui." + id + ".drawn")).thenApply(r -> (Map<?, ?>) r.json());
        }
    }

    // ---- the native side: egui's widgets, called as the frame runs ----

    private static final Linker LINKER = Linker.nativeLinker();
    // Not "critical" (the cheapest kind of call, which may not call back into Java): any of egui's calls may log, and
    // the library's log goes to Java as it happens, a call back into Java.
    private static MethodHandle leaf(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow(name), descriptor);
    }

    private static MethodHandle call(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow(name), descriptor);
    }

    private static final class Calls {
        static final MethodHandle HEADING = leaf("wv_ui_heading", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
        static final MethodHandle LABEL = leaf("wv_ui_label", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
        static final MethodHandle BUTTON = leaf("wv_ui_button", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
        static final MethodHandle CHECKBOX = leaf("wv_ui_checkbox", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT));
        static final MethodHandle SLIDER = leaf("wv_ui_slider", FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS, JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));
        static final MethodHandle TEXT_FIELD = leaf("wv_ui_text_field", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG));
        static final MethodHandle TAKE_TEXT = leaf("wv_ui_take_text", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG));
        static final MethodHandle SEPARATOR = leaf("wv_ui_separator", FunctionDescriptor.ofVoid());
        static final MethodHandle MONOSPACE = leaf("wv_ui_monospace", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
        static final MethodHandle SCROLL = call("wv_ui_scroll", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_FLOAT, JAVA_INT));
        static final MethodHandle SCROLL_ROWS = call("wv_ui_scroll_rows",
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_FLOAT, JAVA_LONG, JAVA_FLOAT, JAVA_INT));
        static final MethodHandle VISIBLE_ROWS = leaf("wv_ui_visible_rows", FunctionDescriptor.of(JAVA_LONG));
        static final MethodHandle WINDOW = leaf("wv_ui_window", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
        static final MethodHandle TITLE_BAR = leaf("wv_ui_title_bar", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
        static final MethodHandle DRAG_REGION = call("wv_ui_drag_region", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_FLOAT));
        static final MethodHandle SPACE = leaf("wv_ui_space", FunctionDescriptor.ofVoid(JAVA_FLOAT));
        // these two call back into Java (the row's, the column's closure): not critical
        static final MethodHandle HORIZONTAL = call("wv_ui_horizontal", FunctionDescriptor.ofVoid(JAVA_LONG));
        static final MethodHandle VERTICAL = call("wv_ui_vertical", FunctionDescriptor.ofVoid(JAVA_LONG));
    }

    /** Native's two callbacks, once: a frame of a window, a nested layout's closure. */
    private static synchronized void hook() {
        if (hooked) return;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MemorySegment frame = LINKER.upcallStub(lookup.findStatic(Egui.class, "frame", MethodType.methodType(void.class, long.class)),
                    FunctionDescriptor.ofVoid(JAVA_LONG), Arena.global());
            MemorySegment nested = LINKER.upcallStub(lookup.findStatic(Egui.class, "nested", MethodType.methodType(void.class, long.class)),
                    FunctionDescriptor.ofVoid(JAVA_LONG), Arena.global());
            MethodHandle set = LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow("wv_egui_callbacks"),
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
            set.invokeExact(frame, nested);
            hooked = true;
        } catch (Throwable e) {
            throw new NativeLibraryException("egui could not be reached: " + e.getMessage(),
                    "Use swaswa-ffi's jar and native library of the same version.", e);
        }
    }

    /** The UI a frame draws with: one per frame, on the loop's thread. */
    private static final class EguiUi implements Ui {
        /** The closures of this frame's rows and columns, by token. */
        final List<Consumer<Ui>> nested = new ArrayList<>();
        /** The frame's texts, in native memory: freed when the frame ends. */
        final Arena arena = Arena.ofConfined();

        private MemorySegment bytes(byte[] text) {
            MemorySegment s = arena.allocate(Math.max(1, text.length));
            MemorySegment.copy(text, 0, s, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, text.length);
            return s;
        }

        private static byte[] utf8(String text) {
            return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void heading(String text) {
            byte[] b = utf8(text);
            try { Calls.HEADING.invokeExact(bytes(b), (long) b.length); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public void label(String text) {
            byte[] b = utf8(text);
            try { Calls.LABEL.invokeExact(bytes(b), (long) b.length); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public boolean button(String text) {
            byte[] b = utf8(text);
            try { return (int) Calls.BUTTON.invokeExact(bytes(b), (long) b.length) == 1; } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public boolean checkbox(String text, boolean checked) {
            byte[] b = utf8(text);
            try { return (int) Calls.CHECKBOX.invokeExact(bytes(b), (long) b.length, checked ? 1 : 0) == 1; } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public double slider(String text, double value, double min, double max) {
            if (!(min <= max)) throw new IllegalArgumentException("a slider's min is at most its max: " + min + " > " + max);
            byte[] b = utf8(text);
            try { return (double) Calls.SLIDER.invokeExact(bytes(b), (long) b.length, value, min, max); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public String textField(String id, String text) {
            byte[] name = utf8(id), current = utf8(text);
            try {
                long length = (long) Calls.TEXT_FIELD.invokeExact(bytes(name), (long) name.length, bytes(current), (long) current.length);
                if (length < 0) return text == null ? "" : text;
                MemorySegment edited = arena.allocate(Math.max(1, length));
                long taken = (long) Calls.TAKE_TEXT.invokeExact(edited, length);
                return new String(edited.asSlice(0, taken).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
            } catch (Throwable e) {
                throw failed(e);
            }
        }

        @Override
        public void monospace(String text) {
            byte[] b = utf8(text);
            try { Calls.MONOSPACE.invokeExact(bytes(b), (long) b.length); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public void scroll(String id, double maxHeight, boolean stickToBottom, Consumer<Ui> content) {
            nested.add(Objects.requireNonNull(content, "content"));
            byte[] name = utf8(id);
            try {
                Calls.SCROLL.invokeExact(bytes(name), (long) name.length, (long) (nested.size() - 1), (float) maxHeight, stickToBottom ? 1 : 0);
            } catch (Throwable e) {
                throw failed(e);
            }
        }

        @Override
        public void rows(String id, int count, double maxHeight, boolean stickToBottom, Rows rows) {
            Objects.requireNonNull(rows, "rows");
            if (count < 0) throw new IllegalArgumentException("a count of rows is 0 or more: " + count);
            nested.add(ui -> {
                long range;
                try {
                    range = (long) Calls.VISIBLE_ROWS.invokeExact();
                } catch (Throwable e) {
                    throw failed(e);
                }
                int from = (int) (range >>> 32), to = (int) (range & 0xFFFF_FFFFL);
                rows.draw(ui, from, Math.min(to, count));
            });
            byte[] name = utf8(id);
            try {
                Calls.SCROLL_ROWS.invokeExact(bytes(name), (long) name.length, (long) (nested.size() - 1), 0f, (long) count,
                        (float) maxHeight, stickToBottom ? 1 : 0);
            } catch (Throwable e) {
                throw failed(e);
            }
        }

        @Override
        public void titleBar(String title) {
            byte[] b = utf8(title);
            try { Calls.TITLE_BAR.invokeExact(bytes(b), (long) b.length); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public void dragRegion(double height, Consumer<Ui> content) {
            nested.add(Objects.requireNonNull(content, "content"));
            try { Calls.DRAG_REGION.invokeExact((long) (nested.size() - 1), (float) height); } catch (Throwable e) { throw failed(e); }
        }

        private static void act(int action) {
            try {
                int done = (int) Calls.WINDOW.invokeExact(action);
            } catch (Throwable e) {
                throw failed(e);
            }
        }

        private static final Ui.Window WINDOW = new Ui.Window() {
            @Override public void startDrag() { act(0); }
            @Override public void minimize() { act(1); }
            @Override public void toggleMaximize() { act(2); }
            @Override public void close() { act(3); }
        };

        @Override
        public Ui.Window window() {
            return WINDOW;
        }

        @Override
        public void separator() {
            try { Calls.SEPARATOR.invokeExact(); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public void space(double points) {
            try { Calls.SPACE.invokeExact((float) points); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public void horizontal(Consumer<Ui> row) {
            nested.add(Objects.requireNonNull(row, "row"));
            try { Calls.HORIZONTAL.invokeExact((long) (nested.size() - 1)); } catch (Throwable e) { throw failed(e); }
        }

        @Override
        public void vertical(Consumer<Ui> column) {
            nested.add(Objects.requireNonNull(column, "column"));
            try { Calls.VERTICAL.invokeExact((long) (nested.size() - 1)); } catch (Throwable e) { throw failed(e); }
        }

        private static RuntimeException failed(Throwable e) {
            return e instanceof RuntimeException r ? r : new IllegalStateException(e);
        }
    }

    /** The frame under way, on the loop's thread (one at a time). */
    private static EguiUi current;

    /** Called by the native side: window {@code number}'s frame. Nothing may escape into native code. */
    private static void frame(long number) {
        Opened opened = OPEN.get(number);
        if (opened == null) return;
        EguiUi ui = new EguiUi();
        current = ui;
        try {
            opened.frame().accept(ui);
        } catch (Throwable e) {
            System.getLogger("io.joshuasalcedo.swaswa.ffi").log(System.Logger.Level.ERROR, "an egui frame failed", e);
        } finally {
            current = null;
            ui.arena.close();
        }
    }

    /** Called by the native side: a row's or a column's closure, inside it. */
    private static void nested(long token) {
        EguiUi ui = current;
        if (ui == null || token < 0 || token >= ui.nested.size()) return;
        try {
            ui.nested.get((int) token).accept(ui);
        } catch (Throwable e) {
            System.getLogger("io.joshuasalcedo.swaswa.ffi").log(System.Logger.Level.ERROR, "an egui row or column failed", e);
        }
    }
}
