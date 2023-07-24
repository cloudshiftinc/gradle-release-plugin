package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.GitService
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
public abstract class AbstractReleaseTask : DefaultTask() {
    @get:Internal
    internal abstract val gitService: Property<GitService>

    protected fun releaseError(msg: String): Nothing = error("Cannot release; $msg")
}
