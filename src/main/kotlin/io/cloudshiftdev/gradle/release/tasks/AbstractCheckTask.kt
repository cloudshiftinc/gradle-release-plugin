package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.GitService
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

public abstract class AbstractCheckTask : AbstractReleaseTask() {

    @get:Internal
    protected abstract val message: String

    @get:Input
    internal abstract val fail: Property<Boolean>

    @TaskAction
    public fun action() {
        val result = executeCheckCommand()
        if (result.outputLines.isNotEmpty()) {
            val msg = "$message: \n${result.output}"
            when (fail.get()) {
                true -> releaseError(msg)
                else -> logger.warn(msg)
            }
        }
    }

    internal abstract fun executeCheckCommand(): GitService.GitOutput
}
