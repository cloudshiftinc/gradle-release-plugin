package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.PreReleaseHookSpec
import io.cloudshiftdev.gradle.release.util.PropertiesFile
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.nextPatch
import io.github.z4kn4fein.semver.nextPreRelease
import io.github.z4kn4fein.semver.toVersion
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.util.*
import javax.inject.Inject

public abstract class ExecuteRelease
@Inject
constructor(private val objects: ObjectFactory, private val fs: FileSystemOperations) :
    AbstractReleaseTask() {
    @get:Internal internal abstract val preReleaseHooks: ListProperty<PreReleaseHookSpec<*>>

    @get:InputFile internal abstract val versionPropertiesFile: RegularFileProperty

    @get:Input internal abstract val versionPropertyName: Property<String>

    @get:Input internal abstract val versionTagTemplate: Property<String>

    @get:Input internal abstract val versionTagCommitMessage: Property<String>

    @get:Input internal abstract val releaseCommitMessage: Property<String>

    @get:Input internal abstract val incrementAfterRelease: Property<Boolean>

    @get:Input internal abstract val newVersionCommitMessage: Property<String>

    @TaskAction
    public fun action() {
        val git = gitRepository.get()

        git.checkCommitNeeded()
        git.checkUpdatedNeeded()

        val versions = incrementVersion {
            // TODO - configuration for which to increment
            it.nextPatch()
        }

        executePreReleaseHooks(versions)

        // add any files that may have been created/modified by pre-release tasks
        git.stageFiles()

        // commit anything from pre-release tasks + version bump
        git.commit(
            "${releaseCommitMessage.get()} ${versions.previousVersion} -> ${versions.version}",
        )

        // tag with incremented version
        val versionTag = versionTagTemplate.get().replace("\$version", versions.version.toString())
        git.tag(
            versionTag,
            "${versionTagCommitMessage.get()} ${versions.previousVersion} -> ${versions.version}",
        )

        // push everything; this finalizes the release
        git.push()

        // commit and push properties files update
        if (incrementAfterRelease.get()) {
            // bump to next pre-release version
            val postReleaseVersions = incrementVersion {
                // TODO - configuration for which to increment
                it.nextPreRelease("SNAPSHOT")
            }
            git.stageFiles()
            git.commit(
                "${newVersionCommitMessage.get()} ${postReleaseVersions.previousVersion} -> ${postReleaseVersions.version}",
            )
            git.push()
        }
    }

    private fun executePreReleaseHooks(versions: Versions) {
        try {
            preReleaseHooks.get().forEach {
                val hook = objects.newInstance(it.clazz, *it.parameters)
                val workingDirectory = temporaryDir.resolve(UUID.randomUUID().toString())
                workingDirectory.mkdirs()
                try {
                    hook.execute(
                        PreReleaseHook.HookContext(
                            versions.previousVersion,
                            versions.version,
                            workingDirectory = workingDirectory,
                        ),
                    )
                } finally {
                    fs.delete { delete(workingDirectory) }
                }
            }
        } catch (t: Throwable) {
            // rollback version change if any exceptions from pre-release hooks
            logger.warn("Rolling back changes to ${versionPropertiesFile.get()} on exception")
            gitRepository.get().restore(versionPropertiesFile.get().asFile)
            throw t
        }
    }

    private fun incrementVersion(versionIncrementer: (Version) -> Version): Versions {

        val propertiesFile = versionPropertiesFile.get().asFile
        val currentVersionStr =
            PropertiesFile.loadProperty(versionPropertyName.get(), propertiesFile)
                ?: "0.1.0-SNAPSHOT"
        val currentVersion = currentVersionStr.toVersion()

        val nextVersion = versionIncrementer(currentVersion)

        logger.lifecycle("Incremented version from $currentVersion to $nextVersion")

        PropertiesFile.updateProperty(
            versionPropertyName.get(),
            nextVersion.toString(),
            propertiesFile,
        )

        return Versions(previousVersion = currentVersion, version = nextVersion)
    }

    private data class Versions(val previousVersion: Version, val version: Version)
}
