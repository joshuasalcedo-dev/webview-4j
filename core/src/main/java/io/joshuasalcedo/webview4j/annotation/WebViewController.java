package io.joshuasalcedo.webview4j.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A class whose {@link Get}/{@link Post}/{@link Put}/{@link Patch}/{@link Delete} methods pages
 * can call. The webview-4j annotation processor generates, at compile time, the route table
 * {@code App.register(controller)} uses (no reflection), and a typed TypeScript client for the page.
 * <pre>{@code
 * @WebViewController("/users")
 * public class UserController {
 *     @Get("/{id}") public User find(@Path long id) { ... }
 *     @Post public CompletableFuture<User> create(@Body CreateUser body) { ... }
 * }
 * }</pre>
 * The page then calls {@code await users.find(42)}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface WebViewController {

    /** The path every route of this controller starts with, e.g. {@code "/users"}. */
    String value() default "";

    /**
     * The name of this controller in the TypeScript client. By default the path's last fixed
     * segment in camelCase ({@code "/users"} is {@code users}, {@code "/api/v1/order-items"} is
     * {@code orderItems}), or, without a path, the class name without "Controller".
     */
    String name() default "";
}
