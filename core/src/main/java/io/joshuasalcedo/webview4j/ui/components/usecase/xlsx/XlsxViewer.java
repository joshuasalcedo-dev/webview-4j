package io.joshuasalcedo.webview4j.ui.components.usecase.xlsx;

/**
 * An Excel workbook, read-only: its sheets as tabs (a preview when the pointer rests on one), zoom, a search of every
 * sheet, download. Charts, images, merged cells, frozen panes and formats show as Excel draws them. The spreadsheet
 * engine loads in the page the first time a workbook is shown.
 * <pre>{@code
 * new XlsxViewer().file(Path.of("report.xlsx")).grow()
 * new XlsxViewer().bytes(workbook).fileName("forecast.xlsx").height(480)
 * }</pre>
 */
public final class XlsxViewer extends SheetFile<XlsxViewer> {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public XlsxViewer() {
        super("xlsxViewer", XLSX);
    }
}
