import com.gradle.publish.PublishTask
import java.util.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    alias(libs.plugins.gradlePluginPublish)
    signing

    // convention plugin from build-logic
    id("io.cloudshiftdev.gradle.conventions")

    alias(libs.plugins.release)
}

// define group here to work around inability to apply published version of this plugin to this project
// when the group is defined in 'gradle.properties'
group = "io.cloudshiftdev.gradle"

gradlePlugin {
    website.set("https://github.com/cloudshiftinc/gradle-release-plugin")
    vcsUrl.set("https://github.com/cloudshiftinc/gradle-release-plugin")
    plugins {
        create("cloudshiftRelease") {
            id = "io.cloudshiftdev.release"
            implementationClass = "io.cloudshiftdev.gradle.release.ReleasePlugin"
            displayName = "Gradle Release Plugin"
            description = project.description
            tags.set(listOf("release", "version", "release management"))
        }
    }
}

release {
    preReleaseHooks {
        processTemplates {
            from(fileTree("gradle/docs") { include("**/*.md") })
            into(layout.projectDirectory)
            propertyFrom(
                "compatTestMatrix",
                provider {
                    file("stutter.lockfile").bufferedReader().use { reader ->
                        val props = Properties()
                        props.load(reader)

                        props.entries.map {
                            val javaVersion = it.key.toString().removePrefix("java").toInt()
                            val gradleVersions =
                                it.value.toString().split(",").joinToString(", ")
                            javaVersion to gradleVersions
                        }.sortedBy { it.first }
                    }
                },
            )
        }
    }
}

val testingBase: Configuration by configurations.creating
configurations.testImplementation.get().extendsFrom(testingBase)
configurations.compatTestImplementation.get().extendsFrom(testingBase)

dependencies {
    implementation(libs.guava)
    implementation(libs.semver)
    implementation(libs.velocity)

    // testing libraries for both unit & compatibility tests
    testingBase(platform(libs.junit.bom))
    testingBase(libs.junit.jupiter.engine)

    testingBase(platform(libs.kotest.bom))
    testingBase(libs.kotest.assertions.core)
    testingBase(libs.kotest.assertions.json)
    testingBase(libs.kotest.framework.datatest)
    testingBase(libs.kotest.property)
    testingBase(libs.kotest.runner.junit5)

    testingBase(libs.jetbrains.kotlinx.datetime)

    // only for compatibility testing
    compatTestImplementation(gradleTestKit())
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
    isRequired = !isSnapshot
}

val publishingPredicate = provider {
    val ci = System.getenv()["CI"] == "true"
    System.getenv()
        .filter {
            it.key.startsWith("GITHUB_") && (it.key.contains("REF") || it.key.contains("EVENT"))
        }
        .forEach { println("Publishing env: ${it.key} -> ${it.value}") }

    val eventName = System.getenv()["GITHUB_EVENT_NAME"]
    val refName = System.getenv()["GITHUB_REF_NAME"]

    when {
        !ci || isSnapshot -> false
        eventName == "push" && refName == "main" -> true
        // TODO - handle PR merges
        else -> false
    }
}

kotlin {
    explicitApi()
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}

tasks {

    withType<ValidatePlugins>().configureEach {
        enableStricterValidation.set(true)
        failOnWarning.set(true)
    }

    named<KotlinCompile>("compileKotlin") {
        kotlinOptions {
            // language version must match the earliest supported Gradle version
            apiVersion = "1.4"
            languageVersion = "1.4"
        }
    }

    withType<PublishToMavenRepository>().configureEach {
        onlyIf("Publishing only allowed on CI for non-snapshot releases") { publishingPredicate.get() }
    }

    withType<PublishTask>().configureEach {
        onlyIf("Publishing only allowed on CI for non-snapshot releases") { publishingPredicate.get() }
    }
}

