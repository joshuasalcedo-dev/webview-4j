package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ActionTest {

    private static List<Action> all() {
        return Stream.of(WindowAction.values(), WebViewAction.values(), InspectorAction.values())
                .<Action>flatMap(Stream::of)
                .toList();
    }

    @Test
    void javaAndRustAgreeOnEveryAction() {
        Set<String> java = new TreeSet<>();
        for (Action action : all()) {
            java.add(action.group() + " " + action.wireName() + " " + action.code());
        }
        Set<String> rust = new TreeSet<>(Native.actions().lines().toList());
        assertEquals(rust, java);
    }

    @Test
    void codesAreUnique() {
        List<Integer> codes = new ArrayList<>();
        all().forEach(action -> codes.add(action.code()));
        assertEquals(codes.size(), Set.copyOf(codes).size());
    }

    @Test
    void codesRoundTripWithinTheirGroup() {
        assertEquals(WindowAction.SET_TITLE, WindowAction.fromCode(102).orElseThrow());
        assertEquals(WebViewAction.LOAD_HTML, WebViewAction.fromCode(201).orElseThrow());
        assertEquals(InspectorAction.CAPTURE, InspectorAction.fromCode(300).orElseThrow());
        assertEquals("websocket-send", WebViewAction.WEBSOCKET_SEND.wireName());
        assertTrue(WindowAction.fromCode(200).isEmpty());
        assertEquals("set-title", WindowAction.SET_TITLE.wireName());
    }
}
