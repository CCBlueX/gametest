plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

val jdk = libs.versions.jdk.get().toInt()
val minecraft = libs.versions.minecraft.get()

// A tag v1.1.0 builds `1.1.0+26.3`, anything else `<version>+26.3-SNAPSHOT`
val release = providers.environmentVariable("GITHUB_REF").orNull?.startsWith("refs/tags/v") == true
version = "${project.version}+$minecraft" + if (release) "" else "-SNAPSHOT"

base {
    archivesName = "gametest-harness"
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

loom {
    accessWidenerPath = file("src/main/resources/gametest-harness.accesswidener")
}

// Mojang's names without a remapping step, like the mods that use it. The mod brings its own Fabric API and
// Kotlin, so they stay out of the published POM.
dependencies {
    minecraft(libs.minecraft)
    compileOnly(libs.fabric.loader)
    compileOnly(libs.fabric.api)
    compileOnly(libs.fabric.kotlin)
}

tasks.processResources {
    val properties = mapOf("version" to project.version.toString(), "minecraft" to minecraft)
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(jdk)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = jdk
}

kotlin {
    jvmToolchain(jdk)
    coreLibrariesVersion = libs.versions.kotlin.get()
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_gametest-harness" }
    }
}

publishing {
    publications {
        create<MavenPublication>("harness") {
            from(components["java"])
            artifactId = "gametest-harness"
            pom {
                name = "Game test harness"
                description = "Fabric client game tests on a shared Paper server with an anticheat of choice watching."
                url = "https://github.com/CCBlueX/gametest"
                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                    }
                }
                scm {
                    url = "https://github.com/CCBlueX/gametest"
                }
            }
        }
    }
    repositories {
        maven {
            name = "CCBlueX"
            url = uri("https://maven.ccbluex.net/" + if (release) "releases" else "snapshots")
            credentials {
                username = providers.environmentVariable("MAVEN_USERNAME").orNull
                password = providers.environmentVariable("MAVEN_PASSWORD").orNull
            }
        }
    }
}
