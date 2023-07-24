package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.GitService.GitOutput
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject


internal abstract class GitServiceImpl
@Inject
constructor(private val execOps: ExecOperations) : BuildService<GitServiceImpl.Params>, GitService {
    private val logger = Logging.getLogger(GitServiceImpl::class.java)

    internal interface Params : BuildServiceParameters {
        val releaseBranchPattern: Property<String>
        val signTag: Property<Boolean>
        val commitOptions: ListProperty<String>
        val pushOptions: ListProperty<String>
    }

    private object GitCommands {
        // Unstaged files: git diff --name-status
        val LocalUnstagedFiles = listOf("diff", "--name-status")

        // Staged files: git diff --cached --name-status
        val LocalStagedFiles = listOf("diff", "--cached", "--name-status")

        // local commits to be pushed: git log @{u}..
        val LocalOutstandingCommits = listOf("log", "@{u}..")

        // remote commits to be pulled: git log ..@{u}
        val RemoteOutstandingCommits = listOf("log", "..@{u}")

        // current branch (see https://git-blame.blogspot.com/2013/06/checking-current-branch-programatically.html)
        val CurrentBranch = listOf("symbolic-ref", "--short", "-q", "HEAD")
    }

    init {
        verifyReleaseBranch()
    }

    private fun verifyReleaseBranch() {
        val cmdResult = git(GitCommands.CurrentBranch)
        if (cmdResult.output.isBlank()) error("Not currently on a branch")
        val patternStr = parameters.releaseBranchPattern.get()
        if (patternStr.isBlank()) return
        val pattern = Regex(patternStr)
        pattern.matchEntire(cmdResult.output) ?: error("Currently on branch ${cmdResult.output}; required branches for release: $patternStr")
    }

    override fun localUnstagedFiles(): GitOutput = git(GitCommands.LocalUnstagedFiles)

    override fun localStagedFiles(): GitOutput = git(GitCommands.LocalStagedFiles)

    override fun localOutstandingCommits(): GitOutput = git(GitCommands.LocalOutstandingCommits)

    override fun remoteOutstandingCommits(): GitOutput = git(GitCommands.RemoteOutstandingCommits)

    override fun addUnstagedFiles() {
        git("add", ".")
    }

    override fun commit(commitMessage: String) {
        val args = mutableListOf("commit", "-m", commitMessage) + parameters.commitOptions.get()
        git(args)
    }

    override fun push() {
        val args = mutableListOf("push", "--porcelain") + parameters.pushOptions.get()
        git("push", "--porcelain")
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
            commandLine(commandLine)
            standardOutput = stdOutput
            errorOutput = stdError
            isIgnoreExitValue = true
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
        if (parameters.signTag.get()) args.add("-s")
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
