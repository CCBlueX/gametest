plugins {
    java
    alias(libs.plugins.paperweight.userdev)
}

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())
}

java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get().toInt())

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.jar {
    archiveFileName = "gametest-plugin.jar"
}
