package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.GitRepository
import io.cloudshiftdev.gradle.release.TemplateService
import io.cloudshiftdev.gradle.release.util.releasePluginError
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
public abstract class AbstractReleaseTask : DefaultTask() {
    // for Gradle 8.0+ this can be @get:ServiceReference; use @get:Internal for backwards compat
    @get:Internal internal abstract val gitRepository: Property<GitRepository>

    @get:Internal internal abstract val templateService: Property<TemplateService>

    internal fun gitRepository(): GitRepository = gitRepository.get()

    protected fun cannotReleaseError(msg: String): Nothing =
        releasePluginError("Cannot release; $msg")
}
