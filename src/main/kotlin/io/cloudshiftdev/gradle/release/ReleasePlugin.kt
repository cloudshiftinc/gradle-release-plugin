@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.tasks.AbstractReleaseTask
import io.cloudshiftdev.gradle.release.tasks.DefaultPreReleaseChecks
import io.cloudshiftdev.gradle.release.tasks.ExecuteRelease
import io.cloudshiftdev.gradle.release.tasks.SetCurrentVersion
import io.cloudshiftdev.gradle.release.util.PluginSpec
import io.cloudshiftdev.gradle.release.util.gradlePlatformCompatibility
import io.cloudshiftdev.gradle.release.util.releasePluginError
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
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

            if (this != rootProject) {
                releasePluginError("Can only apply release plugin on root project")
            }

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

            val templateService =
                gradle.sharedServices.registerIfAbsent("templateService", TemplateService::class) {
                    parameters {
                        missingTemplateVariableAction.set(
                            releaseExtension.missingTemplateVariableAction,
                        )
                    }
                }

            val releaseGroup = "release"
            val releaseImplGroup = "other"

            // configure all release tasks (this catches tasks added later)
            tasks.withType<AbstractReleaseTask>().configureEach {
                this.gitRepository.set(gitRepository)
                this.templateService.set(templateService)
                usesService(gitRepository)
                usesService(templateService)
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

                    dryRun.set(releaseExtension.dryRun)
                    versionPropertiesFile.set(releaseExtension.versionProperties.propertiesFile)
                    versionPropertyName.set(releaseExtension.versionProperties.propertyName)

                    releaseCommitMessage.set(releaseExtension.releaseCommitMessage)

                    versionTagTemplate.set(releaseExtension.versionTagTemplate)
                    versionTagCommitMessage.set(releaseExtension.versionTagCommitMessage)

                    incrementAfterRelease.set(releaseExtension.incrementAfterRelease)
                    newVersionCommitMessage.set(releaseExtension.newVersionCommitMessage)

                    preReleaseHooks.set(releaseExtension.preReleaseHooks.hooks)

                    releaseBump.set(
                        providers
                            .gradleProperty("release.bump")
                            .orElse(releaseExtension.releaseBump),
                    )
                    releaseVersion.convention(providers.gradleProperty("release.version"))
                    nextVersion.convention(providers.gradleProperty("release.next-version"))
                }

            tasks.register(ReleaseTaskName) {
                group = releaseGroup
                dependsOn(preRelease)
                dependsOn(executeRelease)
            }

            tasks.register<SetCurrentVersion>("setCurrentVersion") {
                group = releaseGroup
                versionPropertiesFile.set(releaseExtension.versionProperties.propertiesFile)
                versionPropertyName.set(releaseExtension.versionProperties.propertyName)
            }
        }

    private fun Project.createReleaseExtension(): ReleaseExtension {
        val releaseExtension = extensions.create<ReleaseExtension>("release")

        releaseExtension.apply {
            dryRun.convention(
                providers.gradleProperty("release.dry-run").map { it.toBoolean() }.orElse(false),
            )

            versionProperties {
                propertiesFile.convention(layout.projectDirectory.file("gradle.properties"))
                propertyName.convention("version")
            }

            gitSettings {
                signTag.convention(false)
                remoteName.convention("origin")
            }

            preReleaseChecks {
                releaseBranchPattern.convention("main")
                failOnUntrackedFiles.convention(true)
                failOnUncommittedFiles.convention(true)
                failOnPushNeeded.convention(true)
                failOnPullNeeded.convention(true)
            }

            releaseCommitMessage.convention(
                "[Release] - release commit: {{preReleaseVersion}} -> {{releaseVersion}}",
            )

            versionTagTemplate.convention("v{{releaseVersion}}")
            versionTagCommitMessage.convention(
                "[Release] - creating tag: {{preReleaseVersion}} -> {{releaseVersion}}",
            )

            incrementAfterRelease.convention(true)
            newVersionCommitMessage.convention(
                "[Release] - new version commit: {{releaseVersion}} -> {{nextPreReleaseVersion}}",
            )

            releaseBump.set("patch")
            missingTemplateVariableAction.convention("exception")
        }
        return releaseExtension
    }
}

internal fun checkPlatformCompatibility() {
    gradlePlatformCompatibility.isCompatible(GradleVersion.current(), JavaVersion.current())
}
