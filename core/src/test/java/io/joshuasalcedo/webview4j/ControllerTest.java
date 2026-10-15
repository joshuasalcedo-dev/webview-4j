package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.sample.UserController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * A @WebViewController compiled with the annotation processor (sample.UserController), registered
 * with App.register and called from a real, hidden page over the custom protocol.
 */
@EnabledOnOs(OS.WINDOWS)
class ControllerTest {

    /** Runs a request in the page and logs "<label> <status> <answer>". */
    private static String request(String label, String method, String path, String query, String body) {
        return "window.__webview4j.request('" + method + "', '" + path + "', " + query + ", " + body + ")"
                + ".then(v => console.info('" + label + " 200 ' + JSON.stringify(v)),"
                + " e => console.info('" + label + " ' + e.status + ' ' + e.message))";
    }

    @Test
    @Timeout(90)
    void pagesCallRoutesOnARegisteredController() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<String> console = new LinkedBlockingQueue<>();
            app.subscribe(LogRecord.class, log -> {
                if (log.source() == LogRecord.Source.PAGE) console.add(log.message());
            });
            app.register(new UserController());
            assertThrows(IllegalStateException.class, () -> app.register(new UserController()), "the same routes twice clash");
            assertThrows(IllegalArgumentException.class, () -> app.register(new Object()), "no generated routes for a plain object");

            View view = app.open(WindowOptions.html("<p>controllers</p>").headless(true)).join();

            view.eval(request("create", "POST", "/users", "undefined",
                    "{name: 'Ada', role: 'ADMIN', tags: ['math'], address: {city: 'London', lines: ['12 St']}}")).join();
            assertEquals("create 200 {\"id\":1,\"name\":\"Ada\",\"role\":\"ADMIN\",\"tags\":[\"math\"],\"address\":{\"city\":\"London\",\"lines\":[\"12 St\"]}}",
                    console.poll(15, SECONDS), "a record body in, an async record answer out");
            view.eval(request("create 2", "POST", "/users", "undefined", "{name: 'Grace', role: 'MEMBER', tags: [], address: null}")).join();
            assertEquals("create 2 200 {\"id\":2,\"name\":\"Grace\",\"role\":\"MEMBER\",\"tags\":[],\"address\":null}",
                    console.poll(15, SECONDS), "an empty Optional is null");

            view.eval(request("find", "GET", "/users/2", "undefined", "undefined")).join();
            assertTrue(console.poll(15, SECONDS).startsWith("find 200 {\"id\":2,\"name\":\"Grace\""));
            view.eval(request("search", "GET", "/users", "{role: 'ADMIN', max: 5}", "undefined")).join();
            assertTrue(console.poll(15, SECONDS).startsWith("search 200 [{\"id\":1,\"name\":\"Ada\""), "query parameters, an enum among them");
            view.eval(request("me", "GET", "/users/me", "undefined", "undefined")).join();
            assertEquals("me 200 \"the page in " + view.id() + "\"", console.poll(15, SECONDS),
                    "a literal segment beats {id}, and the View is the one the request came from");
            view.eval(request("rename", "PUT", "/users/2/name", "undefined", "'Hopper'")).join();
            assertTrue(console.poll(15, SECONDS).startsWith("rename 200 {\"id\":2,\"name\":\"Hopper\""));
            view.eval(request("delete", "DELETE", "/users/2", "undefined", "undefined")).join();
            assertEquals("delete 200 null", console.poll(15, SECONDS), "void answers no content");

            // errors carry their status
            view.eval(request("gone", "GET", "/users/2", "undefined", "undefined")).join();
            assertEquals("gone 404 no user 2", console.poll(15, SECONDS));
            view.eval(request("bad id", "GET", "/users/abc", "undefined", "undefined")).join();
            assertEquals("bad id 400 id must be a whole number, not \"abc\"", console.poll(15, SECONDS));
            view.eval(request("bad body", "POST", "/users", "undefined", "{name: 'X', role: 'OWNER', tags: [], address: null}")).join();
            assertEquals("bad body 400 body.role must be one of [ADMIN, MEMBER], not \"OWNER\"", console.poll(15, SECONDS));
            view.eval(request("no name", "POST", "/users", "undefined", "{name: '', role: 'MEMBER', tags: [], address: null}")).join();
            assertEquals("no name 400 a user needs a name", console.poll(15, SECONDS), "IllegalArgumentException is 400");
            view.eval(request("nowhere", "GET", "/orders", "undefined", "undefined")).join();
            assertEquals("nowhere 404 no route GET /orders", console.poll(15, SECONDS));
            view.eval(request("wrong method", "PATCH", "/users/1", "undefined", "{}")).join();
            assertTrue(console.poll(15, SECONDS).startsWith("wrong method 405 PATCH /users/1 is not allowed"));
        }
    }

    @Test
    void theProcessorWroteTheTypeScriptClient() throws Exception {
        String client = Files.readString(Path.of("build/generated/webview4j/api.ts"));
        assertTrue(client.contains("export const users = {"), "named after its path, /users");
        assertTrue(client.contains("export interface User {"), client);
        assertTrue(client.contains("export type Role = \"ADMIN\" | \"MEMBER\";"), client);
        assertTrue(client.contains("find(id: number): Promise<User> {"), client);
        assertTrue(client.contains("search(name?: string | null, role?: Role | null, limit?: number | null): Promise<User[]> {"), client);
        assertTrue(client.contains("me(): Promise<string> {"), "View is filled in by Java, not the page");
    }
}
