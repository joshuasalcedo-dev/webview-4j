package io.joshuasalcedo.webview4j.spring.client;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.LayoutView;
import io.joshuasalcedo.webview4j.ui.PageView;
import io.joshuasalcedo.webview4j.ui.Router;
import io.joshuasalcedo.webview4j.ui.Screen;
import java.util.concurrent.CompletableFuture;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** A {@link WebViewApplication}'s main window: a {@link Router} of its {@link Page} and {@link Layout} beans. */
public final class PagesWindow {

    private PagesWindow() {}

    /** The app's pages and layouts, from its beans; a start page that says what to do when there is none. */
    public static Router router(ApplicationContext context, String title, boolean development) {
        Router router = new Router().title(title);
        int pages = 0;
        for (Object bean : context.getBeansWithAnnotation(Page.class).values()) {
            Class<?> type = AopUtils.getTargetClass(bean);
            Page page = AnnotatedElementUtils.findMergedAnnotation(type, Page.class);
            if (page == null) continue;
            PageView view = switch (bean) {
                case PageView v -> v;
                case Screen s -> p -> s.render();
                default -> throw new IllegalStateException(type.getName() + " is a @Page: make it a PageView or a Screen");
            };
            boolean slug = page.value().contains("{");
            if (page.nav() && !slug) {
                String name = page.title().isEmpty() ? Values.words(type.getSimpleName().replaceAll("Page$", "")) : page.title();
                router.page(new Router.Entry(page.value(), name, page.section(), page.order()), view);
            } else {
                router.page(page.value(), view);
            }
            pages++;
        }
        for (Object bean : context.getBeansWithAnnotation(Layout.class).values()) {
            Class<?> type = AopUtils.getTargetClass(bean);
            Layout layout = AnnotatedElementUtils.findMergedAnnotation(type, Layout.class);
            if (layout == null) continue;
            if (!(bean instanceof LayoutView view)) throw new IllegalStateException(type.getName() + " is a @Layout: make it a LayoutView");
            router.layout(layout.value(), view);
        }
        if (pages == 0) {
            router.page(new Router.Entry("/", "Start", "", 0), p -> column(
                    title(title),
                    text("This app has no pages yet. A page is a bean:").tone(Tone.SECONDARY),
                    code("""
                            @Page(value = "/", title = "Home")
                            class HomePage implements PageView {
                                public Component<?> render(PageContext page) {
                                    return title("Hello");
                                }
                            }""", "java"),
                    text(development
                            ? "The developer window beside this one shows the server's API as forms, with the code to put each in a page."
                            : "Run it from your IDE to see the developer window: the server's API as forms, with the code for each.")
                            .tone(Tone.SECONDARY)).padding(24).gap(12));
        }
        return router;
    }

    public static CompletableFuture<View> open(App app, Router router, double width, double height, boolean headless) {
        return router.open(app, o -> o.size(width, height).minSize(640, 420).decorations(false).headless(headless))
                .thenApply(window -> window.view());
    }
}
