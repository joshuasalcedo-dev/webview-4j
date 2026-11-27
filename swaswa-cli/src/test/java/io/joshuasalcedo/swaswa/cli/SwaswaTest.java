package io.joshuasalcedo.swaswa.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwaswaTest {

    @TempDir
    Path dir;

    private Swaswa swaswa(Path here) {
        return new Swaswa(new PrintStream(new ByteArrayOutputStream()), here);
    }

    @Test
    void namesFromTheAppsName() {
        assertEquals("MyNotes", Swaswa.className("my-notes"));
        assertEquals("My Notes", Swaswa.title("my-notes"));
        assertEquals("Shop2", Swaswa.className("shop2"));
        assertEquals("1.0.0", Swaswa.appVersion("1.0.0-SNAPSHOT"));
        assertEquals("2.1", Swaswa.appVersion("2.1"));
    }

    @Test
    void newMakesAnAppItsProjectReads() throws Exception {
        assertEquals(0, swaswa(dir).run(List.of("new", "my-notes", "--group", "org.acme")));
        Path app = dir.resolve("my-notes");
        Path source = app.resolve("src/main/java/org/acme/mynotes/MyNotes.java");
        assertTrue(Files.isRegularFile(source));
        String java = Files.readString(source);
        assertTrue(java.contains("package org.acme.mynotes;"));
        assertTrue(java.contains("public class MyNotes extends SwaApplication"));
        assertFalse(java.contains("{{"), "every value filled");
        String pom = Files.readString(app.resolve("pom.xml"));
        assertTrue(pom.contains("<swaswa.version>" + Swaswa.version() + "</swaswa.version>"));
        assertFalse(pom.contains("{{"));
        assertTrue(Files.isRegularFile(app.resolve(".gitignore")));

        Swaswa.Project project = Swaswa.Project.at(app.resolve("src/main/java"));   // from below, too
        assertEquals("my-notes", project.artifactId());
        assertEquals("org.acme.mynotes.MyNotes", project.mainClass());
        assertEquals("My Notes", project.name());
        assertEquals("1.0.0-SNAPSHOT", project.version());

        assertThrows(Swaswa.Failure.class, () -> swaswa(dir).run(List.of("new", "my-notes")), "not over an app");
    }

    @Test
    void wrongUseIsSaid() {
        assertThrows(Swaswa.Failure.class, () -> swaswa(dir).run(List.of("new")));
        assertThrows(Swaswa.Failure.class, () -> swaswa(dir).run(List.of("new", "x", "--package", "Not.A.Package")));
        assertThrows(Swaswa.Failure.class, () -> swaswa(dir).run(List.of("frobnicate")));
        assertThrows(Swaswa.Failure.class, () -> swaswa(dir).run(List.of("run")), "no pom.xml");
    }
}
