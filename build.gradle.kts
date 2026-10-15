// What every module shares. The modules: core (the runtime). Each has its own build.gradle.kts.

subprojects {
    group = "io.joshuasalcedo"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
