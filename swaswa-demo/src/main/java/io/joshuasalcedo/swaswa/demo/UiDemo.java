package io.joshuasalcedo.swaswa.demo;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.state.Store;
import io.joshuasalcedo.swaswa.ui.button.UiButton;
import io.joshuasalcedo.swaswa.ui.codeeditor.UiCodeEditor;
import io.joshuasalcedo.swaswa.ui.progressbar.UiProgressBar;
import io.joshuasalcedo.swaswa.ui.statusbar.UiStatusBar;
import io.joshuasalcedo.swaswa.ui.statusbar.UiStatusItem;
import io.joshuasalcedo.swaswa.ui.switch_.UiSwitch;
import io.joshuasalcedo.swaswa.ui.text.UiText;
import io.joshuasalcedo.swaswa.ui.text.UiTextField;
import io.joshuasalcedo.swaswa.ui.toolbar.UiToolbar;
import io.joshuasalcedo.swaswa.ui.toolbar.UiToolbarButton;
import io.joshuasalcedo.swaswa.ui.toolbar.UiToolbarSpacer;

/**
 * swaswa-ui's demo: desktop controls that look like the system's own (Windows 11 here, macOS on a Mac), made like a
 * Swing window: create each one, set it up, add it. The code editor is Monaco, loaded from the jar when first shown.
 * Not deployed (swaswa-demo). {@code just ui-demo}.
 */
public class UiDemo extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    /** Whether the person wants to be told when an order ships: the app's own state. */
    static final Store<Boolean> NOTIFY = Store.of("notify", Boolean.class, () -> true);

    // ---- the parts that change ----

    private final UiTextField customer = new UiTextField();
    private final UiProgressBar progress = new UiProgressBar();
    private final UiStatusItem status = new UiStatusItem("Ready");
    private int saved;

    @Override
    protected WindowOptions window() {
        return WindowOptions.html("").size(900, 720);
    }

    @Override
    protected void build() {
        setTitle("swaswa-ui");
        buildToolbar();
        buildForm();
        buildEditor();
        buildStatusBar();
    }

    // ---- the window, part by part ----

    private void buildToolbar() {
        UiToolbarButton save = new UiToolbarButton();
        save.setLabel("Save");
        save.onClick(e -> saveOrder());

        UiToolbarButton clear = new UiToolbarButton();
        clear.setLabel("Clear");
        clear.onClick(e -> clearOrder());

        UiToolbar toolbar = new UiToolbar(new UiText("Orders"), new UiToolbarSpacer(), save, clear);
        getHeader().add(toolbar);
    }

    private void buildForm() {
        UiText title = new UiText("New order");
        title.setVariant(UiText.Variant.TITLE);

        customer.setLabel("Customer");
        customer.setPlaceholder("Ana Reyes");

        UiSwitch notify = new UiSwitch();
        notify.setLabel("Tell me when it ships");
        notify.checkedProperty().bindBidirectional(state(NOTIFY));

        UiButton send = new UiButton("Save order");
        send.setVariant(UiButton.Variant.ACCENT);
        send.onClick(e -> saveOrder());

        progress.setValue(0);

        Div form = new Div(title, customer, notify, progress, send);
        form.getStyle().set(io.joshuasalcedo.swaswa.css.CssProperty.DISPLAY, "grid");
        form.getStyle().set(io.joshuasalcedo.swaswa.css.CssProperty.GAP, "12px");
        form.getStyle().set(io.joshuasalcedo.swaswa.css.CssProperty.PADDING, "20px");
        form.getStyle().set(io.joshuasalcedo.swaswa.css.CssProperty.MAX_WIDTH, "520px");
        getMain().add(form);
    }

    private void buildEditor() {
        UiCodeEditor notes = new UiCodeEditor();
        notes.setFileName("order.json");
        notes.setValue("{\n  \"customer\": \"Ana Reyes\",\n  \"items\": 3\n}\n");
        notes.setHeight("220px");

        Div box = new Div(notes);
        box.getStyle().set(io.joshuasalcedo.swaswa.css.CssProperty.PADDING, "0 20px 20px");
        getMain().add(box);
    }

    private void buildStatusBar() {
        getFooter().add(new UiStatusBar(status));
    }

    // ---- what the buttons do ----

    private void saveOrder() {
        saved++;
        progress.setValue(Math.min(100, saved * 25));
        status.setText(saved + " saved, the last for " + customer.valueProperty().get());
    }

    private void clearOrder() {
        saved = 0;
        progress.setValue(0);
        customer.valueProperty().set("");
        status.setText("Ready");
    }
}
