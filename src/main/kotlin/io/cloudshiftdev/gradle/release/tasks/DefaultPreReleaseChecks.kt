package io.cloudshiftdev.gradle.release.tasks

import io.cloudshiftdev.gradle.release.GitRepository
import io.cloudshiftdev.gradle.release.ReleaseExtension
import io.cloudshiftdev.gradle.release.util.releasePluginError
import io.cloudshiftdev.gradle.release.util.warningOrError
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "No value in caching")
internal abstract class DefaultPreReleaseChecks : AbstractReleaseTask() {

    @get:Nested internal abstract val preReleaseChecks: Property<ReleaseExtension.PreReleaseChecks>

    @TaskAction
    internal fun action() {
        val preReleaseChecks = preReleaseChecks.get()
        verifyReleaseBranch(preReleaseChecks.releaseBranchPattern.get())
        checkCommitNeeded(
            failOnUntrackedFiles = preReleaseChecks.failOnUntrackedFiles.get(),
            failOnUncommittedFiles = preReleaseChecks.failOnUncommittedFiles.get(),
        )

        checkUpdatedNeeded(
            failOnPushNeeded = preReleaseChecks.failOnPushNeeded.get(),
            failOnPullNeeded = preReleaseChecks.failOnPullNeeded.get()
        )
    }

    private fun verifyReleaseBranch(releaseBranchPatternStr: String) {
        if (releaseBranchPatternStr.isBlank()) return

        val currentBranch = gitRepository().currentBranch()
        logger.info("Currently on branch $currentBranch")
        val pattern = Regex(releaseBranchPatternStr)
        pattern.matchEntire(currentBranch)
            ?: releasePluginError(
                "Currently on branch ${currentBranch}; required branches for release: $releaseBranchPatternStr",
            )
    }

    private fun checkCommitNeeded(failOnUntrackedFiles: Boolean, failOnUncommittedFiles: Boolean) {
        val statusPaths = gitRepository().status()

        val untracked = statusPaths.filter { it.status == GitRepository.PathStatus.Untracked }
        val uncommitted = statusPaths.filter { it.status == GitRepository.PathStatus.Uncommitted }

        when {
            untracked.isNotEmpty() ->
                warningOrError(
                    failOnUntrackedFiles,
                    "You have untracked files:\n${untracked.joinToString("\n")}",
                )
            uncommitted.isNotEmpty() ->
                warningOrError(
                    failOnUncommittedFiles,
                    "You have uncommitted files:\n${uncommitted.joinToString("\n")}",
                )
        }
    }

    private fun checkUpdatedNeeded(failOnPushNeeded: Boolean, failOnPullNeeded: Boolean) {
        val remoteStatus = gitRepository().remoteStatus()
        when {
            remoteStatus.commitsAhead > 0 ->
                warningOrError(
                    failOnPushNeeded,
                    "You have ${remoteStatus.commitsAhead} change(s) to push.",
                )
            remoteStatus.commitsBehind > 0 ->
                warningOrError(
                    failOnPullNeeded,
                    "You have $${remoteStatus.commitsBehind} change(s) to pull.",
                )
        }
    }
}
