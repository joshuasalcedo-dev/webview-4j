package io.joshuasalcedo.swaswa.html;

/** A page's {@code <body>}: what {@link Document#body()} gives, to add elements to. The page's own; never made in Java. */
public final class Body extends HtmlElement<Body> {

    Body(Document document) {
        super("body", false);
        this.document = document;
        this.key = 0;
    }
}
