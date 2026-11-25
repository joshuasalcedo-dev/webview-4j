package io.joshuasalcedo.webview4j.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebSessionsTest {

    @TempDir
    Path root;

    @Test
    void a_session_is_kept_on_disk_and_opens_where_it_was_left() {
        WebSessions sessions = WebSessions.in(root);
        WebSessions.Session work = sessions.create("work");
        assertTrue(Files.isDirectory(work.profile()), "the webview's profile folder is made");
        assertEquals(Optional.empty(), work.lastUrl(), "a new session has been nowhere");

        work.remember("https://en.wikipedia.org/wiki/WebView");
        work.remember("javascript:alert(1)"); // not an address: not kept

        WebSessions.Session again = WebSessions.in(root).find("work").orElseThrow();
        assertEquals(Optional.of("https://en.wikipedia.org/wiki/WebView"), again.lastUrl());
        assertEquals(work, again);
        assertEquals(1, sessions.list().size());
    }

    @Test
    void new_sessions_get_names_of_their_own_and_go_when_deleted() {
        WebSessions sessions = WebSessions.in(root);
        WebSessions.Session one = sessions.create();
        WebSessions.Session two = sessions.create();
        assertFalse(one.name().equals(two.name()));
        one.delete();
        assertEquals(1, sessions.list().size());
        assertTrue(sessions.find(one.name()).isEmpty());
    }

    @Test
    void a_name_cannot_leave_the_sessions_folder() {
        WebSessions sessions = WebSessions.in(root);
        for (String bad : new String[] {"../x", "a/b", "..", ".hidden", "", "a b"}) {
            assertThrows(IllegalArgumentException.class, () -> sessions.create(bad), bad);
        }
    }
}
