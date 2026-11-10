plugins {
    java
    alias(libs.plugins.paperweight.userdev)
}

repositories {
    mavenCentral()
    maven("https://repo.grim.ac/snapshots")
    // PluginHooker publishes its API only as a release asset
    exclusiveContent {
        forRepository {
            ivy("https://github.com/DionaMC/PluginHooker/releases/download/") {
                patternLayout { artifact("[revision]/[module]-[revision].[ext]") }
                metadataSources { artifact() }
            }
        }
        filter { includeModule("dev.diona", "hooker-api") }
    }
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())
    compileOnly(libs.grim.api)
    compileOnly(libs.pluginhooker.api)
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
