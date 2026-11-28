package io.joshuasalcedo.swaswa.webview;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.HttpException;
import io.joshuasalcedo.swaswa.ffi.PageRequest;
import io.joshuasalcedo.swaswa.ffi.PageRequestHandler;
import java.util.Objects;

/**
 * An app's {@code @WebViewController}s, answering its pages' requests: {@code GET /users/42} runs the route
 * {@code GET /users/{id}}, with what the processor generated for its class ({@code <Controller>_Routes}). Installed on
 * an app with {@link #on(App)}; {@link WebViewApplication} does it for its own.
 * <pre>{@code
 * Controllers.on(app).register(new UsersController()).register(new OrdersController());
 * }</pre>
 */
public final class Controllers implements PageRequestHandler {

    private final Router router = new Router();

    private Controllers() {}

    /** New controllers answering {@code app}'s pages' requests (in place of what answered them before). */
    public static Controllers on(App app) {
        Controllers controllers = new Controllers();
        Objects.requireNonNull(app, "app").onPageRequest(controllers);
        return controllers;
    }

    /**
     * Makes {@code controller}'s routes answer, from the route table the processor generated for its class
     * ({@code <Controller>_Routes}). In a named module, the controller's package must be readable by
     * {@code io.joshuasalcedo.swaswa.webview} (exported or opened to it); otherwise use {@link #register(Routes, Object)}.
     *
     * @throws IllegalArgumentException if no route table was generated for the controller's class
     * @throws IllegalStateException    if a route clashes with one already registered
     */
    @SuppressWarnings("unchecked")
    public <T> Controllers register(T controller) {
        Objects.requireNonNull(controller, "controller");
        Class<?> type = controller.getClass();
        String generated = type.getPackageName().isEmpty() ? "" : type.getPackageName() + ".";
        generated += type.getName().substring(generated.length()).replace('$', '_') + "_Routes";
        Routes<T> routes;
        try {
            routes = (Routes<T>) Class.forName(generated, true, type.getClassLoader()).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("no route table for " + type.getName()
                    + ": is it a @WebViewController compiled with swaswa-webview's processor?", e);
        }
        return register(routes, controller);
    }

    /** Makes these routes answer, calling {@code controller}. */
    public <T> Controllers register(Routes<T> routes, T controller) {
        Objects.requireNonNull(routes, "routes");
        if (!routes.controller().isInstance(controller))
            throw new IllegalArgumentException(controller + " is not a " + routes.controller().getName());
        for (Route route : routes.routes(controller)) router.add(route);
        return this;
    }

    @Override
    public Object handle(PageRequest request) throws Exception {
        return switch (router.match(request.method(), request.path())) {
            case Router.Match.NotFound notFound ->
                    throw new HttpException(404, "no route " + request.method() + " " + request.path());
            case Router.Match.NotAllowed notAllowed ->
                    throw new HttpException(405, request.method() + " " + request.path() + " is not allowed; it answers " + notAllowed.allowed());
            case Router.Match.Found found -> found.handler().handle(new RouteRequest(request.view(), request.method(), request.path(),
                    found.pathParams(), Router.query(request.rawQuery()), request.body()));
        };
    }
}
