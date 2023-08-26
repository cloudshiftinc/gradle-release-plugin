
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.gradle.publish.PublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    alias(libs.plugins.gradlePluginPublish)
    signing
    alias(libs.plugins.jetbrains.binaryCompatibilityValidator)

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
    dryRun.set(true)
    preReleaseHooks {
        processTemplates {
            from(fileTree("docs") { include("**/*.md") })
            into(layout.projectDirectory)
            propertyFrom(
                "compatTestMatrix",
                provider {
                    file(".github/compatibility-test-matrix.json").bufferedReader().use { reader ->

                        val mapper = jacksonObjectMapper()
                        mapper.readValue<List<CompatEntry>>(reader).groupBy { it.javaVersion }
                            .mapValues { entry ->
                                entry.value.map { GradleVersion.version(it.gradleVersion) }
                                    .sorted().joinToString(", ") { it.version }
                            }.toSortedMap().entries
                    }
                },
            )
        }
    }
}

data class CompatEntry(val javaVersion: Int, val gradleVersion: String)

dependencies {
    implementation(libs.guava)
    implementation(libs.semver)
    implementation(libs.mustache)

    // testing libraries for both unit & compatibility tests
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.engine)

    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.framework.datatest)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)

    testImplementation(libs.jetbrains.kotlinx.datetime)

    // only for compatibility testing
    testImplementation(gradleTestKit())
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
    when {
        ci -> !isSnapshot
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
            // language version must be compatible with the earliest supported Gradle version
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

    val javaToolchainService = extensions.getByType<JavaToolchainService>()
    named<Test>("test") {
        systemProperty("compat.gradle.version", GradleVersion.current().version)
    }

    register<Test>("compatibilityTest") {
        javaLauncher.set(
            providers.gradleProperty("compatibility-test.java-version").flatMap {
                javaToolchainService.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(it))
                }
            },
        )

        // TODO: remove .get() in Gradle 9, once systemProperty is a MapProperty
        systemProperty(
            "compat.gradle.version",
            providers.gradleProperty("compatibility-test.gradle-version")
                .orElse(GradleVersion.current().version).get(),
        )
    }

    register<Test>("compatibilityTestJava8Gradle702") {
        javaLauncher.set(
            javaToolchainService.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(8))
            },
        )

        systemProperty("compat.gradle.version", "7.0.2")
    }

    register<Test>("compatibilityTestJava20Gradle83") {
        javaLauncher.set(
            javaToolchainService.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(20))
            },
        )

        systemProperty("compat.gradle.version", "8.3")
    }
}
