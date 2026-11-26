package io.joshuasalcedo.webview4j.explorer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.joshuasalcedo.webview4j.ui.components.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoriesTest {

    private static final String STORY = """
            package io.joshuasalcedo.webview4j.explorer.stories;

            import static io.joshuasalcedo.webview4j.ui.Ui.*;

            import io.joshuasalcedo.webview4j.explorer.Args;
            import io.joshuasalcedo.webview4j.explorer.Story;
            import io.joshuasalcedo.webview4j.explorer.Variable;
            import io.joshuasalcedo.webview4j.ui.Component;
            import io.joshuasalcedo.webview4j.ui.components.Button;
            import java.util.List;

            public final class ButtonTried implements Story {

                static final Variable<String> LABEL = Variable.text("Label", "Save");

                public Class<?> component() { return Button.class; }
                public String title() { return "Tried"; }
                public List<Variable<?>> variables() { return List.of(LABEL); }

                public Component<?> showcase(Args args) {
                    return button(args.get(LABEL))
                            .accent();
                }
            }
            """;

    @Test
    void aStoryIsCompiledFromItsSourceAndShownWithItsVariables(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ButtonTried.java"), STORY);
        Stories stories = new Stories(StoriesTest.class.getClassLoader(), dir);
        Stories.Loaded loaded = stories.load();
        assertNull(loaded.errors());
        Story story = loaded.stories().stream().filter(s -> s.title().equals("Tried")).findFirst().orElseThrow();
        assertEquals(Button.class, story.component());

        // its code is the showcase's body, as written
        assertEquals("return button(args.get(LABEL))\n        .accent();", stories.code(story).orElseThrow());

        // a variable's value reaches the showcase
        Args args = Args.of(story.variables());
        Stories.Outcome outcome = Stories.run(story, args);
        assertTrue(outcome.ok(), outcome.error());
        assertEquals("Save", ((Button) outcome.value()).getText());
        @SuppressWarnings("unchecked")
        Variable<String> label = (Variable<String>) story.variables().getFirst();
        assertEquals("Delete", ((Button) Stories.run(story, args.set(label, "Delete")).value()).getText());

        // an edit in the Code tab compiles the story with that body (the file is left as it is)
        StringBuilder errors = new StringBuilder();
        Story edited = stories.withBody(story, "return button(args.get(LABEL) + \"!\");", errors).orElseThrow(() -> new AssertionError(errors));
        assertEquals("Delete!", ((Button) Stories.run(edited, args).value()).getText());
        assertEquals(STORY, Files.readString(dir.resolve("ButtonTried.java")));

        // an edit that does not compile says why
        StringBuilder broken = new StringBuilder();
        assertTrue(stories.withBody(story, "return buton(\"x\");", broken).isEmpty());
        assertTrue(broken.toString().contains("cannot find symbol"), broken.toString());
    }

    @Test
    void everyWrittenStoryShows() {
        Stories stories = new Stories(StoriesTest.class.getClassLoader(), Stories.findSourceDir());
        Stories.Loaded loaded = stories.load();
        assertNull(loaded.errors(), loaded.errors());
        List<String> failures = new ArrayList<>();
        for (Story story : loaded.stories()) {
            Stories.Outcome outcome = Stories.run(story, Args.of(story.variables()));
            if (!outcome.ok()) failures.add(story.getClass().getSimpleName() + ": " + outcome.error());
            else assertInstanceOf(Object.class, outcome.value());
        }
        if (!failures.isEmpty()) fail(String.join("\n\n", failures));
    }
}
