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
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

val cargoBuild = tasks.register<Exec>("cargoBuild") {
    description = "Builds the native library with cargo ($cargoProfile)."
    group = "build"
    workingDir = rootDir
    commandLine("cargo", "build", "-p", "webview", "--lib", "--profile", cargoProfile)
    inputs.files(rootProject.fileTree("crates") { exclude("**/target/**", "**/node_modules/**") })
    inputs.files(rootProject.file("Cargo.toml"), rootProject.file("Cargo.lock"))
    inputs.property("cargoProfile", cargoProfile)
    outputs.file(rootProject.file("target/$cargoOutputDir/$nativeLibrary"))
}

val natives = tasks.register<Sync>("natives") {
    description = "Puts the native library where the jar carries it: natives/$nativePlatform/."
    from(cargoBuild) { into("natives/$nativePlatform") }
    into(layout.buildDirectory.dir("generated/natives"))
}

sourceSets.main {
    resources.srcDir(natives)
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
