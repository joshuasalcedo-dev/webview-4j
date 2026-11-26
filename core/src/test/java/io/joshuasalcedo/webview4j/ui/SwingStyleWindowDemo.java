package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.layout.HBox;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** A look at a Swing-style window, by hand: -Ddemo.out=<folder> writes its screenshot there. */
@EnabledIfSystemProperty(named = "demo.out", matches = ".+")
class SwingStyleWindowDemo {

    static final class Counter extends Window {
        final Text label = new Text("Clicked 0 times");
        final Button button = new Button("Click me").accent();
        final TextField name = new TextField("Name");
        final Checkbox agree = new Checkbox("Notify me");
        int count;

        Counter() {
            super("Counter");
            setSize(520, 420);
            // typed styles: each component takes only its own parts
            this.button.style(io.joshuasalcedo.webview4j.ui.style.ButtonPart.ROOT, Style.of().radius(20).padding(8, 28));
            this.name.style(io.joshuasalcedo.webview4j.ui.style.TextFieldPart.LABEL, Style.of().color(Style.Token.ACCENT).fontWeight(700));
            button.addActionListener(e -> label.setText("Clicked " + ++count + " times" + (name.getText().isBlank() ? "" : ", " + name.getText())));
            add(new Text("Swing-style, typed Java").variant(TextVariant.TITLE), name, agree,
                    new HBox(8, button, new Button("Reset").subtle()), label, new ProgressBar("Upload", 40));
        }
    }

    @Test
    @Timeout(90)
    void show() throws Exception {
        Path out = Path.of(System.getProperty("demo.out"));
        try (App app = App.start()) {
            Counter counter = new Counter();
            View view = counter.open(app).join().view();
            for (int i = 0; i < 100 && !String.valueOf(view.eval("document.body.innerText").join()).contains("Click me"); i++) Thread.sleep(100);
            System.out.println("DEMO url=" + view.eval("location.href").join());
            System.out.println("DEMO sheets=" + view.eval("[...document.styleSheets].map(s => s.href + ':' + (()=>{try{return s.cssRules.length}catch(e){return 'x'}})()).join(' | ')").join());
            System.out.println("DEMO button-radius=" + view.eval("getComputedStyle([...document.querySelectorAll('button')].find(b => b.innerText.includes('Click me'))).borderRadius").join());
            System.out.println("DEMO label-color=" + view.eval("getComputedStyle(document.querySelector('.nt-field-label')).color").join());
            System.out.println("DEMO button-bg=" + view.eval("getComputedStyle([...document.querySelectorAll('button')].find(b => b.innerText.includes('Click me'))).backgroundColor").join());
            view.eval("[...document.querySelectorAll('button')].find(b => b.innerText.includes('Click me')).click()").join();
            Thread.sleep(800);
            System.out.println("DEMO after-click=" + view.eval("document.body.innerText").join());
            view.screenshot(out.resolve("swing-style-window.png")).join();
        }
    }
}
