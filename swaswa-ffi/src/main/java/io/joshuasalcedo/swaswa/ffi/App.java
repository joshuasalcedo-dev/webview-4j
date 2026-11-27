package io.joshuasalcedo.swaswa.ffi;

import io.joshuasalcedo.swaswa.internal.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The running event loop: opens windows, performs actions, delivers events. One per process at a time.
 * <pre>{@code
 * try (App app = App.start()) {
 *     app.subscribe(LogRecord.class, log -> System.out.println(log.level() + " " + log.message()));
 *     View view = app.open(WindowOptions.url("https://example.com")).join();
 *     Object title = view.eval("document.title").join();
 *     app.whenStopped().join();      // until the user closes the app, or app.quit()
 * }
 * }</pre>
 *
 * <p>Nothing the app calls back runs on the event loop's thread: messages go to a dispatcher that
 * hands each {@link Subscriber} its own virtual thread, and futures complete on virtual threads.
 * Underneath, App speaks the protocol in PROTOCOL.md; none of it is visible from here.
 */
public final class App implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(App.class.getName());
    private static final String PROTOCOL = "1";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** Put in the inbox once the loop has returned. */
    private static final Object STOPPED = new Object();

    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong inboxes = new AtomicLong();
    private final List<Sub<?>> subscriptions = new CopyOnWriteArrayList<>();
    private final Map<String, CommandHandler> commands = new ConcurrentHashMap<>();
    /** What answers the pages' requests to Java; none until the app gives one. */
    private volatile PageRequestHandler pageRequests;
    private final Assets assets = new Assets();
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final CompletableFuture<App> ready = new CompletableFuture<>();
    private final CompletableFuture<Integer> stopped = new CompletableFuture<>();
    private final Meter meter = new Meter();
    private volatile int exitCode;

    /** A request waiting for its reply: what it asked, for error messages, and when, for its timing. */
    private record Pending(String subject, long sentAt, CompletableFuture<Message> reply) {}

    /**
     * Where any page in any window finds the shared files, those {@link #serveShared} names: a component library's
     * built page (its script and its CSS), loaded with no bundler.
     */
    public static final String SHARED_URL = Assets.baseUrl() + "ui/";

    private App() {
        Thread.ofVirtual().name("swaswa-dispatcher").start(this::dispatch);
        // the window's state on its page's <html>, for the page's own styles: active, being dragged
        subscribe(Focused.class, f -> pageState(f.view(), "active", f.focused()));
        subscribe(WindowMove.class, m -> {
            if (m.phase() != WindowMove.Phase.MOVING) pageState(m.view(), "dragging", m.phase() == WindowMove.Phase.STARTED);
            if (m.phase() == WindowMove.Phase.ENDED) pageState(m.view(), "holding", false);   // the move took the release
        });
    }

    /**
     * Serves {@code classPathDirectory}'s files, read by {@code loader}, at {@link #SHARED_URL}: what every page in
     * every window may load ({@code <SHARED_URL>ui.js}). One directory per app; serving the same one again does
     * nothing. A component library calls it before its first window opens; core itself serves nothing there.
     *
     * @throws IllegalStateException if another directory is served there already
     */
    public App serveShared(String classPathDirectory, ClassLoader loader) {
        assets.serveShared(Objects.requireNonNull(classPathDirectory, "classPathDirectory"), Objects.requireNonNull(loader, "loader"));
        return this;
    }

    /**
     * Starts the event loop on a new thread and returns once it is ready. Not on macOS, where the
     * loop needs the main thread: use {@link #run} there.
     *
     * @throws WebViewException if a loop already runs or it does not start within 30 seconds
     */
    public static App start() {
        App app = new App();
        Thread.ofPlatform().name("swaswa-event-loop").start(() -> {
            try {
                app.loop();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "the event loop failed", e);
            }
        });
        try {
            return app.ready.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof WebViewException failed ? failed
                    : new NotRunningException("the event loop did not start", "See the cause; run with -Dswaswa.log=debug for the native log.", e.getCause());
        } catch (TimeoutException e) {
            app.quit();
            throw new NotRunningException("the event loop did not start within 30 seconds",
                    "The browser engine (WebView2 on Windows) may be missing or stuck: install the WebView2 Runtime, "
                            + "or run with -Dswaswa.log=debug to see where it stopped.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotRunningException("interrupted while starting the event loop", "Do not interrupt the thread that calls App.start().", e);
        }
    }

    /**
     * Runs the event loop on this thread, the main thread on macOS, until the app quits; returns
     * the loop's exit code. {@code onReady} runs on a virtual thread once actions can be performed.
     */
    public static int run(Consumer<App> onReady) {
        Objects.requireNonNull(onReady, "onReady");
        App app = new App();
        app.ready.thenAcceptAsync(onReady, app.virtualThreads).exceptionally(e -> {
            LOG.log(System.Logger.Level.ERROR, "onReady failed", e);
            return null;
        });
        return app.loop();
    }

    private int loop() {
        if (!RUNNING.compareAndSet(false, true)) {
            WebViewException running = new AlreadyRunningException("an App is already running in this process",
                    "There is one App per process: use the one you have (pass it where it is needed), or close() it before starting another.");
            ready.completeExceptionally(running);
            inbox.add(STOPPED);
            throw running;
        }
        try {
            exitCode = Native.run(inbox::add);
            return exitCode;
        } catch (RuntimeException e) {
            ready.completeExceptionally(e);
            throw e;
        } catch (LinkageError e) {   // ExceptionInInitializerError is one
            // the native library could not load: said to whoever waits for the app, at once, not after 30 seconds
            Throwable cause = e instanceof ExceptionInInitializerError init && init.getCause() != null ? init.getCause() : e;
            WebViewException failed = cause instanceof WebViewException w ? w
                    : new NativeLibraryException("the native library could not load: " + cause.getMessage(),
                            "Put swaswa-ffi's native library for this system (" + NativeLibrary.platform() + ") on the class path, "
                                    + "or set -Dswaswa.library to the library file.", cause);
            ready.completeExceptionally(failed);
            throw failed;
        } finally {
            RUNNING.set(false);
            inbox.add(STOPPED);
        }
    }

    // ---- actions ----

    /**
     * Whether this process runs with administrator rights (Windows: elevated, "Run as administrator"; elsewhere: root).
     * An app that needs them can say so before trying, instead of meeting a {@link PermissionException}. Needs no running
     * app; false where the native library cannot say.
     */
    public static boolean elevated() {
        try {
            return Native.elevated();
        } catch (RuntimeException | LinkageError unknown) {
            return false;
        }
    }

    /** Where the next windows with a title open: a live reload's, so they open where the ones before them were. */
    private final Map<String, java.util.Deque<Geometry>> placements = new ConcurrentHashMap<>();

    /**
     * The next window titled {@code title} opens at {@code where} (its place, size and maximized): how live reload
     * puts the windows it opens again where they were.
     */
    public void placeNext(String title, Geometry where) {
        placements.computeIfAbsent(title, t -> new java.util.concurrent.ConcurrentLinkedDeque<>()).add(where);
    }

    /** Forgets the places no window took. */
    public void clearPlacements() {
        placements.clear();
    }

    /** Opens a window; the future completes once it exists. */
    public CompletableFuture<View> open(WindowOptions options) {
        Objects.requireNonNull(options, "options");
        if (memoryLimit > 0) return withinLimit("open another window").thenCompose(ok -> openNow(options));
        return openNow(options);
    }

    private CompletableFuture<View> openNow(WindowOptions options) {
        java.util.Deque<Geometry> places = placements.get(options.currentTitle());
        Geometry where = places == null ? null : places.poll();
        if (where != null) {
            options.position(where.x(), where.y()).size(where.width(), where.height());
            if (where.maximized()) options.maximized(true);
        }
        if (options.remembered() == null) return open(options, options.toPayload());
        return WindowPlaces.open(this, options.toPayload(), options.remembered(), payload -> open(options, payload));
    }

    /** Opens what {@code payload} says (a window, or with {@code parent} an embedded view); {@code options} for its files. */
    CompletableFuture<View> open(WindowOptions options, Map<String, Object> payload) {
        // Before the window exists: its page asks for its files as soon as it loads.
        if (options.assets() != null) assets.serve(options.assets(), options.assetLoader());
        CompletableFuture<View> opened = request(Message.of("app.open").withJson(payload))
                .thenApply(reply -> new View(this, Events.text(object(reply), "view")));
        if (!options.isDropTarget() && !options.isDraggable()) return opened;
        boolean embedded = payload.containsKey("parent");
        return opened.thenCompose(view -> {
            if (options.isDropTarget()) dropTarget(view.id(), true);
            if (!options.isDraggable()) return CompletableFuture.completedFuture(view);
            return draggable(view, true, embedded).thenApply(done -> view);
        });
    }

    // ---- docking by flags: windows that take others in (drop targets), and what can be dragged (draggables) ----

    private final java.util.Set<String> dropTargets = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> draggables = ConcurrentHashMap.newKeySet();
    private Docking docking;

    /**
     * The app's own docking: windows opened {@link WindowOptions#dropTarget} take in windows opened
     * {@link WindowOptions#draggable}, dragged onto them. Started the first time either is asked for; its style, zones,
     * key and feedback can be set on what this returns (which views it takes, it keeps to the flags).
     */
    public synchronized Docking docking() {
        if (docking == null) {
            docking = Docking.on(this).hosts(v -> dropTargets.contains(v.id())).dockable(v -> draggables.contains(v.id())).start();
            subscribe(Closed.class, closed -> {
                dropTargets.remove(closed.view());
                draggables.remove(closed.view());
            });
        }
        return docking;
    }

    void dropTarget(String view, boolean on) {
        if (on) {
            dropTargets.add(view);
            docking();
        } else {
            dropTargets.remove(view);
        }
    }

    boolean isDropTarget(String view) { return dropTargets.contains(view); }

    boolean isDraggable(String view) { return draggables.contains(view); }

    /** A view made draggable or not: a window, by the app's docking; an embedded view, an MDI child or not. */
    CompletableFuture<Void> draggable(View view, boolean on, boolean embedded) {
        if (on) draggables.add(view.id());
        else draggables.remove(view.id());
        if (embedded) return view.setFloating(on);
        if (on) docking();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * A handle on a view by its id, e.g. from an event.
     *
     * @throws IllegalArgumentException if {@code id} cannot be a view id
     */
    public View view(String id) {
        return new View(this, id);
    }

    /** The open views. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<ViewInfo>> views() {
        return request(Message.of("app.list")).thenApply(reply -> {
            List<ViewInfo> views = new ArrayList<>();
            for (Object item : (List<Object>) reply.json()) {
                Map<String, Object> v = (Map<String, Object>) item;
                String directory = Events.text(v, "dataDirectory");
                views.add(new ViewInfo(Events.text(v, "view"), Events.text(v, "title"), Events.text(v, "url"),
                        directory == null ? null : Path.of(directory)));
            }
            return List.copyOf(views);
        });
    }

    /** Sets {@code data-swaswa-<name>} on a view's page's <html> (true: there; false: gone; else its value). */
    void pageState(String view, String name, Object value) {
        String said = value instanceof Boolean b ? b.toString() : Json.write(String.valueOf(value));
        view(view).eval("window.__webview4j && window.__webview4j.state(" + Json.write(name) + ", " + said + ")")
                .exceptionally(closed -> null);
    }

    /** What to do when an egui window closes ({@link Egui}). */
    private final Map<String, Runnable> eguiClosed = new ConcurrentHashMap<>();

    void onEguiClosed(String id, Runnable then) {
        eguiClosed.put(id, then);
    }

    // ---- the pointer ----

    /** Where the pointer is on the screen now. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Cursor> cursor() {
        return request(Message.of("app.cursor")).thenApply(reply -> {
            Map<String, Object> m = object(reply);
            return new Cursor(((Number) m.get("x")).doubleValue(), ((Number) m.get("y")).doubleValue(),
                    ((Number) m.get("physicalX")).doubleValue(), ((Number) m.get("physicalY")).doubleValue(),
                    ((Number) m.get("scale")).doubleValue());
        });
    }

    /**
     * Which of the app's windows is at the screen point ({@code physicalX}, {@code physicalY}: physical pixels, as
     * {@link Cursor#physicalX()}), as the system has them stacked: the top one. Empty when none of them is there.
     * Windows; elsewhere always empty for now.
     */
    public CompletableFuture<java.util.Optional<View>> windowAt(double physicalX, double physicalY) {
        return request(Message.of("app.window-at").withJson(Map.of("x", physicalX, "y", physicalY)))
                .thenApply(reply -> java.util.Optional.ofNullable(Events.text(object(reply), "view")).map(this::view));
    }

    /** The app's window under the pointer, if any: where a drag was let go. */
    public CompletableFuture<java.util.Optional<View>> windowUnderCursor() {
        return cursor().thenCompose(at -> windowAt(at.physicalX(), at.physicalY()));
    }

    /**
     * The app's window at the screen point below {@code except} (the window being dragged, which is on top of all):
     * what it was let go over, to dock it into. Empty if none of the app's windows is there below it.
     */
    public CompletableFuture<java.util.Optional<View>> windowAt(double physicalX, double physicalY, View except) {
        Objects.requireNonNull(except, "except");
        return request(Message.of("app.window-at").withJson(Map.of("x", physicalX, "y", physicalY, "except", except.id())))
                .thenApply(reply -> java.util.Optional.ofNullable(Events.text(object(reply), "view")).map(this::view));
    }

    /** {@link #windowAt(double, double, View)} at the pointer: the window a dragged one was let go over. */
    public CompletableFuture<java.util.Optional<View>> windowUnderCursor(View except) {
        return cursor().thenCompose(at -> windowAt(at.physicalX(), at.physicalY(), except));
    }

    // ---- child processes ----

    private final java.util.Set<ChildProcess> children = ConcurrentHashMap.newKeySet();

    /**
     * Starts {@code command} as a child process tied to the app (see {@link ChildProcess}): its lines come as
     * {@link ProcessOutput} events, its end as {@link ProcessExited}; {@code name} names it in them.
     *
     * @throws SystemException if it could not start (no such program); a {@link PermissionException} if the system
     *                         refused to run it
     */
    public ChildProcess process(String name, String... command) {
        return process(name, new ProcessBuilder(command));
    }

    /** {@link #process(String, String...)} from a {@link ProcessBuilder} (its folder, its environment); its output is read here. */
    public ChildProcess process(String name, ProcessBuilder builder) {
        Objects.requireNonNull(name, "name");
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE);
        Process started;
        try {
            started = builder.start();
        } catch (java.io.IOException e) {
            String program = builder.command().isEmpty() ? "" : builder.command().getFirst();
            if (String.valueOf(e.getMessage()).contains("error=5")) {
                throw new PermissionException("could not start " + program + ": access denied",
                        "This user may not run it: check its permissions, or whether it needs administrator rights.", false, 5, e);
            }
            throw new SystemException("could not start " + program + ": " + e.getMessage(),
                    "Check the program's name and that it is on the PATH (or give its full path); a .bat file or a "
                            + "command built into the shell runs through cmd /c on Windows.", null, e);
        }
        boolean tied;
        try {
            tied = Native.assignToJob(started.pid()) == 1;
        } catch (RuntimeException | LinkageError unknown) {
            tied = false;
        }
        java.nio.charset.Charset charset = java.nio.charset.Charset.forName(System.getProperty("native.encoding",
                java.nio.charset.Charset.defaultCharset().name()), java.nio.charset.Charset.defaultCharset());
        ChildProcess child = new ChildProcess(name, started, tied, charset);
        children.add(child);
        Thread out = Thread.ofVirtual().name("swaswa-process-" + name + "-out")
                .start(() -> lines(child, started.getInputStream(), ProcessOutput.Stream.OUTPUT, charset));
        Thread err = Thread.ofVirtual().name("swaswa-process-" + name + "-err")
                .start(() -> lines(child, started.getErrorStream(), ProcessOutput.Stream.ERROR, charset));
        started.onExit().thenAccept(p -> {
            try {
                out.join();   // its last lines, before its end
                err.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            children.remove(child);
            publish(new ProcessExited(name, p.pid(), p.exitValue(), java.time.Duration.between(child.started(), java.time.Instant.now())));
        });
        return child;
    }

    private void lines(ChildProcess child, java.io.InputStream stream, ProcessOutput.Stream which, java.nio.charset.Charset charset) {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, charset))) {
            String line;
            while ((line = reader.readLine()) != null) publish(new ProcessOutput(child.name(), child.pid(), which, line));
        } catch (java.io.IOException ended) {
            // its stream closed: it ended
        }
    }

    /** The child processes the app started that still run. */
    public List<ChildProcess> children() {
        return List.copyOf(children);
    }

    /** Ends the children as the app closes, so none outlives it (on Windows the system would end them too). */
    private void endChildren() {
        List<CompletableFuture<Integer>> ending = new ArrayList<>();
        for (ChildProcess child : children) ending.add(child.kill(java.time.Duration.ofSeconds(2)));
        try {
            CompletableFuture.allOf(ending.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOG.log(System.Logger.Level.WARNING, "a child process did not end in time", e);
        }
    }

    // ---- background work ----

    /**
     * Starts {@code work} in the background, on a virtual thread, as a {@link Worker}: its progress and its end come as
     * {@link WorkerProgress} and {@link WorkerFinished} events, and it can be cancelled.
     */
    public <T> Worker<T> worker(String name, Worker.Work<T> work) {
        Objects.requireNonNull(work, "work");
        return new Worker<>(name, work, this::publish);
    }

    // ---- memory ----

    /**
     * How much memory the app uses now: Java's heap, this process, and each process of the browser engine (usually most
     * of it). Cheap enough to call every second or so for a dashboard.
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Memory> memory() {
        return request(Message.of("app.memory")).thenApply(reply -> {
            Runtime runtime = Runtime.getRuntime();
            List<Memory.Process> processes = new ArrayList<>();
            for (Object item : (List<Object>) reply.json()) {
                Map<String, Object> p = (Map<String, Object>) item;
                processes.add(new Memory.Process(((Number) p.get("pid")).longValue(), (String) p.get("kind"),
                        ((Number) p.get("resident")).longValue(), ((Number) p.get("private")).longValue()));
            }
            return new Memory(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory(), processes);
        });
    }

    private volatile Thread budgetWatch;
    /** Over the budget, as last told; kept across budgets, so a raised one says "back under". */
    private boolean budgetOver;
    /** The windows lowered while over the budget: set back to normal once under it. */
    private final java.util.Set<String> lowered = ConcurrentHashMap.newKeySet();

    /**
     * A soft limit on the memory the app uses ({@link Memory#total()}), checked every 2 seconds. Going over it, a
     * {@link MemoryPressure} event says so (once) and the engine is asked to use less in every window not shown
     * (hidden or minimized: {@link View#setMemoryLevel}); coming back under, another says so and those windows are back
     * to normal. Nothing is refused or closed: the app decides what to free. 0 or less: no budget.
     */
    public synchronized void memoryBudget(long bytes) {
        memoryBudget(bytes, java.time.Duration.ofSeconds(2));
    }

    /** {@link #memoryBudget(long)}, checked every {@code every}. */
    public synchronized void memoryBudget(long bytes, java.time.Duration every) {
        Thread old = budgetWatch;
        if (old != null) {
            old.interrupt();
            try {
                old.join(10_000);   // one watcher at a time: the next goes on from where it was
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        budgetWatch = null;
        if (bytes <= 0) {
            if (budgetOver) {   // no budget is never over it
                budgetOver = false;
                relieve();
                memory().thenAccept(now -> publish(new MemoryPressure(now, 0, false)));
            }
            return;
        }
        budgetWatch = Thread.ofVirtual().name("swaswa-memory-budget").start(() -> watchBudget(bytes, every));
    }

    /** The windows lowered while over the budget, back to normal. */
    private void relieve() {
        for (String id : List.copyOf(lowered)) view(id).setMemoryLevel(View.MemoryLevel.NORMAL);
        lowered.clear();
    }

    private void watchBudget(long budget, java.time.Duration every) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Memory now = memory().get(10, TimeUnit.SECONDS);
                boolean isOver = now.total() > budget;
                if (isOver) {
                    // the windows not shown: asked to use less (and those shown again: back to normal)
                    for (ViewInfo info : views().get(10, TimeUnit.SECONDS)) {
                        View v = view(info.id());
                        boolean shown;
                        try {
                            Geometry g = v.geometry().get(10, TimeUnit.SECONDS);
                            shown = g.visible() && !g.minimized();
                        } catch (ExecutionException embedded) {
                            continue;   // an embedded view goes with its window
                        }
                        if (!shown && lowered.add(info.id())) v.setMemoryLevel(View.MemoryLevel.LOW);
                        if (shown && lowered.remove(info.id())) v.setMemoryLevel(View.MemoryLevel.NORMAL);
                    }
                }
                if (isOver != budgetOver) {
                    budgetOver = isOver;
                    if (!isOver) relieve();
                    publish(new MemoryPressure(now, budget, isOver));
                }
                Thread.sleep(every);
            } catch (InterruptedException e) {
                return;
            } catch (ExecutionException | TimeoutException e) {
                if (e.getCause() instanceof NotRunningException) return;   // the app has quit
            }
        }
    }

    // ---- the memory limit ----

    private volatile long memoryLimit;

    /**
     * A hard limit on the memory the app uses ({@link Memory#total()}): over it, what would take more is refused with a
     * {@link ResourceLimitException} (opening another window, embedding another view), and what runs goes on. For a
     * warning before it, see {@link #memoryBudget(long)}; for the system to hold the app to a limit,
     * {@link #enforceMemoryLimit(long)}. 0 or less: no limit.
     */
    public void memoryLimit(long bytes) {
        memoryLimit = Math.max(0, bytes);
    }

    private static volatile long enforced;

    /**
     * Holds the whole app to {@code bytes} of memory, by the system (Windows): this process (Java, its heap included)
     * and every process it starts from then on: the browser engine and each process the engine starts, the app's child
     * processes. Past it, what any of them asks for is refused: a page gets "allocation failed" (or its renderer
     * fails: {@link EngineProcessFailed}), Java an {@link OutOfMemoryError}. Call it once, before {@link #start()} or
     * {@link #run}: processes started before it are outside. It holds until the process ends; it cannot be raised or
     * taken off. Leave room for Java's heap ({@code -Xmx}): it is counted too.
     *
     * @return true if the system holds the app to it; false if it could not (not on this system, or set already)
     */
    public static synchronized boolean enforceMemoryLimit(long bytes) {
        if (bytes <= 0) throw new IllegalArgumentException("a memory limit is positive: " + bytes);
        if (enforced != 0) return false;
        if (RUNNING.get()) {
            throw new IllegalStateException("an App runs already: its browser engine started outside the limit. "
                    + "Call App.enforceMemoryLimit(...) before App.start().");
        }
        boolean held;
        try {
            held = Native.capMemory(bytes) == 1;
        } catch (RuntimeException | LinkageError unknown) {
            held = false;
        }
        if (held) enforced = bytes;
        return held;
    }

    /** The limit the system holds the app to ({@link #enforceMemoryLimit}); 0 if none. */
    public static long enforcedMemoryLimit() {
        return enforced;
    }

    /** Completes if the app is within its limit; fails with a ResourceLimitException saying what uses the most if not. */
    private CompletableFuture<Void> withinLimit(String what) {
        long limit = memoryLimit;
        return memory().thenCompose(now -> {
            if (now.total() <= limit) return CompletableFuture.completedFuture(null);
            Memory.Process most = now.processes().stream().max(java.util.Comparator.comparingLong(Memory.Process::privateBytes)).orElseThrow();
            return CompletableFuture.failedFuture(new ResourceLimitException(
                    "could not " + what + ": the app uses " + mb(now.total()) + ", over its limit of " + mb(limit),
                    "The most is the " + most.kind() + " process (pid " + most.pid() + ", " + mb(most.privateBytes()) + "). Close windows "
                            + "or views the person is done with, or free what can be made again; or raise the limit (memoryLimit).",
                    now, limit));
        });
    }

    private static String mb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0) + " MB";
    }

    /** Whether opening one more would go over the limit: for embed (View). */
    CompletableFuture<Void> withinMemoryLimit(String what) {
        return memoryLimit > 0 ? withinLimit(what) : CompletableFuture.completedFuture(null);
    }

    // ---- dialogs ----

    /** Asks for one file to open; empty if the user cancelled. */
    public CompletableFuture<Optional<Path>> openFile(FileDialog dialog) {
        return fileDialog(dialog, "open").thenApply(paths -> paths.stream().findFirst());
    }

    /** Asks for files to open, one or more; empty if the user cancelled. */
    public CompletableFuture<List<Path>> openFiles(FileDialog dialog) {
        return fileDialog(dialog, "open-many");
    }

    /** Asks where to save a file; empty if the user cancelled. The system asks before replacing a file. */
    public CompletableFuture<Optional<Path>> saveFile(FileDialog dialog) {
        return fileDialog(dialog, "save").thenApply(paths -> paths.stream().findFirst());
    }

    /** Asks for a directory; empty if the user cancelled. */
    public CompletableFuture<Optional<Path>> pickFolder(FileDialog dialog) {
        return fileDialog(dialog, "folder").thenApply(paths -> paths.stream().findFirst());
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<List<Path>> fileDialog(FileDialog dialog, String kind) {
        Objects.requireNonNull(dialog, "dialog");
        return request(Message.of("app.dialog.file").withJson(dialog.toPayload(kind))).thenApply(reply -> {
            List<Path> paths = new ArrayList<>();
            for (Object path : (List<Object>) object(reply).get("paths")) paths.add(Path.of((String) path));
            return List.copyOf(paths);
        });
    }

    /** Shows a message box; completes with the button the user chose. */
    public CompletableFuture<MessageDialog.Answer> message(MessageDialog dialog) {
        Objects.requireNonNull(dialog, "dialog");
        return request(Message.of("app.dialog.message").withJson(dialog.toPayload()))
                .thenApply(reply -> MessageDialog.answer(Events.text(object(reply), "answer")));
    }

    /** An OK / Cancel question: true for OK. */
    public CompletableFuture<Boolean> confirm(String title, String text) {
        return message(MessageDialog.of(title, text).level(MessageDialog.Level.WARNING).buttons(MessageDialog.Buttons.OK_CANCEL))
                .thenApply(answer -> answer == MessageDialog.Answer.OK);
    }

    // ---- sounds ----

    /**
     * Plays a sound once on the default output device: an MP3, WAV, Ogg Vorbis or FLAC of up to
     * 4 MB. The future completes once it starts; it fails if the bytes are not a sound.
     */
    public CompletableFuture<Void> playSound(byte[] sound) {
        Objects.requireNonNull(sound, "sound");
        if (sound.length == 0 || sound.length > 4 * 1024 * 1024)
            throw new IllegalArgumentException("a sound is 1 byte to 4 MB, not " + sound.length + " bytes");
        return request(Message.of("app.play").withPayload(sound, "application/octet-stream")).thenApply(reply -> null);
    }

    /**
     * Sends {@code message} with a fresh {@code Reply-To} and completes with the reply, or fails
     * with a {@link WebViewException} when its {@code Status} is not 200. Package-private on purpose:
     * raw messages could answer a page's call with forged data or run unchecked scripts. The typed
     * methods on {@code App} and {@link View} are the API, and are built on this.
     */
    CompletableFuture<Message> request(Message message) {
        String inbox = "_inbox." + inboxes.incrementAndGet();
        CompletableFuture<Message> reply = new CompletableFuture<>();
        pending.put(inbox, new Pending(message.subject(), System.nanoTime(), reply));   // before sending: the reply may beat us back
        int sent;
        try {
            sent = send(message.with("Reply-To", inbox).encode());
        } catch (RuntimeException e) {
            pending.remove(inbox);
            reply.completeExceptionally(e);
            return reply;
        }
        if (sent != Native.SENT) {
            pending.remove(inbox);
            reply.completeExceptionally(sent == Native.NOT_RUNNING
                    ? new NotRunningException("the event loop is not running",
                            "Start the app first (App.start(), or App.run on macOS), and do not use it after close() or quit().", message.subject())
                    : new InvalidRequestException("the message is malformed or too large",
                            "A message is at most 64 MB: send a large picture as PNG or JPEG, or a smaller one.", message.subject()));
        }
        return reply;
    }

    /** Sends one encoded frame to the native side, counted. */
    private int send(byte[] frame) {
        int sent = Native.send(frame);
        if (sent == Native.SENT) meter.frameOut(frame.length);
        return sent;
    }

    /**
     * What the app has done since it started: frames both ways, how long actions and the pages'
     * calls took. Cheap to call often, e.g. each second for a dashboard.
     */
    public Metrics metrics() {
        return meter.snapshot();
    }

    /** Sends a message nobody answers, e.g. the answer to a page's call. */
    private void tell(Message message) {
        int sent = send(message.encode());
        if (sent != Native.SENT) LOG.log(System.Logger.Level.DEBUG, "could not send " + message.subject() + ": " + sent);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Message reply) {
        return (Map<String, Object>) reply.json();
    }

    // ---- the system's clipboard ----

    /** The text on the system's clipboard; empty if it holds none (a picture, files, nothing). */
    public CompletableFuture<java.util.Optional<String>> readClipboard() {
        return request(Message.of("app.clipboard.read")).thenApply(reply -> {
            Object text = object(reply).get("text");
            return java.util.Optional.ofNullable(text == null ? null : String.valueOf(text));
        });
    }

    /** Puts {@code text} on the system's clipboard, as a copy does. */
    public CompletableFuture<Void> writeClipboard(String text) {
        Objects.requireNonNull(text, "text");
        return request(Message.of("app.clipboard.write").withText(text, "text/plain")).thenApply(reply -> null);
    }

    /** Closes every window and stops the event loop; {@link #whenStopped()} completes after. False if it is not running. */
    public boolean quit() {
        return send(Message.of("app.quit").encode()) == Native.SENT;
    }

    /** Completes with the loop's exit code once it has stopped and every pending action has failed. */
    public CompletableFuture<Integer> whenStopped() {
        return stopped;
    }

    /** Quits and waits up to 10 seconds for the loop to stop. */
    @Override
    public void close() {
        endChildren();
        quit();
        try {
            stopped.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOG.log(System.Logger.Level.WARNING, "the event loop did not stop in time", e);
        }
    }

    // ---- commands the page invokes ----

    /**
     * Lets every page call {@code command}: {@code invoke("users.find", {id: 42})} with
     * {@code @webview4j/api}. A name is dot-separated words of letters, digits, {@code -} and
     * {@code _}. Pages can call only commands registered here. Closing the returned registration
     * removes the command.
     *
     * @throws IllegalStateException if the command already has a handler
     */
    public Subscription handle(String command, CommandHandler handler) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(handler, "handler");
        if (!Message.validSubject(command)) {
            throw new IllegalArgumentException("'" + command + "' is not a command name: dot-separated words of letters, digits, - and _");
        }
        if (commands.putIfAbsent(command, handler) != null) {
            throw new IllegalStateException("'" + command + "' already has a handler");
        }
        return new Subscription() {
            @Override public boolean isActive() { return commands.get(command) == handler; }
            @Override public void close()       { commands.remove(command, handler); }
        };
    }

    /** Runs the command's handler on a virtual thread and answers the page's call with the result. */
    private void invoke(String viewId, String command, Message call) {
        String replyTo = call.header("Reply-To");
        if (replyTo == null) return;
        View view = view(viewId);
        CommandHandler handler = commands.get(command);
        if (handler == null) {
            answer(view, replyTo, null, new WebViewException("no command '" + command + "'"));
            return;
        }
        long received = System.nanoTime();
        virtualThreads.execute(() -> {
            Object result;
            Object args = call.json();
            try {
                result = handler.handle(new Invocation(view, command, args));
            } catch (Exception e) {
                answer(view, replyTo, null, e);
                called(viewId, command, args, null, e, received);
                return;
            }
            if (result instanceof CompletionStage<?> stage) {
                stage.whenComplete((value, error) -> {
                    Throwable failure = error instanceof CompletionException c && c.getCause() != null ? c.getCause() : error;
                    answer(view, replyTo, value, failure);
                    called(viewId, command, args, value, failure, received);
                });
            } else {
                answer(view, replyTo, result, null);
                called(viewId, command, args, result, null, received);
            }
        });
    }

    private void answer(View view, String replyTo, Object value, Throwable error) {
        if (error == null) {
            String json;
            try {
                json = Json.write(value);   // fail here, with the reason, rather than in the page
            } catch (IllegalArgumentException notJson) {
                answer(view, replyTo, null, notJson);
                return;
            }
            tell(Message.of(replyTo).with("Status", "200").withText(json, "application/json"));
            return;
        }
        LOG.log(System.Logger.Level.DEBUG, "a command failed in " + view, error);
        int status = error instanceof IllegalArgumentException ? 400 : error instanceof WebViewException ? 404 : 500;
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        tell(Message.of(replyTo).with("Status", String.valueOf(status)).withText(message, "text/plain"));
    }

    // ---- the pages' requests to Java ----

    /**
     * What answers the pages' requests to Java ({@code request('GET', '/users/42')} in a page, or a fetch of
     * {@code <base>api/...}), in place of the one before; {@code null} for none (each is then a 404). A web app's own
     * page talks to Java this way: swaswa-webview's controllers are a handler.
     */
    public App onPageRequest(PageRequestHandler handler) {
        this.pageRequests = handler;
        return this;
    }

    /** A page's request to Java (PROTOCOL.md §2.4): handed to the app's handler on a virtual thread, answered. */
    private void request(String viewId, Message message) {
        String replyTo = message.header("Reply-To");
        if (replyTo == null) return;
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(String.valueOf(message.header("Method")));
        } catch (IllegalArgumentException e) {
            respond(replyTo, 405, "text/plain", "not a route method: " + message.header("Method"));
            return;
        }
        String path = String.valueOf(message.header("Path"));
        PageRequestHandler handler = pageRequests;
        if (handler == null) {
            respond(replyTo, 404, "text/plain", "nothing answers " + method + " " + path + " (App.onPageRequest)");
            return;
        }
        String query = message.header("Query");
        PageRequest request = new PageRequest(view(viewId), method, path, query == null ? "" : query, message.text());
        long received = System.nanoTime();
        String name = method + " " + path;
        virtualThreads.execute(() -> {
            Object result;
            try {
                result = handler.handle(request);
            } catch (Exception e) {
                respondFailure(replyTo, request, e);
                called(viewId, name, request.body(), null, e, received);
                return;
            }
            if (result instanceof CompletionStage<?> stage) {
                stage.whenComplete((value, error) -> {
                    Throwable failure = error instanceof CompletionException c && c.getCause() != null ? c.getCause() : error;
                    if (failure == null) respondValue(replyTo, request, value);
                    else respondFailure(replyTo, request, failure);
                    called(viewId, name, request.body(), value, failure, received);
                });
            } else {
                respondValue(replyTo, request, result);
                called(viewId, name, request.body(), result, null, received);
            }
        });
    }

    /** A page's request for one of the app's files (PROTOCOL.md §2.4): read on a virtual thread, sent. */
    private void asset(Message message) {
        String replyTo = message.header("Reply-To");
        if (replyTo == null) return;
        virtualThreads.execute(() -> {
            try {
                Assets.File file = assets.read(message.header("Root"), message.header("Path"));
                tell(Message.of(replyTo).with("Status", "200").withPayload(file.bytes(), file.contentType()));
            } catch (HttpException e) {
                respond(replyTo, e.status(), "text/plain", e.getMessage());
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "could not serve " + message.header("Path"), e);
                respond(replyTo, 500, "text/plain", "could not serve the file");
            }
        });
    }

    private void respondValue(String replyTo, PageRequest request, Object value) {
        if (value == null) {
            respond(replyTo, 204, "application/json", "");
            return;
        }
        String json;
        try {
            json = Json.write(value);
        } catch (IllegalArgumentException notJson) {
            respondFailure(replyTo, request, new IllegalStateException(request.method() + " " + request.path() + " answered something that is not JSON: " + notJson.getMessage()));
            return;
        }
        respond(replyTo, 200, "application/json", json);
    }

    private void respondFailure(String replyTo, PageRequest request, Throwable error) {
        int status = switch (error) {
            case HttpException http -> http.status();
            case IllegalArgumentException bad -> 400;
            default -> 500;
        };
        if (status >= 500) LOG.log(System.Logger.Level.WARNING, request.method() + " " + request.path() + " failed", error);
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        respond(replyTo, status, "text/plain", message);
    }

    private void respond(String replyTo, int status, String contentType, String body) {
        tell(Message.of(replyTo).with("Status", String.valueOf(status)).withText(body, contentType));
    }

    // ---- events ----

    /** Events of one kind from every view; {@code WebViewEvent.class} for all of them. */
    public <E extends WebViewEvent> Subscription subscribe(Class<E> type, Subscriber<? super E> subscriber) {
        return subscribe(type, null, subscriber);
    }

    <E extends WebViewEvent> Subscription subscribe(Class<E> type, String view, Subscriber<? super E> subscriber) {
        Sub<E> sub = new Sub<>(Objects.requireNonNull(type, "type"), view, Objects.requireNonNull(subscriber, "subscriber"));
        subscriptions.add(sub);
        return sub;
    }

    /** Reads the inbox in order: completes replies, runs commands, publishes events. Never runs user code itself. */
    private void dispatch() {
        while (true) {
            Object next;
            try {
                next = inbox.take();
            } catch (InterruptedException e) {
                return;
            }
            if (next == STOPPED) {
                stop();
                return;
            }
            Message message;
            try {
                byte[] frame = (byte[]) next;
                meter.frameIn(frame.length);
                message = Message.decode(frame);
            } catch (Message.FrameException e) {
                LOG.log(System.Logger.Level.WARNING, "dropped a frame the native side sent: " + e.kind());
                continue;
            }
            try {
                route(message);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "dropped a message that could not be read: " + message, e);
            }
        }
    }

    /** What each subject from the native side means (PROTOCOL.md §2.2). */
    private void route(Message message) {
        String subject = message.subject();
        if (subject.startsWith("_inbox.")) {
            reply(subject, message);
            return;
        }
        String[] tokens = subject.split("\\.");
        if (subject.equals("app.ready")) {
            if (!PROTOCOL.equals(message.header("Protocol"))) {
                ready.completeExceptionally(new NativeLibraryException("the native library speaks protocol "
                        + message.header("Protocol") + ", this jar " + PROTOCOL,
                        "Use swaswa-ffi's jar and its native library of the same version (an old library may be left in -Dswaswa.library)."));
                quit();
                return;
            }
            // The browser engine starts now, while the app gets ready, not when its first window opens: that
            // window then takes ~100 ms instead of ~450 (WebView2). -Dswaswa.prewarm=false leaves it for then.
            if (!"false".equals(System.getProperty("swaswa.prewarm"))) {
                // on the windows' own profile: an engine started on another would be of no use to them
                request(Message.of("app.warm").withJson(Map.of("dataDirectory", WindowOptions.defaultDataDirectory().toString()))).exceptionally(e -> {
                    LOG.log(System.Logger.Level.DEBUG, "the browser engine did not start early: {0}", e.getMessage());
                    return null;
                });
            }
            ready.complete(this);
            publish(new Ready());
        } else if (tokens.length == 3 && tokens[0].equals("egui") && tokens[2].equals("closed")) {
            Runnable then = eguiClosed.remove(tokens[1]);
            if (then != null) then.run();
        } else if (subject.equals("app.log")) {
            publish(Events.nativeLog(object(message)));
        } else if (tokens.length >= 3 && tokens[0].equals("view")) {
            String view = tokens[1];
            String kind = tokens[2];
            if (kind.equals("closed") && tokens.length == 3) {
                publish(new Closed(view));
            } else if (kind.equals("close-requested") && tokens.length == 3) {
                publish(new CloseRequested(view));
            } else if (kind.equals("request") && tokens.length == 3) {
                request(view, message);
            } else if (kind.equals("asset") && tokens.length == 3) {
                asset(message);
            } else if (kind.equals("invoke") && tokens.length > 3) {
                invoke(view, subject.substring(("view." + view + ".invoke.").length()), message);
            } else if (tokens.length == 3) {
                WebViewEvent event = Events.fromView(view, kind, object(message));
                if (event != null) publish(event);
            }
        }
    }

    private void publish(WebViewEvent event) {
        for (Sub<?> sub : subscriptions) sub.offer(event);
    }

    /** A page's call answered: timed, and told to those watching calls ({@link PageCall}). */
    private void called(String viewId, String name, Object args, Object result, Throwable error, long received) {
        long took = System.nanoTime() - received;
        meter.pageCall(took);
        if (subscriptions.isEmpty()) return;
        String why = error == null ? null : error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        publish(new PageCall(viewId, name, args, error == null ? result : null, why, took / 1_000_000.0));
    }

    private void reply(String inbox, Message reply) {
        Pending waiting = pending.remove(inbox);
        if (waiting == null) return;
        meter.action(waiting.subject(), System.nanoTime() - waiting.sentAt());
        if ("200".equals(reply.header("Status"))) {
            virtualThreads.execute(() -> waiting.reply().complete(reply));
        } else {
            WebViewException failure = WebViewException.fromReply(waiting.subject(), reply);
            virtualThreads.execute(() -> waiting.reply().completeExceptionally(failure));
        }
    }

    private void stop() {
        WebViewException quit = new NotRunningException("the app has quit",
                "Its windows are gone and nothing more can be done with it: start a new App if the program goes on.");
        ready.completeExceptionally(quit);
        for (String inbox : List.copyOf(pending.keySet())) {
            Pending waiting = pending.remove(inbox);
            if (waiting != null) waiting.reply().completeExceptionally(quit);
        }
        subscriptions.forEach(Sub::close);
        stopped.complete(exitCode);
        virtualThreads.shutdown();
    }

    /** One subscriber: its filter, and the serial executor that keeps its events in order. */
    private final class Sub<E extends WebViewEvent> implements Subscription {
        private final Class<E> type;
        private final String view;
        private final Subscriber<? super E> subscriber;
        private final SerialExecutor executor = new SerialExecutor(virtualThreads);
        private volatile boolean active = true;

        Sub(Class<E> type, String view, Subscriber<? super E> subscriber) {
            this.type = type;
            this.view = view;
            this.subscriber = subscriber;
        }

        void offer(WebViewEvent event) {
            if (!active || !type.isInstance(event) || (view != null && !view.equals(event.view()))) return;
            E typed = type.cast(event);
            executor.execute(() -> {
                if (!active) return;
                try {
                    subscriber.on(typed);
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.ERROR, "a subscriber failed on " + typed, e);
                }
            });
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
            subscriptions.remove(this);
        }
    }
}
