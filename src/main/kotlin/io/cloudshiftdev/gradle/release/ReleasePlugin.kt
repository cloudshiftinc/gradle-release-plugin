@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.tasks.AbstractReleaseTask
import io.cloudshiftdev.gradle.release.tasks.ExecuteRelease
import io.cloudshiftdev.gradle.release.util.releasePluginError
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.registering
import org.gradle.kotlin.dsl.withType
import org.gradle.util.GradleVersion

public abstract class ReleasePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit =
        project.run {
            checkPlatformCompatibility()

            val releaseExtension = createReleaseExtension()

            val gitRepositoryService =
                gradle.sharedServices.registerIfAbsent(
                    "${PluginSpec.Id}-${project.path}",
                    GitRepository::class
                ) {
                    parameters {
                        projectDir.set(layout.projectDirectory)
                        gitSettings.set(releaseExtension.git)
                    }
                }

            // configure all release tasks (this catches tasks added later)
            tasks.withType<AbstractReleaseTask>().configureEach {
                gitRepository = gitRepositoryService
                group = "release"
            }

            val checkRelease by tasks.registering

            val preRelease by tasks.registering { dependsOn(checkRelease) }

            val executeRelease by
                tasks.registering(ExecuteRelease::class) {
                    mustRunAfter(preRelease)

                    versionPropertiesFile = releaseExtension.versionProperties.propertiesFile
                    versionPropertyName = releaseExtension.versionProperties.propertyName

                    releaseCommitMessage = releaseExtension.releaseCommitMessage

                    versionTagTemplate = releaseExtension.versionTagTemplate
                    versionTagCommitMessage = releaseExtension.versionTagCommitMessage

                    incrementAfterRelease = releaseExtension.incrementAfterRelease
                    newVersionCommitMessage = releaseExtension.newVersionCommitMessage

                    preReleaseHooks = releaseExtension.preReleaseHooks
                }

            val release by
                tasks.registering {
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

            git {
                signTag.convention(false)
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

    val minimumJavaVersion = 11

    val supportedVersions =
        listOf(
                //        SupportedGradleVersion(gradleVersion = GradleVersion.version("6.9"),
                // javaVersionRange = minimumJavaVersion..15),
                //        SupportedGradleVersion(gradleVersion = GradleVersion.version("7.3"),
                // javaVersionRange = minimumJavaVersion..16),
                //        SupportedGradleVersion(gradleVersion = GradleVersion.version("7.3"),
                // javaVersionRange = minimumJavaVersion..17),
                //        SupportedGradleVersion(gradleVersion = GradleVersion.version("7.5"),
                // javaVersionRange = minimumJavaVersion..18),
                //        SupportedGradleVersion(gradleVersion = GradleVersion.version("7.6"),
                // javaVersionRange = minimumJavaVersion..19),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.0"),
                    javaVersionRange = minimumJavaVersion..19
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.1"),
                    javaVersionRange = minimumJavaVersion..19
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.2"),
                    javaVersionRange = minimumJavaVersion..19
                ),
                GradleSupportSpec(
                    gradleVersion = GradleVersion.version("8.3"),
                    javaVersionRange = minimumJavaVersion..20
                )
            )
            .sortedBy { it.gradleVersion }

    val x = supportedVersions.map { it.gradleVersion }.first()
    val y = supportedVersions.map { it.gradleVersion }.last()
    val supportedGradleVersionRange = x..y

    val gradleVersion = GradleVersion.current()

    val gradleSupportSpec =
        supportedVersions.firstOrNull() {
            gradleVersion.version.startsWith(it.gradleVersion.version)
        }

    when (gradleSupportSpec) {
        // not a supported Gradle version; see if it is a newer or older version
        null -> {
            val lastSupportedVersion = supportedVersions.last()
            when {
                // if a newer version of Gradle than what we support, warn
                gradleVersion > lastSupportedVersion.gradleVersion -> {
                    val logger = Logging.getLogger("releasePluginPlatformSupport")
                    logger.warn(
                        "[${PluginSpec.Id}] Gradle $gradleVersion is not formally supported by this version of the plugin (supported: Gradle $supportedGradleVersionRange"
                    )
                }
                else ->
                    releasePluginError(
                        "Gradle $gradleVersion not supported; supported: Gradle $supportedGradleVersionRange"
                    )
            }
        }
        else -> {
            val javaVersion = JavaVersion.current().majorVersion.toInt()
            if (javaVersion !in gradleSupportSpec.javaVersionRange) {
                releasePluginError(
                    "Java $javaVersion is not supported for this Gradle version (supported Java versions are ${gradleSupportSpec.javaVersionRange}"
                )
            }
        }
    }
}

private data class GradleSupportSpec(
    val gradleVersion: GradleVersion,
    val javaVersionRange: IntRange
)
