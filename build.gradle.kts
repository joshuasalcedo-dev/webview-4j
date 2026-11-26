plugins {
    idea
    id("io.joshuasalcedo.pretty-docs")
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

// What every module shares. One module: core (the runtime, its components and use cases, dev tools among them, and
// the annotation processor for @WebViewController).
// Each has its own build.gradle.kts. ./gradlew prettyDocs makes the documentation site.
// explorer is an app, not a library: it browses that site (and ships it), so it is not documented itself.

prettyDocs {
    exclude = listOf("explorer")
}

// The version: a release passes -PreleaseVersion=1.2.3 (the release workflow takes it from the tag v1.2.3).
val releaseVersion = providers.gradleProperty("releaseVersion").getOrElse("0.2.0-SNAPSHOT")

subprojects {
    group = "io.joshuasalcedo"
    version = releaseVersion

    repositories {
        mavenCentral()
    }

    // Each module is published as io.joshuasalcedo:webview-4j-<module>, with its sources and Javadoc:
    //   ./gradlew publishToMavenLocal      on this machine
    //   ./gradlew publishToMavenCentral    Maven Central (the release workflow does this; it needs the Central
    //                                      token, mavenCentralUsername/Password, and the signing key)
    plugins.withId("java-library") {
        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)
            if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
            coordinates("io.joshuasalcedo", "webview-4j-${project.name}", releaseVersion)
            pom {
                name = "webview-4j ${project.name}"
                description = when (project.name) {
                    "core" -> "A small Tauri-like desktop framework: a native webview driven from Java over FFM."
                    else -> "webview-4j ${project.name}"
                }
                url = "https://github.com/joshuasalcedo-dev/webview-4j"
                inceptionYear = "2026"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "joshuasalcedo-dev"
                        name = "Joshua Salcedo"
                        url = "https://github.com/joshuasalcedo-dev"
                    }
                }
                scm {
                    url = "https://github.com/joshuasalcedo-dev/webview-4j"
                    connection = "scm:git:https://github.com/joshuasalcedo-dev/webview-4j.git"
                    developerConnection = "scm:git:ssh://git@github.com/joshuasalcedo-dev/webview-4j.git"
                }
            }
        }
    }
}

// What IntelliJ leaves out of its index: build output and downloaded packages, ~90k files no one edits
// (Cargo's target/, the kit's node_modules and builds). IntelliJ reads it when it syncs with Gradle.
idea {
    module {
        excludeDirs.addAll(files("target", "build", ".gradle"))
    }
}
project(":core") {
    apply(plugin = "idea")
    extensions.configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
        module {
            excludeDirs.addAll(files(
                "build",
                "src/main/ui/node_modules", "src/main/ui/dist", "src/main/ui/storybook-static",
                "src/main/typescript/node_modules", "src/main/typescript/dist",
            ))
        }
    }
}
