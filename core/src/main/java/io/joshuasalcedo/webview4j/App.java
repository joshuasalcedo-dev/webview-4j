package io.joshuasalcedo.webview4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>Nothing the app calls back runs on the event loop's thread: events go to a dispatcher that
 * hands each {@link Subscriber} its own virtual thread, and futures complete on virtual threads.
 */
public final class App implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(App.class.getName());
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** Put in the inbox once the loop has returned. */
    private static final Object STOPPED = new Object();

    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final List<Sub<?>> subscriptions = new CopyOnWriteArrayList<>();
    private final Map<String, CommandHandler> commands = new ConcurrentHashMap<>();
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final CompletableFuture<App> ready = new CompletableFuture<>();
    private final CompletableFuture<Integer> stopped = new CompletableFuture<>();
    private volatile int exitCode;

    private record Pending(Action action, CompletableFuture<Map<String, Object>> reply) {}

    private App() {
        Thread.ofVirtual().name("webview4j-dispatcher").start(this::dispatch);
    }

    /**
     * Starts the event loop on a new thread and returns once it is ready. Not on macOS, where the
     * loop needs the main thread: use {@link #run} there.
     *
     * @throws WebViewException if a loop already runs or it does not start within 30 seconds
     */
    public static App start() {
        App app = new App();
        Thread.ofPlatform().name("webview4j-event-loop").start(() -> {
            try {
                app.loop();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "the event loop failed", e);
            }
        });
        try {
            return app.ready.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new WebViewException("the event loop did not start", e.getCause());
        } catch (TimeoutException e) {
            app.quit();
            throw new WebViewException("the event loop did not start within 30 seconds", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebViewException("interrupted while starting the event loop", e);
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
            WebViewException running = new WebViewException("an App is already running in this process");
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
        } finally {
            RUNNING.set(false);
            inbox.add(STOPPED);
        }
    }

    // ---- actions ----

    /** Opens a window; the future completes once it exists. */
    public CompletableFuture<View> open(WindowOptions options) {
        return submit(WindowAction.OPEN, null, options.toPayload())
                .thenApply(reply -> new View(this, Events.text(reply, "id")));
    }

    /** A handle on a view by its id, e.g. from an event. */
    public View view(String id) {
        return new View(this, id);
    }

    /** The open views. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<ViewInfo>> views() {
        return submit(WindowAction.LIST, null, null).thenApply(reply -> {
            List<ViewInfo> views = new ArrayList<>();
            for (Object item : (List<Object>) reply.getOrDefault("webviews", List.of())) {
                Map<String, Object> v = (Map<String, Object>) item;
                String directory = Events.text(v, "dataDirectory");
                views.add(new ViewInfo(Events.text(v, "id"), Events.text(v, "title"), Events.text(v, "url"),
                        directory == null ? null : Path.of(directory)));
            }
            return List.copyOf(views);
        });
    }

    /**
     * Any action, with a JSON-able payload (maps, lists, strings, numbers, booleans); the future
     * gets the reply object. Package-private on purpose: raw actions could answer a page's call
     * with forged data ({@code RESOLVE}) or run unchecked scripts. The typed methods on
     * {@code App} and {@link View} are the API, and are built on this.
     */
    CompletableFuture<Map<String, Object>> submit(Action action, String view, Map<String, ?> payload) {
        Objects.requireNonNull(action, "action");
        long request = requests.incrementAndGet();
        CompletableFuture<Map<String, Object>> reply = new CompletableFuture<>();
        pending.put(request, new Pending(action, reply));   // before submitting: the reply may beat us back
        String refused;
        try {
            refused = Native.submit(action, view, payload == null ? null : Json.write(payload), request);
        } catch (RuntimeException e) {
            pending.remove(request);
            reply.completeExceptionally(e);
            return reply;
        }
        if (refused != null) {
            pending.remove(request);
            reply.completeExceptionally(failure(action, Json.object(refused)));
        }
        return reply;
    }

    /** Closes every window and stops the event loop; {@link #whenStopped()} completes after. */
    public boolean quit() {
        return Native.quit();
    }

    /** Completes with the loop's exit code once it has stopped and every pending action has failed. */
    public CompletableFuture<Integer> whenStopped() {
        return stopped;
    }

    /** Quits and waits up to 10 seconds for the loop to stop. */
    @Override
    public void close() {
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
     * {@code @webview4j/api}. Pages can call only commands registered here. Closing the returned
     * registration removes the command.
     *
     * @throws IllegalStateException if the command already has a handler
     */
    public Subscription handle(String command, CommandHandler handler) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(handler, "handler");
        if (command.isEmpty()) throw new IllegalArgumentException("a command needs a name");
        if (commands.putIfAbsent(command, handler) != null) {
            throw new IllegalStateException("'" + command + "' already has a handler");
        }
        return new Subscription() {
            @Override public boolean isActive() { return commands.get(command) == handler; }
            @Override public void close()       { commands.remove(command, handler); }
        };
    }

    /** Runs the command's handler on a virtual thread and settles the page's promise with the result. */
    private void invoke(Map<String, Object> event) {
        View view = view(Events.text(event, "view"));
        long call = Events.number(event, "call");
        String command = Events.text(event, "command");
        CommandHandler handler = commands.get(command);
        if (handler == null) {
            settle(view, call, null, new WebViewException("no command '" + command + "'"));
            return;
        }
        virtualThreads.execute(() -> {
            Object result;
            try {
                result = handler.handle(new Invocation(view, command, event.get("args")));
            } catch (Exception e) {
                settle(view, call, null, e);
                return;
            }
            if (result instanceof CompletionStage<?> stage) {
                stage.whenComplete((value, error) -> settle(view, call, value,
                        error instanceof CompletionException c && c.getCause() != null ? c.getCause() : error));
            } else {
                settle(view, call, result, null);
            }
        });
    }

    private void settle(View view, long call, Object value, Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("call", call);
        if (error == null) {
            try {
                Json.write(value);   // fail here, with the reason, rather than in the page
                payload.put("ok", true);
                payload.put("value", value);
            } catch (IllegalArgumentException notJson) {
                error = notJson;
            }
        }
        if (error != null) {
            LOG.log(System.Logger.Level.DEBUG, "a command failed in " + view, error);
            payload.put("ok", false);
            payload.put("error", error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
        }
        submit(WebViewAction.RESOLVE, view.id(), payload).exceptionally(failed -> {
            // The view may have closed meanwhile: nobody is waiting for the answer any more.
            LOG.log(System.Logger.Level.DEBUG, "could not answer a call in " + view, failed);
            return null;
        });
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

    /** Reads the inbox in order: completes replies, publishes events. Never runs user code itself. */
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
            String json = (String) next;
            try {
                Map<String, Object> event = Json.object(json);
                if ("reply".equals(event.get("type"))) {
                    reply(event);
                    continue;
                }
                if ("invoke".equals(event.get("type"))) {
                    invoke(event);
                    continue;
                }
                WebViewEvent typed = Events.from(event);
                if (typed == null) continue;
                if (typed instanceof Ready) ready.complete(this);
                for (Sub<?> sub : subscriptions) sub.offer(typed);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "dropped an event that could not be read: " + json, e);
            }
        }
    }

    private void reply(Map<String, Object> event) {
        Pending waiting = pending.remove(Events.number(event, "request"));
        if (waiting == null) return;
        if (Boolean.TRUE.equals(event.get("ok"))) {
            virtualThreads.execute(() -> waiting.reply().complete(event));
        } else {
            virtualThreads.execute(() -> waiting.reply().completeExceptionally(failure(waiting.action(), event)));
        }
    }

    private void stop() {
        WebViewException quit = new WebViewException("the app has quit");
        ready.completeExceptionally(quit);
        for (Long request : List.copyOf(pending.keySet())) {
            Pending waiting = pending.remove(request);
            if (waiting != null) waiting.reply().completeExceptionally(quit);
        }
        subscriptions.forEach(Sub::close);
        stopped.complete(exitCode);
        virtualThreads.shutdown();
    }

    private static WebViewException failure(Action action, Map<String, Object> reply) {
        return new WebViewException(action.group() + " " + action.wireName() + ": " + Events.text(reply, "error"));
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
