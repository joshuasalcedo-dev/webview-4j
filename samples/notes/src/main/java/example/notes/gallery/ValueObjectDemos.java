package example.notes.gallery;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.valueobject.contact.Email;
import io.joshuasalcedo.valueobject.contact.PhoneNumber;
import io.joshuasalcedo.valueobject.financial.CardNumber;
import io.joshuasalcedo.valueobject.financial.FiatCurrency;
import io.joshuasalcedo.valueobject.financial.Iban;
import io.joshuasalcedo.valueobject.financial.Money;
import io.joshuasalcedo.valueobject.financial.Percentage;
import io.joshuasalcedo.valueobject.geo.Country;
import io.joshuasalcedo.valueobject.geo.Language;
import io.joshuasalcedo.valueobject.temporal.CalendarDate;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.valueobject.*;
import java.util.Optional;
import java.util.function.Function;

/** The gallery's pages: Value objects as fields and as text: email, phone, link, date, percentage, IBAN, card number, country, currency, language. */
final class ValueObjectDemos implements Demos {

    // Each field keeps what was typed, its value and its error: the page draws them again after every change.
    private final ValueField<Email> email = ValueField.email().required().type("ada@example.com");
    private final ValueField<Email> badEmail = ValueField.email().type("ada@");
    private final ValueField<PhoneNumber> phone = ValueField.phone().type("+63 917 555 0142");
    private final ValueField<PhoneNumber> badPhone = ValueField.phone().type("555");
    private final ValueField<io.joshuasalcedo.valueobject.contact.Link> site = ValueField.link().type("https://github.com/joshuasalcedo-dev");
    private final ValueField<io.joshuasalcedo.valueobject.contact.Link> badSite = ValueField.link().type("not a link");
    private final ValueField<CalendarDate> due = ValueField.date().type("25 Nov 2026");
    private final ValueField<CalendarDate> badDue = ValueField.date().type("31 Feb 2026");
    private final ValueField<Percentage> share = ValueField.percentage().type("12.5%");
    private final ValueField<Percentage> badShare = ValueField.percentage().type("lots");
    private final ValueField<Iban> iban = ValueField.iban().type("DE89 3704 0044 0532 0130 00");
    private final ValueField<Iban> badIban = ValueField.iban().type("DE00 1234");
    private final ValueField<CardNumber> card = ValueField.cardNumber().type("4111 1111 1111 1111");
    private final ValueField<CardNumber> badCard = ValueField.cardNumber().type("4111 1111 1111 1112");
    private final ValueField<Country> country = ValueField.country().required().type("PH");
    private final ValueField<FiatCurrency> currency = ValueField.currency().type("PHP");
    private final ValueField<Language> language = ValueField.language().type("en");
    private String pickedCountry = "jp";

    // the payment form
    private final ValueField<Email> payEmail = ValueField.email().required();
    private final ValueField<CardNumber> payCard = ValueField.cardNumber().required();
    private final ValueField<Money> payAmount = ValueField.money(FiatCurrency.PHP).required();
    private final ValueField<Country> payCountry = ValueField.country().required();
    private String paid = "";

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Value objects", "Email field", () -> pair(email, badEmail, "Email",
                        e -> "Domain " + e.domain() + ", masked " + e.masked()),
                """
                ValueField<Email> email = ValueField.email().required();
                email.field("Email")                  // an EmailField: typing calls email.type(text)
                email.value()                         // Optional<Email>, or email.error(): its own words
                ValueViews.show(email.value().orElse(null))   // a mailto: link""",
                EmailField.class);
        gallery.page("Value objects", "Phone field", () -> pair(phone, badPhone, "Phone",
                        p -> "E.164 " + p.toE164() + ", country code +" + p.countryCode()),
                """
                ValueField<PhoneNumber> phone = ValueField.phone();
                phone.field("Phone")                  // a PhoneField
                phone.value().map(PhoneNumber::toE164)""",
                PhoneField.class);
        gallery.page("Value objects", "URL field", () -> pair(site, badSite, "Website",
                        l -> "Host " + l.host() + (l.isSecure() ? ", secure" : ", not secure")),
                """
                var site = ValueField.link();
                site.field("Website")                 // a UrlField
                site.value().map(Link::host)""",
                UrlField.class);
        gallery.page("Value objects", "Date field", () -> pair(due, badDue, "Due",
                        d -> "A " + d.dayOfWeek().toString().toLowerCase() + ", " + CalendarDate.today().daysUntil(d) + " days from today"),
                """
                ValueField<CalendarDate> due = ValueField.date();   // "25 Nov 2026", "2026-11-25"...
                due.field("Due")                      // a DateField
                due.value().map(CalendarDate::dayOfWeek)""",
                DateField.class);
        gallery.page("Value objects", "Percent field", () -> pair(share, badShare, "Share",
                        p -> "As a value: " + p.value().toPlainString()),
                """
                ValueField<Percentage> share = ValueField.percentage();   // "12.5%"
                share.field("Share")                  // a PercentField""",
                PercentField.class);
        gallery.page("Value objects", "IBAN field", () -> pair(iban, badIban, "IBAN",
                        i -> "A bank in " + ValueViews.name(i.country()) + ", " + i.formatted()),
                """
                ValueField<Iban> iban = ValueField.iban();
                iban.field("IBAN")                    // an IbanField: the check digits are checked
                iban.value().map(Iban::country)""",
                IbanField.class);
        gallery.page("Value objects", "Card number field", () -> pair(card, badCard, "Card number",
                        c -> ValueViews.name(c.brand()) + ", " + c.masked()),
                """
                ValueField<CardNumber> card = ValueField.cardNumber();
                card.field("Card number")             // a CardNumberField: the Luhn check too
                card.value().map(CardNumber::masked)""",
                CardNumberField.class);
        gallery.page("Value objects", "Country, currency, language", () -> column(
                        country.field("Country"),
                        shown(country),
                        currency.field("Currency"),
                        shown(currency),
                        language.field("Language"),
                        shown(language),
                        new CountrySelect("Ship to (a few countries)")
                                .options(Demos.ordered("jp", "Japan", "ph", "Philippines", "sg", "Singapore"))
                                .value(pickedCountry).onChange(v -> pickedCountry = v),
                        text("Ship to: " + pickedCountry).tone(Tone.SECONDARY)),
                """
                ValueField<Country> country = ValueField.country().required();
                country.field("Country")              // a CountrySelect of every country
                ValueField.currency().field("Currency")   // a CurrencySelect
                ValueField.language().field("Language")   // a LanguageSelect
                new CountrySelect("Ship to").options(Map.of("jp", "Japan", ...))
                        .value(code).onChange(v -> code = v)""",
                CountrySelect.class, CurrencySelect.class, LanguageSelect.class);
        gallery.page("Value objects", "Value fields by hand", () -> column(
                        new EmailField("Email").value("ada@").error("That is not an email address"),
                        new PhoneField("Phone").value("+63 917 555 0142").hint("With the country's code"),
                        new UrlField("Website").value("https://example.com").disabled(true),
                        new DateField("Born").value("1815-12-10"),
                        new PercentField("Tax").value("12%"),
                        new IbanField("IBAN").value("GB82 WEST 1234 5698 7654 32"),
                        new CardNumberField("Card").value("5555 5555 5555 4444").hint("Shown with its brand"),
                        new CurrencySelect("Pay in").options(Demos.ordered("PHP", "Philippine peso", "USD", "US dollar")).value("USD"),
                        new LanguageSelect("Speaks").value("ja").optional()),
                """
                // Each field without a ValueField: the text, an error or a hint, and onChange(text -> ...)
                new EmailField("Email").value("ada@").error("That is not an email address")
                new PhoneField("Phone").value("+63 917 555 0142").hint("With the country's code")
                new UrlField("Website").value("https://example.com").disabled(true)
                new CurrencySelect("Pay in").options(Map.of("PHP", "Philippine peso", ...)).value("USD")""",
                EmailField.class, PhoneField.class, UrlField.class, DateField.class, PercentField.class,
                IbanField.class, CardNumberField.class, CurrencySelect.class, LanguageSelect.class);
        gallery.page("Value objects", "Payment form", () -> column(
                        payEmail.field("Email"),
                        payCard.field("Card number"),
                        payAmount.field("Amount"),
                        payCountry.field("Billing country"),
                        row(button("Pay").accent().onClick(this::pay),
                                button("Clear").onClick(this::clearPayment)),
                        paid.isEmpty() ? text("Fill in the form and pay").tone(Tone.SECONDARY)
                                : text(paid).tone(paid.startsWith("Paid") ? Tone.SUCCESS : Tone.ERROR)),
                """
                var email = ValueField.email().required();
                var card = ValueField.cardNumber().required();
                var amount = ValueField.money(FiatCurrency.PHP).required();   // a MoneyField
                var country = ValueField.country().required();

                column(email.field("Email"), card.field("Card number"),
                        amount.field("Amount"), country.field("Billing country"),
                        button("Pay").accent().onClick(() -> {
                            boolean ok = email.check() & card.check() & amount.check() & country.check();
                            if (ok) charge(card.value().get(), amount.value().get());
                        }))""",
                EmailField.class, CardNumberField.class, CountrySelect.class);
    }

    /** A valid field and a wrong one, each with what it read below it. */
    private static <T> Component<?> pair(ValueField<T> good, ValueField<T> bad, String label, Function<T, String> about) {
        return column(
                good.field(label),
                read(good, about),
                bad.field(label + " (typed wrong)"),
                read(bad, about));
    }

    private static <T> Component<?> read(ValueField<T> field, Function<T, String> about) {
        Optional<T> value = field.value();
        if (value.isPresent()) {
            return row(text("Read:").tone(Tone.SECONDARY), ValueViews.show(value.get()),
                    text(about.apply(value.get())).tone(Tone.SECONDARY));
        }
        if (field.isEmpty()) return text("Nothing typed yet").tone(Tone.TERTIARY);
        return text("Not read: " + field.error().orElse("not valid")).tone(Tone.ERROR);
    }

    private static Component<?> shown(ValueField<?> field) {
        return row(text("Shown as:").tone(Tone.SECONDARY), ValueViews.show(field.value().orElse(null)));
    }

    private void pay() {
        boolean ok = payEmail.check() & payCard.check() & payAmount.check() & payCountry.check();
        if (!ok) {
            paid = "Check the fields marked in red";
            return;
        }
        Money amount = payAmount.value().orElseThrow();
        paid = "Paid " + amount.amount().toPlainString() + " " + amount.currency().code() + " with "
                + payCard.value().orElseThrow().masked() + "; the receipt goes to " + payEmail.value().orElseThrow().value();
    }

    private void clearPayment() {
        payEmail.set(null);
        payCard.set(null);
        payAmount.set(null);
        payCountry.set(null);
        paid = "";
    }
}
