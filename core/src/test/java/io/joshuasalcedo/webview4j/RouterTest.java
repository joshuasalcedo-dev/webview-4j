package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouterTest {

    private static Route route(HttpMethod method, String pattern, String answer) {
        return new Route(method, pattern, request -> answer);
    }

    private static Object answer(Router.Match match) throws Exception {
        return ((Router.Match.Found) match).handler().handle(null);
    }

    @Test
    void literalsBeatParametersAndParametersAreDecoded() throws Exception {
        Router router = new Router();
        router.add(route(HttpMethod.GET, "/users/{id}", "one user"));
        router.add(route(HttpMethod.GET, "/users/me", "me"));
        router.add(route(HttpMethod.GET, "/users", "all"));
        assertEquals("me", answer(router.match(HttpMethod.GET, "/users/me")));
        assertEquals("all", answer(router.match(HttpMethod.GET, "/users/")));
        Router.Match.Found found = (Router.Match.Found) router.match(HttpMethod.GET, "/users/a%20b%2Fc");
        assertEquals(Map.of("id", "a b/c"), found.pathParams(), "an escaped slash stays inside its segment");
    }

    @Test
    void aKnownPathWithAnotherMethodIsNotAllowedAndAnUnknownOneNotFound() {
        Router router = new Router();
        router.add(route(HttpMethod.GET, "/users/{id}", "x"));
        router.add(route(HttpMethod.DELETE, "/users/{id}", "x"));
        assertEquals(new Router.Match.NotAllowed(Set.of(HttpMethod.GET, HttpMethod.DELETE)), router.match(HttpMethod.PUT, "/users/1"));
        assertEquals(new Router.Match.NotFound(), router.match(HttpMethod.GET, "/orders/1"));
        assertEquals(new Router.Match.NotFound(), router.match(HttpMethod.GET, "/users/%zz"));
    }

    @Test
    void routesThatWouldAnswerTheSameRequestsClash() {
        Router router = new Router();
        router.add(route(HttpMethod.GET, "/users/{id}", "x"));
        assertThrows(IllegalStateException.class, () -> router.add(route(HttpMethod.GET, "/users/{name}", "y")));
        assertThrows(IllegalArgumentException.class, () -> router.add(route(HttpMethod.GET, "/users/{bad name}", "y")));
        assertThrows(IllegalArgumentException.class, () -> router.add(route(HttpMethod.GET, "/a//b", "y")));
    }

    @Test
    void queriesAreDecodedAndKeepRepeatedNames() {
        assertEquals(Map.of("a", List.of("1", "2"), "b", List.of("two words"), "c", List.of("")),
                Router.query("a=1&b=two+words&a=2&c"));
        assertTrue(Router.query("").isEmpty());
    }
}
