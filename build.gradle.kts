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
}
