// webview-4j-fx: a modern native webview (WebView2, the Edge/Chromium engine) as a JavaFX node, instead of JavaFX's
// own WebView and its old WebKit. WebViewPane is the node; WebViewGrid lays several out; BrowserPane is a browser
// with an address bar and the page's network, console and clicks beside it; Dom changes the page from Java.
// ./gradlew :fx:runDemo opens a sample app with all of them. Windows for now: see the README.

plugins {
    `java-library`
}

base {
    archivesName = "webview-4j-fx"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val javafxVersion = "27.0.1"
/** OpenJFX's classifier for this machine: its jars are per system. */
val javafxPlatform = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    when {
        os.startsWith("windows") -> "win"
        os.startsWith("mac") -> if (arm) "mac-aarch64" else "mac"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}
val javafx = listOf("base", "graphics", "controls").map { "org.openjfx:javafx-$it:$javafxVersion:$javafxPlatform" }

dependencies {
    api(project(":core"))
    // The app brings its own JavaFX (any version from 21, any system): it is not pulled in with this one.
    javafx.forEach { compileOnly(it) }

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    javafx.forEach { testImplementation(it) }
}

tasks.test {
    useJUnitPlatform()
}

// ---- a sample app: ./gradlew :fx:runDemo ----

val demo = sourceSets.create("demo") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get() + configurations.runtimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}
dependencies {
    javafx.forEach { "demoImplementation"(it) }
}

tasks.register<JavaExec>("runDemo") {
    description = "Opens a JavaFX app with an embedded webview, a grid of them, and a browser with its network beside it."
    group = "application"
    classpath = demo.runtimeClasspath
    mainClass = "io.joshuasalcedo.webview4j.fx.demo.Main"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}
