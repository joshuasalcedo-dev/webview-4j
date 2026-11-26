// webview-4j explorer: the UI components, as a component library's docs show them.
//
// Made by the build (never by hand): the pages. Written by hand: the stories (src/main/java/.../explorer/stories).
//   1. ./gradlew prettyDocs (the root project's pretty-docs plugin) documents core: the site, and its model docs.json.
//   2. apiIndex reads docs.json, keeps what core's module exports (module-info.java), and writes api-index.json:
//      every public type and member, with its signature and javadoc: each component's page and API reference.
//   3. apiSite copies the site, which the app serves to its Docs window from the jar.
//
//   just explorer        the explorer (just explorer fast: without making the docs again)

plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "io.joshuasalcedo.webview4j.explorer.Explorer"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

val skipDocs = providers.gradleProperty("explorer.skipDocs").isPresent
val site = rootProject.layout.buildDirectory.dir("docs/pretty")
val generated = layout.buildDirectory.dir("generated/explorer")

val apiSite = tasks.register<Sync>("apiSite") {
    description = "Copies core's pretty-docs site into the explorer's resources (explorer-docs/)."
    group = "build"
    if (!skipDocs) dependsOn(":prettyDocs")
    from(site) {
        // The site reads docs-data.js and modules/*.js; docs.json is the model apiIndex reads, not needed by the page.
        exclude("docs.json", ".pretty-docs")
    }
    into(generated.map { it.dir("site/explorer-docs") })
}

val apiIndex = tasks.register<JavaExec>("apiIndex") {
    description = "Writes the explorer's api-index.json from pretty-docs' docs.json: core's exported types and members."
    group = "build"
    if (!skipDocs) dependsOn(":prettyDocs")
    // The explorer's own classes (not its resources, which this task makes) and core.
    classpath = files(sourceSets.main.get().java.classesDirectory) + configurations.runtimeClasspath.get()
    dependsOn(tasks.compileJava)
    mainClass = "io.joshuasalcedo.webview4j.explorer.ApiIndex"
    val docs = site.map { it.file("docs.json") }
    val moduleInfo = project(":core").layout.projectDirectory.file("src/main/java/module-info.java")
    val out = generated.map { it.file("index/explorer/api-index.json") }
    inputs.file(docs)
    inputs.file(moduleInfo)
    outputs.file(out)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(docs.get().asFile.absolutePath, moduleInfo.asFile.absolutePath, out.get().asFile.absolutePath)
    })
}

sourceSets.main {
    resources.srcDir(files(generated.map { it.dir("site") }).builtBy(apiSite))
    resources.srcDir(files(generated.map { it.dir("index") }).builtBy(apiIndex))
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
