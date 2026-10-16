// webview-4j-devtools: a Swing window to keep beside an app while developing it. It shows the app's
// log (Java's, the native side's, the pages' console) and what it is doing now: frames per second
// each way, how long actions and the pages' calls take, and each page's frames per second.
// ./gradlew :devtools:runDemo opens a sample app with it.

plugins {
    `java-library`
}

base {
    archivesName = "webview-4j-devtools"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    api(project(":core"))
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // The window's models are tested without a screen.
    systemProperty("java.awt.headless", "true")
}

// ---- a sample app to look at it with: ./gradlew :devtools:runDemo ----

val demo = sourceSets.create("demo") {
    compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

tasks.register<JavaExec>("runDemo") {
    description = "Opens a sample app with the dev window beside it."
    group = "application"
    classpath = demo.runtimeClasspath
    mainClass = "io.joshuasalcedo.webview4j.devtools.demo.Demo"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}
