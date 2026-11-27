package io.joshuasalcedo.swaswa.demo;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.css.CssClasses;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.H1;
import io.joshuasalcedo.swaswa.html.H2;
import io.joshuasalcedo.swaswa.html.H3;
import io.joshuasalcedo.swaswa.html.Input;
import io.joshuasalcedo.swaswa.html.P;
import io.joshuasalcedo.swaswa.html.Pre;
import io.joshuasalcedo.swaswa.html.Span;
import io.joshuasalcedo.swaswa.tailwind.Basic;
import io.joshuasalcedo.swaswa.tailwind.Tw;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * swaswa-tailwind's demo: a page made like a Swing window, styled the Tailwind way. Its CSS is a file of its own
 * ({@code TaildWindDemo.css}, beside it, loaded by itself), its classes are typed, and it shows its own code.
 * Not deployed (swaswa-demo). {@code just tailwind-demo}.
 */
public class TaildWindDemo extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    /** The demo's own classes, made in its CSS file: HERO is "hero", HERO_TITLE is "hero-title". */
    enum Styles implements CssClasses { HERO, HERO_TITLE, HERO_TEXT, CODE }

    // ---- the parts that change ----

    private final Input name = new Input();
    private final Span hello = new Span("Hello!");
    private final Span badge = new Span("0");
    private final Pre code = new Pre();
    private int count;

    @Override
    protected void build() {
        setTitle("swaswa-tailwind");
        cssClass(Basic.PAGE);
        getMain().cssClass(Basic.CONTAINER);

        buildHeader();
        buildHero();
        buildCards();
        buildForm();
        buildCode();
    }

    // ---- the page, part by part ----

    private void buildHeader() {
        H1 title = new H1("swaswa-tailwind");
        title.cssClass(Basic.TITLE);

        Button light = new Button("Light");
        light.cssClass(Basic.BTN);
        light.addActionListener(e -> setTheme(Theme.LIGHT));

        Button dark = new Button("Dark");
        dark.cssClass(Basic.BTN);
        dark.addActionListener(e -> setTheme(Theme.DARK));

        Div themes = new Div(light, dark);
        themes.cssClass(Basic.ROW);

        Div header = new Div(title, themes);
        header.cssClass(Basic.ROW, Tw.Justify.JUSTIFY_BETWEEN, Tw.Mb.MB_8);
        getMain().add(header);
    }

    private void buildHero() {
        H2 title = new H2("Tailwind, from Java");
        title.cssClass(Styles.HERO_TITLE);

        P text = new P("Typed classes, a CSS file of its own, and a colour token of its own: --color-brand.");
        text.cssClass(Styles.HERO_TEXT);

        Div hero = new Div(title, text);
        hero.cssClass(Styles.HERO);
        getMain().add(hero);
    }

    private void buildCards() {
        Div cards = new Div();
        cards.cssClass(Basic.GRID_CARDS);
        cards.add(card("Basic classes", "Basic.CARD, Basic.BTN_PRIMARY: typed, their CSS comes with them."));
        cards.add(card("Your own CSS", "TaildWindDemo.css: @apply, @theme and @utility, like Tailwind's own files."));
        cards.add(card("Tailwind's classes", "Tw.P.P_4, Tw.md(...), Tw.dark(...): every class Tailwind has."));
        getMain().add(cards);
    }

    private Div card(String heading, String text) {
        H3 title = new H3(heading);
        title.cssClass(Basic.SUBTITLE);

        P body = new P(text);
        body.cssClass(Basic.MUTED);

        Div card = new Div(title, body);
        card.cssClass(Basic.CARD);
        return card;
    }

    private void buildForm() {
        H3 title = new H3("A form");
        title.cssClass(Basic.SUBTITLE);

        name.setPlaceholder("Your name");
        name.cssClass(Basic.INPUT);
        name.addChangeListener(e -> sayHello());

        hello.cssClass(Basic.MUTED);

        Button countButton = new Button("Count");
        countButton.cssClass(Basic.BTN_PRIMARY);
        countButton.addActionListener(e -> countUp());

        badge.cssClass(Basic.BADGE);

        Div counter = new Div(countButton, badge);
        counter.cssClass(Basic.ROW);

        Div form = new Div(title, name, hello, counter);
        form.cssClass(Basic.CARD, Basic.STACK, Tw.Mt.MT_8, Tw.Max.MAX_W_MD);
        getMain().add(form);
    }

    private void buildCode() {
        Button java = new Button("Show the Java");
        java.cssClass(Basic.BTN);
        java.addActionListener(e -> showCode("TaildWindDemo.java"));

        Button css = new Button("Show the CSS");
        css.cssClass(Basic.BTN);
        css.addActionListener(e -> showCode("TaildWindDemo.css"));

        Div buttons = new Div(java, css);
        buttons.cssClass(Basic.ROW, Tw.Mt.MT_8);

        code.cssClass(Styles.CODE);
        code.setVisible(false);

        getMain().add(buttons, code);
    }

    // ---- what the buttons and the field do ----

    private void sayHello() {
        String typed = name.getValue();
        hello.setText(typed == null || typed.isBlank() ? "Hello!" : "Hello, " + typed + "!");
    }

    private void countUp() {
        count++;
        badge.setText(String.valueOf(count));
    }

    /** Shows one of this demo's own files: the code that made this page, or its style sheet. */
    private void showCode(String file) {
        code.setText(source(file));
        code.setVisible(true);
        code.scrollIntoView();
    }

    /** A file beside this class, in the jar: its text. */
    private static String source(String file) {
        try (InputStream in = TaildWindDemo.class.getResourceAsStream(file)) {
            return in == null ? file + " is not in the jar" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return file + " could not be read: " + e.getMessage();
        }
    }
}
