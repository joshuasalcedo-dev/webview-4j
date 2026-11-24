package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** An amount of money as text, each currency its way (₱499.00, ¥4,990). The amount is in the smallest unit. */
public final class Money extends Component<Money> {

    public Money(long amount, String currency) {
        super("money");
        prop("amount", amount);
        prop("currency", currency);
    }

    /** Below zero in brackets, as statements write it: (₱5.00). */
    public Money accounting() { return prop("negative", "accounting"); }
    /** A plus on amounts above zero too: +₱5.00. */
    public Money signed() { return prop("signed", true); }
}
