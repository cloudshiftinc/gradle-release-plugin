package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.hooks.PreReleaseHook
import io.cloudshiftdev.gradle.release.util.ReleasePluginLogger
import io.cloudshiftdev.gradle.release.util.VersionProperties
import io.cloudshiftdev.gradle.release.util.releasePluginError
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.nextMajor
import io.github.z4kn4fein.semver.nextMinor
import io.github.z4kn4fein.semver.nextPatch
import javax.inject.Inject
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
    because = "Releases are infrequent and by definition change the task inputs",
)
public abstract class ExecuteRelease
@Inject
internal constructor(private val fs: FileSystemOperations) : AbstractReleaseTask() {

    @get:Internal internal abstract val preReleaseHooks: ListProperty<PreReleaseHook>

    @get:Input internal abstract val dryRun: Property<Boolean>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    internal abstract val versionPropertiesFile: RegularFileProperty

    @get:Input internal abstract val versionPropertyName: Property<String>

    @get:Input internal abstract val versionTagTemplate: Property<String>

    @get:Input internal abstract val versionTagCommitMessage: Property<String>

    @get:Input internal abstract val releaseCommitMessage: Property<String>

    @get:Input internal abstract val incrementAfterRelease: Property<Boolean>

    @get:Input internal abstract val newVersionCommitMessage: Property<String>

    @get:Input internal abstract val releaseBump: Property<String>

    @get:Input @get:Optional internal abstract val releaseVersion: Property<String>

    @get:Input @get:Optional internal abstract val nextVersion: Property<String>

    @TaskAction
    public fun action() {
        // prepare the incrementers first to catch any validation issues w/ configuration
        val releaseVersionIncrementer = releaseVersionIncrementer()
        val nextPreReleaseVersionIncrementer = nextPreReleaseVersionIncrementer()

        val git = gitRepository.get()
        val templateService = templateService.get()

        val hooks = preReleaseHooks.get()

        val hookServices = PreReleaseHook.HookServices(templateService = templateService)
        // validate all hooks before any mutating activities
        hooks.forEach { it.validate(hookServices) }

        val versions = incrementVersion(releaseVersionIncrementer)

        executePreReleaseHooks(hooks, versions)

        if (dryRun.get()) {
            logger.warn(
                "DRY RUN: release not committed, tagged or pushed.  Modified files remain in-place for inspection.",
            )
            return
        }

        val templateContext =
            mapOf(
                "preReleaseVersion" to versions.previousVersion.toString(),
                "releaseVersion" to versions.version.toString(),
            )

        // add any files that may have been created/modified by pre-release tasks
        git.stageFiles()

        // commit anything from pre-release tasks + version bump
        git.commit(
            templateService.evaluateTemplate(
                releaseCommitMessage,
                "releaseCommitMessage",
                templateContext
            )
        )

        // tag with incremented version
        git.tag(
            templateService.evaluateTemplate(versionTagTemplate, "versionTag", templateContext),
            templateService.evaluateTemplate(
                versionTagCommitMessage,
                "versionTagCommitMessage",
                templateContext
            ),
        )

        // push everything; this finalizes the release
        git.push()

        // commit and push properties files update
        if (incrementAfterRelease.get()) {
            // bump to next pre-release version
            val postReleaseVersions = incrementVersion(nextPreReleaseVersionIncrementer)
            val postReleaseTemplateContext =
                mapOf(
                    "preReleaseVersion" to versions.previousVersion.toString(),
                    "releaseVersion" to versions.version.toString(),
                    "nextPreReleaseVersion" to postReleaseVersions.version.toString(),
                )

            git.stageFiles()
            git.commit(
                templateService.evaluateTemplate(
                    newVersionCommitMessage,
                    "newVersionCommitMessage",
                    postReleaseTemplateContext,
                ),
            )
            git.push()
        }
    }

    private fun releaseVersionIncrementer(): (Version) -> Version {
        return when (val releaseVersionStr = releaseVersion.orNull) {
            null -> { it -> it.nextPatch() }
            else -> {
                val releaseVersion = Version.parse(releaseVersionStr.trim())
                check(!releaseVersion.isPreRelease) {
                    "Cannot use pre-release version for release version: $releaseVersionStr"
                }
                val incrementer = { _: Version -> releaseVersion }
                incrementer
            }
        }
    }

    private fun nextPreReleaseVersionIncrementer(): (Version) -> Version {
        return when (val nextVersionStr = nextVersion.orNull) {
            // no explicit next version; bump from release version as specified
            null ->
                when (val value = releaseBump.get()) {
                    "major" -> { it -> it.nextMajor("SNAPSHOT") }
                    "minor" -> { it -> it.nextMinor("SNAPSHOT") }
                    "patch" -> { it -> it.nextPatch("SNAPSHOT") }
                    else ->
                        releasePluginError(
                            "Invalid release bump value '$value'; expected 'major', 'minor', or 'patch'"
                        )
                }

            // explicit version specified; validate and use it
            else -> {
                val nextVersion = Version.parse(nextVersionStr.trim())
                check(nextVersion.isPreRelease) {
                    "Expected pre-release version for next version: $nextVersion"
                }
                val incrementer = { _: Version -> nextVersion }
                incrementer
            }
        }
    }

    private fun executePreReleaseHooks(
        hooks: List<PreReleaseHook>,
        versions: VersionProperties.Versions
    ) {
        try {
            val hookServices = PreReleaseHook.HookServices(templateService = templateService.get())
            hooks.forEachIndexed { index, hook ->
                val workingDirectory = temporaryDir.resolve("pre-release-hook-$index")
                fs.delete { delete(workingDirectory) }
                workingDirectory.mkdirs()
                hook.execute(
                    hookServices,
                    PreReleaseHook.HookContext(
                        versions.previousVersion,
                        versions.version,
                        workingDirectory = workingDirectory,
                        dryRun.get(),
                    ),
                )
            }
        } catch (t: Throwable) {
            // rollback version change if any exceptions from pre-release hooks
            logger.warn("Rolling back changes to ${versionPropertiesFile.get()} on exception")
            gitRepository.get().restore(versionPropertiesFile.get().asFile)
            throw t
        }
    }

    private fun incrementVersion(
        versionIncrementer: (Version) -> Version
    ): VersionProperties.Versions {
        return VersionProperties.incrementVersion(
            versionPropertiesFile = versionPropertiesFile.get().asFile,
            versionPropertyName = versionPropertyName.get(),
            versionIncrementer = versionIncrementer
        )
    }

    @Internal
    override fun getLogger(): Logger {
        return ReleasePluginLogger.wrapLogger(super.getLogger())
    }
}
