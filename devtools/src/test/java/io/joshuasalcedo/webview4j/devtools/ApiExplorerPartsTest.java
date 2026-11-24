package io.joshuasalcedo.webview4j.devtools;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The API explorer's parts: the description, example values, the mock server, JUnit reports. */
class ApiExplorerPartsTest {

    static final String SPEC = """
            {"openapi": "3.0.1", "info": {"title": "Shop", "version": "1.2"},
             "servers": [{"url": "/api"}],
             "paths": {
               "/users/{id}": {
                 "parameters": [{"name": "id", "in": "path", "schema": {"type": "integer"}}],
                 "get": {"tags": ["users"], "summary": "One user",
                         "responses": {"200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/User"}}}}}},
                 "delete": {"tags": ["users"], "responses": {"204": {"description": "gone"}}}
               },
               "/users": {
                 "post": {"tags": ["users"],
                          "parameters": [{"name": "notify", "in": "query", "schema": {"type": "boolean"}}],
                          "requestBody": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/User"}}}},
                          "responses": {"201": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/User"}}}}}}
               }
             },
             "components": {"schemas": {"User": {"type": "object", "properties": {
                 "id": {"type": "integer", "minimum": 7},
                 "email": {"type": "string", "format": "email"},
                 "name": {"type": "string"},
                 "role": {"type": "string", "enum": ["ADMIN", "MEMBER"]},
                 "tags": {"type": "array", "items": {"type": "string", "example": "vip"}},
                 "joined": {"type": "string", "format": "date"}}}}}}
            """;

    @Test
    void aDescriptionHasItsOperationsWithTheirPartsFollowed() {
        OpenApi api = OpenApi.parse(SPEC, URI.create("http://localhost:8080/v3/api-docs"));
        assertEquals("Shop", api.title);
        assertEquals(URI.create("http://localhost:8080/api"), api.server);
        assertEquals(List.of("GET /users/{id}", "DELETE /users/{id}", "POST /users"), api.operations.stream().map(OpenApi.Operation::label).toList());
        OpenApi.Operation post = api.operations.get(2);
        assertEquals(201, post.successStatus());
        assertEquals("users", post.tag());
        assertEquals(List.of("notify"), post.parameters().stream().map(OpenApi.Parameter::name).toList());
        assertEquals("id", api.operations.getFirst().parameters().getFirst().name(), "parameters on the path apply to each method");
        assertThrows(IllegalArgumentException.class, () -> OpenApi.parse("openapi: 3.0.1", null), "YAML is refused, with why");
        assertThrows(IllegalArgumentException.class, () -> OpenApi.parse("{\"swagger\": \"2.0\"}", null));
    }

    @Test
    void examplesComeFromTheSchemas() {
        OpenApi api = OpenApi.parse(SPEC, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) Examples.of(api, api.operations.get(2).requestSchema(), null);
        assertEquals(7L, user.get("id"), "the minimum");
        assertEquals("ada@example.com", user.get("email"));
        assertEquals("Ada Lovelace", user.get("name"));
        assertEquals("ADMIN", user.get("role"), "the first of an enum");
        assertEquals(List.of("vip"), user.get("tags"), "an example of the items");
        assertTrue(String.valueOf(user.get("joined")).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
    }

    @Test
    void theMockServerAnswersFromTheDescription() throws Exception {
        try (MockServer mock = MockServer.start(SPEC); HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> user = http.send(HttpRequest.newBuilder(mock.url().resolve("users/42")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, user.statusCode());
            assertEquals("ada@example.com", Json.object(user.body()).get("email"));
            assertEquals("*", user.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

            HttpResponse<String> created = http.send(HttpRequest.newBuilder(mock.url().resolve("users"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode());
            HttpResponse<String> deleted = http.send(HttpRequest.newBuilder(mock.url().resolve("users/1")).DELETE().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(204, deleted.statusCode());
            HttpResponse<String> missing = http.send(HttpRequest.newBuilder(mock.url().resolve("orders")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
            HttpResponse<String> wrong = http.send(HttpRequest.newBuilder(mock.url().resolve("users")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, wrong.statusCode());
        }
    }

    @Test
    void aJUnitReportIsRead(@TempDir Path dir) throws Exception {
        Path report = dir.resolve("TEST-example.xml");
        Files.writeString(report, """
                <testsuite name="ExampleTest" tests="3">
                  <testcase name="adds" classname="example.ExampleTest" time="0.012"/>
                  <testcase name="divides" classname="example.ExampleTest" time="0.003">
                    <failure message="expected: &lt;2&gt; but was: &lt;3&gt;" type="AssertionFailedError">stack</failure>
                  </testcase>
                  <testcase name="later" classname="example.ExampleTest"><skipped/></testcase>
                </testsuite>
                """);
        List<TestRunner.TestResult> results = TestRunner.parse(report);
        assertEquals(3, results.size());
        assertEquals(TestRunner.Status.PASSED, results.get(0).status());
        assertEquals(TestRunner.Status.FAILED, results.get(1).status());
        assertEquals("expected: <2> but was: <3>", results.get(1).message());
        assertEquals(TestRunner.Status.SKIPPED, results.get(2).status());
        assertThrows(IllegalArgumentException.class, () -> TestRunner.command(dir), "no build there");
    }
}
