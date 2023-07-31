@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.tasks.AbstractReleaseTask
import io.cloudshiftdev.gradle.release.tasks.DefaultPreReleaseChecks
import io.cloudshiftdev.gradle.release.tasks.ExecuteRelease
import io.cloudshiftdev.gradle.release.util.releasePluginError
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withType
import org.gradle.util.GradleVersion

public abstract class ReleasePlugin : Plugin<Project> {
    public companion object {
        public const val PluginId: String = PluginSpec.Id

        public const val PreReleaseChecksTaskName: String = "preReleaseChecks"
        public const val PreReleaseTaskName: String = "preRelease"
        public const val ReleaseTaskName: String = "release"

        private const val DefaultPreReleaseChecksTaskName: String = "defaultPreReleaseChecks"
        private const val ExecuteReleaseTaskName: String = "executeRelease"
    }

    override fun apply(project: Project): Unit =
        project.run {
            checkPlatformCompatibility()

            val releaseExtension = createReleaseExtension()

            // register this as a project-scoped service (by encoding project path in the service
            // name)
            // such that we can benefit from all the service-management infra (lazy-instantiation,
            // lifecycle management, etc.)
            val gitRepository =
                gradle.sharedServices.registerIfAbsent(
                    "${PluginSpec.Id}-${project.path}",
                    GitRepository::class,
                ) {
                    parameters {
                        projectDir.set(layout.projectDirectory)
                        gitSettings.set(releaseExtension.gitSettings)
                    }
                }

            val releaseGroup = "release"
            val releaseImplGroup = "other"

            // configure all release tasks (this catches tasks added later)
            tasks.withType<AbstractReleaseTask>().configureEach {
                this.gitRepository.set(gitRepository)
                usesService(gitRepository)
            }

            val defaultPreReleaseChecks =
                tasks.register<DefaultPreReleaseChecks>(DefaultPreReleaseChecksTaskName) {
                    group = releaseImplGroup
                    preReleaseChecks.set(releaseExtension.preReleaseChecks)
                }

            val preReleaseChecks =
                tasks.register(PreReleaseChecksTaskName) {
                    group = releaseGroup
                    dependsOn(defaultPreReleaseChecks)
                }

            val preRelease =
                tasks.register(PreReleaseTaskName) {
                    group = releaseImplGroup
                    dependsOn(preReleaseChecks)
                    mustRunAfter(preReleaseChecks)
                }

            val executeRelease =
                tasks.register<ExecuteRelease>(ExecuteReleaseTaskName) {
                    group = releaseImplGroup
                    mustRunAfter(preRelease)

                    versionPropertiesFile.set(releaseExtension.versionProperties.propertiesFile)
                    versionPropertyName.set(releaseExtension.versionProperties.propertyName)

                    releaseCommitMessage.set(releaseExtension.releaseCommitMessage)

                    versionTagTemplate.set(releaseExtension.versionTagTemplate)
                    versionTagCommitMessage.set(releaseExtension.versionTagCommitMessage)

                    incrementAfterRelease.set(releaseExtension.incrementAfterRelease)
                    newVersionCommitMessage.set(releaseExtension.newVersionCommitMessage)

                    preReleaseHooks.set(releaseExtension.preReleaseHooks)
                }

            tasks.register(ReleaseTaskName) {
                group = releaseGroup
                dependsOn(preRelease)
                dependsOn(executeRelease)
            }
        }

    private fun Project.createReleaseExtension(): ReleaseExtension {
        val releaseExtension = extensions.create<ReleaseExtension>("release")

        releaseExtension.apply {
            versionProperties {
                propertiesFile.convention(layout.projectDirectory.file("gradle.properties"))
                propertyName.convention("version")
            }

            gitSettings { signTag.convention(false) }

            preReleaseChecks {
                releaseBranchPattern.convention("main")
                failOnUntrackedFiles.convention(true)
                failOnUncommittedFiles.convention(true)
                failOnPushNeeded.convention(true)
                failOnPullNeeded.convention(true)
            }

            releaseCommitMessage.convention("[Release] - release commit:")

            versionTagTemplate.convention("v\$version")
            versionTagCommitMessage.convention("[Release] - creating tag:")

            incrementAfterRelease.convention(true)
            newVersionCommitMessage.convention("[Release] - new version commit:")
        }
        return releaseExtension
    }
}

internal fun checkPlatformCompatibility() {

    val supportedVersions =
        listOf(
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("7.0"),
                    javaVersionRange = 8..16,
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("7.3"),
                    javaVersionRange = 8..17,
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("7.5"),
                    javaVersionRange = 8..18,
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("7.6"),
                    javaVersionRange = 8..19,
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.0"),
                    javaVersionRange = 8..19,
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.1"),
                    javaVersionRange = 8..19,
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.2"),
                    javaVersionRange = 8..19,
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.3"),
                    javaVersionRange = 8..20,
                ),
            )
            .sortedBy { it.gradleVersion }

    val x = supportedVersions.map { it.gradleVersion }.first()
    val y = supportedVersions.map { it.gradleVersion }.last()
    val supportedGradleVersionRange = x..y

    val gradleVersion = GradleVersion.current()

    val gradleSupportSpec =
        supportedVersions.firstOrNull { gradleVersion.version.startsWith(it.gradleVersion.version) }

    when (gradleSupportSpec) {
        // not a supported Gradle version; see if it is a newer or older version
        null -> {
            val lastSupportedVersion = supportedVersions.last()
            when {
                // if a newer version of Gradle than what we support, warn
                gradleVersion > lastSupportedVersion.gradleVersion -> {
                    val logger = Logging.getLogger("releasePluginPlatformSupport")
                    logger.warn(
                        "[${PluginSpec.Id}] Gradle $gradleVersion is not formally supported by this version of the plugin (supported: Gradle $supportedGradleVersionRange",
                    )
                }
                else ->
                    releasePluginError(
                        "Gradle $gradleVersion not supported; supported: Gradle $supportedGradleVersionRange",
                    )
            }
        }
        else -> {
            val javaVersion = JavaVersion.current().majorVersion.toInt()
            if (javaVersion !in gradleSupportSpec.javaVersionRange) {
                releasePluginError(
                    "Java $javaVersion is not supported for this Gradle version (supported Java versions are ${gradleSupportSpec.javaVersionRange}",
                )
            }
        }
    }
}

private data class GradleSupportSpec(
    val gradleVersion: GradleVersion,
    val javaVersionRange: IntRange
)
