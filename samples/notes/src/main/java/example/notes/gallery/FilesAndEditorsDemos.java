package example.notes.gallery;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.LogView.LogLine;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The gallery's pages: Files (chips, icons, lists, grids, trees, the explorer), editors (code), messaging (the composer, bubbles) and logs. */
final class FilesAndEditorsDemos implements Demos {

    private static final Instant NOW = Instant.parse("2026-11-25T09:30:00Z");

    // Files
    private String notes = "<p>Layouts, <strong>state</strong> and a gallery.</p>";
    private final Set<String> deleted = new LinkedHashSet<>();
    private List<String> selected = List.of();
    private String fileEvent = "Click a file; double-click or Enter opens it; right-click renames or deletes";
    private final List<String> attachments = new ArrayList<>(List.of("quarterly-report.pdf", "team-photo.jpg", "budget.xlsx"));
    private String chipEvent = "";

    // Editors
    private String code = """
            package example;

            public final class Greeter {

                public String greet(String name) {
                    return "Hello, " + name + "!";
                }
            }
            """;
    private boolean wrap;

    // Messaging
    private final List<Message> chat = new ArrayList<>(List.of(
            new Message("Maria", "Hi! Did you get a chance to look at the release notes?", NOW.minus(Duration.ofMinutes(14))),
            new Message(null, "Yes, I read them this morning. Looks good to me.", NOW.minus(Duration.ofMinutes(12))),
            new Message("Maria", "Great. Can we ship it today?", NOW.minus(Duration.ofMinutes(11))),
            new Message(null, "After lunch, once the build is green.", NOW.minus(Duration.ofMinutes(9)))));

    // Logs
    private final List<LogLine> log = new ArrayList<>(List.of(
            new LogLine(NOW.minusSeconds(40), LogLevel.INFO, "Server started on port 8080", "http", null),
            new LogLine(NOW.minusSeconds(38), LogLevel.DEBUG, "Loaded 12 routes", "http", null),
            new LogLine(NOW.minusSeconds(30), LogLevel.INFO, "Connected to the database", "db", "jdbc:postgresql://localhost:5432/notes"),
            new LogLine(NOW.minusSeconds(21), LogLevel.WARN, "Slow query: 1.8 s", "db", "SELECT * FROM notes WHERE body ILIKE '%release%'"),
            new LogLine(NOW.minusSeconds(9), LogLevel.ERROR, "Request failed: 500", "http", "java.lang.IllegalStateException: no note 42\n    at example.Notes.find(Notes.java:31)")));
    private int logCount;

    // Infinite scroll
    private final List<String> feed = new ArrayList<>();
    private static final int FEED_TOTAL = 60;

    private record Message(String sender, String text, Instant at) {
        boolean mine() { return sender == null; }
    }

    FilesAndEditorsDemos() {
        loadMore();
    }

    @Override
    public void addTo(Gallery gallery) {
        files(gallery);
        editors(gallery);
        messaging(gallery);
        logs(gallery);
    }

    // ---- Files --------------------------------------------------------------------------------------------------

    /** A small project, without what was deleted in the demos. */
    private List<FileNode> project() {
        Instant d = NOW.minus(Duration.ofDays(3));
        List<FileNode> tree = List.of(
                FileNode.folder("src",
                        FileNode.folder("main",
                                FileNode.file("App.java", 4_210, d),
                                FileNode.file("Notes.java", 2_870, d.plus(Duration.ofHours(5))),
                                FileNode.file("logo.png", 18_400, d.minus(Duration.ofDays(20)))),
                        FileNode.folder("test",
                                FileNode.file("AppTest.java", 1_530, d))),
                FileNode.folder("docs",
                        FileNode.file("guide.md", 9_800, d.minus(Duration.ofDays(2))),
                        FileNode.file("diagram.svg", 6_120, d.minus(Duration.ofDays(9)))),
                FileNode.file("build.gradle.kts", 1_204, d),
                FileNode.file("README.md", 3_310, d.plus(Duration.ofHours(1))),
                FileNode.file(".gitignore", 88, d.minus(Duration.ofDays(30))));
        return without(tree, "");
    }

    private List<FileNode> without(List<FileNode> nodes, String parent) {
        List<FileNode> kept = new ArrayList<>();
        for (FileNode n : nodes) {
            String path = parent.isEmpty() ? n.name() : parent + "/" + n.name();
            if (deleted.contains(path)) continue;
            kept.add(n.isFolder() ? new FileNode(n.name(), without(n.children(), path), null, null) : n);
        }
        return kept;
    }

    /** The files of one folder (the project's top, or a path in it). */
    private List<FileNode> folder(String path) {
        List<FileNode> nodes = project();
        if (path.isEmpty()) return nodes;
        for (String name : path.split("/")) {
            nodes = nodes.stream().filter(n -> n.name().equals(name) && n.isFolder()).findFirst()
                    .map(FileNode::children).orElse(List.of());
        }
        return nodes;
    }

    private Component<?> status() {
        return column(
                text(fileEvent).tone(Tone.SECONDARY),
                row(text(selected.isEmpty() ? "Nothing selected" : "Selected: " + String.join(", ", selected)).tone(Tone.SECONDARY).grow(),
                        button("Restore deleted").subtle().disabled(deleted.isEmpty()).onClick(deleted::clear)));
    }

    private void files(Gallery gallery) {
        gallery.page("Files", "File icon", () -> column(
                row(fileIcon("src").folder().size(32),
                        fileIcon("App.java").size(32),
                        fileIcon("README.md").size(32),
                        fileIcon("report.pdf").size(32),
                        fileIcon("photo.jpg").size(32),
                        fileIcon("budget.xlsx").size(32),
                        fileIcon("archive.zip").size(32),
                        fileIcon("song.mp3").size(32),
                        fileIcon("data.json").size(32)).gap(16).wrap(),
                row(fileIcon("notes.txt"), text("notes.txt, at its default size")).gap(8).align("center")),
                """
                fileIcon("src").folder().size(32)
                fileIcon("App.java").size(32)      // the name's extension picks the icon
                fileIcon("report.pdf")""",
                FileIcon.class);

        gallery.page("Files", "File chip", () -> column(
                row(attachments.stream().<Component<?>>map(name -> fileChip(name)
                        .size(name.length() * 37_000L)
                        .onOpen(() -> chipEvent = "Opened " + name)
                        .onRemove(() -> { attachments.remove(name); chipEvent = "Removed " + name; }))
                        .toList()).gap(8).wrap(),
                row(fileChip("uploading-video.mp4").size(48_000_000).progress(62).status("Uploading… 62%"),
                        fileChip("too-big.iso").size(4_700_000_000L).error(true).status("Too large to upload")).gap(8).wrap(),
                row(text("Inline:"), fileChip("contract.docx").size(120_000).inline()).gap(8).align("center"),
                row(text(chipEvent.isEmpty() ? attachments.size() + " attachments" : chipEvent).tone(Tone.SECONDARY).grow(),
                        button("Reset").subtle().onClick(() -> {
                            attachments.clear();
                            attachments.addAll(List.of("quarterly-report.pdf", "team-photo.jpg", "budget.xlsx"));
                            chipEvent = "";
                        }))),
                """
                fileChip("budget.xlsx").size(111_000)
                        .onOpen(() -> open(name)).onRemove(() -> attachments.remove(name))
                fileChip("video.mp4").progress(62).status("Uploading… 62%")
                fileChip("too-big.iso").error(true).status("Too large to upload")
                fileChip("contract.docx").inline()""",
                FileChip.class);

        gallery.page("Files", "File tree", () -> column(
                fileTree("Project", project()).selected(selected).height(300)
                        .onSelectionChange(paths -> selected = paths)
                        .onOpen(path -> fileEvent = "Opened " + path)
                        .onRename((path, name) -> fileEvent = "Renamed " + path + " to " + name)
                        .onDelete(path -> { deleted.add(path); fileEvent = "Deleted " + path; }),
                status()),
                """
                List<FileNode> project = List.of(
                        FileNode.folder("src", FileNode.folder("main", FileNode.file("App.java", 4_210, modified))),
                        FileNode.file("README.md", 3_310, modified));
                fileTree("Project", project).selected(selected)
                        .onSelectionChange(paths -> selected = paths)
                        .onOpen(path -> open(path))
                        .onDelete(path -> delete(path))""",
                FileTree.class);

        gallery.page("Files", "File list", () -> column(
                fileList("src/main", folder("src/main")).selected(selected).height(220)
                        .onSelectionChange(paths -> selected = paths)
                        .onOpen(path -> fileEvent = "Opened " + path)
                        .onRename((path, name) -> fileEvent = "Renamed " + path + " to " + name)
                        .onDelete(path -> { deleted.add("src/main/" + path); fileEvent = "Deleted " + path; }),
                status()),
                """
                fileList("src/main", files)          // name, size, modified
                        .onSelectionChange(paths -> selected = paths)
                        .onOpen(path -> open(path))
                        .onDelete(path -> delete(path))""",
                FileList.class);

        gallery.page("Files", "File grid", () -> column(
                fileGrid("Project", project()).selected(selected).height(260)
                        .onSelectionChange(paths -> selected = paths)
                        .onOpen(path -> fileEvent = "Opened " + path)
                        .onRename((path, name) -> fileEvent = "Renamed " + path + " to " + name)
                        .onDelete(path -> { deleted.add(path); fileEvent = "Deleted " + path; }),
                status()),
                """
                fileGrid("Project", files)            // large icons
                        .onOpen(path -> open(path))
                        .onDelete(path -> delete(path))""",
                FileGrid.class);

        gallery.page("Files", "File explorer", () -> column(
                fileExplorer("Project", project()).selected(selected).height(360)
                        .onSelectionChange(paths -> selected = paths)
                        .onOpen(path -> fileEvent = "Opened " + path)
                        .onRename((path, name) -> fileEvent = "Renamed " + path + " to " + name)
                        .onDelete(path -> { deleted.add(path); fileEvent = "Deleted " + path; }),
                status()),
                """
                fileExplorer("Project", project)      // folders beside a folder's contents
                        .selected(selected)
                        .onSelectionChange(paths -> selected = paths)
                        .onOpen(path -> open(path))
                        .onRename((path, name) -> rename(path, name))   // the page asks for the name
                        .onDelete(path -> delete(path))""",
                FileExplorer.class);
    }

    // ---- Editors ------------------------------------------------------------------------------------------------

    private void editors(Gallery gallery) {
        gallery.page("Editors", "Rich text editor", () -> column(
                        richText("Release notes").value(notes).placeholder("What changed?").minHeight(160).onChange(v -> notes = v),
                        richText("Read only").value("<p>Shipped <strong>0.2.0</strong> with <em>layouts</em> and <a href=\"#\">state</a>.</p>").readOnly(true),
                        text(notes.replaceAll("<[^>]+>", "").length() + " characters, kept as HTML").tone(Tone.SECONDARY)).gap(12),
                """
                richText("Release notes").value(html).placeholder("What changed?").onChange(v -> html = v)
                richText("Read only").value(html).readOnly(true)""",
                RichTextEditor.class);
        gallery.page("Editors", "Code editor", () -> {
            long lines = code.lines().count();
            return column(
                    row(toggle("Word wrap").checked(wrap).onChange(v -> wrap = v)),
                    codeEditor("Greeter.java").fileName("Greeter.java").value(code).wordWrap(wrap).minimap(false)
                            .height(260).onChange(v -> code = v),
                    text(lines + " lines, " + code.length() + " characters").tone(Tone.SECONDARY),
                    codeEditor("settings.json (read only)").language("json").readOnly(true).lineNumbers(false).minimap(false)
                            .value("""
                                    { "theme": "dark", "fontSize": 14, "autosave": true }""")
                            .height(60));
        }, """
                codeEditor("Greeter.java").fileName("Greeter.java")   // the name picks the language
                        .value(code).wordWrap(wrap).minimap(false)
                        .height(260).onChange(v -> code = v)
                codeEditor("settings").language("json").readOnly(true)""",
                CodeEditor.class);
    }

    // ---- Messaging ----------------------------------------------------------------------------------------------

    private void messaging(Gallery gallery) {
        gallery.page("Messaging", "Chat", () -> column(
                column(chat.stream().<Component<?>>map(m -> {
                    MessageBubble b = messageBubble(m.text(), m.mine()).sentAt(m.at());
                    return m.mine() ? b.state("read") : b.sender(m.sender());
                }).toList()).gap(4).scroll().height(300),
                composer("Message Maria").placeholder("Message Maria").maxLength(500)
                        .onSend(text -> {
                            Instant at = chat.getLast().at().plus(Duration.ofMinutes(1));
                            chat.add(new Message(null, text, at));
                            chat.add(new Message("Maria", "Got it: \"" + text + "\"", at.plusSeconds(20)));
                        })),
                """
                column(chat.stream().map(m -> m.mine()
                        ? messageBubble(m.text(), true).sentAt(m.at()).state("read")
                        : messageBubble(m.text(), false).sentAt(m.at()).sender(m.sender())).toList())
                        .scroll().height(300)
                composer("Message Maria").placeholder("Message Maria")
                        .onSend(text -> chat.add(new Message(null, text, now())))""",
                MessageBubble.class, Composer.class);

        gallery.page("Messaging", "Message bubble", () -> column(
                messageBubble("Shall we meet at three?", false).sender("Maria").sentAt(NOW.minusSeconds(600)),
                messageBubble("Sending…", true).sentAt(NOW.minusSeconds(5)).state("sending"),
                messageBubble("Sent, not delivered yet", true).sentAt(NOW.minusSeconds(60)).state("sent"),
                messageBubble("Delivered", true).sentAt(NOW.minusSeconds(120)).state("delivered"),
                messageBubble("Read", true).sentAt(NOW.minusSeconds(180)).state("read"),
                messageBubble("This one failed to send", true).sentAt(NOW.minusSeconds(240)).state("failed")).gap(4),
                """
                messageBubble("Shall we meet at three?", false).sender("Maria").sentAt(at)
                messageBubble("Delivered", true).sentAt(at).state("delivered")
                messageBubble("This one failed", true).state("failed")""",
                MessageBubble.class);

        gallery.page("Messaging", "Composer", () -> column(
                composer("Note").placeholder("Ctrl+Enter sends, Enter is a new line").modEnterSends()
                        .onSend(text -> chat.add(new Message(null, text, chat.getLast().at().plusSeconds(60)))),
                composer("Sending").placeholder("A message on its way").sending(true),
                composer("Disabled").placeholder("You can't post in this channel").disabled(true),
                text(chat.size() + " messages in the Chat page's conversation").tone(Tone.SECONDARY)),
                """
                composer("Note").modEnterSends().onSend(text -> send(text))
                composer("Sending").sending(true)
                composer("Disabled").disabled(true)""",
                Composer.class);
    }

    // ---- Logs and scrolling -------------------------------------------------------------------------------------

    private void loadMore() {
        int from = feed.size();
        for (int i = from; i < Math.min(FEED_TOTAL, from + 15); i++) feed.add("Notification " + (i + 1));
    }

    private void logs(Gallery gallery) {
        gallery.page("Logs", "Log view", () -> column(
                logView("Server log", log).height(300).empty("No lines yet"),
                row(button("Add an info line").onClick(() -> addLog(LogLevel.INFO)),
                        button("Add a warning").onClick(() -> addLog(LogLevel.WARN)),
                        button("Add an error").onClick(() -> addLog(LogLevel.ERROR)),
                        button("Clear").subtle().onClick(log::clear)).wrap(),
                text(log.size() + " lines").tone(Tone.SECONDARY)),
                """
                logView("Server log", List.of(
                        new LogLine(time, LogLevel.INFO, "Server started on port 8080", "http", null),
                        new LogLine(time, LogLevel.ERROR, "Request failed: 500", "http", stackTrace)))
                        .height(300).empty("No lines yet")""",
                LogView.class);

        gallery.page("Logs", "Infinite scroll", () -> column(
                infiniteScroll().with(feed.stream().<Component<?>>map(item ->
                                row(fileIcon(item.endsWith("0") ? "report.pdf" : "notes.txt"), text(item).grow())
                                        .gap(8).padding(8).key(item)).toList())
                        .hasMore(feed.size() < FEED_TOTAL)
                        .endText("That's all " + FEED_TOTAL)
                        .onLoadMore(this::loadMore)
                        .height(300),
                row(text(feed.size() + " of " + FEED_TOTAL + " loaded").tone(Tone.SECONDARY).grow(),
                        button("Start over").subtle().onClick(() -> { feed.clear(); loadMore(); }))),
                """
                infiniteScroll().with(items.stream().map(i -> text(i).key(i)).toList())
                        .hasMore(items.size() < total)
                        .endText("That's all")
                        .onLoadMore(() -> items.addAll(nextPage()))
                        .height(300)""",
                InfiniteScroll.class);
    }

    private void addLog(LogLevel level) {
        logCount++;
        Instant at = log.isEmpty() ? NOW : log.getLast().time().plusSeconds(2);
        log.add(switch (level) {
            case WARN -> new LogLine(at, level, "Cache is 90% full (warning " + logCount + ")", "cache", null);
            case ERROR -> new LogLine(at, level, "Upload failed (error " + logCount + ")", "files",
                    "java.io.IOException: disk full\n    at example.Uploads.save(Uploads.java:58)");
            default -> new LogLine(at, level, "GET /notes 200 in " + (10 + logCount * 3) + " ms", "http", null);
        });
    }
}
