@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.tasks.AbstractReleaseTask
import io.cloudshiftdev.gradle.release.tasks.CheckLocalOutstandingCommits
import io.cloudshiftdev.gradle.release.tasks.CheckLocalStagedFiles
import io.cloudshiftdev.gradle.release.tasks.CheckLocalUnstagedFiles
import io.cloudshiftdev.gradle.release.tasks.CheckRemoteOutstandingCommits
import io.cloudshiftdev.gradle.release.tasks.ExecuteRelease
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.registering
import org.gradle.kotlin.dsl.withType
import org.gradle.kotlin.dsl.assign
import org.gradle.util.GradleVersion

public abstract class ReleasePlugin : Plugin<Project> {

    override fun apply(project: Project) : Unit = project.run {

        val minimumGradleVersion = "8.0"
        require(GradleVersion.current() >= GradleVersion.version(minimumGradleVersion) ) { "A minimum of Gradle $minimumGradleVersion is required for the CloudShift Gradle Release Plugin"}

        val releaseExtension = createReleaseExtension()

        val gitServiceProvider = gradle.sharedServices.registerIfAbsent("gitService", GitServiceImpl::class) {
            parameters {
                releaseBranchPattern = releaseExtension.git.releaseBranchPattern
            }
        }

        // configure all release tasks (this catches tasks added later)
        tasks.withType<AbstractReleaseTask>().configureEach {
            gitService = gitServiceProvider
            group = "release"
        }

        val checkRelease by tasks.registering

        val preRelease by tasks.registering {
            dependsOn(checkRelease)
        }

        val executeRelease by tasks.registering(ExecuteRelease::class) {
            mustRunAfter(preRelease)

            versionPropertiesFile = releaseExtension.versionProperties.propertiesFile
            versionPropertyName = releaseExtension.versionProperties.propertyName

            releaseCommitMessage = releaseExtension.releaseCommitMessage

            versionTagTemplate = releaseExtension.versionTagTemplate
            versionTagCommitMessage = releaseExtension.versionTagCommitMessage

            incrementAfterRelease = releaseExtension.incrementAfterRelease
            newVersionCommitMessage = releaseExtension.newVersionCommitMessage

            preReleaseHooks = releaseExtension.preReleaseHooks

            signTag = releaseExtension.git.signTag
        }

        val release by tasks.registering {
            dependsOn(preRelease)
            dependsOn(executeRelease)
        }

        registerPreReleaseChecks(releaseExtension, checkRelease)
    }

    private fun Project.registerPreReleaseChecks(
        releaseExtension: ReleaseExtension,
        checkRelease: TaskProvider<Task>
    ) {
        val checkLocalUnstagedFiles by tasks.registering(CheckLocalUnstagedFiles::class) {
            fail = releaseExtension.checks.failOnUnstagedFiles
        }

        val checkLocalStagedFiles by tasks.registering(CheckLocalStagedFiles::class) {
            fail = releaseExtension.checks.failOnStagedFiles
        }

        val checkLocalOutstandingCommits by tasks.registering(CheckLocalOutstandingCommits::class) {
            fail = releaseExtension.checks.failOnPushNeeded
        }

        val checkRemoteOutstandingCommits by tasks.registering(CheckRemoteOutstandingCommits::class) {
            fail = releaseExtension.checks.failOnPullNeeded
        }

        checkRelease.configure {
            dependsOn(checkLocalUnstagedFiles)
            dependsOn(checkLocalOutstandingCommits)
            dependsOn(checkRemoteOutstandingCommits)
            dependsOn(checkLocalStagedFiles)
        }
    }

    private fun Project.createReleaseExtension(): ReleaseExtension {
        val releaseExtension = extensions.create<ReleaseExtension>("release")

        releaseExtension.apply {
            checks {
                failOnUnstagedFiles.convention(true)
                failOnStagedFiles.convention(true)
                failOnPushNeeded.convention(true)
                failOnPullNeeded.convention(true)
            }

            versionProperties {
                propertiesFile.convention(layout.projectDirectory.file("gradle.properties"))
                propertyName.convention("version")
            }

            git {
                signTag.convention(false)
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