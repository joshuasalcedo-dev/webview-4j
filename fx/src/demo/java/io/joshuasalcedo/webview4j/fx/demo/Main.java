package io.joshuasalcedo.webview4j.fx.demo;

import javafx.application.Application;

/** Starts the demo. Not the Application itself: JavaFX on the class path needs a main that is not one. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        Application.launch(FxDemo.class, args);
    }
}
