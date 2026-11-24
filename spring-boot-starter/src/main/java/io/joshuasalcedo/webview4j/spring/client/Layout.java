package io.joshuasalcedo.webview4j.spring.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * A layout of a {@link WebViewApplication}'s main window (Next.js' {@code layout.tsx}): a bean that is a
 * {@link io.joshuasalcedo.webview4j.ui.LayoutView}, wrapping every page at its path or under it. One at {@code /}
 * replaces the default frame (title bar, side bar, toolbar, status bar): return a {@code frame(...)} from it.
 * <pre>{@code
 * @Layout("/settings")
 * class SettingsLayout implements LayoutView {
 *     public Component<?> render(PageContext page, Component<?> content) {
 *         return column(title("Settings"), content).padding(24);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Layout {

    /** The path it wraps, and everything under it. */
    String value() default "/";
}
