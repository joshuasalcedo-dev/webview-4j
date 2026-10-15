package io.joshuasalcedo.webview4j;

import java.util.List;

/**
 * The routes of a controller. The webview-4j annotation processor generates one, named
 * {@code <Controller>_Routes} next to it, for every {@code @WebViewController}.
 */
public interface Routes<T> {

    /** The controller class these routes call. */
    Class<T> controller();

    /** The routes, each calling {@code controller}. */
    List<Route> routes(T controller);
}
