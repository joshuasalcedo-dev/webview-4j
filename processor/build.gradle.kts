// webview-4j-processor: the annotation processor for @WebViewController. Used only at compile
// time (annotationProcessor(...)); it has no dependencies and reads the annotations by name.

plugins {
    `java-library`
}

base {
    archivesName = "webview-4j-processor"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(project(":core"))
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Generated TypeScript is type-checked against @webview4j/api when its node_modules are installed.
    systemProperty("webview4j.api", rootProject.file("core/src/main/typescript").absolutePath)
}
