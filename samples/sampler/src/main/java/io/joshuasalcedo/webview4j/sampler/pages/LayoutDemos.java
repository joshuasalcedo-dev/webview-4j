package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.sampler.pages.Demos.ordered;
import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.layout.Box;
import io.joshuasalcedo.webview4j.ui.components.layout.Spacer;
import io.joshuasalcedo.webview4j.ui.components.layout.Split;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Sampler's pages: How a window is laid out: rows and columns, the frame (title bar, toolbar, status bar), side bars, splits, tabs, dialogs, inspectors. */
final class LayoutDemos implements Demos {

    // Split and side bar
    private String folder = "Inbox";
    private String mailbox = "Inbox";
    private boolean mailFolded;
    private String mailSearch = "";

    // Tabs
    private final Map<String, String> openTabs = new LinkedHashMap<>(ordered(
            "readme", "README.md", "main", "Main.java", "build", "build.gradle.kts"));
    private String tab = "main";
    private int untitled;

    // Breadcrumb
    private static final Map<String, List<String>> FOLDERS = Map.of(
            "Home", List.of("Documents", "Pictures"),
            "Documents", List.of("Work", "Taxes"),
            "Pictures", List.of("2025", "2026"),
            "Work", List.of("Reports"));
    private final List<String> path = new ArrayList<>(List.of("Home", "Documents"));

    // Inspector
    private boolean inspectorOpen = true;
    private String file = "Quarterly report.pdf";
    private boolean shared;

    // Swipeable
    private final List<String> messages = new ArrayList<>(List.of(
            "Ada: The build is green", "Grace: Lunch on Friday?", "Linus: Patch v3 attached"));
    private String swiped = "Swipe a message left or right";

    // Dialog
    private String dialogShown = "";
    private String document = "Untitled document";
    private String renameTo = "";
    private String dialogResult = "No dialog yet";

    // Toolbar
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private int edits;
    private String lastCommand = "None yet";

    // Status bar
    private DotState connection = DotState.ONLINE;

    // Window frame
    private ColorScheme frameScheme = ColorScheme.LIGHT;
    private boolean frameSidebar = true;
    private int saves;

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Layout", "Row and column", () -> column(
                row(button("One"), button("Two"), button("Three")).gap(8),
                column(text("Under"), text("each"), text("other")).gap(4)),
                """
                row(button("One"), button("Two"), button("Three")).gap(8)
                column(text("Under"), text("each"), text("other")).gap(4)""",
                Box.class);

        gallery.page("Layout", "Split and side bar", () -> column(split(
                sidebar(
                        sidebarSection("Mail",
                                folderItem("Inbox", 12), folderItem("Drafts", 2), folderItem("Sent", 0)),
                        sidebarSection("Labels",
                                folderItem("Work", 4), folderItem("Family", 0)))
                        .footer(text("ada@example.com").tone(Tone.SECONDARY)),
                column(text(folder).variant(TextVariant.TITLE),
                        text("The messages in " + folder + " would be here.").tone(Tone.SECONDARY)).padding(16)))
                .height(280),
                """
                split(
                        sidebar(sidebarSection("Mail",
                                        sidebarItem("Inbox").badge(12).selected(folder.equals("Inbox"))
                                                .onSelect(() -> folder = "Inbox"),
                                        sidebarItem("Drafts").badge(2)),
                                sidebarSection("Labels", sidebarItem("Work")))
                                .footer(text("ada@example.com")),
                        column(text(folder).variant(TextVariant.TITLE)))""",
                Split.class, Sidebar.class, SidebarSection.class, SidebarItem.class);

        gallery.page("Layout", "Sidebar from parts", () -> column(split(
                sidebar(
                        sidebarGroup("Mail",
                                mailbox("Inbox", "mail", 12), mailbox("Drafts", "file", 2), mailbox("Sent", "upload", 0),
                                sidebarMenuButton("Archive").icon("bookmark").items(
                                        mailbox("2026", null, 0), mailbox("2025", null, 0))),
                        sidebarSeparator(),
                        sidebarGroup("Labels", mailbox("Work", "tag", 4), mailbox("Family", "heart", 0)).collapsible(true))
                        .header(sidebarMenuButton("Acme Mail").description("ada@example.com").icon("A").size(SidebarMenuButton.Size.LARGE),
                                sidebarInput("Search mail").value(mailSearch).onChange(v -> mailSearch = v))
                        .footer(sidebarMenuButton("Settings").icon("settings"))
                        .collapsed(mailFolded).onCollapsedChange(v -> mailFolded = v),
                column(row(sidebarTrigger(() -> mailFolded = !mailFolded), text(mailbox).variant(TextVariant.TITLE)).gap(8),
                        text(mailSearch.isBlank() ? "The messages in " + mailbox + " would be here." : "Searching for " + mailSearch)
                                .tone(Tone.SECONDARY),
                        text("Ctrl+B folds the sidebar to its icons.").tone(Tone.SECONDARY)).padding(16).gap(8)))
                .height(420),
                """
                split(
                        sidebar(
                                sidebarGroup("Mail",
                                        sidebarMenuButton("Inbox").icon("mail").badge(12)
                                                .active(mailbox.equals("Inbox")).onClick(() -> mailbox = "Inbox"),
                                        sidebarMenuButton("Archive").icon("bookmark").items(
                                                sidebarMenuButton("2026"), sidebarMenuButton("2025"))),
                                sidebarSeparator(),
                                sidebarGroup("Labels", sidebarMenuButton("Work").icon("tag")).collapsible(true))
                                .header(sidebarMenuButton("Acme Mail").description("ada@example.com")
                                                .icon("A").size(SidebarMenuButton.Size.LARGE),
                                        sidebarInput("Search mail").value(search).onChange(v -> search = v))
                                .footer(sidebarMenuButton("Settings").icon("settings"))
                                .collapsed(folded).onCollapsedChange(v -> folded = v),
                        column(row(sidebarTrigger(() -> folded = !folded), text(mailbox))))""",
                SidebarGroup.class, SidebarMenuButton.class, SidebarInput.class, SidebarSeparator.class, SidebarTrigger.class);

        gallery.page("Layout", "Tabs", () -> {
            Tabs tabs = tabs("Open files").value(tab)
                    .onChange(v -> tab = v)
                    .onClose(v -> {
                        openTabs.remove(v);
                        if (v.equals(tab)) tab = openTabs.isEmpty() ? "" : openTabs.keySet().iterator().next();
                    })
                    .onAdd(() -> {
                        String key = "untitled" + (++untitled);
                        openTabs.put(key, "Untitled " + untitled);
                        tab = key;
                    });
            openTabs.forEach((value, label) -> tabs.tab(value, label, true));
            return column(tabs,
                    openTabs.isEmpty()
                            ? text("Every tab is closed: + opens a new one.").tone(Tone.SECONDARY)
                            : text("Editing " + openTabs.get(tab)).variant(TextVariant.HEADLINE),
                    text(openTabs.size() + " open").tone(Tone.SECONDARY)).gap(12);
        },
                """
                Tabs tabs = tabs("Open files").value(tab)
                        .onChange(v -> tab = v)
                        .onClose(v -> openTabs.remove(v))
                        .onAdd(() -> openTabs.put("untitled1", "Untitled 1"));
                openTabs.forEach((value, label) -> tabs.tab(value, label, true));
                column(tabs, text("Editing " + openTabs.get(tab)))""",
                Tabs.class);

        gallery.page("Layout", "Breadcrumb", () -> {
            Breadcrumb trail = breadcrumb();
            for (int i = 0; i < path.size(); i++) {
                int depth = i + 1;
                if (depth == path.size()) trail.crumb(path.get(i));
                else trail.crumb(path.get(i), () -> path.subList(depth, path.size()).clear());
            }
            String here = path.getLast();
            List<String> inside = FOLDERS.getOrDefault(here, List.of());
            List<Component<?>> open = new ArrayList<>();
            for (String f : inside) open.add(button(f).onClick(() -> path.add(f)));
            return column(trail,
                    inside.isEmpty() ? text(here + " has no folders inside.").tone(Tone.SECONDARY) : row(open).gap(8),
                    text("You are in " + String.join(" / ", path)).tone(Tone.SECONDARY)).gap(12);
        },
                """
                breadcrumb()
                        .crumb("Home", () -> path.subList(1, path.size()).clear())
                        .crumb("Documents", () -> path.subList(2, path.size()).clear())
                        .crumb("Work")   // the last: where you are""",
                Breadcrumb.class);

        gallery.page("Layout", "Inspector", () -> {
            List<Component<?>> files = new ArrayList<>();
            for (String f : List.of("Quarterly report.pdf", "Team photo.jpg", "Budget.xlsx")) {
                files.add(listItem(f).key(f).selected(f.equals(file)).onSelect(() -> file = f));
            }
            Component<?> preview = column(
                    list("Files", files),
                    inspectorOpen ? text("Close the panel with its ×").tone(Tone.SECONDARY)
                            : button("Show details").onClick(() -> inspectorOpen = true)).padding(12).grow();
            return row(preview,
                    inspector("Details",
                            inspectorSection("General",
                                    text(file).strong(),
                                    text(file.endsWith(".pdf") ? "PDF document · 2.4 MB" : file.endsWith(".jpg") ? "JPEG image · 5.1 MB" : "Spreadsheet · 88 KB")
                                            .tone(Tone.SECONDARY)),
                            inspectorSection("Sharing",
                                    toggle("Shared with the team").checked(shared).onChange(v -> shared = v)),
                            inspectorSection("Activity",
                                    text("Opened today"), text("Edited yesterday"), text("Created last week")).count(3).folded())
                            .open(inspectorOpen).onOpenChange(v -> inspectorOpen = v).panelWidth(240))
                    .align("stretch").gap(0).height(300);
        },
                """
                row(preview,
                        inspector("Details",
                                inspectorSection("General", text(file).strong()),
                                inspectorSection("Sharing", toggle("Shared").checked(shared).onChange(v -> shared = v)),
                                inspectorSection("Activity", text("Opened today")).count(3).folded())
                                .open(open).onOpenChange(v -> open = v).panelWidth(240))
                        .height(300)""",
                Inspector.class, InspectorSection.class);

        gallery.page("Layout", "Swipeable", () -> {
            List<Component<?>> rows = new ArrayList<>();
            for (String m : messages) {
                rows.add(swipeable(listItem(m).subtitle("Swipe me"))
                        .key(m)
                        .right("Read", "accent", () -> swiped = "Marked as read: " + m)
                        .left("Delete", "error", () -> {
                            messages.remove(m);
                            swiped = "Deleted: " + m;
                        }));
            }
            return column(
                    messages.isEmpty()
                            ? button("Bring them back").onClick(() -> messages.addAll(List.of(
                                    "Ada: The build is green", "Grace: Lunch on Friday?", "Linus: Patch v3 attached")))
                            : column(rows).gap(0),
                    text(swiped).tone(Tone.SECONDARY))
                    .gap(12);
        },
                """
                swipeable(listItem(message))
                        .right("Read", "accent", () -> markRead(message))
                        .left("Delete", "error", () -> messages.remove(message))""",
                Swipeable.class);

        gallery.page("Window", "Dialog", () -> column(
                row(button("Delete…").onClick(() -> dialogShown = "delete"),
                        button("Rename…").onClick(() -> {
                            renameTo = document;
                            dialogShown = "rename";
                        }),
                        button("About").onClick(() -> dialogShown = "about")).gap(8),
                text("Document: " + document).strong(),
                text(dialogResult).tone(Tone.SECONDARY),
                dialog("Delete “" + document + "”?")
                        .kind("warning")
                        .message("It goes for good: this cannot be undone.")
                        .open(dialogShown.equals("delete"))
                        .cancel("Cancel", () -> closeDialog("Kept it"))
                        .destructive("Delete", () -> closeDialog("Deleted " + document)),
                dialog("Rename")
                        .kind("question")
                        .open(dialogShown.equals("rename"))
                        .with(textField("Name").value(renameTo).onChange(v -> renameTo = v))
                        .cancel("Cancel", () -> closeDialog("Not renamed"))
                        .primary("Rename", () -> {
                            if (!renameTo.isBlank()) document = renameTo.strip();
                            closeDialog("Renamed to " + document);
                        }),
                dialog("Component gallery")
                        .kind("info")
                        .message("Every component of webview-4j, written in Java only.")
                        .open(dialogShown.equals("about"))
                        .primary("OK", () -> closeDialog("Read the about box"))).gap(12),
                """
                dialog("Delete “" + document + "”?")
                        .kind("warning")
                        .message("It goes for good: this cannot be undone.")
                        .open(showing)
                        .cancel("Cancel", () -> showing = false)
                        .destructive("Delete", () -> { delete(); showing = false; })

                dialog("Rename").kind("question").open(renaming)
                        .with(textField("Name").value(name).onChange(v -> name = v))
                        .cancel("Cancel", () -> renaming = false)
                        .primary("Rename", () -> rename(name))""",
                Dialog.class);

        gallery.page("Window", "Toolbar", () -> column(
                toolbar("Document",
                        toolbarButton("Undo").disabled(edits == 0).onClick(() -> {
                            edits--;
                            lastCommand = "Undo";
                        }),
                        toolbarButton("Redo").disabled(true),
                        toolbarSeparator(),
                        toolbarButton("Bold").pressed(bold).onClick(() -> {
                            bold = !bold;
                            edited("Bold " + (bold ? "on" : "off"));
                        }),
                        toolbarButton("Italic").pressed(italic).onClick(() -> {
                            italic = !italic;
                            edited("Italic " + (italic ? "on" : "off"));
                        }),
                        toolbarButton("Underline").pressed(underline).onClick(() -> {
                            underline = !underline;
                            edited("Underline " + (underline ? "on" : "off"));
                        }),
                        spacer(),
                        toolbarButton("Share").onClick(() -> lastCommand = "Share")),
                text("Last command: " + lastCommand).tone(Tone.SECONDARY),
                text(edits + " edits to undo").tone(Tone.SECONDARY)).gap(12),
                """
                toolbar("Document",
                        toolbarButton("Undo").disabled(edits == 0).onClick(this::undo),
                        toolbarSeparator(),
                        toolbarButton("Bold").pressed(bold).onClick(() -> bold = !bold),
                        toolbarButton("Italic").pressed(italic).onClick(() -> italic = !italic),
                        spacer(),   // what follows goes to the far end
                        toolbarButton("Share").onClick(this::share))""",
                Toolbar.class, ToolbarButton.class, ToolbarSeparator.class, Spacer.class);

        gallery.page("Window", "Status bar", () -> column(
                statusBar(
                        statusItem(switch (connection) {
                            case ONLINE -> "Connected";
                            case CONNECTING -> "Connecting…";
                            case OFFLINE -> "Offline";
                            case ERROR -> "Connection failed";
                        }).dot(connection),
                        statusItem("3 of 12 selected"),
                        spacer(),
                        statusItem("UTF-8"),
                        statusItem("Ln 14, Col 2")),
                segmented("Connection", ordered("ONLINE", "Online", "CONNECTING", "Connecting", "OFFLINE", "Offline", "ERROR", "Error"))
                        .value(connection.name()).onChange(v -> connection = DotState.valueOf(v))).gap(12),
                """
                statusBar(
                        statusItem("Connected").dot(DotState.ONLINE),
                        statusItem("3 of 12 selected"),
                        spacer(),   // what follows goes to the far end
                        statusItem("UTF-8"),
                        statusItem("Ln 14, Col 2"))""",
                StatusBar.class, StatusItem.class);

        gallery.page("Window", "Window frame", () -> {
            Component<?> body = column(
                    text("Notes").variant(TextVariant.TITLE),
                    text("Saved " + saves + " times").tone(Tone.SECONDARY)).padding(16);
            Component<?> framed = frameSidebar
                    ? split(sidebar(sidebarSection("Notebooks",
                            sidebarItem("Personal").selected(true), sidebarItem("Work").badge(3))), body)
                    : body;
            return column(
                    row(segmented("Theme", ordered("LIGHT", "Light", "DARK", "Dark"))
                                    .value(frameScheme.name()).onChange(v -> frameScheme = ColorScheme.valueOf(v)),
                            toggle("Side bar").checked(frameSidebar).onChange(v -> frameSidebar = v)).gap(16),
                    column(frame(framed)
                            .scheme(frameScheme)
                            .accent("#7c3aed")
                            .titleBar(titleBar("Notes"))
                            .toolbar(toolbar("Personal",
                                    toolbarButton("New note"),
                                    spacer(),
                                    toolbarButton("Save").onClick(() -> saves++)))
                            .statusBar(statusBar(statusItem("Synced").dot(DotState.ONLINE), spacer(),
                                    statusItem(saves + " saves"))))
                            .height(320),
                    text("Its title bar's buttons work on the real window: they minimize, maximize and close this gallery.")
                            .variant(TextVariant.FOOTNOTE).tone(Tone.TERTIARY)).gap(12);
        },
                """
                frame(split(sidebar(...), body))
                        .scheme(ColorScheme.LIGHT)
                        .accent("#7c3aed")
                        .titleBar(titleBar("Notes"))
                        .toolbar(toolbar("Personal", toolbarButton("New note"), spacer(),
                                toolbarButton("Save").onClick(() -> saves++)))
                        .statusBar(statusBar(statusItem("Synced").dot(DotState.ONLINE)))""",
                Frame.class, TitleBar.class);
    }

    private SidebarItem folderItem(String name, long unread) {
        SidebarItem item = sidebarItem(name).key(name).selected(name.equals(folder)).onSelect(() -> folder = name);
        return unread > 0 ? item.badge(unread) : item;
    }

    private void closeDialog(String result) {
        dialogShown = "";
        dialogResult = result;
    }

    private void edited(String command) {
        edits++;
        lastCommand = command;
    }

    private SidebarMenuButton mailbox(String name, String icon, long count) {
        SidebarMenuButton b = sidebarMenuButton(name).key(name).badge(count).active(name.equals(mailbox)).onClick(() -> mailbox = name);
        return icon == null ? b : b.icon(icon);
    }
}
