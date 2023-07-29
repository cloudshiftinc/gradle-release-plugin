package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.GitRepository
import io.cloudshiftdev.gradle.release.util.releasePluginError
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
public abstract class AbstractReleaseTask : DefaultTask() {
    @get:ServiceReference internal abstract val gitRepository: Property<GitRepository>

    protected fun releaseError(msg: String): Nothing = releasePluginError("Cannot release; $msg")
}
