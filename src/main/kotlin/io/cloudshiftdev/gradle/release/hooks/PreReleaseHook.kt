package io.cloudshiftdev.gradle.release.hooks

import io.github.z4kn4fein.semver.Version
import java.io.File

public interface PreReleaseHook {
    public fun validate() {}

    public fun execute(context: HookContext)

    public data class HookContext(
        val preReleaseVersion: Version,
        val releaseVersion: Version,
        val workingDirectory: File,
        val dryRun: Boolean
    )
}
