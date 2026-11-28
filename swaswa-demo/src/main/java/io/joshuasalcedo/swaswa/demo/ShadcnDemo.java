package io.joshuasalcedo.swaswa.demo;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.H1;
import io.joshuasalcedo.swaswa.html.P;
import io.joshuasalcedo.swaswa.shadcn.badge.ShadcnBadge;
import io.joshuasalcedo.swaswa.shadcn.button.ShadcnButton;
import io.joshuasalcedo.swaswa.shadcn.card.ShadcnCard;
import io.joshuasalcedo.swaswa.shadcn.card.ShadcnCardContent;
import io.joshuasalcedo.swaswa.shadcn.card.ShadcnCardDescription;
import io.joshuasalcedo.swaswa.shadcn.card.ShadcnCardFooter;
import io.joshuasalcedo.swaswa.shadcn.card.ShadcnCardHeader;
import io.joshuasalcedo.swaswa.shadcn.card.ShadcnCardTitle;
import io.joshuasalcedo.swaswa.shadcn.dialog.ShadcnDialog;
import io.joshuasalcedo.swaswa.shadcn.dialog.ShadcnDialogContent;
import io.joshuasalcedo.swaswa.shadcn.dialog.ShadcnDialogDescription;
import io.joshuasalcedo.swaswa.shadcn.dialog.ShadcnDialogFooter;
import io.joshuasalcedo.swaswa.shadcn.dialog.ShadcnDialogHeader;
import io.joshuasalcedo.swaswa.shadcn.dialog.ShadcnDialogTitle;
import io.joshuasalcedo.swaswa.shadcn.input.ShadcnInput;
import io.joshuasalcedo.swaswa.shadcn.label.ShadcnLabel;
import io.joshuasalcedo.swaswa.shadcn.progress.ShadcnProgress;
import io.joshuasalcedo.swaswa.shadcn.switch_.ShadcnSwitch;
import io.joshuasalcedo.swaswa.shadcn.tabs.ShadcnTabs;
import io.joshuasalcedo.swaswa.shadcn.tabs.ShadcnTabsContent;
import io.joshuasalcedo.swaswa.shadcn.tabs.ShadcnTabsList;
import io.joshuasalcedo.swaswa.shadcn.tabs.ShadcnTabsTrigger;
import io.joshuasalcedo.swaswa.state.Store;
import io.joshuasalcedo.swaswa.tailwind.Tw;

/**
 * swaswa-shadcn's demo: shadcn/ui's real components, made like a Swing window: create each one, set it up, add it.
 * Its state is the app's own (a {@link Store}, live): the dark switch is bound to it. Not deployed (swaswa-demo).
 * {@code just shadcn-demo}.
 */
public class ShadcnDemo extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    /** The app's state: dark or not, kept in its session. */
    static final Store<Boolean> DARK = Store.of("dark", Boolean.class, () -> false);

    // ---- the parts that change ----

    private final ShadcnInput name = new ShadcnInput();
    private final ShadcnBadge saved = new ShadcnBadge("0 saved");
    private final ShadcnProgress progress = new ShadcnProgress();
    private final ShadcnDialog confirm = new ShadcnDialog();
    private int count;

    @Override
    protected void build() {
        setTitle("swaswa-shadcn");
        getMain().cssClass(Tw.Max.MAX_W_2XL, Tw.Mx.MX_AUTO, Tw.P.P_8, Tw.Flex.FLEX, Tw.Flex.FLEX_COL, Tw.Gap.GAP_6);

        buildHeader();
        buildOrderCard();
        buildTabs();
        buildConfirm();
    }

    // ---- the page, part by part ----

    private void buildHeader() {
        H1 title = new H1("shadcn/ui, from Java");
        title.cssClass(Tw.Text.TEXT_2XL, Tw.Font.FONT_SEMIBOLD);

        ShadcnSwitch dark = new ShadcnSwitch();
        dark.checkedProperty().bindBidirectional(state(DARK));
        state(DARK).addListener((was, now) -> setTheme(now ? Theme.DARK : Theme.LIGHT));

        ShadcnLabel darkLabel = new ShadcnLabel("Dark");

        Div themes = new Div(darkLabel, dark);
        themes.cssClass(Tw.Flex.FLEX, Tw.Items.ITEMS_CENTER, Tw.Gap.GAP_2);

        Div header = new Div(title, themes);
        header.cssClass(Tw.Flex.FLEX, Tw.Items.ITEMS_CENTER, Tw.Justify.JUSTIFY_BETWEEN);
        getMain().add(header);
    }

    private void buildOrderCard() {
        ShadcnCardHeader header = new ShadcnCardHeader(new ShadcnCardTitle("New order"), new ShadcnCardDescription("Who is it for?"));

        ShadcnLabel label = new ShadcnLabel("Customer");
        name.setPlaceholder("Ana Reyes");

        Div field = new Div(label, name);
        field.cssClass(Tw.Flex.FLEX, Tw.Flex.FLEX_COL, Tw.Gap.GAP_2);

        progress.setValue(0);
        ShadcnCardContent content = new ShadcnCardContent(field, progress);
        content.cssClass(Tw.Flex.FLEX, Tw.Flex.FLEX_COL, Tw.Gap.GAP_4);

        ShadcnButton save = new ShadcnButton("Save");
        save.onClick(e -> saveOrder());

        ShadcnButton delete = new ShadcnButton("Delete…");
        delete.setVariant(ShadcnButton.Variant.OUTLINE);
        delete.onClick(e -> confirm.openProperty().set(true));

        ShadcnCardFooter footer = new ShadcnCardFooter(save, delete, saved);
        footer.cssClass(Tw.Gap.GAP_2);

        getMain().add(new ShadcnCard(header, content, footer));
    }

    private void buildTabs() {
        ShadcnTabsList list = new ShadcnTabsList(open("open", "Open"), open("shipped", "Shipped"));

        ShadcnTabs tabs = new ShadcnTabs(list, panel("open", "Three orders wait to be shipped."), panel("shipped", "Ten went out this week."));
        tabs.setValue("open");
        getMain().add(tabs);
    }

    private ShadcnTabsTrigger open(String value, String text) {
        ShadcnTabsTrigger trigger = new ShadcnTabsTrigger(text);
        trigger.setValue(value);
        return trigger;
    }

    private ShadcnTabsContent panel(String value, String text) {
        ShadcnTabsContent panel = new ShadcnTabsContent(new P(text));
        panel.setValue(value);
        return panel;
    }

    private void buildConfirm() {
        ShadcnDialogHeader header = new ShadcnDialogHeader(new ShadcnDialogTitle("Delete the order?"), new ShadcnDialogDescription("It cannot be undone."));

        ShadcnButton cancel = new ShadcnButton("Cancel");
        cancel.setVariant(ShadcnButton.Variant.OUTLINE);
        cancel.onClick(e -> confirm.openProperty().set(false));

        ShadcnButton delete = new ShadcnButton("Delete");
        delete.setVariant(ShadcnButton.Variant.DESTRUCTIVE);
        delete.onClick(e -> deleteOrder());

        confirm.add(new ShadcnDialogContent(header, new ShadcnDialogFooter(cancel, delete)));
        getMain().add(confirm);
    }

    // ---- what the buttons do ----

    private void saveOrder() {
        count++;
        saved.setText(count + " saved");
        progress.setValue(Math.min(100, count * 20));
        Property<String> customer = name.valueProperty();
        System.out.println("saved an order for " + customer.get());
    }

    private void deleteOrder() {
        count = 0;
        saved.setText("0 saved");
        progress.setValue(0);
        name.valueProperty().set("");
        confirm.openProperty().set(false);
    }
}
