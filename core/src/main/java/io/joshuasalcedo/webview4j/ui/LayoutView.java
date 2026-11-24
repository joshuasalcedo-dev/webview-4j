package io.joshuasalcedo.webview4j.ui;

/**
 * What every page under a path is shown in (Next.js' {@code layout.tsx}): a layout at {@code /} wraps the whole
 * app (its title bar, side bar, status bar: return a {@code frame(...)}), one at {@code /settings} wraps the pages
 * under {@code /settings} inside it. The pages' content arrives as {@code content}.
 * <pre>{@code
 * router.layout("/settings", (page, content) -> column(title("Settings"), content).padding(24));
 * }</pre>
 */
@FunctionalInterface
public interface LayoutView {

    Component<?> render(PageContext page, Component<?> content);
}
