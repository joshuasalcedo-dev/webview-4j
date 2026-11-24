// The webview-4j Sampler: every component in one app, a Gradle build of its own.
rootProject.name = "sampler"

// While it sits in webview-4j's repository, it builds against webview-4j's own sources (a composite build).
// In a copy of your own, delete this block: the dependencies then come from Maven Central.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("io.joshuasalcedo:webview-4j-core")).using(project(":core"))
        substitute(module("io.joshuasalcedo:webview-4j-processor")).using(project(":processor"))
    }
}
