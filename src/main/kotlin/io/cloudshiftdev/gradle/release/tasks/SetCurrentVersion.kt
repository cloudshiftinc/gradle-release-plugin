package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.util.VersionProperties
import io.github.z4kn4fein.semver.Version
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "No value in caching")
internal abstract class SetCurrentVersion : DefaultTask() {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    internal abstract val versionPropertiesFile: RegularFileProperty

    @get:Input internal abstract val versionPropertyName: Property<String>

    @get:Option(option = "version", description = "version to set")
    @get:Input
    protected abstract val version: Property<String>

    @TaskAction
    fun action() {
        val newVersion = Version.parse(version.get().trim())
        check(newVersion.isPreRelease) { "New version must be pre-release: $newVersion" }

        VersionProperties.incrementVersion(
            versionPropertiesFile = versionPropertiesFile.get().asFile,
            versionPropertyName = versionPropertyName.get(),
            versionIncrementer = { _ -> newVersion },
        )
    }
}
