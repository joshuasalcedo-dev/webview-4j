package io.joshuasalcedo.webview4j.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

/**
 * The project's documentation site, made by pretty-docs (io.joshuasalcedo.pretty:pretty-docs-maven-plugin).
 * pretty-docs reads Maven builds, so {@code ./gradlew prettyDocs} writes one beside the Gradle build,
 * in build/pretty-docs/maven: a pom per Java module whose source and class directories are the Gradle
 * module's own, and runs the plugin's generate goal on it with {@code mvn}. The site is written to
 * build/docs/pretty; open its index.html. Applied to the root project; every module with the java
 * plugin is documented.
 * <pre>
 * prettyDocs {
 *     version = "1.1.0"          // of pretty-docs, from the local Maven repository or Maven Central
 *     mode = "resolved"          // or "source": parse the sources only
 *     visibility = "protected"   // public, protected, package or private
 * }
 * </pre>
 */
public class PrettyDocsPlugin implements Plugin<Project> {

    /** What the site is made with. */
    public abstract static class Settings {
        public abstract Property<String> getVersion();
        public abstract Property<String> getMode();
        public abstract Property<String> getVisibility();
        public abstract Property<String> getTitle();
        public abstract Property<String> getJavaRelease();
    }

    @Override
    public void apply(Project root) {
        if (root != root.getRootProject()) throw new GradleException("io.joshuasalcedo.pretty-docs is applied to the root project");
        Settings settings = root.getExtensions().create("prettyDocs", Settings.class);
        settings.getVersion().convention("1.1.0");
        settings.getMode().convention("resolved");
        settings.getVisibility().convention("protected");
        settings.getTitle().convention(root.getName());
        settings.getJavaRelease().convention("25");

        root.getTasks().register("prettyDocs", PrettyDocs.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Makes the documentation site with pretty-docs into build/docs/pretty.");
            task.getPluginVersion().set(settings.getVersion());
            task.getMode().set(settings.getMode());
            task.getVisibility().set(settings.getVisibility());
            task.getTitle().set(settings.getTitle());
            task.getJavaRelease().set(settings.getJavaRelease());
            task.getMavenProject().set(root.getLayout().getBuildDirectory().dir("pretty-docs/maven"));
            task.getOutputDirectory().set(root.getLayout().getBuildDirectory().dir("docs/pretty"));
            // SpotBugs, one of pretty-docs' checks, reads the compiled classes.
            root.getSubprojects().forEach(module -> module.getPluginManager().withPlugin("java",
                    java -> task.dependsOn(module.getTasks().named("classes"))));
            // A site is never up to date: the sources it reads are not declared as inputs.
            task.getOutputs().upToDateWhen(t -> false);
        });
    }

    /** Writes the Maven build beside the Gradle one and runs pretty-docs on it. */
    public abstract static class PrettyDocs extends DefaultTask {

        @Input public abstract Property<String> getPluginVersion();
        @Input public abstract Property<String> getMode();
        @Input public abstract Property<String> getVisibility();
        @Input public abstract Property<String> getTitle();
        @Input public abstract Property<String> getJavaRelease();
        @Internal public abstract DirectoryProperty getMavenProject();
        @OutputDirectory public abstract DirectoryProperty getOutputDirectory();

        @TaskAction
        public void generate() throws IOException, InterruptedException {
            Project root = getProject().getRootProject();
            Path maven = getMavenProject().get().getAsFile().toPath();
            List<Project> modules = new ArrayList<>();
            for (Project module : root.getSubprojects()) {
                if (module.getPluginManager().hasPlugin("java")) modules.add(module);
            }
            if (modules.isEmpty()) throw new GradleException("no Java module to document");

            delete(maven);
            Files.createDirectories(maven);
            StringBuilder list = new StringBuilder();
            for (Project module : modules) {
                list.append("    <module>").append(module.getName()).append("</module>\n");
                Path dir = maven.resolve(module.getName());
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("pom.xml"), modulePom(root, module), StandardCharsets.UTF_8);
            }
            Files.writeString(maven.resolve("pom.xml"), rootPom(root, list.toString()), StandardCharsets.UTF_8);

            Path output = getOutputDirectory().get().getAsFile().toPath();
            List<String> command = new ArrayList<>(mvn());
            command.addAll(List.of("-B", "-ntp",
                    "io.joshuasalcedo.pretty:pretty-docs-maven-plugin:" + getPluginVersion().get() + ":generate",
                    "-Dpretty-docs.outputDirectory=" + output,
                    "-Dpretty-docs.mode=" + getMode().get(),
                    "-Dpretty-docs.visibility=" + getVisibility().get(),
                    "-Dpretty-docs.title=" + getTitle().get()));
            getLogger().lifecycle("pretty-docs {} on {} module(s): {}", getPluginVersion().get(), modules.size(),
                    String.join(" ", command));
            Process process = new ProcessBuilder(command).directory(maven.toFile()).redirectErrorStream(true).start();
            // Maven's own lines, through Gradle's log: a daemon's inherited output goes nowhere.
            try (var lines = process.inputReader(StandardCharsets.UTF_8)) {
                for (String line; (line = lines.readLine()) != null; ) getLogger().lifecycle(line);
            }
            int exit = process.waitFor();
            if (exit != 0) throw new GradleException("pretty-docs failed (mvn exited " + exit + "): see its output above");
            getLogger().lifecycle("The site: {}", output.resolve("index.html").toUri());
        }

        private String rootPom(Project root, String modules) {
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!-- Written by ./gradlew prettyDocs for pretty-docs to read: the Gradle build is the real one. -->
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>%s</groupId>
                      <artifactId>%s</artifactId>
                      <version>%s</version>
                      <packaging>pom</packaging>
                      <name>%s</name>
                      <properties>
                        <maven.compiler.release>%s</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      </properties>
                      <modules>
                    %s  </modules>
                    </project>
                    """.formatted(xml(group(root)), xml(root.getName()), xml(version(root)), xml(getTitle().get()),
                    xml(getJavaRelease().get()), modules);
        }

        private String modulePom(Project root, Project module) {
            SourceSetContainer sources = module.getExtensions().getByType(SourceSetContainer.class);
            var main = sources.getByName("main");
            String sourceDirectory = main.getJava().getSrcDirs().iterator().next().getAbsolutePath();
            String classes = main.getJava().getClassesDirectory().get().getAsFile().getAbsolutePath();
            String testDirectory = sources.getByName("test").getJava().getSrcDirs().iterator().next().getAbsolutePath();
            StringBuilder dependencies = new StringBuilder();
            for (String configuration : List.of("api", "implementation")) {
                var found = module.getConfigurations().findByName(configuration);
                if (found == null) continue;
                found.getDependencies().withType(ProjectDependency.class).forEach(dependency -> {
                    Project other = root.project(dependency.getPath());
                    dependencies.append("""
                                <dependency>
                                  <groupId>%s</groupId>
                                  <artifactId>%s</artifactId>
                                  <version>%s</version>
                                </dependency>
                            """.formatted(xml(group(other)), xml(other.getName()), xml(version(other))));
                });
            }
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!-- Written by ./gradlew prettyDocs for pretty-docs to read: the Gradle module %s is the real one. -->
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                        <groupId>%s</groupId>
                        <artifactId>%s</artifactId>
                        <version>%s</version>
                      </parent>
                      <groupId>%s</groupId>
                      <artifactId>%s</artifactId>
                      <version>%s</version>
                      <name>%s</name>
                      <description>%s</description>
                      <dependencies>
                    %s  </dependencies>
                      <build>
                        <sourceDirectory>%s</sourceDirectory>
                        <testSourceDirectory>%s</testSourceDirectory>
                        <outputDirectory>%s</outputDirectory>
                      </build>
                    </project>
                    """.formatted(xml(module.getPath()), xml(group(root)), xml(root.getName()), xml(version(root)),
                    xml(group(module)), xml(module.getName()), xml(version(module)), xml(archiveName(module)),
                    xml(module.getDescription() == null ? "" : module.getDescription()), dependencies,
                    xml(sourceDirectory), xml(testDirectory), xml(classes));
        }

        private static String archiveName(Project module) {
            var base = module.getExtensions().findByType(BasePluginExtension.class);
            return base == null ? module.getName() : base.getArchivesName().get();
        }

        private static String group(Project project) {
            String group = String.valueOf(project.getGroup());
            return group.isBlank() ? "local" : group;
        }

        private static String version(Project project) {
            String version = String.valueOf(project.getVersion());
            return version.isBlank() || version.equals("unspecified") ? "0-SNAPSHOT" : version;
        }

        private static List<String> mvn() {
            return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                    ? List.of("cmd", "/c", "mvn")
                    : List.of("mvn");
        }

        private static String xml(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private static void delete(Path dir) throws IOException {
            if (!Files.exists(dir)) return;
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }
}
