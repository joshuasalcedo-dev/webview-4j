package io.joshuasalcedo.swaswa.component;

/**
 * A value shown for people and kept for machines (HTML's {@code <data value>}): an amount of money, a quantity, a cell's value.
 * Every library's is one (<data> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface DataValue<Self extends DataValue<Self>> extends Component<Self> {}
