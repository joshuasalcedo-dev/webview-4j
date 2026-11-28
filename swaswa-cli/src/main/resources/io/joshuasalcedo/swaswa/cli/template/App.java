package {{package}};

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.ui.button.UiButton;
import io.joshuasalcedo.swaswa.ui.text.UiText;
import io.joshuasalcedo.swaswa.ui.text.UiTextField;

/** {{name}}. */
public class {{className}} extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    private final UiTextField name = new UiTextField();
    private final UiText greeting = new UiText("");

    @Override
    protected WindowOptions window() {
        return WindowOptions.html("").size(720, 480);
    }

    @Override
    protected void build() {
        setTitle("{{name}}");
        buildForm();
    }

    private void buildForm() {
        UiText title = new UiText("{{name}}");
        title.setVariant(UiText.Variant.TITLE);

        name.setLabel("Your name");
        name.setPlaceholder("Ana Reyes");

        UiButton hello = new UiButton("Say hello");
        hello.setVariant(UiButton.Variant.ACCENT);
        hello.onClick(e -> sayHello());

        Div form = new Div(title, name, hello, greeting);
        form.getStyle().set(CssProperty.DISPLAY, "grid");
        form.getStyle().set(CssProperty.GAP, "12px");
        form.getStyle().set(CssProperty.PADDING, "24px");
        form.getStyle().set(CssProperty.MAX_WIDTH, "480px");
        getMain().add(form);
    }

    private void sayHello() {
        String who = name.getValue();
        greeting.setText(who == null || who.isBlank() ? "Hello!" : "Hello, " + who + "!");
    }
}
