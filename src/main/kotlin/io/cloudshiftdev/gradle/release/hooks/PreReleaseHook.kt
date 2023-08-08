package io.cloudshiftdev.gradle.release.hooks

import io.cloudshiftdev.gradle.release.TemplateService
import io.github.z4kn4fein.semver.Version
import java.io.File

public interface PreReleaseHook {
    public fun validate(hookServices: HookServices) {}

    public fun execute(hookServices: HookServices, context: HookContext)

    public class HookContext
    internal constructor(
        public val preReleaseVersion: Version,
        public val releaseVersion: Version,
        public val workingDirectory: File,
        public val dryRun: Boolean
    )

    public class HookServices internal constructor(internal val templateService: TemplateService)
}
