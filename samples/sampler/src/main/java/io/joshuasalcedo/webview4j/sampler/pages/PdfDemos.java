package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.Tone;
import io.joshuasalcedo.webview4j.ui.components.layout.SplitPane;
import io.joshuasalcedo.webview4j.ui.components.usecase.pdf.BoundingBoxCitations;
import io.joshuasalcedo.webview4j.ui.components.usecase.pdf.BoundingBoxCitations.Box;
import io.joshuasalcedo.webview4j.ui.components.usecase.pdf.BoundingBoxCitations.Field;
import io.joshuasalcedo.webview4j.ui.components.usecase.pdf.DocumentSplits;
import io.joshuasalcedo.webview4j.ui.components.usecase.pdf.FileThumbnail;
import io.joshuasalcedo.webview4j.ui.components.usecase.pdf.PdfViewer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Sampler's pages: Documents: a PDF viewer, a file's thumbnail, values linked to their boxes, a PDF split. */
final class PdfDemos implements Demos {

    /** A four-page bank statement (made with pdf-lib), in the Sampler's resources. */
    private static final byte[] STATEMENT = resource("/samples/statement.pdf");
    /** The IRS's Form W-9 (a US government work, public domain): a real form, six pages, with fields. */
    private static final byte[] W9 = resource("/samples/irs-w9.pdf");

    private String document = "statement";

    private int page = 1;
    private int pages;
    private int jumpTo = 1;
    private String chosen;
    private boolean review = true;
    private String corrected = "";
    private List<DocumentSplits.Split> splits = DocumentSplits.evenly(4, 2);
    private int splitPage = 1;

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Documents", "PDF viewer", () -> column(
                        segmented("Document", Demos.ordered("statement", "Bank statement", "w9", "IRS Form W-9"))
                                .value(document).onChange(v -> {
                                    document = v;
                                    jumpTo = 1;
                                    pages = 0;
                                }),
                        row(segmented("Go to", Demos.ordered("1", "Page 1", "2", "Page 2", "3", "Page 3", "4", "Page 4"))
                                        .value(String.valueOf(jumpTo)).onChange(v -> jumpTo = Integer.parseInt(v)),
                                text("In view: page " + page + " of " + (pages == 0 ? "…" : pages)).tone(Tone.SECONDARY)),
                        (document.equals("w9") ? PdfViewer.of(W9).fileName("fw9.pdf") : PdfViewer.of(STATEMENT).fileName("Northwind statement.pdf"))
                                .key(document).zoom(PdfViewer.Zoom.FIT_WIDTH)
                                .page(jumpTo).onPageChange(n -> page = n).onLoad(n -> pages = n).height(520)),
                """
                PdfViewer.of(Path.of("statement.pdf")).zoom(PdfViewer.Zoom.FIT_WIDTH)
                        .page(page).onPageChange(n -> page = n).onLoad(n -> pages = n)""",
                PdfViewer.class);

        gallery.page("Documents", "File thumbnail", () -> row(
                        FileThumbnail.of(STATEMENT, "statement.pdf").width(120),
                        FileThumbnail.of(STATEMENT, "statement.pdf").page(2).width(120),
                        FileThumbnail.of(new byte[0], "notes.txt").width(120)),
                """
                FileThumbnail.of(Path.of("statement.pdf")).width(120)
                FileThumbnail.of(bytes, "statement.pdf").page(2)
                FileThumbnail.src(imageUrl, "photo.jpg").cover()""",
                FileThumbnail.class);

        gallery.page("Documents", "Bounding box citations", () -> {
            BoundingBoxCitations citations = BoundingBoxCitations.of(STATEMENT, fields()).fileName("statement.pdf")
                    .zoom(PdfViewer.Zoom.FIT_WIDTH).active(chosen).onActiveChange(k -> chosen = k)
                    .onExpectedChange((k, v) -> corrected = k + " = " + v).height(560);
            if (review) citations.review();
            return column(
                    row(toggle("Review").checked(review).onChange(v -> review = v),
                            text(chosen == null ? "Point at a value, or click a box" : "Chosen: " + chosen).tone(Tone.SECONDARY),
                            text(corrected.isEmpty() ? "" : "Corrected: " + corrected).tone(Tone.SECONDARY)),
                    citations);
        }, """
                BoundingBoxCitations.of(Path.of("statement.pdf"),
                        Field.of("ending_balance", "Ending balance", 12840.18).expected(12858.18)
                                .at(Box.of(1, 11.11, 53.03, 77.78, 2.98)),
                        Field.group("account_details", "Account details",
                                Field.of("holder_name", "Holder name", "Jordan Lee").at(Box.of(1, 11.11, 20.2, 30.4, 2.83))))
                    .review().active(chosen).onActiveChange(k -> chosen = k)""",
                BoundingBoxCitations.class);

        gallery.page("Documents", "Document splits", () -> column(
                        text(splits.size() + " documents: " + String.join("; ", splits.stream()
                                .map(s -> s.title() + " " + s.pages()).toList())).tone(Tone.SECONDARY),
                        new SplitPane(
                                PdfViewer.of(STATEMENT).fileName("statement.pdf").zoom(PdfViewer.Zoom.FIT_PAGE).page(splitPage)
                                        .upload(false),
                                DocumentSplits.of(STATEMENT, splits).onChange(s -> splits = s).onSelectPage(n -> splitPage = n))
                                .dividerPositions(0.55).height(560)),
                """
                new SplitPane(
                        PdfViewer.of(pdf).page(page),
                        DocumentSplits.of(pdf, splits).onChange(s -> splits = s).onSelectPage(n -> page = n))""",
                DocumentSplits.class);
    }

    /** The statement's values, each with its box (percent of the page, as the sample was drawn). */
    private static List<Field> fields() {
        Map<String, Object> payroll = new LinkedHashMap<>();
        payroll.put("date", "2026-01-03");
        payroll.put("description", "ACH CREDIT PAYROLL");
        payroll.put("amount", 4250);
        Map<String, Object> grocery = new LinkedHashMap<>();
        grocery.put("date", "2026-01-12");
        grocery.put("description", "POS PURCHASE GROCERY MART");
        grocery.put("amount", -86.42);
        return List.of(
                Field.of("statement_period", "Statement period", "Jan 1-31, 2026").expected("January 1-31, 2026")
                        .description("Date range covered by the statement.").at(Box.of(1, 11.11, 17.17, 37.48, 2.83)),
                Field.group("account_details", "Account details",
                        Field.of("holder_name", "Holder name", "Jordan Lee").at(Box.of(1, 11.11, 20.2, 30.4, 2.83)),
                        Field.of("account_type", "Account type", "Checking").expected("Premier Checking")
                                .at(Box.of(1, 11.11, 23.23, 44.63, 2.83))),
                Field.of("transactions", "Transactions", List.of(payroll, grocery)).at(Box.of(1, 11.11, 39.14, 77.78, 11.01)),
                Field.of("ending_balance", "Ending balance", 12840.18).expected(12858.18).at(Box.of(1, 11.11, 53.03, 77.78, 2.98)),
                Field.of("overdraft_protection_enabled", "Overdraft protection enabled", false).expected(true)
                        .at(Box.of(2, 11.11, 13.38, 55.23, 2.83)));
    }

    private static byte[] resource(String name) {
        try (InputStream in = PdfDemos.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("the Sampler has no " + name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
