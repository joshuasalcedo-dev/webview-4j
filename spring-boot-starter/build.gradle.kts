// webview-4j-spring-boot-starter: a Spring Boot app opens in a native window when it is ready, with no code.
// Add it as a dependency; webview4j.* in application.properties sets the window.

plugins {
    `java-library`
}

base {
    archivesName = "webview-4j-spring-boot-starter"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val springBoot = "4.2.0"

dependencies {
    api(project(":core"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBoot")
    // The API explorer, when the app has devtools too (webview4j.api-explorer=true).
    compileOnly(project(":devtools"))

    testImplementation(project(":devtools"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc:$springBoot")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBoot")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
