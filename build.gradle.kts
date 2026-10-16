plugins {
    id("io.joshuasalcedo.pretty-docs")
}

// What every module shares. The modules: core (the runtime), processor (the annotation
// processor, compile time only) and devtools (a Swing window beside an app while developing it).
// Each has its own build.gradle.kts. ./gradlew prettyDocs makes the documentation site.

subprojects {
    group = "io.joshuasalcedo"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    // ./gradlew publishToMavenLocal: each module as io.joshuasalcedo:<its archive name>, so Maven
    // and Gradle apps can depend on it (webview-4j-core, webview-4j-processor).
    plugins.withId("java-library") {
        apply(plugin = "maven-publish")
        extensions.configure<PublishingExtension> {
            publications.create<MavenPublication>("maven") {
                from(components["java"])
                // Read once the module has set its archive name.
                afterEvaluate { artifactId = the<BasePluginExtension>().archivesName.get() }
            }
        }
    }
}
