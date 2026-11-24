// Notes: Java behind a React page made with @webview4j/ui.
//   ./gradlew run          builds web/ with Vite into the jar's web/ and opens the app
//   ./gradlew run -Pdev    opens the Vite dev server instead (`bun run dev` in web/ first): edit, save, see it
//   ./gradlew run -Pshowcase  also opens the showcase window (MDI, an embedded site) from the start
//   ./gradlew run -Pgallery   also opens the component gallery, a window written only in Java
// Needs Java 25 and bun (https://bun.sh).

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

// The app's own version: in its installer, its jar (META-INF/webview4j/app.properties) and its update manifest.
version = "0.2.0"

val webview4j = "0.2.0-SNAPSHOT"

dependencies {
    implementation("io.joshuasalcedo:webview-4j-core:$webview4j")
    annotationProcessor("io.joshuasalcedo:webview-4j-processor:$webview4j")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The gallery's test: every component has a demo, and every page draws (-Pgallery.section=Layout for one category).
tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    providers.gradleProperty("gallery.section").orNull?.let { systemProperty("gallery.section", it) }
}

val web = layout.projectDirectory.dir("web")
val apiClient = web.file("src/api.generated.ts")

// The controllers' TypeScript client, for the page: written while Java compiles.
tasks.compileJava {
    options.compilerArgs.add("-Awebview4j.typescript=" + apiClient.asFile.absolutePath)
    outputs.file(apiClient)
}

val webInstall = tasks.register<Exec>("webInstall") {
    description = "Installs the page's dependencies with bun."
    group = "build"
    workingDir = web.asFile
    commandLine("bun", "install")
    inputs.file(web.file("package.json"))
    outputs.dir(web.dir("node_modules"))
}

val webOut = layout.buildDirectory.dir("generated/web")

val webBuild = tasks.register<Exec>("webBuild") {
    description = "Builds the page with Vite into the jar's web/."
    group = "build"
    dependsOn(webInstall, tasks.compileJava)
    // In webview-4j's repository the page uses the kit's sources, so their dependencies must be installed.
    gradle.includedBuilds.find { it.name == "webview-4j" }?.let { dependsOn(it.task(":core:uiInstall")) }
    workingDir = web.asFile
    commandLine("bun", "run", "build", "--outDir", webOut.get().dir("web").asFile.absolutePath, "--emptyOutDir")
    inputs.dir(web.dir("src"))
    inputs.files(web.file("index.html"), web.file("vite.config.ts"), web.file("package.json"))
    outputs.dir(webOut)
}

sourceSets.main {
    resources.srcDir(files(webOut).builtBy(webBuild))
}

application {
    mainClass = "example.notes.NotesApp"
    applicationDefaultJvmArgs = buildList {
        add("--enable-native-access=ALL-UNNAMED")
        // macOS: the event loop needs the process's first thread (App.run).
        if (System.getProperty("os.name").lowercase().startsWith("mac")) add("-XstartOnFirstThread")
    }
}

tasks.named<JavaExec>("run") {
    if (providers.gradleProperty("dev").isPresent) systemProperty("notes.dev", "true")
    if (providers.gradleProperty("showcase").isPresent) systemProperty("notes.showcase", "true")
    if (providers.gradleProperty("gallery").isPresent) systemProperty("notes.gallery", "true")
}

// ./gradlew runWeb: the component gallery on the web, http://localhost:8080/ (no window: a browser shows it).
tasks.register<JavaExec>("runWeb") {
    description = "Serves the component gallery to browsers."
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "example.notes.GalleryOnTheWeb"
    providers.gradleProperty("port").orNull?.let { systemProperty("notes.port", it) }
}

// ---- releases: the changelog in the app, its installer, its signed update manifest ----
// The app's CHANGELOG.md and app.properties go into its jar: AutoUpdate.start(app) reads them (What's new, the
// changelog page, where to look for updates). -PupdateManifest=https://... says where the release's manifest is; the
// public key is in update-public.key (made by ./gradlew updateKey; the private key never in the repository).
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
            "name=Notes\nversion=${project.version}\nupdate.manifest=${manifest.get()}\nupdate.publicKey=$key\n")
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

// ./gradlew updateKey: a release key pair. The private key goes to -PupdateKeyFile (~/.webview4j/notes-update.key
// unless set) and belongs in a CI secret (WEBVIEW4J_UPDATE_KEY); the public key to update-public.key, into the app.
tasks.register<JavaExec>("updateKey") {
    description = "Makes the release key pair for signed updates."
    group = "release"
    val keyFile = providers.gradleProperty("updateKeyFile").orElse(System.getProperty("user.home") + "/.webview4j/notes-update.key")
    updateTool(this, "keygen", keyFile.get())
    standardOutput = ByteArrayOutputStream()
    doLast {
        val printed = standardOutput.toString()
        val public = printed.lines().first { it.startsWith("update.publicKey=") }.removePrefix("update.publicKey=")
        layout.projectDirectory.file("update-public.key").asFile.writeText(public + "\n")
        println("The public key is in update-public.key (commit it); the private key is in ${keyFile.get()}: keep it secret.")
    }
}

// ./gradlew packageApp: the installer for this system, by jpackage, with the Java runtime inside.
// -PinstallerType=msi|exe|dmg|pkg|deb|rpm (app-image unless set: a folder, no installer tools needed).
val installers = layout.buildDirectory.dir("jpackage")
tasks.register<Exec>("packageApp") {
    description = "Builds the app's installer with jpackage."
    group = "release"
    dependsOn(tasks.installDist)
    val type = providers.gradleProperty("installerType").orElse("app-image")
    val lib = layout.buildDirectory.dir("install/notes/lib")
    doFirst { installers.get().asFile.deleteRecursively() }
    commandLine(
        "jpackage", "--type", type.get(), "--name", "Notes", "--app-version", project.version.toString(),
        "--input", lib.get().asFile.absolutePath, "--main-jar", "notes-${project.version}.jar", "--main-class", "example.notes.NotesApp",
        "--java-options", "--enable-native-access=ALL-UNNAMED", "--dest", installers.get().asFile.absolutePath,
        *(if (type.get() == "msi" || type.get() == "exe") arrayOf("--win-menu", "--win-shortcut", "--win-upgrade-uuid",
            "5f3a8c2e-9b71-4d06-a6c4-0b8d7e1f2a93") else emptyArray()))
}

// ./gradlew updateManifest -PdownloadBase=https://downloads.example.com/notes/: the signed manifest for what
// packageApp made, into build/update (manifest.json and manifest.json.sig); the key from WEBVIEW4J_UPDATE_KEY.
tasks.register<JavaExec>("updateManifest") {
    description = "Writes and signs the update manifest for the installers packageApp made."
    group = "release"
    val base = providers.gradleProperty("downloadBase").orElse("https://downloads.example.com/notes/")
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
        updateTool(this as JavaExec, "manifest", "--app", "notes", "--version", project.version.toString(),
            "--changelog", layout.projectDirectory.file("CHANGELOG.md").asFile.absolutePath,
            "--out", layout.buildDirectory.file("update/manifest.json").get().asFile.absolutePath, *downloads.toTypedArray())
    }
    updateTool(this)
}
