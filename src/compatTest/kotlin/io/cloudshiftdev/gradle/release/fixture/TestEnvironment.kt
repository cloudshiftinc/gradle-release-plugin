package io.cloudshiftdev.gradle.release.fixture

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*
import org.gradle.api.GradleException
import org.gradle.testkit.runner.GradleRunner

internal data class TestEnvironment(val runner: GradleRunner, val workingDir: File)

internal fun TestEnvironment.gitLog(refSpec: String = ""): List<String> {
    return execGit("log", "--oneline", "--no-color", refSpec).lines.map {
        val pieces = it.split(" ", limit = 2)
        when (pieces.size) {
            2 -> pieces[1]
            else -> error("Invalid git log output: '$it'")
        }
    }
}

internal fun TestEnvironment.unpushedCommits(): List<String> = gitLog("origin/main..HEAD")

internal fun TestEnvironment.gitTags(): List<String> {
    return execGit("tag").lines
}

internal fun TestEnvironment.currentVersion(): String {
    return workingDir.resolve("gradle.properties").bufferedReader().use {
        val props = Properties()

        props.load(it)
        props["version"] as String
    }
}

private fun TestEnvironment.execGit(vararg args: String): ExecOutput {
    return execGit(workingDir, *args)
}

internal fun execGit(workingDir: File, vararg args: String): ExecOutput {
    val commandLine = mutableListOf("git")
    commandLine.addAll(args.toList())

    val builder = ProcessBuilder(commandLine.filter { it.isNotBlank() })
    builder.directory(workingDir)

    val env = builder.environment()

    env.clear()
    env["GIT_TRACE"] = "1"

    println("[test fixtures] executing '${commandLine.joinToString(" ")}' in ${workingDir.name}")
    val process = builder.start()
    val br = BufferedReader(InputStreamReader(process.inputStream))
    var line: String?
    val sb = StringBuilder()
    while (br.readLine().also { line = it } != null) sb.append(line + "\n")

    val br2 = BufferedReader(InputStreamReader(process.errorStream))
    var line2: String?
    val sb2 = StringBuilder()
    while (br2.readLine().also { line2 = it } != null) sb2.append(line2 + "\n")

    return when (val exitCode = process.waitFor()) {
        0 -> ExecOutput(sb.toString())
        else -> {
            val standardOutput = sb.toString()
            val stdErr = sb2.toString()
            var msg = "Error executing $commandLine; exit code $exitCode"
            if (standardOutput.isNotBlank()) msg = "$msg\n$standardOutput"
            if (stdErr.isNotBlank()) msg = "$msg\n$stdErr"

            throw GradleException(msg)
        }
    }
}

internal data class ExecOutput(val output: String) {
    val lines = output.lines().filter { it.isNotBlank() }
}
