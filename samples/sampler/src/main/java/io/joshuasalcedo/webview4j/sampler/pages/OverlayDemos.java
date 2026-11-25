package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.List;

/** The Sampler's pages: the rest of shadcn/ui's components (overlays, pickers, menus, groups). */
final class OverlayDemos implements Demos {

    private static final List<ComboboxOption> COUNTRIES = List.of(
            new ComboboxOption("ph", "Philippines"), new ComboboxOption("jp", "Japan"), new ComboboxOption("de", "Germany"), new ComboboxOption("br", "Brazil"),
            new ComboboxOption("ca", "Canada"), new ComboboxOption("ke", "Kenya"), new ComboboxOption("nz", "New Zealand"), new ComboboxOption("no", "Norway"));

    private String said = "";
    private boolean sheetOpen;
    private boolean drawerOpen;
    private boolean paletteOpen;
    private String country = "ph";
    private String day = "2026-11-25";
    private String domain = "";
    private String page = "docs";
    private boolean sideBar = true;

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Overlays", "Popover and hover card", () -> column(
                        row(popover("Filters", checkbox("Only unread").checked(true), checkbox("With files"))
                                        .title("Show"),
                                hoverCard("@ana", text("Ana Reyes").strong(), text("Designs the Sampler's pages. Joined in 2024.").tone(Tone.SECONDARY))).gap(16).align("center"),
                        text("Click Filters; rest the pointer on @ana.").tone(Tone.SECONDARY)).gap(12),
                """
                popover("Filters", checkbox("Only unread"), checkbox("With files")).title("Show")
                hoverCard("@ana", text("Ana Reyes"), text("Designs the Sampler's pages."))""",
                Popover.class, HoverCard.class);
        gallery.page("Overlays", "Sheet and drawer", () -> column(
                        row(button("Open the sheet").onClick(() -> sheetOpen = true),
                                button("Open a drawer").onClick(() -> drawerOpen = true)).gap(8),
                        sheet(sheetOpen, textField("Name"), textField("E-mail"), button("Save").accent().onClick(() -> sheetOpen = false))
                                .title("Edit profile").description("What others see of you.").onOpenChange(open -> sheetOpen = open),
                        sheet(drawerOpen, text("A drawer is a sheet from the bottom: for a phone-like flow, or a quick action."))
                                .side(Sheet.Side.BOTTOM).size(220).title("Share").onOpenChange(open -> drawerOpen = open)).gap(12),
                """
                sheet(open, textField("Name"), button("Save").onClick(() -> open = false))
                        .title("Edit profile").onOpenChange(o -> open = o)
                sheet(open, ...).side(Sheet.Side.BOTTOM)     // a drawer""",
                Sheet.class);
        gallery.page("Overlays", "Command palette", () -> column(
                        button("Open (Ctrl+K)").onClick(() -> paletteOpen = true),
                        commandPalette(paletteOpen).onOpenChange(open -> paletteOpen = open)
                                .command("Files", "New file", "Mod+N", "file", () -> said = "New file")
                                .command("Files", "Open…", "Mod+O", "folder", () -> said = "Open…")
                                .command("View", "Toggle side bar", "Mod+B", "layout", () -> said = "Side bar toggled")
                                .command("View", "Dark theme", () -> said = "Dark theme")
                                .command("Help", "Keyboard shortcuts", "Mod+/", "help", () -> said = "Shortcuts"),
                        text(said.isEmpty() ? "Press Ctrl+K (⌘K) anywhere in the window." : "Ran: " + said).tone(Tone.SECONDARY)).gap(12),
                """
                commandPalette(open).onOpenChange(o -> open = o)
                        .command("Files", "New file", "Mod+N", "file", this::newFile)
                        .command("View", "Toggle side bar", "Mod+B", "layout", ...)""",
                CommandPalette.class);
        gallery.page("Input", "Combobox", () -> column(
                        combobox("Country", COUNTRIES).value(country).onChange(v -> country = v),
                        text("Chosen: " + country).tone(Tone.SECONDARY)).gap(12),
                """
                combobox("Country", countries).value(country).onChange(v -> country = v)""",
                Combobox.class);
        gallery.page("Input", "Calendar", () -> column(
                        calendar(day).min("2026-11-03").onChange(v -> day = v),
                        text("Day: " + day).tone(Tone.SECONDARY)).gap(12),
                """
                calendar(day).min("2026-11-03").onChange(v -> day = v)     // days as ISO text""",
                Calendar.class);
        gallery.page("Input", "Input group", () -> column(
                        inputGroup("Website").before("https://").after(".com").value(domain).onChange(v -> domain = v),
                        inputGroup("Search").icon("search").placeholder("Find a file"),
                        inputGroup("Amount").before("PHP").after("per month"),
                        text(domain.isEmpty() ? "" : "https://" + domain + ".com").tone(Tone.SECONDARY)).gap(12),
                """
                inputGroup("Website").before("https://").after(".com").onChange(v -> domain = v)
                inputGroup("Search").icon("search")""",
                InputGroup.class);
        gallery.page("Actions", "Button group", () -> column(
                        buttonGroup(button("Left"), button("Center"), button("Right")).label("Alignment"),
                        buttonGroup(button("Save").accent(), dropdownMenu("").item("Save as…", () -> {}).item("Save a copy", () -> {}))
                                .label("Save")).gap(12),
                """
                buttonGroup(button("Left"), button("Center"), button("Right")).label("Alignment")""",
                ButtonGroup.class);
        gallery.page("Content", "Aspect ratio", () -> column(
                        aspectRatio(16.0 / 9, surface("16 : 9", text("Stays 16 : 9 as the window's width changes."))).maxWidth(480),
                        aspectRatio(1, surface("1 : 1")).maxWidth(160)).gap(12),
                """
                aspectRatio(16.0 / 9, picture)""",
                AspectRatio.class);
        gallery.page("Window", "Menubar", () -> column(
                        menubar()
                                .menu("File", m -> m.item("New", "Mod+N", () -> said = "New").item("Open…", "Mod+O", () -> said = "Open").separator().item("Close", () -> said = "Close"))
                                .menu("Edit", m -> m.item("Undo", "Mod+Z", () -> said = "Undo").item("Redo", "Mod+Shift+Z", () -> said = "Redo"))
                                .menu("View", m -> m.check("Side bar", sideBar, on -> sideBar = on)),
                        text((said.isEmpty() ? "Open a menu." : "Chose: " + said) + (sideBar ? "" : " (side bar off)")).tone(Tone.SECONDARY)).gap(12),
                """
                menubar()
                        .menu("File", m -> m.item("New", "Mod+N", this::newFile).separator().item("Close", this::close))
                        .menu("View", m -> m.check("Side bar", sideBar, on -> sideBar = on))""",
                Menubar.class);
        gallery.page("Layout", "Navigation menu", () -> column(
                        navigationMenu(List.of(
                                        new NavigationItem("home", "Home"),
                                        new NavigationItem("docs", "Docs").withLinks(List.of(new NavigationLink("start", "Getting started", "Install it and open a window."),
                                                new NavigationLink("ui", "Java screens", "Components from Java, no HTML."),
                                                new NavigationLink("react", "React", "The kit in your Vite app."),
                                                new NavigationLink("protocol", "Protocol", "How Java and Rust talk."))),
                                        new NavigationItem("blog", "Blog")))
                                .current(page).onNavigate(id -> page = id),
                        text("Page: " + page).tone(Tone.SECONDARY)).gap(12),
                """
                // items: NavigationItem("docs", "Docs").withLinks(List.of(new NavigationLink("start", "Getting started", "..."), ...))
                navigationMenu(items).current(page).onNavigate(id -> page = id)""",
                NavigationMenu.class);
    }
}
