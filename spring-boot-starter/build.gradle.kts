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
    // The API explorer, when the app has devtools too.
    compileOnly(project(":devtools"))
    // A desktop client of a server (@WebViewApplication): Spring's HTTP service clients, and Jakarta Validation to
    // check a call before it is sent. The app brings them; the starter works without them.
    compileOnly("org.springframework:spring-web:7.1.0")
    // The server's invalidations endpoint (StateInvalidations): a stream of server-sent events, on a Spring MVC server.
    compileOnly("org.springframework:spring-webmvc:7.1.0")
    compileOnly("jakarta.validation:jakarta.validation-api:3.1.1")
    // Spring Data's Page and Pageable for the collections' queries (SpringPaging), when the app has Spring Data.
    compileOnly("org.springframework.data:spring-data-commons:4.2.0")

    testImplementation(project(":devtools"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc:$springBoot")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBoot")
    testImplementation("org.springframework.boot:spring-boot-starter-validation:$springBoot")
    testImplementation("org.springframework.data:spring-data-commons:4.2.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
