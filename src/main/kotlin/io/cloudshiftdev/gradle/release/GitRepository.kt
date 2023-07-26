package io.cloudshiftdev.gradle.release

import java.io.File

internal interface GitRepository {
    fun checkCommitNeeded()
    fun checkUpdatedNeeded()
    fun addUnstagedFiles()
    fun commit(commitMessage: String)
    fun push()
    fun tag(tagName: String, tagMessage: String)
    fun restore(file: File)

    data class GitOutput(val output: String) {
        val outputLines = output.split("\n")
            .map {
                it.replace("\n", "")
                    .replace("\r", "")
            }
            .dropWhile { it.isBlank() }
    }
}