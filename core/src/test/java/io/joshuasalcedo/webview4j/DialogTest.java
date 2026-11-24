package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What the dialogs send (PROTOCOL.md §5.7). Showing one needs a person, so that is not tested here. */
class DialogTest {

    @Test
    void aFileDialogSendsOnlyWhatIsSet() {
        assertEquals(Map.of("kind", "open"), new FileDialog().toPayload("open"));

        Path home = Path.of(System.getProperty("user.home"));
        Map<String, Object> payload = new FileDialog().title("Pick").directory(home).fileName("a.txt")
                .filter("Images", ".png", "jpg").filter("Text", "txt").toPayload("save");
        assertEquals("save", payload.get("kind"));
        assertEquals("Pick", payload.get("title"));
        assertEquals(home.toAbsolutePath().toString(), payload.get("directory"));
        assertEquals("a.txt", payload.get("fileName"));
        assertEquals(List.of(Map.of("name", "Images", "extensions", List.of("png", "jpg")),
                Map.of("name", "Text", "extensions", List.of("txt"))), payload.get("filters"));
        assertFalse(payload.containsKey("view"));

        assertThrows(IllegalArgumentException.class, () -> new FileDialog().filter("None"));
        assertThrows(IllegalArgumentException.class, () -> new FileDialog().filter("Dot", "."));
    }

    @Test
    void aMessageDialogNamesItsLevelAndButtonsAsTheProtocolDoes() {
        assertEquals(Map.of("title", "Hi", "text", "There", "level", "info", "buttons", "ok"),
                MessageDialog.of("Hi", "There").toPayload());
        Map<String, Object> payload = MessageDialog.of("Save?", "Or lose it")
                .level(MessageDialog.Level.WARNING).buttons(MessageDialog.Buttons.YES_NO_CANCEL).toPayload();
        assertEquals("warning", payload.get("level"));
        assertEquals("yes-no-cancel", payload.get("buttons"));
        assertEquals(MessageDialog.Answer.CANCEL, MessageDialog.answer("cancel"));
        assertEquals(MessageDialog.Answer.YES, MessageDialog.answer("yes"));
    }

    @Test
    void windowReportsBecomeEvents() {
        assertEquals(new Resized("wv-1", 800, 600), Events.fromView("wv-1", "resized", Map.of("width", 800L, "height", 600.0)));
        assertEquals(new Moved("wv-1", -8, 20.5), Events.fromView("wv-1", "moved", Map.of("x", -8L, "y", 20.5)));
        assertEquals(new Focused("wv-1", true), Events.fromView("wv-1", "focused", Map.of("focused", true)));
        assertEquals(new FileDrop("wv-1", FileDrop.Kind.DROP, List.of(Path.of("a.txt")), 3, 4),
                Events.fromView("wv-1", "file-drop", Map.of("kind", "drop", "paths", List.of("a.txt"), "x", 3L, "y", 4L)));
        assertEquals(List.of(), ((FileDrop) Events.fromView("wv-1", "file-drop", Map.of("kind", "leave", "paths", List.of()))).paths());
    }
}
