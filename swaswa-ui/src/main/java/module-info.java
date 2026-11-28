/**
 * swaswa-ui: desktop controls that look like the system's own (Windows 11 on Windows, macOS on a Mac, light or dark),
 * and the bigger components (code editor, rich text, PDF and Office viewers, charts, chat, data tables), from Java:
 * {@code new UiButton("Send")}, {@code new UiTitleBar()}, drawn by React in the page (swaswa-react).
 */
module io.joshuasalcedo.swaswa.ui {
    requires transitive io.joshuasalcedo.swaswa.react;
    exports io.joshuasalcedo.swaswa.ui;
    // java-gen: exports (one package per component file)
    exports io.joshuasalcedo.swaswa.ui.authentication;
    exports io.joshuasalcedo.swaswa.ui.autoupdate;
    exports io.joshuasalcedo.swaswa.ui.avatar;
    exports io.joshuasalcedo.swaswa.ui.breadcrumb;
    exports io.joshuasalcedo.swaswa.ui.button;
    exports io.joshuasalcedo.swaswa.ui.carousel;
    exports io.joshuasalcedo.swaswa.ui.chart;
    exports io.joshuasalcedo.swaswa.ui.chat;
    exports io.joshuasalcedo.swaswa.ui.checkbox;
    exports io.joshuasalcedo.swaswa.ui.codeeditor;
    exports io.joshuasalcedo.swaswa.ui.collection;
    exports io.joshuasalcedo.swaswa.ui.composer;
    exports io.joshuasalcedo.swaswa.ui.content;
    exports io.joshuasalcedo.swaswa.ui.contextmenu;
    exports io.joshuasalcedo.swaswa.ui.controls;
    exports io.joshuasalcedo.swaswa.ui.dialog;
    exports io.joshuasalcedo.swaswa.ui.document.docx;
    exports io.joshuasalcedo.swaswa.ui.document.pdf;
    exports io.joshuasalcedo.swaswa.ui.document.xlsx;
    exports io.joshuasalcedo.swaswa.ui.filemenu;
    exports io.joshuasalcedo.swaswa.ui.files;
    exports io.joshuasalcedo.swaswa.ui.fileviews;
    exports io.joshuasalcedo.swaswa.ui.grammartextarea;
    exports io.joshuasalcedo.swaswa.ui.iconpicker;
    exports io.joshuasalcedo.swaswa.ui.infinitescroll;
    exports io.joshuasalcedo.swaswa.ui.inspector;
    exports io.joshuasalcedo.swaswa.ui.layout;
    exports io.joshuasalcedo.swaswa.ui.list;
    exports io.joshuasalcedo.swaswa.ui.logview;
    exports io.joshuasalcedo.swaswa.ui.markdown;
    exports io.joshuasalcedo.swaswa.ui.mdi;
    exports io.joshuasalcedo.swaswa.ui.mediaplayer;
    exports io.joshuasalcedo.swaswa.ui.money;
    exports io.joshuasalcedo.swaswa.ui.overlays;
    exports io.joshuasalcedo.swaswa.ui.pickers;
    exports io.joshuasalcedo.swaswa.ui.pill;
    exports io.joshuasalcedo.swaswa.ui.progressbar;
    exports io.joshuasalcedo.swaswa.ui.provider;
    exports io.joshuasalcedo.swaswa.ui.quantity;
    exports io.joshuasalcedo.swaswa.ui.richtext;
    exports io.joshuasalcedo.swaswa.ui.searchfield;
    exports io.joshuasalcedo.swaswa.ui.segmentedcontrol;
    exports io.joshuasalcedo.swaswa.ui.select;
    exports io.joshuasalcedo.swaswa.ui.sidebar;
    exports io.joshuasalcedo.swaswa.ui.statusbar;
    exports io.joshuasalcedo.swaswa.ui.structure;
    exports io.joshuasalcedo.swaswa.ui.surface;
    exports io.joshuasalcedo.swaswa.ui.swipeable;
    exports io.joshuasalcedo.swaswa.ui.switch_;
    exports io.joshuasalcedo.swaswa.ui.tabs;
    exports io.joshuasalcedo.swaswa.ui.text;
    exports io.joshuasalcedo.swaswa.ui.titlebar;
    exports io.joshuasalcedo.swaswa.ui.titledpane;
    exports io.joshuasalcedo.swaswa.ui.toolbar;
    exports io.joshuasalcedo.swaswa.ui.valuefields;
    // java-gen: end
}
