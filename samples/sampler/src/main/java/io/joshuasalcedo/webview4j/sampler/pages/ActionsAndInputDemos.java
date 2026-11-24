package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.sampler.pages.Demos.ordered;
import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.List;
import java.util.Map;

/** The Sampler's pages: Buttons, links and every field and choice a person fills in. */
final class ActionsAndInputDemos implements Demos {

    private int clicks;
    private String name = "";
    private String notes = "";
    private boolean remember = true;
    private boolean sync;
    private String country = "ph";
    private String show = "all";
    private String password = "";
    private String query = "";
    private String searched = "";
    private Double quantity = 3.0;
    private Double discount = 12.5;
    private Long price = 49_900L;
    private String currency = "PHP";
    private Double weight = 2.5;
    private Unit weightUnit = Unit.KILOGRAM;
    private List<String> tags = List.of("work", "urgent");

    private static final List<String> NOTES = List.of(
            "Quarterly report draft", "Grocery list", "Flight to Tokyo", "Team offsite agenda",
            "Book recommendations", "Dentist appointment", "Report card for Mia");

    private static String strength(String p) {
        if (p.isEmpty()) return "Type a password";
        int kinds = (p.chars().anyMatch(Character::isUpperCase) ? 1 : 0)
                + (p.chars().anyMatch(Character::isLowerCase) ? 1 : 0)
                + (p.chars().anyMatch(Character::isDigit) ? 1 : 0)
                + (p.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0);
        if (p.length() < 12) return "Too short (" + p.length() + " of 12)";
        return kinds >= 3 ? "Strong" : "Fair: mix in digits or symbols";
    }

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Actions", "Button", () -> column(
                row(button("Accent").accent().onClick(() -> clicks++),
                        button("Default").onClick(() -> clicks++),
                        button("Subtle").subtle().onClick(() -> clicks++),
                        button("Disabled").disabled(true)),
                text("Clicked " + clicks + " times").tone(Tone.SECONDARY)),
                """
                button("Accent").accent().onClick(() -> clicks++)
                button("Default").onClick(() -> clicks++)
                button("Subtle").subtle()
                button("Disabled").disabled(true)""",
                Button.class);
        gallery.page("Actions", "Link", () -> column(
                link("webview-4j on GitHub", "https://github.com/joshuasalcedo-dev/webview-4j"),
                link("A link that runs Java", "#").onClick(() -> clicks++),
                text("Clicked " + clicks + " times").tone(Tone.SECONDARY)),
                """
                link("webview-4j on GitHub", "https://github.com/...")
                link("A link that runs Java", "#").onClick(() -> clicks++)""",
                Link.class);
        gallery.page("Input", "Text field", () -> column(
                textField("Name").value(name).onChange(v -> name = v).hint("As people see it"),
                textField("Email").value("ada@").error("That is not an email address"),
                textField("Password").password().placeholder("At least 12 characters"),
                text(name.isBlank() ? "Type your name" : "Hello, " + name + "!")),
                """
                textField("Name").value(name).onChange(v -> name = v).hint("As people see it")
                textField("Email").value("ada@").error("That is not an email address")
                textField("Password").password()""",
                TextField.class);
        gallery.page("Input", "Text area", () -> column(
                textArea("Notes").value(notes).rows(5).onChange(v -> notes = v),
                text(notes.length() + " characters").tone(Tone.SECONDARY)),
                """
                textArea("Notes").value(notes).rows(5).onChange(v -> notes = v)""",
                TextArea.class);
        gallery.page("Input", "Checkbox and switch", () -> column(
                checkbox("Remember me").checked(remember).onChange(v -> remember = v),
                toggle("Sync").checked(sync).onChange(v -> sync = v),
                text((remember ? "Remembered" : "Not remembered") + ", sync " + (sync ? "on" : "off")).tone(Tone.SECONDARY)),
                """
                checkbox("Remember me").checked(remember).onChange(v -> remember = v)
                toggle("Sync").checked(sync).onChange(v -> sync = v)""",
                Checkbox.class, Toggle.class);
        gallery.page("Input", "Select", () -> column(
                select("Country", Map.of("ph", "Philippines", "jp", "Japan", "us", "United States"))
                        .value(country).onChange(v -> country = v).width(260),
                text("Chosen: " + country).tone(Tone.SECONDARY)),
                """
                select("Country", Map.of("ph", "Philippines", "jp", "Japan", "us", "United States"))
                        .value(country).onChange(v -> country = v)""",
                Select.class);
        gallery.page("Input", "Segmented control", () -> column(
                segmented("Show", ordered("all", "All", "unread", "Unread", "starred", "Starred"))
                        .value(show).onChange(v -> show = v),
                text("Showing " + show).tone(Tone.SECONDARY)),
                """
                segmented("Show", Map.of("all", "All", "unread", "Unread", "starred", "Starred"))
                        .value(show).onChange(v -> show = v)""",
                Segmented.class);
        gallery.page("Input", "Label", () -> column(
                label("Workspace name"),
                label("Billing address").strong(),
                label("Optional").tone(Tone.SECONDARY),
                row(label("Status").strong(), text("Active"))),
                """
                label("Workspace name")
                label("Billing address").strong()
                label("Optional").tone(Tone.SECONDARY)""",
                Label.class);
        gallery.page("Input", "Password field", () -> column(
                passwordField("New password").value(password).onChange(v -> password = v)
                        .placeholder("At least 12 characters").hint("Use letters, digits and a symbol")
                        .error(!password.isEmpty() && password.length() < 12 ? "Too short" : null),
                passwordField("Current password").value("hunter2").disabled(true),
                text(strength(password)).tone(Tone.SECONDARY)),
                """
                passwordField("New password").value(password).onChange(v -> password = v)
                        .placeholder("At least 12 characters")
                        .error(password.length() < 12 ? "Too short" : null)
                passwordField("Current password").disabled(true)""",
                PasswordField.class);
        gallery.page("Input", "Search field", () -> {
            var hits = NOTES.stream().filter(n -> n.toLowerCase().contains(query.toLowerCase())).toList();
            var items = new java.util.ArrayList<io.joshuasalcedo.webview4j.ui.Component<?>>();
            items.add(searchField("Search notes").value(query).placeholder("Search 7 notes")
                    .onChange(v -> query = v).onSubmit(v -> searched = v).width(320));
            hits.forEach(n -> items.add(text(n)));
            items.add(text(hits.size() + " of " + NOTES.size() + " notes"
                    + (searched.isBlank() ? "" : " · last submitted: \"" + searched + "\"")).tone(Tone.SECONDARY));
            return column(items);
        },
                """
                searchField("Search notes").value(query).placeholder("Search 7 notes")
                        .onChange(v -> query = v)      // as they type
                        .onSubmit(v -> searched = v)   // on Enter""",
                SearchField.class);
        gallery.page("Input", "Number field", () -> column(
                numberField("Quantity").value(quantity).min(1).max(99).step(1).fractionDigits(0)
                        .onChange(v -> quantity = v).hint("1 to 99"),
                numberField("Discount").value(discount).min(0).max(100).step(0.5).fractionDigits(1)
                        .after("%").onChange(v -> discount = v)
                        .error(discount != null && discount > 50 ? "Discounts over 50% need approval" : null),
                numberField("Tip").before("$").value(null).hint("Empty until typed"),
                text("Quantity " + (quantity == null ? "empty" : String.format("%.0f", quantity))
                        + ", discount " + (discount == null ? "empty" : discount + "%")).tone(Tone.SECONDARY)),
                """
                numberField("Quantity").value(quantity).min(1).max(99).step(1).fractionDigits(0)
                        .onChange(v -> quantity = v)   // null when empty
                numberField("Discount").value(discount).step(0.5).after("%")
                        .error(discount > 50 ? "Discounts over 50% need approval" : null)""",
                NumberField.class);
        gallery.page("Input", "Money field", () -> column(
                moneyField("Price", currency).value(price).currencies(List.of("PHP", "USD", "EUR", "JPY"))
                        .min(0).onChange(v -> price = v).onCurrencyChange(v -> currency = v)
                        .hint("Amounts are in the smallest unit: 49900 is 499.00"),
                moneyField("Refund", "USD").value(-1_250L).min(0).error("A refund can't be negative"),
                text(price == null ? "No price" : "Stored: " + price + " (" + currency + ")").tone(Tone.SECONDARY)),
                """
                moneyField("Price", currency).value(price)          // 49900 = 499.00
                        .currencies(List.of("PHP", "USD", "EUR", "JPY"))
                        .onChange(v -> price = v)                // Long, smallest unit
                        .onCurrencyChange(v -> currency = v)""",
                MoneyField.class);
        gallery.page("Input", "Quantity field", () -> column(
                quantityField("Parcel weight", weightUnit).value(weight)
                        .units(List.of(Unit.GRAM, Unit.KILOGRAM, Unit.OUNCE, Unit.POUND)).convert()
                        .onChange(v -> weight = v).onUnitChange(u -> weightUnit = u)
                        .hint("Changing the unit converts the number"),
                quantityField("Body temperature", Unit.CELSIUS).value(39.4).error("That is a fever"),
                text(weight == null ? "No weight" : weight + " " + weightUnit.key()).tone(Tone.SECONDARY)),
                """
                quantityField("Parcel weight", weightUnit).value(weight)
                        .units(List.of(Unit.GRAM, Unit.KILOGRAM, Unit.OUNCE, Unit.POUND))
                        .convert()                              // unit change converts
                        .onChange(v -> weight = v).onUnitChange(u -> weightUnit = u)""",
                QuantityField.class);
        gallery.page("Input", "Token field", () -> column(
                tokenField("Tags").value(tags)
                        .suggestions(List.of("work", "personal", "urgent", "ideas", "travel", "reading"))
                        .placeholder("Add a tag").onChange(v -> tags = v),
                text(tags.isEmpty() ? "No tags" : tags.size() + " tags: " + String.join(", ", tags)).tone(Tone.SECONDARY)),
                """
                tokenField("Tags").value(tags)
                        .suggestions(List.of("work", "personal", "urgent", "ideas"))
                        .placeholder("Add a tag")
                        .onChange(v -> tags = v)        // List<String>""",
                TokenField.class);
        gallery.page("Actions", "Shortcut", () -> column(
                row(text("Search"), shortcut("Mod+K")),
                row(text("New note"), shortcut("Mod+N")),
                row(text("Save"), shortcut("Mod+S")),
                row(text("Command palette"), shortcut("Mod+Shift+P")),
                row(text("Close dialog"), shortcut("Escape")),
                text("\"Mod\" is Cmd on a Mac and Ctrl elsewhere").tone(Tone.SECONDARY)),
                """
                row(text("Search"), shortcut("Mod+K"))   // Cmd+K on a Mac, Ctrl+K elsewhere
                shortcut("Mod+Shift+P")""",
                Shortcut.class);
    }
}
