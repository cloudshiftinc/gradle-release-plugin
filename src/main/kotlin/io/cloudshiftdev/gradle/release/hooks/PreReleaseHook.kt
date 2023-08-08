package io.cloudshiftdev.gradle.release.hooks

import io.cloudshiftdev.gradle.release.TemplateService
import io.github.z4kn4fein.semver.Version
import java.io.File

public interface PreReleaseHook {
    public fun validate(hookServices: HookServices) {}

    public fun execute(hookServices: HookServices, context: HookContext)

    public data class HookContext(
        val preReleaseVersion: Version,
        val releaseVersion: Version,
        val workingDirectory: File,
        val dryRun: Boolean
    )

    public data class HookServices(internal val templateService: TemplateService)
}
