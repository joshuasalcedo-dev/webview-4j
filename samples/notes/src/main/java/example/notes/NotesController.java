package example.notes;

import io.joshuasalcedo.webview4j.HttpException;
import io.joshuasalcedo.webview4j.annotation.Body;
import io.joshuasalcedo.webview4j.annotation.Delete;
import io.joshuasalcedo.webview4j.annotation.Get;
import io.joshuasalcedo.webview4j.annotation.Path;
import io.joshuasalcedo.webview4j.annotation.Post;
import io.joshuasalcedo.webview4j.annotation.WebViewController;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The page's API: {@code GET /notes}, {@code POST /notes}, {@code DELETE /notes/{id}}. The annotation processor
 * turns it into {@code web/src/api.generated.ts}, so the page calls {@code notes.list()} with types. Keep your own
 * logic here, and in plain Java classes it calls.
 */
@WebViewController("/notes")
public class NotesController {

    public record Note(long id, String text, String created) {}

    public record NewNote(String text) {}

    private final Map<Long, Note> notes = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public NotesController() {
        add("Welcome to your webview-4j app. This note came from Java.");
    }

    @Get
    public List<Note> list() {
        return notes.values().stream().sorted(Comparator.comparingLong(Note::id).reversed()).toList();
    }

    @Post
    public Note create(@Body NewNote body) {
        if (body == null || body.text() == null || body.text().isBlank()) throw new IllegalArgumentException("a note needs some text");
        return add(body.text().strip());
    }

    @Delete("/{id}")
    public void delete(@Path long id) {
        if (notes.remove(id) == null) throw new HttpException(404, "no note " + id);
    }

    private Note add(String text) {
        long id = ids.incrementAndGet();
        Note note = new Note(id, text, Instant.now().toString());
        notes.put(id, note);
        return note;
    }
}
