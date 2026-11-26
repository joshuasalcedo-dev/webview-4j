package io.joshuasalcedo.webview4j;

import io.joshuasalcedo.webview4j.plugin.devtools.DevWindow;
import io.joshuasalcedo.webview4j.plugin.devtools.LiveReload;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A webview-4j app's entry point, as JavaFX's {@code Application}: extend it, open the first window in
 * {@link #start}, and launch it from {@code main}. It runs the event loop on {@code main}'s thread (what macOS asks),
 * and returns when the app quits: when its last window closes, or on {@link #quit()}.
 * <pre>{@code
 * public class CounterApp extends WebviewApplication {
 *
 *     @Override
 *     public void start(App app) {
 *         new Counter().open(app);          // a ui.Window: components in fields, listeners on them
 *     }
 *
 *     public static void main(String[] args) {
 *         launch(args);
 *     }
 * }
 * }</pre>
 * The order: {@link #init()} before the loop starts (on {@code main}'s thread, no windows yet), {@link #start} once
 * windows can open (on a virtual thread), {@link #stop()} after the app quits. The class needs a constructor with
 * no parameters.
 *
 * <h2>While developing</h2>
 * With {@code -Dwebview4j.dev=true} (or {@code WEBVIEW4J_DEV=true}) the dev tools open beside the app, and the app
 * <b>reloads live</b>: when its source code changes ({@code src/main/java}, {@code src/main/resources}), it is
 * compiled, and the app starts again from it in the same process, a new instance of the class: its {@link #start}
 * opens the new windows, then the old ones close (the old instance's {@link #stop()} runs first, to let go of what it
 * holds). Fields and statics start over. See {@link LiveReload}.
 */
public abstract class WebviewApplication {

    private static final System.Logger LOG = System.getLogger(WebviewApplication.class.getName());

    private volatile List<String> parameters = List.of();
    private volatile App app;
    /** The instance running now: this one, or the newest a live reload made. */
    private volatile WebviewApplication current = this;
    /** While a reload swaps the windows: closing the old ones does not quit. */
    private volatile boolean reloading;

    /** Before the event loop starts: read settings, open files. No windows can open yet. */
    public void init() throws Exception {}

    /** The app is ready: open its first window here. */
    public abstract void start(App app) throws Exception;

    /** After the app quits (its last window closed, or {@link #quit()}): save what is left to save. */
    public void stop() throws Exception {}

    /** What the app was launched with: {@code main}'s arguments. */
    public final List<String> getParameters() {
        return parameters;
    }

    /** The running app, once {@link #start} is called; null before. */
    public final App getApp() {
        return app;
    }

    /** Quits the app: its windows close, and {@link #launch} returns. */
    public final void quit() {
        App running = app;
        if (running != null) running.quit();
    }

    /**
     * Launches the {@code WebviewApplication} subclass whose {@code main} calls it (JavaFX's {@code launch(args)}),
     * and returns the event loop's exit code once the app quits.
     */
    public static int launch(String... args) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if (!WebviewApplication.class.isAssignableFrom(caller)) {
            throw new IllegalStateException(caller.getName() + " is not a WebviewApplication: call launch(YourApp.class, args)");
        }
        return launch(caller.asSubclass(WebviewApplication.class), args);
    }

    /** Launches {@code type}, made with its constructor with no parameters; returns the exit code once the app quits. */
    public static int launch(Class<? extends WebviewApplication> type, String... args) {
        return make(Objects.requireNonNull(type, "type")).run(args);
    }

    private static WebviewApplication make(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (WebviewApplication) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getName() + " needs a constructor with no parameters", e);
        }
    }

    /** Runs this instance: init, the loop with start (and live reload while developing), stop. */
    public final int run(String... args) {
        parameters = List.of(args == null ? new String[0] : args);
        try {
            init();
        } catch (Exception e) {
            throw new WebViewException("init() failed", e);
        }
        // the design spec and light or dark chosen last time, for this app
        io.joshuasalcedo.webview4j.plugin.theme.Appearance.current().remember(getClass().getName());
        LiveReload[] reload = new LiveReload[1];
        int code = App.run(ready -> {
            app = ready;
            ready.subscribe(Closed.class, closed -> quitWhenNoWindowIsLeft(ready));
            if (LiveReload.wanted() && System.getProperty("webview4j.devtools") == null) {
                // while developing, every window can open the browser's developer tools (the dev window's Inspect)
                System.setProperty("webview4j.devtools", "true");
            }
            DevWindow.attachIfDev(ready);
            if (LiveReload.wanted()) {
                // the first start comes from the dev server's own compile; if it has none (no sources), from here
                reload[0] = LiveReload.start(getClass().getClassLoader(), loader -> generation(ready, loader)).orElse(null);
            }
            // no sources to watch, or they did not compile: the classes the app was launched with
            if (!startedOnce) begin(this, ready);
        });
        if (reload[0] != null) reload[0].close();
        try {
            current.stop();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "stop() failed", e);
        }
        return code;
    }

    private volatile boolean startedOnce;

    /** {@code instance}'s start, the app quitting if it fails before any window opened; false when it failed. */
    private boolean begin(WebviewApplication instance, App ready) {
        instance.parameters = parameters;
        instance.app = ready;
        // the provider interfaces it implements (DesignSpecProvider...): the plugins' from now on
        io.joshuasalcedo.webview4j.plugin.Providers.registerAll(instance);
        try {
            Thread.currentThread().setContextClassLoader(instance.getClass().getClassLoader());
            instance.start(ready);
            startedOnce = true;
            return true;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, instance.getClass().getName() + ".start() failed", e);
            if (!startedOnce) ready.quit();
            return false;
        }
    }

    /** A live reload: a new instance from {@code loader} starts, then the windows from before close. */
    private void generation(App ready, ClassLoader loader) {
        WebviewApplication next;
        try {
            next = make(Class.forName(getClass().getName(), true, loader));
        } catch (ClassNotFoundException e) {
            LOG.log(System.Logger.Level.WARNING, "live reload: " + getClass().getName() + " is not among the compiled sources");
            return;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "live reload: the new " + getClass().getSimpleName() + " could not be made", e);
            return;
        }
        reloading = true;
        try {
            List<ViewInfo> open = ready.views().get(10, TimeUnit.SECONDS);
            Set<String> before = open.stream().map(ViewInfo::id).collect(Collectors.toSet());
            // the new windows open where the old ones are: the same title, the same place and size
            for (ViewInfo view : open) {
                try {
                    ready.placeNext(view.title(), ready.view(view.id()).geometry().get(5, TimeUnit.SECONDS));
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.DEBUG, "live reload: no geometry for " + view.id(), e);
                }
            }
            WebviewApplication previous = current;
            if (startedOnce && previous != null) {
                try {
                    previous.stop();
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "stop() before the reload failed", e);
                }
            }
            current = next;
            // the windows from before close only once the new ones opened: a start that fails leaves them
            if (begin(next, ready)) for (String id : before) ready.view(id).close();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "live reload failed", e);
        } finally {
            ready.clearPlacements();
            reloading = false;
        }
    }

    private void quitWhenNoWindowIsLeft(App ready) {
        if (reloading) return;
        ready.views().thenAccept(views -> {
            if (views.isEmpty() && !reloading) ready.quit();
        });
    }

    /** The instance running now, for tests: after a live reload, not this one. */
    final Optional<WebviewApplication> current() {
        return Optional.ofNullable(current);
    }
}
