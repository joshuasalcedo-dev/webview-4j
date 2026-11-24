package io.joshuasalcedo.webview4j.spring.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

/**
 * A page of a {@link WebViewApplication}'s main window, at a path (Next.js' {@code page.tsx}): a bean that is a
 * {@link io.joshuasalcedo.webview4j.ui.PageView} (it gets the {@link io.joshuasalcedo.webview4j.ui.PageContext}: its
 * slugs, its query, navigation) or a {@link io.joshuasalcedo.webview4j.ui.Screen}. It can inject the app's clients.
 * <pre>{@code
 * @Page(value = "/users", title = "Users", section = "Admin")
 * class UsersPage implements PageView {
 *     private final UsersApi users;
 *     UsersPage(UsersApi users) { this.users = users; }
 *
 *     public Component<?> render(PageContext page) {
 *         return page.load("users", users::getUsers)
 *                 .<Component<?>>map(list -> list("Users", list.stream()
 *                         .map(u -> listItem(u.name()).onSelect(() -> page.navigate("/users/" + u.id()))).toList()))
 *                 .orElseGet(() -> spinner("Loading users"));
 *     }
 * }
 *
 * @Page("/users/{id}")   // a slug: page.param("id"); not in the side bar
 * class UserPage implements PageView { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Page {

    /** The bean's name; made from the class's name when empty (the path is not a name). */
    @AliasFor(annotation = Component.class, attribute = "value")
    String name() default "";

    /** The path: {@code /users}, {@code /users/{id}} (a slug), {@code /docs/{*rest}} (the rest of the path). */
    String value();

    /** Its name in the side bar and the toolbar; made from the class's name when empty ("UsersPage" is "Users"). */
    String title() default "";

    /** Its group in the side bar. */
    String section() default "";

    /** Its place in the side bar: lower first. */
    int order() default 0;

    /** Whether it is in the side bar; a page with a slug never is. */
    boolean nav() default true;
}
