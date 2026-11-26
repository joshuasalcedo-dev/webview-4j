/**
 * Documents, viewed and edited in the app, each drawn in the page by its own engine, loaded the first time one is
 * shown:
 * <ul>
 *   <li><b>Word</b> (.docx): {@link io.joshuasalcedo.webview4j.plugin.document.DocxViewer} (pages,
 *   thumbnails, outline, sections, comments and tracked changes, find, zoom),
 *   {@link io.joshuasalcedo.webview4j.plugin.document.DocxEditor} (Word's formatting, tables, pictures,
 *   links, Save giving the .docx bytes), and their parts: the side panel, the comment and change cards, the layout
 *   blocks.</li>
 *   <li><b>PDF</b>: {@link io.joshuasalcedo.webview4j.plugin.document.PdfViewer} (pages, zoom, search,
 *   text selection, thumbnails, rotate, download; EmbedPDF, PDFium as WebAssembly), a file's thumbnail, values taken
 *   from a PDF linked to their boxes on its pages, and a PDF's pages split into documents.</li>
 *   <li><b>Spreadsheets</b>: Excel workbooks shown
 *   ({@link io.joshuasalcedo.webview4j.plugin.document.XlsxViewer}) and edited
 *   ({@link io.joshuasalcedo.webview4j.plugin.document.XlsxEditor}), and CSV files as a table
 *   ({@link io.joshuasalcedo.webview4j.plugin.document.CsvViewer}).</li>
 * </ul>
 * The Java side of the kit's {@code components/plugin/document}.
 */
package io.joshuasalcedo.webview4j.plugin.document;
