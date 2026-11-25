package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.Tone;
import io.joshuasalcedo.webview4j.ui.components.usecase.xlsx.CsvViewer;
import io.joshuasalcedo.webview4j.ui.components.usecase.xlsx.XlsxEditor;
import io.joshuasalcedo.webview4j.ui.components.usecase.xlsx.XlsxViewer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** The Sampler's pages: Documents: an Excel workbook shown and edited, a CSV file as a table. */
final class XlsxDemos implements Demos {

    private static final byte[] WORKBOOK = sample("quarterly.xlsx");
    private static final byte[] CSV = sample("customers.csv");

    private String saved = "Edit a cell, then Save (or Ctrl+S / ⌘S).";
    private boolean edited;
    private String cell = "Click a cell, or use the arrow keys.";

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Documents", "Excel viewer", () -> new XlsxViewer().bytes(WORKBOOK).fileName("quarterly.xlsx").height(480),
                """
                new XlsxViewer().file(Path.of("quarterly.xlsx")).grow()
                new XlsxViewer().bytes(workbook).fileName("quarterly.xlsx").height(480)""",
                XlsxViewer.class);
        gallery.page("Documents", "Excel editor", () -> column(
                new XlsxEditor().bytes(WORKBOOK).fileName("quarterly.xlsx").height(480)
                        .onChange(() -> edited = true)
                        .onSave(bytes -> {
                            edited = false;
                            saved = String.format("Saved %,d bytes at %s", bytes.length,
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                        }),
                text(edited ? "Edited, not saved yet" : saved).tone(Tone.SECONDARY)),
                """
                new XlsxEditor().file(path).grow()
                        .onChange(() -> edited = true)
                        .onSave(bytes -> Files.write(path, bytes))""",
                XlsxEditor.class);
        gallery.page("Documents", "CSV viewer", () -> column(
                new CsvViewer().bytes(CSV).fileName("customers.csv").height(420)
                        .onCellSelect(c -> cell = "Row " + (c.row() + 1) + ", column " + (c.col() + 1) + ": " + c.value()),
                text(cell).tone(Tone.SECONDARY)),
                """
                new CsvViewer().file(Path.of("customers.csv")).grow()
                        .onCellSelect(c -> chosen = c.value())
                new CsvViewer().text("name,score\\nAna,92\\n")""",
                CsvViewer.class);
    }

    /** A sample file from the Sampler's resources. */
    private static byte[] sample(String name) {
        try (InputStream in = XlsxDemos.class.getResourceAsStream("/samples/" + name)) {
            if (in == null) throw new IllegalStateException("the Sampler has no sample " + name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
