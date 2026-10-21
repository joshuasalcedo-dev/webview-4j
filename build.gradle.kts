plugins {
    id("io.joshuasalcedo.pretty-docs")
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

// What every module shares. The modules: core (the runtime), processor (the annotation
// processor, compile time only) and devtools (a Swing window beside an app while developing it).
// Each has its own build.gradle.kts. ./gradlew prettyDocs makes the documentation site.

// The version: a release passes -PreleaseVersion=1.2.3 (the release workflow takes it from the tag v1.2.3).
val releaseVersion = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

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
                    "processor" -> "webview-4j's annotation processor: @WebViewController classes and their TypeScript."
                    else -> "webview-4j devtools: a Swing window with an app's log and live metrics, beside it while developing."
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
