package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** An amount of money: the currency's symbol inside, its decimals, and a currency picker that keeps the amount. Amounts are whole numbers of the currency's smallest unit (49900 is ₱499.00). */
public final class MoneyField extends Component<MoneyField> {

    public MoneyField(String label, String currency) {
        super("moneyField");
        prop("label", label);
        prop("currency", currency);
    }

    public MoneyField value(Long amount) { return prop("value", amount); }
    /** The currencies to choose from (ISO codes); none: no picker. */
    public MoneyField currencies(java.util.List<String> currencies) { return prop("currencies", java.util.List.copyOf(currencies)); }
    public MoneyField min(long amount) { return prop("min", amount); }
    public MoneyField max(long amount) { return prop("max", amount); }
    public MoneyField hint(String hint) { return prop("hint", hint); }
    public MoneyField error(String error) { return prop("error", error); }
    /** The amount as it changes, in the smallest unit; null when empty. */
    public MoneyField onChange(java.util.function.Consumer<Long> action) { return on("onChange", v -> action.accept(Keys.whole(v))); }
    public MoneyField onCurrencyChange(java.util.function.Consumer<String> action) { return on("onCurrencyChange", v -> action.accept(Keys.text(v))); }
}
