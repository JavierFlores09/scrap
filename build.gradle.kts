import io.papermc.paperweight.core.tasks.CheckoutRepo
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.24"
}

paperweight {
    upstreams.paper {
        ref = providers.gradleProperty("paperRef")

        patchFile {
            path = "paper-server/build.gradle.kts"
            outputFile = file("scrap-server/build.gradle.kts")
            patchFile = file("scrap-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "paper-api/build.gradle.kts"
            outputFile = file("scrap-api/build.gradle.kts")
            patchFile = file("scrap-api/build.gradle.kts.patch")
        }
        patchFile {
            path = "paper-server/.checkstyle/checkstyle.xml"
            outputFile = file("scrap-server/.checkstyle/checkstyle.xml")
            patchFile = file("scrap-server/checkstyle.xml.patch")
        }
        patchDir("paperApi") {
            upstreamPath = "paper-api"
            excludes = setOf("build.gradle.kts")
            patchesDir = file("scrap-api/paper-patches")
            outputDir = file("paper-api")
        }
    }
}

project(":paper-checkstyle") {
    layout.buildDirectory = rootProject.layout.buildDirectory.dir("paper-checkstyle")
}

fun syncPaperDirectory(name: String, source: String, destination: String) =
    tasks.register<Sync>(name) {
        from(tasks.named<CheckoutRepo>("checkoutPaperRepo").flatMap { it.outputDir.dir(source) })
        exclude("build/**")
        into(layout.projectDirectory.dir(destination))
    }

val checkstyleInputs = listOf(
    syncPaperDirectory("syncPaperCheckstyle", "paper-checkstyle", "paper-checkstyle"),
    syncPaperDirectory("syncCheckstyleRules", ".checkstyle", ".checkstyle"),
    syncPaperDirectory("syncApiCheckstyleRules", "paper-api/.checkstyle", "scrap-api/.checkstyle"),
)
// TODO: figure out a better way to do this, feels dirty but only because task is registered in an afterEvaluate...
tasks.named { it == "applyPaperPatches" }.configureEach {
    dependsOn(checkstyleInputs)
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.isFork = true
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://artifactory.papermc.io/artifactory/releases/") {
                name = "paperReleases"
                credentials(PasswordCredentials::class)
            }
        }
    }
}
