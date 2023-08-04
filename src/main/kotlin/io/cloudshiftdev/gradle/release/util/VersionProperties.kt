package io.cloudshiftdev.gradle.release.util

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import java.io.File

internal object VersionProperties {
    private val logger = ReleasePluginLogger.getLogger(VersionProperties::class)

    fun incrementVersion(
        versionPropertiesFile: File,
        versionPropertyName: String,
        defaultVersion: String = "0.1.0-SNAPSHOT",
        versionIncrementer: (Version) -> Version
    ): Versions {

        val currentVersionStr =
            PropertiesFile.loadProperty(versionPropertyName, versionPropertiesFile)
                ?: defaultVersion
        val currentVersion = currentVersionStr.toVersion()

        val nextVersion = versionIncrementer(currentVersion)

        logger.lifecycle("Incremented version from $currentVersion to $nextVersion")

        PropertiesFile.updateProperty(
            versionPropertyName,
            nextVersion.toString(),
            versionPropertiesFile,
        )

        return Versions(previousVersion = currentVersion, version = nextVersion)
    }

    data class Versions(val previousVersion: Version, val version: Version)
}
