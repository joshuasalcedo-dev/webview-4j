package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.Html;
import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.List;

/** The Sampler's pages: the controls shadcn/ui has that the kit lacked (slider, radio group, toggle, separator, tooltip, dropdown menu). */
final class ControlsDemos implements Demos {


    private double volume = 40;
    private String size = "m";
    private boolean bold;
    private boolean italic = true;
    private String sort = "Name";
    private boolean newestFirst;
    private boolean unsaved = true;
    private String hovered = "not yet";
    private String sentEmail = "";

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Input", "Slider", () -> column(
                        slider("Volume", volume).min(0).max(100).step(1).showValue().onChange(v -> volume = v),
                        slider("Disabled", 70).disabled(),
                        text("Volume " + (int) volume).tone(Tone.SECONDARY)).gap(12),
                """
                slider("Volume", volume).min(0).max(100).showValue().onChange(v -> volume = v)""",
                Slider.class);
        gallery.page("Input", "Radio group", () -> column(
                        radioGroup(List.of(new RadioOption("s", "Small"), new RadioOption("m", "Medium"), new RadioOption("l", "Large")))
                                .label("Size").value(size).onChange(v -> size = v),
                        radioGroup(List.of(new RadioOption("card", "Card"), new RadioOption("cash", "Cash"), new RadioOption("later", "Pay later")))
                                .label("Payment").orientation(RadioGroup.Orientation.HORIZONTAL).value("card"),
                        text("Size " + size).tone(Tone.SECONDARY)).gap(16),
                """
                radioGroup(List.of(new RadioOption("s", "Small"), new RadioOption("m", "Medium"), new RadioOption("l", "Large")))
                        .label("Size").value(size).onChange(v -> size = v)""",
                RadioGroup.class);
        gallery.page("Actions", "Toggle button", () -> column(
                        row(toggleButton("Bold", bold).icon("text").onPressedChange(on -> bold = on),
                                toggleButton("Italic", italic).onPressedChange(on -> italic = on),
                                toggleButton("Star", false).icon("star").iconOnly()).gap(4),
                        text((bold ? "Bold " : "") + (italic ? "Italic" : "")).tone(Tone.SECONDARY)).gap(12),
                """
                toggleButton("Bold", bold).icon("text").onPressedChange(on -> bold = on)
                toggleButton("Star", starred).icon("star").iconOnly()""",
                ToggleButton.class);
        gallery.page("Actions", "Dropdown menu", () -> column(
                        row(dropdownMenu("Sort: " + sort).icon("list")
                                        .item("Name", () -> sort = "Name")
                                        .item("Date", () -> sort = "Date")
                                        .item("Size", () -> sort = "Size")
                                        .separator()
                                        .check("Newest first", newestFirst, on -> newestFirst = on),
                                dropdownMenu("More").subtle()
                                        .item(ContextMenu.menuItem("Export", () -> {}).icon("download").shortcut("Mod+E"))
                                        .item(ContextMenu.menuItem("Archive", () -> {}).disabled(true))).gap(8),
                        text("Sorted by " + sort + (newestFirst ? ", newest first" : "")).tone(Tone.SECONDARY)).gap(12),
                """
                dropdownMenu("Sort").icon("list")
                        .item("Name", () -> sort = NAME)
                        .separator()
                        .check("Newest first", newestFirst, on -> newestFirst = on)""",
                DropdownMenu.class);
        gallery.page("Content", "HTML attributes and style", () -> column(
                        styleSheet("""
                                .sampler-note { border-left: 3px solid var(--nt-accent); padding-left: 12px; }
                                .sampler-unsaved { outline: 2px dashed var(--nt-error); outline-offset: 2px; }
                                """),
                        text("Every component takes HTML's shape: an id, classes for the app's own CSS, data-* and aria-*, a style.")
                                .className("sampler-note"),
                        row(button(unsaved ? "Save (unsaved)" : "Save").accent().id("save")
                                        .className(unsaved ? "sampler-unsaved" : "sampler-saved")
                                        .dataAttribute("state", unsaved ? "unsaved" : "saved").attr("title", "Saves the document")
                                        .onClick(() -> unsaved = !unsaved),
                                surface("Styled from Java", text("Padding, a token's colour, corners, a shadow."))
                                        .style(io.joshuasalcedo.webview4j.ui.Style.of().padding(12).background(io.joshuasalcedo.webview4j.ui.Style.Token.ACCENT_SUBTLE)
                                                .radius(10).elevation(1))).gap(16).align("center")).gap(12),
                """
                styleSheet(".unsaved { outline: 2px dashed var(--nt-error); }")
                button("Save").id("save").className(unsaved ? "unsaved" : "saved")
                        .dataAttribute("state", "unsaved").attr("title", "Saves the document")
                surface("Styled", ...).style(Style.of().padding(12).background(Style.Token.ACCENT_SUBTLE).radius(10).elevation(1))""",
                StyleSheet.class);
        gallery.page("Content", "Hover, focus and pressed", () -> column(
                        row(button("Hover me")
                                        .hoverStyle(io.joshuasalcedo.webview4j.ui.Style.of().background(io.joshuasalcedo.webview4j.ui.Style.Token.ACCENT)
                                                .color(io.joshuasalcedo.webview4j.ui.Style.Token.ON_ACCENT))
                                        .pressedStyle(io.joshuasalcedo.webview4j.ui.Style.of().opacity(0.7))
                                        .onHover(on -> hovered = on ? "on the button" : "off the button"),
                                surface("A card that lifts", text("Rest the pointer on it."))
                                        .style(io.joshuasalcedo.webview4j.ui.Style.of().radius(10))
                                        .hoverStyle(io.joshuasalcedo.webview4j.ui.Style.of().elevation(3).background(io.joshuasalcedo.webview4j.ui.Style.Token.ACCENT_SUBTLE)),
                                textField("Focus me").focusStyle(io.joshuasalcedo.webview4j.ui.Style.of().background(io.joshuasalcedo.webview4j.ui.Style.Token.ACCENT_SUBTLE)
                                        .radius(6))).gap(16).align("center"),
                        text("Pointer: " + hovered + " (onHover tells Java; the styles change in the page at once)").tone(Tone.SECONDARY)).gap(12),
                """
                button("Hover me")
                        .hoverStyle(Style.of().background(Style.Token.ACCENT).color(Style.Token.ON_ACCENT))   // in the page, at once
                        .pressedStyle(Style.of().opacity(0.7))
                        .onHover(on -> hovered = on)                                                         // tells Java
                surface("A card that lifts", ...).hoverStyle(Style.of().elevation(3))
                textField("Focus me").focusStyle(Style.of().background(Style.Token.ACCENT_SUBTLE))""");
        gallery.page("Content", "HTML elements", () -> column(
                        Html.article(
                                Html.h2("Invoice 42"),
                                Html.p(Html.text("Paid on "), Html.strong("Monday"), Html.text(", by "), Html.em("card"), Html.text(".")),
                                Html.ul(Html.li("Design — 4 h"), Html.li("Build — 12 h"), Html.li(Html.text("Review — "), Html.mark("pending"))),
                                Html.details(Html.summary("Notes"), Html.p("Sent to Ana for approval.")),
                                Html.table(
                                        Html.thead(Html.tr(Html.th("Item"), Html.th("Hours"))),
                                        Html.tbody(Html.tr(Html.td("Design"), Html.td("4")), Html.tr(Html.td("Build"), Html.td("12")))),
                                Html.form(
                                        Html.p(Html.label("E-mail").htmlFor("mail").style(io.joshuasalcedo.webview4j.ui.Style.of().set("display", "block").margin(0, 0, 4, 0)),
                                                Html.input().type(Html.Input.Type.EMAIL).id("mail").name("email").placeholder("you@example.com").required()),
                                        Html.p(Html.select(Html.option("Card").value("card"), Html.option("Cash").value("cash")).name("pay"),
                                                Html.text("  "),
                                                Html.label(Html.input().type(Html.Input.Type.CHECKBOX).name("receipt").checked(true), Html.text("E-mail a receipt")),
                                                Html.text("  "),
                                                Html.label(Html.input().type(Html.Input.Type.RADIO).name("speed").value("now").checked(true), Html.text("Now")),
                                                Html.label(Html.input().type(Html.Input.Type.RADIO).name("speed").value("later"), Html.text("Later"))),
                                        Html.p(Html.input().type(Html.Input.Type.RANGE).name("tip").min(0).max(20)),
                                        Html.p(Html.textarea().name("note").placeholder("A note").rows(2)),
                                        Html.p(Html.button("Send").type(Html.Button.Type.SUBMIT), Html.text(" "),
                                                Html.button("Browser look").type(Html.Button.Type.BUTTON).browserLook(), Html.text(" "),
                                                Html.a("Terms").href("https://example.com/terms")))
                                        .onSubmit(fields -> sentEmail = String.valueOf(fields.get("email"))),
                                button("A kit button inside HTML").subtle())
                                .style(io.joshuasalcedo.webview4j.ui.Style.of().padding(16).radius(8).border(1, io.joshuasalcedo.webview4j.ui.Style.Token.BORDER)),
                        text(sentEmail.isEmpty() ? "Send the form: its fields come to Java, the page stays." : "Sent: " + sentEmail).tone(Tone.SECONDARY)).gap(12),
                """
                Html.article(
                        Html.h2("Invoice 42"),
                        Html.p(Html.text("Paid on "), Html.strong("Monday"), Html.text(".")),
                        Html.details(Html.summary("Notes"), Html.p("Sent to Ana.")),
                        Html.form(Html.input().type(Html.Input.Type.EMAIL).name("email").required(),
                                  Html.button("Send").type(Html.Button.Type.SUBMIT))
                            .onSubmit(fields -> send(fields.get("email"))),
                        button("A kit button inside HTML"))      // kit components mix in
                    .style(Style.of().padding(16).radius(8))""");
        gallery.page("Content", "Separator and tooltip", () -> column(
                        row(tooltip("Saves the document (Ctrl+S)", button("Save")),
                                tooltip("Shown on the right", button("Right").subtle()).side(Tooltip.Side.RIGHT)).gap(8),
                        separator(),
                        text("Above and below a line"),
                        separator().label("or"),
                        row(text("Left"), separator().orientation(Separator.Orientation.VERTICAL), text("Right")).gap(8).height(24)).gap(12),
                """
                tooltip("Saves the document (Ctrl+S)", button("Save"))
                separator()
                separator().label("or")
                separator().orientation(Separator.Orientation.VERTICAL)""",
                Separator.class, Tooltip.class);
    }
}
