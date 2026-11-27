package io.joshuasalcedo.swaswa.component;

/**
 * Rows and columns of data (HTML's {@code <table>}, JavaFX's {@code TableView}): a table, a data table, a data grid.
 * Every library's is one (<table> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface TableView<Self extends TableView<Self>> extends Component<Self> {}
