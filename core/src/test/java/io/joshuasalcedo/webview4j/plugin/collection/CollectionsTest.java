package io.joshuasalcedo.webview4j.plugin.collection;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** DataList, DataTree, DataMap and DataGrid from Java, in a real window, and what the person does back in Java. */
@EnabledOnOs(OS.WINDOWS)
class CollectionsTest {

    record Person(String id, String name, String email, String role) {}

    record Team(String id, String name, int people, List<Team> children, boolean hasChildren) {
        Team(String id, String name, int people) {
            this(id, name, people, null, true);   // children not loaded yet
        }
    }

    static final class Everything implements Screen {
        final List<Person> people = List.of(new Person("1", "Ana Reyes", "ana@example.com", "Admin"), new Person("2", "Grace Hopper", "grace@example.com", "Chatter"));
        List<Team> teams = List.of(new Team("eng", "Engineering", 12));
        final Map<String, Object> settings = new LinkedHashMap<>(Map.of("port", 8080L, "database", Map.of("url", "jdbc:postgresql://db/office")));
        String opened = "";
        List<String> chosen = List.of();
        String edited = "";

        @Override
        public Component<?> render() {
            return column(
                    DataList.of("People", people, "name").subtitle("email").avatar("name").pill("role").onOpen(id -> opened = "person " + id),
                    DataTree.of("Teams", teams).onExpand(id -> teams = List.of(new Team("eng", "Engineering", 12,
                            List.of(new Team("web", "Web team", 5, List.of(), false)), true))),
                    DataMap.of("Settings", settings).open(2).onEdit(e -> {
                        settings.put(e.path(), e.value());
                        edited = e.path() + "=" + e.value();
                    }),
                    DataGrid.of("Cards", people, "name").subtitle("role").onSelectionChange(ids -> chosen = ids),
                    text("opened " + opened + " chosen " + chosen + " edited " + edited)).padding(16).gap(16).scroll();
        }
    }

    @Test
    @Timeout(120)
    void eachCollectionShowsJavasDataAndTellsJavaWhatWasDone() throws Exception {
        try (App app = App.start()) {
            Everything screen = new Everything();
            View view = Ui.open(app, screen, o -> o.headless(true).size(1100, 1200)).join().view();
            String text = until(view, "Grace Hopper");
            assertTrue(text.contains("grace@example.com") && text.contains("Engineering") && text.contains("jdbc:postgresql://db/office"), text);

            // the list: an item opened
            view.eval("[...document.querySelectorAll('li')].find(li => li.innerText.includes('Ana Reyes')).dispatchEvent(new MouseEvent('dblclick', {bubbles: true}))").join();
            until(view, "opened person 1");

            // the tree: a row opened, its children loaded by Java
            view.eval("document.querySelector('[role=treegrid] button[aria-label=Open]').click()").join();
            until(view, "Web team");

            // the map: a value edited in place
            view.eval("""
                    (() => { const row = [...document.querySelectorAll('.nt-datamap-row')].find(r => r.innerText.startsWith('port'));
                      row.querySelector('.nt-datamap-value').dispatchEvent(new MouseEvent('dblclick', {bubbles: true})); })()
                    """).join();
            Thread.sleep(200);
            view.eval("""
                    (() => { const input = document.querySelector('.nt-datamap-value input');
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(input, '9090'); input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true})); })()
                    """).join();
            until(view, "edited port=9090");
            assertEquals(9090L, ((Number) screen.settings.get("port")).longValue(), "JSON read: a number");

            // the grid: a card chosen
            view.eval("[...document.querySelectorAll('.nt-datagrid-card')].find(c => c.innerText.includes('Grace')).click()").join();
            until(view, "chosen [2]");
            assertEquals(List.of("2"), screen.chosen);
        }
    }

    static String until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 150; i++) {
            text = String.valueOf(view.eval("document.body.innerText").join());
            if (text.contains(part)) return text;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
