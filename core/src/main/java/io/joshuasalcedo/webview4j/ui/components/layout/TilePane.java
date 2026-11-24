package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * Children in tiles of the same size, as many in a row as fit, as JavaFX's {@code TilePane}: a gallery, a dashboard
 * of cards.
 * <pre>{@code
 * new TilePane(cards).tileWidth(200).hgap(12).vgap(12)
 * }</pre>
 */
public final class TilePane extends Pane<TilePane> {

    public TilePane(Component<?>... children) {
        super("tilepane");
        addAll(children);
    }

    /** The narrowest a tile is, CSS pixels: as many in a row as fit (160 unless set; JavaFX's prefTileWidth). */
    public TilePane tileWidth(double width) { return prop("tileWidth", width); }
    /** Every tile this tall. */
    public TilePane tileHeight(double height) { return prop("tileHeight", height); }
    /** A fixed number of columns instead (JavaFX's prefColumns). */
    public TilePane columns(int columns) { return prop("columns", columns); }
    public TilePane hgap(double gap) { return prop("hgap", gap); }
    public TilePane vgap(double gap) { return prop("vgap", gap); }

    /** Where each child sits in its tile (JavaFX's tileAlignment). */
    public TilePane tileAlignment(Pos alignment) { return prop("tileAlignment", alignment.key()); }
}
