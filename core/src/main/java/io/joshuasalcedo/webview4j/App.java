package io.joshuasalcedo.webview4j;

import java.nio.file.Path;
import java.util.ArrayList;
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
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final CompletableFuture<App> ready = new CompletableFuture<>();
    private final CompletableFuture<Integer> stopped = new CompletableFuture<>();
    private volatile int exitCode;

    /** A request waiting for its reply: what it asked, for error messages. */
    private record Pending(String subject, CompletableFuture<Message> reply) {}

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
        return request(Message.of("app.open").withJson(options.toPayload()))
                .thenApply(reply -> new View(this, Events.text(object(reply), "view")));
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

    /**
     * Sends {@code message} with a fresh {@code Reply-To} and completes with the reply, or fails
     * with a {@link WebViewException} when its {@code Status} is not 200. Package-private on purpose:
     * raw messages could answer a page's call with forged data or run unchecked scripts. The typed
     * methods on {@code App} and {@link View} are the API, and are built on this.
     */
    CompletableFuture<Message> request(Message message) {
        String inbox = "_inbox." + inboxes.incrementAndGet();
        CompletableFuture<Message> reply = new CompletableFuture<>();
        pending.put(inbox, new Pending(message.subject(), reply));   // before sending: the reply may beat us back
        int sent;
        try {
            sent = Native.send(message.with("Reply-To", inbox).encode());
        } catch (RuntimeException e) {
            pending.remove(inbox);
            reply.completeExceptionally(e);
            return reply;
        }
        if (sent != Native.SENT) {
            pending.remove(inbox);
            reply.completeExceptionally(new WebViewException(message.subject() + ": "
                    + (sent == Native.NOT_RUNNING ? "the event loop is not running" : "the message is malformed or too large")));
        }
        return reply;
    }

    /** Sends a message nobody answers, e.g. the answer to a page's call. */
    private void tell(Message message) {
        int sent = Native.send(message.encode());
        if (sent != Native.SENT) LOG.log(System.Logger.Level.DEBUG, "could not send " + message.subject() + ": " + sent);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Message reply) {
        return (Map<String, Object>) reply.json();
    }

    /** Closes every window and stops the event loop; {@link #whenStopped()} completes after. False if it is not running. */
    public boolean quit() {
        return Native.send(Message.of("app.quit").encode()) == Native.SENT;
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
        virtualThreads.execute(() -> {
            Object result;
            try {
                result = handler.handle(new Invocation(view, command, call.json()));
            } catch (Exception e) {
                answer(view, replyTo, null, e);
                return;
            }
            if (result instanceof CompletionStage<?> stage) {
                stage.whenComplete((value, error) -> answer(view, replyTo, value,
                        error instanceof CompletionException c && c.getCause() != null ? c.getCause() : error));
            } else {
                answer(view, replyTo, result, null);
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
                message = Message.decode((byte[]) next);
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
                ready.completeExceptionally(new WebViewException("the native library speaks protocol "
                        + message.header("Protocol") + ", this jar " + PROTOCOL + ": use the same version of both"));
                quit();
                return;
            }
            ready.complete(this);
            publish(new Ready());
        } else if (subject.equals("app.log")) {
            publish(Events.nativeLog(object(message)));
        } else if (tokens.length >= 3 && tokens[0].equals("view")) {
            String view = tokens[1];
            String kind = tokens[2];
            if (kind.equals("closed") && tokens.length == 3) {
                publish(new Closed(view));
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

    private void reply(String inbox, Message reply) {
        Pending waiting = pending.remove(inbox);
        if (waiting == null) return;
        if ("200".equals(reply.header("Status"))) {
            virtualThreads.execute(() -> waiting.reply().complete(reply));
        } else {
            WebViewException failure = new WebViewException(waiting.subject() + ": " + reply.text());
            virtualThreads.execute(() -> waiting.reply().completeExceptionally(failure));
        }
    }

    private void stop() {
        WebViewException quit = new WebViewException("the app has quit");
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
