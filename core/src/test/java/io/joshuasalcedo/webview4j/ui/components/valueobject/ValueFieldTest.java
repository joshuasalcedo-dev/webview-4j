package io.joshuasalcedo.webview4j.ui.components.valueobject;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.valueobject.contact.Email;
import io.joshuasalcedo.valueobject.financial.FiatCurrency;
import io.joshuasalcedo.valueobject.financial.Money;
import io.joshuasalcedo.valueobject.financial.Percentage;
import io.joshuasalcedo.valueobject.geo.Country;
import io.joshuasalcedo.valueobject.temporal.CalendarDate;
import io.joshuasalcedo.webview4j.ui.components.Link;
import io.joshuasalcedo.webview4j.ui.components.MoneyField;
import io.joshuasalcedo.webview4j.ui.components.NumberField;
import io.joshuasalcedo.webview4j.ui.components.Select;
import io.joshuasalcedo.webview4j.ui.components.Text;
import io.joshuasalcedo.webview4j.ui.components.TextField;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Value objects as fields: read as typed, the type's own words when wrong, the component that fits the type. */
class ValueFieldTest {

    @Test
    void anEmailIsReadAsItIsTypedAndSaysWhatIsWrongInItsOwnWords() {
        ValueField<Email> email = ValueField.email().required();
        assertTrue(email.error().isEmpty(), "nothing is said before anything is typed");
        assertFalse(email.isValid());

        email.type("ada@");
        assertTrue(email.value().isEmpty());
        assertFalse(email.isValid());
        String said = email.error().orElseThrow();
        assertFalse(said.isBlank(), "the value object's own message");

        email.type("ada@example.com");
        assertEquals(Email.parse("ada@example.com"), email.value().orElseThrow());
        assertTrue(email.isValid() && email.error().isEmpty());

        email.type("");
        assertEquals("Required", email.error().orElseThrow());
        assertInstanceOf(TextField.class, email.field("Email"));
    }

    @Test
    void anOptionalFieldMayStayEmpty() {
        ValueField<CalendarDate> date = ValueField.date();
        assertTrue(date.check(), "empty and not required is fine");
        date.type("25 Nov 2026");
        assertEquals(LocalDate.of(2026, 11, 25), date.value().orElseThrow().value(), "a date in a printed form");
    }

    @Test
    void eachTypeGetsTheComponentThatFitsIt() {
        assertInstanceOf(Select.class, ValueField.country().field("Country"), "an enum is a dropdown of its constants");
        ValueField<Country> country = ValueField.country().type("PH");
        assertEquals(Country.parse("PH"), country.value().orElseThrow());

        ValueField<Money> price = ValueField.money(FiatCurrency.PHP).set(Money.of(new BigDecimal("499.00"), FiatCurrency.PHP));
        assertInstanceOf(MoneyField.class, price.field("Price"));
        assertEquals(new BigDecimal("499.00"), price.value().orElseThrow().amount());

        ValueField<Percentage> share = ValueField.percentage().type("12.5%");
        assertEquals(0, new BigDecimal("12.5").compareTo(share.value().orElseThrow().value()));
        assertInstanceOf(NumberField.class, share.field("Share"));
    }

    @Test
    void anyTypeWithParseIsAField() {
        assertThrows(IllegalArgumentException.class, () -> ValueField.of(Object.class), "no parse: says so");
        ValueField<Country> byOf = ValueField.of(Country.class).type("Philippines");
        assertEquals(Country.parse("PH"), byOf.value().orElseThrow());
    }

    @Test
    void valuesAreShownTheWayEachIsRead() {
        assertInstanceOf(Link.class, ValueViews.show(Email.parse("ada@example.com")), "an email to write to");
        assertInstanceOf(io.joshuasalcedo.webview4j.ui.components.Money.class,
                ValueViews.show(Money.of(new BigDecimal("499.00"), FiatCurrency.PHP)));
        assertInstanceOf(Text.class, ValueViews.show(Country.parse("PH")));
        assertEquals(Country.parse("PH").displayName(), ValueViews.name(Country.parse("PH")));
    }
}
