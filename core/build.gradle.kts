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
    // The tests' own @WebViewController classes are compiled with the processor, as an app's would be.
    testAnnotationProcessor(project(":processor"))
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
    inputs.files(uiDir.file("vite.bundle.ts"), uiDir.file("vite.lib.ts"), uiDir.file("package.json"), uiDir.file("bun.lock"))
    outputs.dir(uiOut)
}

sourceSets.main {
    resources.srcDir(natives)
    resources.srcDir(files(uiOut).builtBy(uiBundle))
}

tasks.test {
    useJUnitPlatform()
    // Tests run on the class path (whitebox, same packages), where the module is unnamed.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // -Pbenchmark runs Benchmark too: ./gradlew :core:test --tests '*Benchmark' -Pbenchmark
    systemProperty("benchmark", providers.gradleProperty("benchmark").isPresent)
    inputs.property("benchmark", providers.gradleProperty("benchmark").isPresent)
    // The protocol's conformance fixtures, shared with the Rust tests.
    val fixtures = rootProject.file("protocol/fixtures")
    systemProperty("webview4j.fixtures", fixtures.absolutePath)
    inputs.dir(fixtures)
}
