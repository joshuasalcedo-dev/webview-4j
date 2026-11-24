package io.joshuasalcedo.webview4j.ui;

/**
 * One page of an app with several: what shows at a path such as {@code /users} or {@code /users/{id}} (Next.js'
 * {@code page.tsx}). Its state is in its fields, as a {@link Screen}'s; the {@link PageContext} says which path it is
 * at (its slugs, its query) and moves to another.
 * <pre>{@code
 * router.page("/users/{id}", page -> column(
 *         title("User " + page.param("id")),
 *         button("Back to the list").onClick(() -> page.navigate("/users"))));
 * }</pre>
 */
@FunctionalInterface
public interface PageView {

    /** What to show now, at {@code page.path()}. */
    Component<?> render(PageContext page);
}
