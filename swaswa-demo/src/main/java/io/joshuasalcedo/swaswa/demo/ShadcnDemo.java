package io.joshuasalcedo.swaswa.demo;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.H1;
import io.joshuasalcedo.swaswa.html.P;
import io.joshuasalcedo.swaswa.shadcn.Badge;
import io.joshuasalcedo.swaswa.shadcn.Button;
import io.joshuasalcedo.swaswa.shadcn.Card;
import io.joshuasalcedo.swaswa.shadcn.CardContent;
import io.joshuasalcedo.swaswa.shadcn.CardDescription;
import io.joshuasalcedo.swaswa.shadcn.CardFooter;
import io.joshuasalcedo.swaswa.shadcn.CardHeader;
import io.joshuasalcedo.swaswa.shadcn.CardTitle;
import io.joshuasalcedo.swaswa.shadcn.Dialog;
import io.joshuasalcedo.swaswa.shadcn.DialogContent;
import io.joshuasalcedo.swaswa.shadcn.DialogDescription;
import io.joshuasalcedo.swaswa.shadcn.DialogFooter;
import io.joshuasalcedo.swaswa.shadcn.DialogHeader;
import io.joshuasalcedo.swaswa.shadcn.DialogTitle;
import io.joshuasalcedo.swaswa.shadcn.Input;
import io.joshuasalcedo.swaswa.shadcn.Label;
import io.joshuasalcedo.swaswa.shadcn.Progress;
import io.joshuasalcedo.swaswa.shadcn.Switch;
import io.joshuasalcedo.swaswa.shadcn.Tabs;
import io.joshuasalcedo.swaswa.shadcn.TabsContent;
import io.joshuasalcedo.swaswa.shadcn.TabsList;
import io.joshuasalcedo.swaswa.shadcn.TabsTrigger;
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

    private final Input name = new Input();
    private final Badge saved = new Badge("0 saved");
    private final Progress progress = new Progress();
    private final Dialog confirm = new Dialog();
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

        Switch dark = new Switch();
        dark.checkedProperty().bindBidirectional(state(DARK));
        state(DARK).addListener((was, now) -> setTheme(now ? Theme.DARK : Theme.LIGHT));

        Label darkLabel = new Label("Dark");

        Div themes = new Div(darkLabel, dark);
        themes.cssClass(Tw.Flex.FLEX, Tw.Items.ITEMS_CENTER, Tw.Gap.GAP_2);

        Div header = new Div(title, themes);
        header.cssClass(Tw.Flex.FLEX, Tw.Items.ITEMS_CENTER, Tw.Justify.JUSTIFY_BETWEEN);
        getMain().add(header);
    }

    private void buildOrderCard() {
        CardHeader header = new CardHeader(new CardTitle("New order"), new CardDescription("Who is it for?"));

        Label label = new Label("Customer");
        name.setPlaceholder("Ana Reyes");

        Div field = new Div(label, name);
        field.cssClass(Tw.Flex.FLEX, Tw.Flex.FLEX_COL, Tw.Gap.GAP_2);

        progress.setValue(0);
        CardContent content = new CardContent(field, progress);
        content.cssClass(Tw.Flex.FLEX, Tw.Flex.FLEX_COL, Tw.Gap.GAP_4);

        Button save = new Button("Save");
        save.onClick(e -> saveOrder());

        Button delete = new Button("Delete…");
        delete.setVariant(Button.Variant.OUTLINE);
        delete.onClick(e -> confirm.openProperty().set(true));

        CardFooter footer = new CardFooter(save, delete, saved);
        footer.cssClass(Tw.Gap.GAP_2);

        getMain().add(new Card(header, content, footer));
    }

    private void buildTabs() {
        TabsList list = new TabsList(open("open", "Open"), open("shipped", "Shipped"));

        Tabs tabs = new Tabs(list, panel("open", "Three orders wait to be shipped."), panel("shipped", "Ten went out this week."));
        tabs.setValue("open");
        getMain().add(tabs);
    }

    private TabsTrigger open(String value, String text) {
        TabsTrigger trigger = new TabsTrigger(text);
        trigger.setValue(value);
        return trigger;
    }

    private TabsContent panel(String value, String text) {
        TabsContent panel = new TabsContent(new P(text));
        panel.setValue(value);
        return panel;
    }

    private void buildConfirm() {
        DialogHeader header = new DialogHeader(new DialogTitle("Delete the order?"), new DialogDescription("It cannot be undone."));

        Button cancel = new Button("Cancel");
        cancel.setVariant(Button.Variant.OUTLINE);
        cancel.onClick(e -> confirm.openProperty().set(false));

        Button delete = new Button("Delete");
        delete.setVariant(Button.Variant.DESTRUCTIVE);
        delete.onClick(e -> deleteOrder());

        confirm.add(new DialogContent(header, new DialogFooter(cancel, delete)));
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
