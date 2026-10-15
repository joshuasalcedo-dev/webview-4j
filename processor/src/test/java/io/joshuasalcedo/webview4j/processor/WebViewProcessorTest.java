package io.joshuasalcedo.webview4j.processor;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.HttpException;
import io.joshuasalcedo.webview4j.HttpMethod;
import io.joshuasalcedo.webview4j.Route;
import io.joshuasalcedo.webview4j.RouteRequest;
import io.joshuasalcedo.webview4j.Routes;
import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebViewProcessorTest {

    @TempDir
    Path work;

    private record Compiled(boolean ok, List<String> errors, Path classes, Path typescript) {}

    private Compiled compile(String... sources) throws IOException {
        Path classes = Files.createDirectories(work.resolve("classes"));
        Path generated = Files.createDirectories(work.resolve("generated"));
        Path typescript = work.resolve("ts/api.ts");
        List<JavaFileObject> units = new ArrayList<>();
        for (String source : sources) {
            String name = source.replaceAll("(?s).*?(?:class|record|enum) (\\w+).*", "$1");
            String pkg = source.replaceAll("(?s)^package ([\\w.]+);.*", "$1");
            units.add(new SimpleJavaFileObject(URI.create("string:///" + pkg.replace('.', '/') + "/" + name + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            });
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics,
                List.of("-d", classes.toString(), "-s", generated.toString(), "-classpath", System.getProperty("java.class.path"),
                        "-A" + WebViewProcessor.TYPESCRIPT_OPTION + "=" + typescript, "-parameters"),
                null, units);
        task.setProcessors(List.of(new WebViewProcessor()));
        boolean ok = task.call();
        List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null)).toList();
        return new Compiled(ok, errors, classes, typescript);
    }

    private static final String NOTES = """
            package app.notes;

            import io.joshuasalcedo.webview4j.annotation.*;
            import io.joshuasalcedo.webview4j.HttpException;
            import java.util.*;
            import java.util.concurrent.CompletableFuture;

            @WebViewController(value = "/notes", name = "notes")
            public class NoteController {
                public enum Color { RED, GREEN }
                public record Note(long id, String text, Color color, Map<String, List<Integer>> marks, Optional<Note> reply) {}
                public record NewNote(String text, Color color, Map<String, List<Integer>> marks) {}

                @Get("/{id}") public Note find(@Path("id") long noteId) {
                    if (noteId == 404) throw new HttpException(404, "no note 404");
                    return new Note(noteId, "n" + noteId, Color.RED, Map.of(), Optional.empty());
                }
                @Get public List<Note> page(@Query int size, @Query Boolean done, @Query Color color) {
                    return List.of(new Note(size, String.valueOf(done), color, Map.of(), Optional.empty()));
                }
                @Post public CompletableFuture<Note> add(@Body NewNote note) {
                    return CompletableFuture.completedFuture(new Note(1, note.text(), note.color(), note.marks(), Optional.empty()));
                }
                @Delete("/{id}") public void remove(@Path long id) {}
            }
            """;

    @Test
    void generatedRoutesBindPathQueryAndBodyWithoutReflection() throws Exception {
        Compiled compiled = compile(NOTES);
        assertTrue(compiled.ok(), compiled.errors().toString());
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {compiled.classes().toUri().toURL()}, getClass().getClassLoader())) {
            Object controller = loader.loadClass("app.notes.NoteController").getDeclaredConstructor().newInstance();
            @SuppressWarnings("unchecked")
            Routes<Object> routes = (Routes<Object>) loader.loadClass("app.notes.NoteController_Routes").getDeclaredConstructor().newInstance();
            Map<String, Route> byKey = new java.util.HashMap<>();
            for (Route route : routes.routes(controller)) byKey.put(route.method() + " " + route.pattern(), route);
            assertEquals(java.util.Set.of("GET /notes/{id}", "GET /notes", "POST /notes", "DELETE /notes/{id}"), byKey.keySet());

            Object found = call(byKey.get("GET /notes/{id}"), HttpMethod.GET, Map.of("id", "7"), Map.of(), null);
            assertTrue(found.toString().contains("id=7, text=n7, color=RED"), found.toString());

            Object page = call(byKey.get("GET /notes"), HttpMethod.GET, Map.of(), Map.of("size", List.of("3"), "done", List.of("true"), "color", List.of("GREEN")), null);
            assertTrue(page.toString().contains("id=3, text=true, color=GREEN"), page.toString());
            HttpException missing = assertThrows(HttpException.class, () -> call(byKey.get("GET /notes"), HttpMethod.GET, Map.of(), Map.of(), null));
            assertEquals("size is required", missing.getMessage(), "a primitive query parameter is required");

            Object added = ((CompletionStage<?>) call(byKey.get("POST /notes"), HttpMethod.POST, Map.of(), Map.of(),
                    "{\"text\":\"hi\",\"color\":\"GREEN\",\"marks\":{\"a\":[1,2]}}")).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(added.toString().contains("text=hi, color=GREEN, marks={a=[1, 2]}"), added.toString());
            HttpException bad = assertThrows(HttpException.class, () -> call(byKey.get("POST /notes"), HttpMethod.POST, Map.of(), Map.of(),
                    "{\"text\":\"hi\",\"color\":\"GREEN\",\"marks\":{\"a\":[1,\"two\"]}}"));
            assertEquals(400, bad.status());
            assertEquals("body.marks.a[1] must be a whole number, not \"two\"", bad.getMessage(), "errors name the place in the body");

            assertNull(call(byKey.get("DELETE /notes/{id}"), HttpMethod.DELETE, Map.of("id", "1"), Map.of(), null), "void answers nothing");
        }
    }

    private static Object call(Route route, HttpMethod method, Map<String, String> path, Map<String, List<String>> query, String body) throws Exception {
        return route.handler().handle(new RouteRequest(null, method, "/x", path, query, body));
    }

    @Test
    void theTypeScriptClientIsTypedFromTheJavaTypesAndTypeChecks() throws Exception {
        Compiled compiled = compile(NOTES);
        assertTrue(compiled.ok(), compiled.errors().toString());
        String client = Files.readString(compiled.typescript());
        assertTrue(client.contains("export type Color = \"RED\" | \"GREEN\";"), client);
        assertTrue(client.contains("  marks: Record<string, number[]>;\n  reply: Note | null;"), client);
        assertTrue(client.contains("find(noteId: number): Promise<Note> {\n    return request<Note>(\"GET\", `/notes/${p(noteId)}`);"), client);
        assertTrue(client.contains("page(size: number, done?: boolean | null, color?: Color | null): Promise<Note[]> {"), client);
        assertTrue(client.contains("add(note: NewNote): Promise<Note> {\n    return request<Note>(\"POST\", `/notes`, { body: note });"), client);
        assertTrue(client.contains("remove(id: number): Promise<void> {"), client);

        // The real TypeScript compiler, against @webview4j/api, with a page that uses the client.
        Path api = Path.of(System.getProperty("webview4j.api", "../core/src/main/typescript"));
        Path tsc = api.resolve("node_modules/typescript/bin/tsc");
        Assumptions.assumeTrue(Files.exists(tsc), "run bun install in core/src/main/typescript to type-check the client");
        Path dir = compiled.typescript().getParent();
        Files.writeString(dir.resolve("page.ts"), """
                import { notes, type Note, type Color } from "./api.ts";
                const color: Color = "GREEN";
                const found: Promise<Note> = notes.find(1);
                const page: Promise<Note[]> = notes.page(10, null, color);
                const added = notes.add({ text: "hi", color, marks: { a: [1] } });
                const gone: Promise<void> = notes.remove(1);
                export { found, page, added, gone };
                """);
        Files.writeString(dir.resolve("tsconfig.json"), """
                {
                  "compilerOptions": {
                    "strict": true, "noEmit": true, "target": "ESNext", "module": "Preserve",
                    "moduleResolution": "bundler", "allowImportingTsExtensions": true,
                    "lib": ["ESNext", "DOM"], "types": [], "skipLibCheck": true,
                    "paths": { "@webview4j/api": ["%s"] }
                  },
                  "files": ["api.ts", "page.ts"]
                }
                """.formatted(api.resolve("index.ts").toAbsolutePath().toString().replace('\\', '/')));
        Process process = new ProcessBuilder("node", tsc.toAbsolutePath().toString(), "-p", dir.resolve("tsconfig.json").toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "tsc did not finish");
        assertEquals(0, process.exitValue(), output);
    }

    @Test
    void aControllerIsNamedAfterItsPathUnlessItSaysOtherwise() throws Exception {
        Compiled compiled = compile("""
                package app.names;
                import io.joshuasalcedo.webview4j.annotation.*;
                @WebViewController("/api/v1/order-items")
                public class OrderItemController { @Get public String list() { return ""; } }
                """, """
                package app.names;
                import io.joshuasalcedo.webview4j.annotation.*;
                @WebViewController("/accounts/{account}")
                public class AccountController { @Get public String one(@Path String account) { return account; } }
                """, """
                package app.names;
                import io.joshuasalcedo.webview4j.annotation.*;
                @WebViewController
                public class ReportController { @Get("/reports") public String all() { return ""; } }
                """, """
                package app.names;
                import io.joshuasalcedo.webview4j.annotation.*;
                @WebViewController(value = "/users", name = "people")
                public class UserController { @Get public String all() { return ""; } }
                """);
        assertTrue(compiled.ok(), compiled.errors().toString());
        String client = Files.readString(compiled.typescript());
        assertTrue(client.contains("export const orderItems = {"), "the last fixed segment, camelCase");
        assertTrue(client.contains("export const accounts = {"), "placeholders are skipped");
        assertTrue(client.contains("export const report = {"), "no path: the class name without Controller");
        assertTrue(client.contains("export const people = {"), "name = ... wins");
    }

    @Test
    void twoControllersWithTheSameNameAreACompileError() throws Exception {
        Compiled compiled = compile("""
                package app.clash;
                import io.joshuasalcedo.webview4j.annotation.*;
                @WebViewController("/users")
                public class UserController { @Get public String all() { return ""; } }
                """, """
                package app.clash;
                import io.joshuasalcedo.webview4j.annotation.*;
                @WebViewController("/admin/users")
                public class AdminUserController { @Get public String all() { return ""; } }
                """);
        assertFalse(compiled.ok());
        assertTrue(String.join("\n", compiled.errors()).contains("two controllers are named 'users' in TypeScript"), compiled.errors().toString());
    }

    @Test
    void mistakesAreCompileErrors() throws Exception {
        Compiled compiled = compile("""
                package app.bad;

                import io.joshuasalcedo.webview4j.annotation.*;

                @WebViewController("/bad")
                public class BadController {
                    public record Holder(java.util.Date when) {}
                    @Get("/{id}") public String noPath() { return ""; }
                    @Get("/x") public String getBody(@Body String body) { return body; }
                    @Post("/y") public String unbound(String loose) { return loose; }
                    @Post("/z") public Holder unsupported(@Body Holder holder) { return holder; }
                    @Get("/{a}/{b}") public String wrongName(@Path long a, @Path("c") long b) { return ""; }
                    @Get("/bad segment") public String segment() { return ""; }
                    @Get("/q") public String queryRecord(@Query Holder holder) { return ""; }
                }
                """);
        assertFalse(compiled.ok());
        String errors = String.join("\n", compiled.errors());
        assertTrue(errors.contains("{id} in /bad/{id} needs a @Path parameter"), errors);
        assertTrue(errors.contains("a GET route has no body"), errors);
        assertTrue(errors.contains("bind this parameter with @Path, @Query or @Body"), errors);
        assertTrue(errors.contains("java.util.Date cannot be carried as JSON"), errors);
        assertTrue(errors.contains("no {c} in /bad/{a}/{b}"), errors);
        assertTrue(errors.contains("bad path segment 'bad segment'"), errors);
        assertTrue(errors.contains("@Query takes text, a number, a boolean or an enum"), errors);
        assertFalse(Files.exists(compiled.typescript()), "no client from a controller with mistakes");
    }
}
