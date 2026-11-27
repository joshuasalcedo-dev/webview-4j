package io.joshuasalcedo.swaswa.webview.showcase;

import io.joshuasalcedo.swaswa.ffi.HttpException;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.webview.annotation.Body;
import io.joshuasalcedo.swaswa.webview.annotation.Delete;
import io.joshuasalcedo.swaswa.webview.annotation.Get;
import io.joshuasalcedo.swaswa.webview.annotation.Path;
import io.joshuasalcedo.swaswa.webview.annotation.Post;
import io.joshuasalcedo.swaswa.webview.annotation.Put;
import io.joshuasalcedo.swaswa.webview.annotation.Query;
import io.joshuasalcedo.swaswa.webview.annotation.WebViewController;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** The showcase's notes: what the page asks for, as a server would answer it, in Java. */
@WebViewController("/notes")
public class NotesController {

    public record Note(long id, String text, boolean done, String at) {}

    public record NewNote(String text) {}

    private final Map<Long, Note> notes = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public NotesController() {
        add(new NewNote("Write the page in plain HTML (or React, Vue...)"));
        add(new NewNote("Answer it from Java: @WebViewController"));
    }

    /** GET /notes?search=...: the notes, those whose text has {@code search}. */
    @Get
    public List<Note> list(@Query String search) {
        return notes.values().stream()
                .filter(n -> search == null || search.isBlank() || n.text().toLowerCase().contains(search.toLowerCase()))
                .sorted(Comparator.comparingLong(Note::id)).toList();
    }

    /** POST /notes {text}: a new note. */
    @Post
    public Note add(@Body NewNote body) {
        if (body == null || body.text() == null || body.text().isBlank()) throw new HttpException(400, "a note needs some text");
        Note note = new Note(ids.incrementAndGet(), body.text().strip(), false, LocalTime.now().withNano(0).toString());
        notes.put(note.id(), note);
        return note;
    }

    /** PUT /notes/{id}/done: done, or not done again. */
    @Put("/{id}/done")
    public Note toggle(@Path long id) {
        Note note = notes.computeIfPresent(id, (k, n) -> new Note(n.id(), n.text(), !n.done(), n.at()));
        if (note == null) throw new HttpException(404, "no note " + id);
        return note;
    }

    /** DELETE /notes/{id}. */
    @Delete("/{id}")
    public void remove(@Path long id) {
        if (notes.remove(id) == null) throw new HttpException(404, "no note " + id);
    }

    /** GET /notes/who: which view asked (a route can take the view that called it). */
    @Get("/who")
    public String who(View view) {
        return "asked by " + view.id() + " at " + LocalTime.now().withNano(0);
    }
}
