package io.joshuasalcedo.swaswa.webview;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Closed;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * An app that brings its own web page (React, Vue, plain HTML...), which calls Java the way a page calls a server:
 * {@code @WebViewController}s answer it. The webview feature of swaswa; for a UI made in Java, use swaswa-core.
 * <pre>{@code
 * public final class Shop extends WebViewApplication {
 *     public void start(App app) {
 *         open(WindowOptions.app("web").title("Shop"), new OrdersController(), new UsersController());
 *     }
 *     public static void main(String[] args) { launch(args); }
 * }
 * }</pre>
 * {@code WindowOptions.app("web")} shows the page in the class path's {@code web/} folder (its {@code index.html});
 * {@code WindowOptions.url(...)} a page served elsewhere (a dev server). The app ends when its last window closes.
 */
public abstract class WebViewApplication {

    private volatile App app;
    private volatile Controllers controllers;

    /** Before the native side starts: read settings, open connections. */
    public void init() throws Exception {}

    /** The app is ready: open its windows. Runs on a virtual thread, so it may block. */
    public abstract void start(App app) throws Exception;

    /** After the last window closed and the event loop stopped. */
    public void stop() throws Exception {}

    /** The running app (null before {@link #start}). */
    public final App getApp() {
        return app;
    }

    /** What answers this app's pages' requests: {@code controllers().register(new UsersController())}. */
    protected final Controllers controllers() {
        Controllers c = controllers;
        if (c == null) throw new IllegalStateException("the app is not running yet: register controllers in start(App)");
        return c;
    }

    /** Opens a window showing {@code page}, with {@code controllers} answering its requests (with those registered before). */
    protected final CompletableFuture<View> open(WindowOptions page, Object... controllers) {
        Objects.requireNonNull(page, "page");
        for (Object controller : controllers) controllers().register(controller);
        return app.open(page);
    }

    /** Starts the class that called it (a {@code WebViewApplication}): {@code init}, {@code start}, the event loop, {@code stop}. */
    public static int launch(String... args) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if (!WebViewApplication.class.isAssignableFrom(caller))
            throw new IllegalStateException("launch(args) is called from a WebViewApplication's main: " + caller.getName());
        @SuppressWarnings("unchecked")
        Class<? extends WebViewApplication> type = (Class<? extends WebViewApplication>) caller;
        return launch(type, args);
    }

    /** Starts {@code type} (with a constructor that takes nothing). */
    public static int launch(Class<? extends WebViewApplication> type, String... args) {
        WebViewApplication application;
        try {
            Constructor<? extends WebViewApplication> made = type.getDeclaredConstructor();
            made.setAccessible(true);
            application = made.newInstance();
            application.init();
        } catch (Exception e) {
            throw new IllegalStateException("could not start " + type.getName(), e);
        }
        int code = App.run(ready -> {
            application.app = ready;
            application.controllers = Controllers.on(ready);
            ready.subscribe(Closed.class, closed -> ready.views().thenAccept(open -> { if (open.isEmpty()) ready.quit(); }));
            try {
                application.start(ready);
            } catch (Exception e) {
                System.getLogger(type.getName()).log(System.Logger.Level.ERROR, "start failed", e);
                ready.quit();
            }
        });
        try {
            application.stop();
        } catch (Exception e) {
            System.getLogger(type.getName()).log(System.Logger.Level.WARNING, "stop failed", e);
        }
        return code;
    }
}
