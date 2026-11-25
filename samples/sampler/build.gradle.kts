// The webview-4j Sampler: every component, working, with the Java for each, in one app written only in Java.
//   ./gradlew run             opens it
//   ./gradlew runWeb          serves it to browsers instead, http://localhost:8080/ (-Pport=...)
//   ./gradlew test            every component has a page, every page draws, on the desktop and on the web
//   ./gradlew nativeCompile   a native executable (GraalVM native-image): build/native/webview4j-sampler(.exe)
//   ./gradlew packageApp      its installer (jpackage): -PinstallerType=msi|dmg|deb, app-image unless set
//   ./gradlew updateKey, updateManifest   the release key and the signed update manifest (see AutoUpdate)
// Needs Java 25; GraalVM 25 for nativeCompile (and, on Windows, Visual Studio's C++ tools).

import java.io.ByteArrayOutputStream

plugins {
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

version = "0.2.0"
val webview4j = "0.2.0-SNAPSHOT"

dependencies {
    implementation("io.joshuasalcedo:webview-4j-core:$webview4j")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "io.joshuasalcedo.webview4j.sampler.Sampler"
    applicationName = "webview4j-sampler"
    applicationDefaultJvmArgs = buildList {
        add("--enable-native-access=ALL-UNNAMED")
        if (System.getProperty("os.name").lowercase().startsWith("mac")) add("-XstartOnFirstThread")
    }
}

// ./gradlew run is development: right-click → Inspect and F12 in every window (the installers leave it off)
tasks.named<JavaExec>("run") {
    systemProperty("webview4j.devtools", "true")
}

/** GraalVM 25, for the tracing agent and native-image. */
val graal = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
    vendor = JvmVendorSpec.GRAAL_VM
}

// Every component has a page, every page draws (-Psampler.section=Panes for one section). -PnativeAgent: the tests run
// under GraalVM's tracing agent, which writes down what the native image needs (reflection, resources, FFM calls).
tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    providers.gradleProperty("sampler.section").orNull?.let { systemProperty("sampler.section", it) }
    providers.gradleProperty("sampler.screenshots").orNull?.let { systemProperty("sampler.screenshots", it) }
    if (providers.gradleProperty("nativeAgent").isPresent) {
        javaLauncher = graal
        jvmArgs("-agentlib:native-image-agent=config-merge-dir=" + layout.projectDirectory.dir("src/main/resources/META-INF/native-image/io.joshuasalcedo.webview4j/sampler").asFile.absolutePath)
    }
}

tasks.register<JavaExec>("runWeb") {
    description = "Serves the Sampler to browsers."
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.joshuasalcedo.webview4j.sampler.SamplerOnTheWeb"
    providers.gradleProperty("port").orNull?.let { systemProperty("sampler.port", it) }
}

// ---- the native executable ----
// The metadata native-image needs is in src/main/resources/META-INF/native-image (webview-4j's own comes with its
// core jar); ./gradlew test -PnativeAgent writes it again after pages change.
tasks.register<Exec>("nativeCompile") {
    description = "Builds the Sampler as a native executable with GraalVM native-image."
    group = "build"
    dependsOn(tasks.jar)
    val out = layout.buildDirectory.dir("native")
    val classpath = files(tasks.jar.map { it.archiveFile }, configurations.runtimeClasspath)
    inputs.files(classpath)
    outputs.dir(out)
    doFirst {
        val home = graal.get().metadata.installationPath.asFile
        val windows = System.getProperty("os.name").lowercase().contains("win")
        val tool = home.resolve("bin/" + if (windows) "native-image.cmd" else "native-image")
        out.get().asFile.mkdirs()
        commandLine(
            tool.absolutePath, "--no-fallback", "-march=compatibility",
            "--enable-native-access=ALL-UNNAMED",
            "-H:+UnlockExperimentalVMOptions", "-H:+ForeignAPISupport", "-H:+SharedArenaSupport",
            "-cp", classpath.joinToString(File.pathSeparator) { it.absolutePath },
            "-o", out.get().file("webview4j-sampler").asFile.absolutePath,
            "io.joshuasalcedo.webview4j.sampler.Sampler")
    }
}

// ---- releases: the changelog in the app, its installer, its signed update manifest ----
// CHANGELOG.md and app.properties go into the jar: AutoUpdate.start(app) reads them (What's new, the changelog, where
// updates are). -PupdateManifest=https://... says where the release's manifest is; the public key is in
// update-public.key (./gradlew updateKey; the private key never in the repository).
val release = layout.buildDirectory.dir("generated/release")
val writeRelease = tasks.register("writeRelease") {
    description = "Puts the changelog and the release's facts into the app's jar."
    val changelog = layout.projectDirectory.file("CHANGELOG.md")
    val publicKey = layout.projectDirectory.file("update-public.key")
    val manifest = providers.gradleProperty("updateManifest").orElse("")
    inputs.files(changelog, publicKey).optional()
    inputs.property("version", project.version.toString())
    inputs.property("manifest", manifest)
    outputs.dir(release)
    doLast {
        val out = release.get().dir("META-INF/webview4j").asFile
        out.mkdirs()
        val log = changelog.asFile
        if (log.exists()) log.copyTo(out.resolve("CHANGELOG.md"), overwrite = true)
        val key = publicKey.asFile.takeIf { it.exists() }?.readText()?.trim() ?: ""
        out.resolve("app.properties").writeText(
            "name=webview-4j Sampler\nversion=${project.version}\nupdate.manifest=${manifest.get()}\nupdate.publicKey=$key\n")
    }
}
sourceSets.main {
    resources.srcDir(files(release).builtBy(writeRelease))
}

/** webview-4j's UpdateTool, run with the app's class path (core is on it). */
fun updateTool(task: JavaExec, vararg args: String) {
    task.classpath = sourceSets.main.get().runtimeClasspath
    task.mainClass = "io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate.UpdateTool"
    task.args(*args)
}

tasks.register<JavaExec>("updateKey") {
    description = "Makes the release key pair for signed updates."
    group = "release"
    val keyFile = providers.gradleProperty("updateKeyFile").orElse(System.getProperty("user.home") + "/.webview4j/sampler-update.key")
    updateTool(this, "keygen", keyFile.get())
    standardOutput = ByteArrayOutputStream()
    doLast {
        val printed = standardOutput.toString()
        val public = printed.lines().first { it.startsWith("update.publicKey=") }.removePrefix("update.publicKey=")
        layout.projectDirectory.file("update-public.key").asFile.writeText(public + "\n")
        println("The public key is in update-public.key (commit it); the private key is in ${keyFile.get()}: keep it secret.")
    }
}

val installers = layout.buildDirectory.dir("jpackage")
tasks.register<Exec>("packageApp") {
    description = "Builds the Sampler's installer with jpackage."
    group = "release"
    dependsOn(tasks.installDist)
    val type = providers.gradleProperty("installerType").orElse("app-image")
    val lib = layout.buildDirectory.dir("install/webview4j-sampler/lib")
    doFirst { installers.get().asFile.deleteRecursively() }
    commandLine(
        "jpackage", "--type", type.get(), "--name", "webview4j-sampler", "--app-version", project.version.toString(),
        "--input", lib.get().asFile.absolutePath, "--main-jar", "sampler-${project.version}.jar",
        "--main-class", "io.joshuasalcedo.webview4j.sampler.Sampler",
        "--java-options", "--enable-native-access=ALL-UNNAMED", "--dest", installers.get().asFile.absolutePath,
        *(if (type.get() == "msi" || type.get() == "exe") arrayOf("--win-menu", "--win-shortcut", "--win-upgrade-uuid",
            "5f3a8c2e-9b71-4d06-a6c4-0b8d7e1f2a93") else emptyArray()))
}

tasks.register<JavaExec>("updateManifest") {
    description = "Writes and signs the update manifest for the installers packageApp made."
    group = "release"
    val base = providers.gradleProperty("downloadBase").orElse("https://downloads.example.com/sampler/")
    val platform = run {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        (if ("win" in os) "windows" else if ("mac" in os) "macos" else "linux") + "-" +
            (if ("aarch64" in arch || "arm64" in arch) "aarch64" else "x64")
    }
    doFirst {
        val files = installers.get().asFile.listFiles { f -> f.isFile }?.toList().orEmpty()
        require(files.isNotEmpty()) { "no installer in build/jpackage: run packageApp -PinstallerType=msi (or dmg, deb) first" }
        val downloads = files.flatMap { listOf("--download", "$platform=${it.absolutePath}=${base.get().trimEnd('/')}/${it.name}") }
        updateTool(this as JavaExec, "manifest", "--app", "sampler", "--version", project.version.toString(),
            "--changelog", layout.projectDirectory.file("CHANGELOG.md").asFile.absolutePath,
            "--out", layout.buildDirectory.file("update/manifest.json").get().asFile.absolutePath, *downloads.toTypedArray())
    }
    updateTool(this)
}
