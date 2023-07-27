package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.GitRepository.GitOutput
import io.cloudshiftdev.gradle.release.GitRepositoryImpl.GitStatus.Uncommitted
import io.cloudshiftdev.gradle.release.GitRepositoryImpl.GitStatus.Untracked
import io.cloudshiftdev.gradle.release.util.releasePluginError
import io.cloudshiftdev.gradle.release.util.warningOrError
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject


internal abstract class GitRepositoryImpl
@Inject
constructor(private val execOps: ExecOperations) : BuildService<GitRepositoryImpl.Params>, GitRepository {
    private val logger = Logging.getLogger(GitRepositoryImpl::class.java)

    internal interface Params : BuildServiceParameters {
        val projectDir: DirectoryProperty
        val gitSettings: Property<ReleaseExtension.Git>
    }

    private object GitCommands {

        // current branch (see https://git-blame.blogspot.com/2013/06/checking-current-branch-programatically.html)
        val CurrentBranch = listOf("symbolic-ref", "--short", "-q", "HEAD")
    }

    private val workingDir: File

    init {
        val gitVersion = git("version")
        logger.info("Git version: ${gitVersion.output}")

        workingDir = findGitDirectory(parameters.projectDir.get().asFile) ?: releasePluginError("Git repository not found")

        verifyGitRepoExists()
        verifyReleaseBranch()
    }

    private fun findGitDirectory(dir: File): File? {
        return when {
            dir.resolve(".git")
                .exists() -> dir

            else -> dir.parentFile?.let { findGitDirectory(it) }
        }
    }

    private fun verifyGitRepoExists() {
        git("rev-parse", "--git-dir")
        val hasCommitsOutput = git("rev-list", "-n", "1", "--all")
        if (hasCommitsOutput.output.isBlank()) releasePluginError("Git repository is empty; please commit something first")
    }

    private fun verifyReleaseBranch() {
        val patternStr = parameters.gitSettings.get().releaseBranchPattern.get()
        if (patternStr.isBlank()) return

        val cmdResult = git(GitCommands.CurrentBranch)
        val currentBranch = cmdResult.outputLines.firstOrNull() ?: releasePluginError("Unable to determine current branch")
        logger.info("Currently on branch $currentBranch")
        val pattern = Regex(patternStr)
        pattern.matchEntire(currentBranch) ?: releasePluginError("Currently on branch ${currentBranch}; required branches for release: $patternStr")
    }

    override fun checkCommitNeeded() {
        val statusResult = git("status", "--porcelain")
        val status = statusResult.outputLines.groupBy {
            when {
                it.trim()
                    .startsWith("??") -> Untracked

                else -> Uncommitted
            }
        }
        val untracked = status[Untracked] ?: emptyList()
        val uncommitted = status[Uncommitted] ?: emptyList()

        when {
            untracked.isNotEmpty() -> warningOrError(
                gitSettings().failOnUntrackedFiles.get(),
                "You have untracked files:\n${untracked.joinToString("\n")}"
            )

            uncommitted.isNotEmpty() ->
                warningOrError(
                    gitSettings().failOnUncommittedFiles.get(),
                    "You have uncommitted files:\n${uncommitted.joinToString("\n")}"
                )
        }
    }

    private enum class GitStatus {
        Untracked,
        Uncommitted
    }

    private fun gitSettings() = parameters.gitSettings.get()
    override fun checkUpdatedNeeded() {

        git("remote", "update")

        val behind = git("rev-list", "--count", "HEAD..@{upstream}", "--").outputLines.first()
            .toInt()
        val ahead = git("rev-list", "--count", "@{upstream}..HEAD").outputLines.first()
            .toInt()

        when {
            ahead > 0 -> warningOrError(gitSettings().failOnPushNeeded.get(), "You have $ahead change(s) to push.")
            behind > 0 -> warningOrError(gitSettings().failOnPullNeeded.get(), "You have $behind change(s) to pull.")
        }
    }

    override fun addUnstagedFiles() {
        git("add", ".")
    }

    override fun commit(commitMessage: String) {
        val args = listOf("commit", "-m", commitMessage) + gitSettings().commitOptions.get()
        git(args)
    }

    override fun push() {
        val args = listOf("push", "--porcelain") + gitSettings().pushOptions.get()
        git(args)
    }

    private fun git(vararg args: String, block: (GitDsl).() -> Unit = {}) = git(args.toList(), block)

    private fun git(args: List<String>, block: (GitDsl).() -> Unit = {}): GitOutput {
        val dsl = GitDsl()
        dsl.args.addAll(args)
        dsl.apply(block)

        val commandLine = mutableListOf("git")
        commandLine.addAll(dsl.args)

        logger.info("Executing $commandLine")

        val stdOutput = ByteArrayOutputStream()
        val stdError = ByteArrayOutputStream()
        val execResult = execOps.exec {
            workingDir = this.workingDir
            commandLine(commandLine)

            standardOutput = stdOutput
            errorOutput = stdError
            isIgnoreExitValue = true
            environment = mapOf(
                "GIT_CURL_VERBOSE" to "1",
                "GIT_TRACE" to "1",
            )
        }
        logger.info("Exit code: ${execResult.exitValue}")

        return when (execResult.exitValue) {
            0 -> GitOutput(String(stdOutput.toByteArray()))
            else -> {
                val standardOutput = String(stdOutput.toByteArray())
                val errorOutput = String(stdError.toByteArray())
                var msg = "Error executing $commandLine; exit code ${execResult.exitValue}"
                if (standardOutput.isNotBlank()) msg = "$msg\n$standardOutput"
                if (errorOutput.isNotBlank()) msg = "$msg\n$errorOutput"

                throw GradleException(msg)
            }
        }
    }

    override fun tag(tagName: String, tagMessage: String) {
        val args = mutableListOf("tag", "-a", tagName, "-m", tagMessage)
        if (gitSettings().signTag.get()) args.add("-s")
        git(args)
    }

    override fun restore(file: File) {
        git("restore", file.absolutePath)
    }

    private class GitDsl {
        val args = mutableListOf<String>()
        fun args(vararg args: String) {
            this.args.addAll(args.toList())
        }
    }
}
