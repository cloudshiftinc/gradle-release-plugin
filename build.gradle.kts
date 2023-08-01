import com.gradle.publish.PublishTask
import java.util.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.0"
    signing

    // convention plugin from build-logic
    id("io.cloudshiftdev.gradle.conventions")

    //    id("io.cloudshiftdev.release") version "0.1.20"
    //    alias(libs.plugins.release)
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

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

tasks.withType<ValidatePlugins>().configureEach {
    enableStricterValidation.set(true)
    failOnWarning.set(true)
}

kotlin {
    explicitApi()
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}

tasks.named<KotlinCompile>("compileKotlin") {
    kotlinOptions {
        // language version must match the earliest supported Gradle version
        apiVersion = "1.4"
        languageVersion = "1.4"
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.semver)

    // testing libraries
    compatTestImplementation(platform(libs.junit.bom))
    compatTestRuntimeOnly(platform(libs.junit.bom))
    compatTestRuntimeOnly(libs.junit.jupiter.engine)

    compatTestImplementation(platform(libs.kotest.bom))
    compatTestImplementation(libs.kotest.assertions.core)
    compatTestImplementation(libs.kotest.assertions.json)
    compatTestImplementation(libs.kotest.framework.datatest)
    compatTestImplementation(libs.kotest.property)
    compatTestImplementation(libs.kotest.runner.junit5)

    compatTestImplementation(libs.jetbrains.kotlinx.datetime)

    compatTestImplementation(gradleTestKit())
}

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

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf("Publishing only allowed on CI for non-snapshot releases") { publishingPredicate.get() }
}

tasks.withType<PublishTask>().configureEach {
    onlyIf("Publishing only allowed on CI for non-snapshot releases") { publishingPredicate.get() }
}

println(buildTestMatrixMarkdown(file("stutter.lockfile")))

fun buildTestMatrixMarkdown(matrixFile: File): String {
    return matrixFile.bufferedReader().use {
        val props = Properties()
        props.load(it)
        val keys =
            props.keys
                .map(Any::toString)
                .map { it to it.removePrefix("java").toInt() }
                .sortedBy { it.second }
        val tableRows =
            keys.map { versionPair ->
                val gradleVersions =
                    props[versionPair.first]?.toString()?.split(",")?.joinToString(", ")
                "| Java ${versionPair.second} | Gradle $gradleVersions |"
            }
        "| Java Version | Gradle Version |\n| --- | --- |\n" + tableRows.joinToString("\n")
    }
}
