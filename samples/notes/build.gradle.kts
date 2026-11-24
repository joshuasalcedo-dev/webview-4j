// Notes: Java behind a React page made with @webview4j/ui.
//   ./gradlew run          builds web/ with Vite into the jar's web/ and opens the app
//   ./gradlew run -Pdev    opens the Vite dev server instead (`bun run dev` in web/ first): edit, save, see it
//   ./gradlew run -Pshowcase  also opens the showcase window (MDI, an embedded site) from the start
//   ./gradlew run -Pgallery   also opens the component gallery, a window written only in Java
// Needs Java 25 and bun (https://bun.sh).

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
