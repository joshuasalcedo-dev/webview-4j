// webview-4j-core: the runtime. App, View, events, the bridge, and the native library it loads
// through FFM. The JPMS module io.joshuasalcedo.webview4j exports only the public API package.

plugins {
    `java-library`
}

base {
    archivesName = "webview-4j-core"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // Value objects (Email, Money, Country...): first-class fields and displays (ui.components.valueobject).
    api("io.joshuasalcedo:value-object:0.1.0")
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The tests' own @WebViewController classes are compiled with core's processor, as an app's would be.
    testAnnotationProcessor(sourceSets.main.get().output)
}

tasks.compileTestJava {
    options.compilerArgs.add("-Awebview4j.typescript=" + layout.buildDirectory.file("generated/webview4j/api.ts").get().asFile.absolutePath)
}

// ---- the native library (crates/webview at the repository root) ----
//
// cargo builds webview4j.dll / libwebview4j.so / libwebview4j.dylib for this machine, and the jar
// carries it at natives/<os>-<arch>/, where NativeLibrary finds it. Each OS builds its own; release
// jars for every platform come from CI on each OS. -PcargoProfile=dev builds faster while iterating.

val cargoProfile = providers.gradleProperty("cargoProfile").getOrElse("release")
val cargoOutputDir = if (cargoProfile == "dev") "debug" else cargoProfile
val nativeLibrary = System.mapLibraryName("webview4j")
val nativePlatform = run {
    val os = System.getProperty("os.name").lowercase().let {
        when {
            it.startsWith("windows") -> "windows"
            it.startsWith("mac") -> "macos"
            else -> "linux"
        }
    }
    val arch = when (val arch = System.getProperty("os.arch")) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "aarch64"
        else -> arch
    }
    "$os-$arch"
}

val nativeSources = files(
    rootProject.fileTree("crates") { exclude("**/target/**", "**/node_modules/**") },
    rootProject.file("Cargo.toml"),
    rootProject.file("Cargo.lock"),
)

// A native library already built from the current sources, by either profile (target/release or target/debug,
// the newer one): used as it is, so starting an app or running the tests never waits for cargo. Cargo runs only
// when the Rust code changed since, or with -PrebuildNative.
val rebuildNative = providers.gradleProperty("rebuildNative").isPresent
val upToDateNative: Provider<File> = provider {
    if (rebuildNative) return@provider null
    val newestSource = nativeSources.files.maxOfOrNull { it.lastModified() } ?: 0L
    listOf("release", "debug")
        .map { rootProject.file("target/$it/$nativeLibrary") }
        .filter { it.isFile && it.lastModified() >= newestSource }
        .maxByOrNull { it.lastModified() }
}

val cargoBuild = tasks.register<Exec>("cargoBuild") {
    description = "Builds the native library with cargo ($cargoProfile), unless one is built from these sources already."
    group = "build"
    onlyIf("no native library is built from the current sources (or -PrebuildNative)") { !upToDateNative.isPresent }
    workingDir = rootDir
    commandLine("cargo", "build", "-p", "webview", "--lib", "--profile", cargoProfile)
    inputs.files(nativeSources)
    inputs.property("cargoProfile", cargoProfile)
    outputs.file(rootProject.file("target/$cargoOutputDir/$nativeLibrary"))
}

// -PprebuiltNatives=<dir>: a folder with natives/<os>-<arch>/ for every platform (the release workflow builds each on
// its own OS and gathers them), so the published jar carries them all; cargo is not run then.
val prebuiltNatives = providers.gradleProperty("prebuiltNatives")

val natives = tasks.register<Sync>("natives") {
    description = "Puts the native library where the jar carries it: natives/$nativePlatform/ (or every platform's, from -PprebuiltNatives)."
    if (prebuiltNatives.isPresent) from(prebuiltNatives.get()) { include("natives/**") }
    else {
        dependsOn(cargoBuild)
        from(provider { upToDateNative.orNull ?: rootProject.file("target/$cargoOutputDir/$nativeLibrary") }) {
            into("natives/$nativePlatform")
        }
    }
    into(layout.buildDirectory.dir("generated/natives"))
}

// ---- webview-4j's own components (src/main/ui, @webview4j/ui) ----
//
// bun builds the kit for the browser, React inside, into webview4j-ui/ (ui.js, ui.css and the chunks they load):
// the jar carries it, and every window loads it from <base>ui/. Needs bun (https://bun.sh).

val uiDir = layout.projectDirectory.dir("src/main/ui")
val uiOut = layout.buildDirectory.dir("generated/ui")

val uiInstall = tasks.register<Exec>("uiInstall") {
    description = "Installs @webview4j/ui's dependencies with bun."
    group = "build"
    workingDir = uiDir.asFile
    commandLine("bun", "install", "--frozen-lockfile")
    inputs.files(uiDir.file("package.json"), uiDir.file("bun.lock"))
    outputs.dir(uiDir.dir("node_modules"))
}

// egui-4j's components (charts, the dev logger) for the page: cargo builds the wasm, wasm-bindgen its JavaScript, into
// src/egui4j/ (not in git), which the kit imports. Needs the wasm32-unknown-unknown target and wasm-bindgen-cli at
// the version in Cargo.lock (cargo install wasm-bindgen-cli --version <it>).
val uiEgui = tasks.register<Exec>("uiEgui") {
    description = "Builds egui-4j for the page (wasm) into @webview4j/ui's src/egui4j/."
    group = "build"
    dependsOn(uiInstall)
    workingDir = uiDir.asFile
    commandLine("bun", "run", "egui")
    inputs.dir(rootProject.file("crates/egui-4j/src"))
    inputs.files(rootProject.file("crates/egui-4j/Cargo.toml"), rootProject.file("Cargo.lock"))
    outputs.dir(uiDir.dir("src/egui4j"))
}

// crates/grammar for the page (wasm) into @webview4j/ui's src/grammar/ (not in git): spelling and grammar in text
// areas. Same tools as uiEgui.
val uiGrammar = tasks.register<Exec>("uiGrammar") {
    description = "Builds crates/grammar for the page (wasm) into @webview4j/ui's src/grammar/."
    group = "build"
    dependsOn(uiInstall)
    workingDir = uiDir.asFile
    commandLine("bun", "run", "grammar")
    inputs.dir(rootProject.file("crates/grammar/src"))
    inputs.files(rootProject.file("crates/grammar/Cargo.toml"), rootProject.file("Cargo.lock"))
    outputs.dir(uiDir.dir("src/grammar"))
}

val uiBundle = tasks.register<Exec>("uiBundle") {
    description = "Builds @webview4j/ui for the browser into the jar's webview4j-ui/."
    group = "build"
    dependsOn(uiInstall, uiEgui, uiGrammar)
    workingDir = uiDir.asFile
    commandLine("bun", "run", "bundle")
    val target = uiOut.get().dir("webview4j-ui").asFile
    environment("WEBVIEW4J_UI_OUT", target.absolutePath)
    inputs.dir(uiDir.dir("src"))
    inputs.dir(uiDir.dir("pages"))
    inputs.files(uiDir.file("vite.bundle.ts"), uiDir.file("vite.lib.ts"), uiDir.file("package.json"), uiDir.file("bun.lock"))
    outputs.dir(uiOut)
}

// The Java classes java-gen makes from React components' props (src/main/ui/java-gen.json): fails when a component's
// props changed and `bun run java-gen` was not run again, or when a generated class was edited by hand.
val uiJavaGenCheck = tasks.register<Exec>("uiJavaGenCheck") {
    description = "Checks that the Java classes made from React components (java-gen) are up to date."
    group = "verification"
    dependsOn(uiInstall)
    workingDir = uiDir.asFile
    commandLine("bun", "run", "java-gen:check")
    // The components' sources; not the wasm uiEgui and uiGrammar build into src/.
    inputs.files(fileTree(uiDir.dir("src")) { exclude("egui4j/**", "grammar/**") })
    inputs.files(uiDir.file("java-gen.json"), uiDir.file("scripts/java-gen.ts"))
    inputs.dir(layout.projectDirectory.dir("src/main/java/io/joshuasalcedo/webview4j/ui/components"))
    val stamp = layout.buildDirectory.file("java-gen/checked")
    outputs.file(stamp)
    doLast { stamp.get().asFile.apply { parentFile.mkdirs(); writeText("ok") } }
}
// The HTML elements html-gen makes from React's types (Html.java): fails when React's types changed and
// `bun run html-gen` was not run again, or when Html.java was edited by hand.
val uiHtmlGenCheck = tasks.register<Exec>("uiHtmlGenCheck") {
    description = "Checks that the HTML elements made from React's types (html-gen) are up to date."
    group = "verification"
    dependsOn(uiInstall)
    workingDir = uiDir.asFile
    commandLine("bun", "run", "html-gen:check")
    inputs.files(uiDir.file("scripts/html-gen.ts"), uiDir.file("bun.lock"), uiDir.file("src/html-tags.generated.ts"))
    inputs.file(layout.projectDirectory.file("src/main/java/io/joshuasalcedo/webview4j/ui/Html.java"))
    val stamp = layout.buildDirectory.file("html-gen/checked")
    outputs.file(stamp)
    doLast { stamp.get().asFile.apply { parentFile.mkdirs(); writeText("ok") } }
}
tasks.named("check") { dependsOn(uiJavaGenCheck, uiHtmlGenCheck) }

// The components' parts, typed (StylePart enums: ButtonPart, DialogPart...), made from the kit's CSS and
// src/main/ui/style-gen.json into build/generated: never into src, never into a CSS file.
val styleOut = layout.buildDirectory.dir("generated/sources/style/java")
val uiStyleGen = tasks.register<Exec>("uiStyleGen") {
    description = "Makes the components' typed style parts (io.joshuasalcedo.webview4j.ui.style) from the kit's CSS."
    group = "build"
    workingDir = uiDir.asFile
    commandLine("bun", "scripts/style-gen.ts")
    environment("STYLE_GEN_OUT", styleOut.get().asFile.absolutePath)
    inputs.files(fileTree(uiDir.dir("src")) { include("**/*.css"); exclude("egui4j/**", "grammar/**") })
    inputs.files(uiDir.file("style-gen.json"), uiDir.file("scripts/style-gen.ts"))
    outputs.dir(styleOut)
}

// Lucide's icons, typed (the Icon enum), from lucide-react's list into build/generated: never into src.
val iconOut = layout.buildDirectory.dir("generated/sources/icons/java")
val uiIconGen = tasks.register<Exec>("uiIconGen") {
    description = "Makes the Icon enum (io.joshuasalcedo.webview4j.ui.icons) from Lucide's icons."
    group = "build"
    dependsOn(uiInstall)
    workingDir = uiDir.asFile
    commandLine("bun", "scripts/icon-gen.ts")
    environment("ICON_GEN_OUT", iconOut.get().asFile.absolutePath)
    inputs.files(uiDir.file("scripts/icon-gen.ts"), uiDir.file("bun.lock"))
    outputs.dir(iconOut)
}

sourceSets.main {
    java.srcDir(files(styleOut).builtBy(uiStyleGen))
    java.srcDir(files(iconOut).builtBy(uiIconGen))
    resources.srcDir(natives)
    resources.srcDir(files(uiOut).builtBy(uiBundle))
}

tasks.test {
    useJUnitPlatform()
    // Tests run on the class path (whitebox, same packages), where the module is unnamed.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // A link out of the app is decided and logged, but opens no real browser while tests run.
    environment("WEBVIEW4J_BROWSER", "off")
    // -Pbenchmark runs Benchmark too: ./gradlew :core:test --tests '*Benchmark' -Pbenchmark
    systemProperty("benchmark", providers.gradleProperty("benchmark").isPresent)
    // The dev tools' window models are tested without a screen.
    systemProperty("java.awt.headless", "true")
    // a test's design spec or light/dark is never saved for the next run (Appearance.remember)
    systemProperty("webview4j.appearance.remember", "false")
    // The processor's generated TypeScript is type-checked against @webview4j/api when its node_modules are installed.
    systemProperty("webview4j.api", layout.projectDirectory.dir("src/main/typescript").asFile.absolutePath)
    providers.gradleProperty("demo.out").orNull?.let { systemProperty("demo.out", it) }
    inputs.property("benchmark", providers.gradleProperty("benchmark").isPresent)
    // The protocol's conformance fixtures, shared with the Rust tests.
    val fixtures = rootProject.file("protocol/fixtures")
    systemProperty("webview4j.fixtures", fixtures.absolutePath)
    inputs.dir(fixtures)
}
