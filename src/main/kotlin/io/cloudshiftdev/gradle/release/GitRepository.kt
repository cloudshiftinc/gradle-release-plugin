package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.GitRepository.PathStatus.Uncommitted
import io.cloudshiftdev.gradle.release.GitRepository.PathStatus.Untracked
import io.cloudshiftdev.gradle.release.util.releasePluginError
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

internal abstract class GitRepository @Inject constructor(private val execOps: ExecOperations) :
    BuildService<GitRepository.Params> {
    private val logger = Logging.getLogger(GitRepository::class.java)

    internal interface Params : BuildServiceParameters {
        val projectDir: DirectoryProperty
        val gitSettings: Property<ReleaseExtension.GitSettings>
    }

    private val workingDir: File

    init {
        val gitVersion = git("version")
        logger.info("Git version: ${gitVersion.output}")

        workingDir =
            findGitDirectory(parameters.projectDir.get().asFile)
                ?: releasePluginError("Git repository not found")

        verifyGitRepoExists()
    }

    private fun findGitDirectory(dir: File): File? {
        return when {
            dir.resolve(".git").exists() -> dir
            else -> dir.parentFile?.let { findGitDirectory(it) }
        }
    }

    private fun verifyGitRepoExists() {
        git("rev-parse", "--git-dir")
        val hasCommitsOutput = git("rev-list", "-n", "1", "--all")
        if (hasCommitsOutput.output.isBlank())
            releasePluginError("Git repository is empty; please commit something first")
    }

    fun currentBranch(): String {
        // https://git-blame.blogspot.com/2013/06/checking-current-branch-programatically.html)
        val cmdResult = git("symbolic-ref", "--short", "-q", "HEAD")
        return cmdResult.outputLines.firstOrNull()
            ?: releasePluginError("Unable to determine current branch")
    }

    fun status(): List<StatusPath> {
        val statusResult = git("status", "--porcelain")
        return statusResult.outputLines.map {
            val pieces = it.split(" ", limit = 2)
            check(pieces.size == 2) { "Malformed git status line: $it" }
            val status =
                when (pieces[0]) {
                    "??" -> Untracked
                    else -> Uncommitted
                }
            StatusPath(path = pieces[1], status = status, statusIndicator = pieces[0])
        }
    }

    fun remoteStatus(): RemoteStatus {
        git("remote", "update")

        val behind =
            git("rev-list", "--count", "HEAD..@{upstream}", "--").outputLines.first().toInt()
        val ahead = git("rev-list", "--count", "@{upstream}..HEAD").outputLines.first().toInt()

        return RemoteStatus(commitsAhead = ahead, commitsBehind = behind)
    }

    data class RemoteStatus(val commitsAhead: Int, val commitsBehind: Int)

    data class StatusPath(val path: String, val status: PathStatus, val statusIndicator: String) {
        override fun toString(): String {
            return "$statusIndicator $path"
        }
    }

    enum class PathStatus {
        Untracked,
        Uncommitted
    }

    private fun gitSettings() = parameters.gitSettings.get()

    fun stageFiles() {
        git("add", ".")
    }

    fun commit(commitMessage: String) {
        val args = listOf("commit", "-m", commitMessage) + gitSettings().commitOptions.get()
        git(args)
    }

    fun push() {
        val args = listOf("push", "--porcelain") + gitSettings().pushOptions.get()
        git(args)
    }

    private fun git(vararg args: String, block: (GitDsl).() -> Unit = {}) =
        git(args.toList(), block)

    private fun git(args: List<String>, block: (GitDsl).() -> Unit = {}): GitOutput {
        val dsl = GitDsl()
        dsl.args.addAll(args)
        dsl.apply(block)

        val commandLine = mutableListOf("git")
        commandLine.addAll(dsl.args)

        logger.info("Executing $commandLine")

        val stdOutput = ByteArrayOutputStream()
        val stdError = ByteArrayOutputStream()
        val execResult =
            execOps.exec {
                workingDir = this.workingDir
                commandLine(commandLine)

                standardOutput = stdOutput
                errorOutput = stdError
                isIgnoreExitValue = true
                environment =
                    mapOf(
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

    fun tag(tagName: String, tagMessage: String) {
        val args = mutableListOf("tag", "-a", tagName, "-m", tagMessage)
        if (gitSettings().signTag.get()) args.add("-s")
        git(args)
    }

    fun restore(file: File) {
        git("restore", file.absolutePath)
    }

    private class GitDsl {
        val args = mutableListOf<String>()

        fun args(vararg args: String) {
            this.args.addAll(args.toList())
        }
    }
}

private data class GitOutput(val output: String) {
    val outputLines =
        output.split("\n").map { it.replace("\n", "").replace("\r", "") }.dropWhile { it.isBlank() }
}
