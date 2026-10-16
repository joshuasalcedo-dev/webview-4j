// The build's own plugins. io.joshuasalcedo.pretty-docs: the documentation site, made by the
// pretty-docs Maven plugin from the Gradle modules' sources.

plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("prettyDocs") {
            id = "io.joshuasalcedo.pretty-docs"
            implementationClass = "io.joshuasalcedo.webview4j.build.PrettyDocsPlugin"
        }
    }
}
