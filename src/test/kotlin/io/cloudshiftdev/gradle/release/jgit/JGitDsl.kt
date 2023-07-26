package io.cloudshiftdev.gradle.release.jgit

import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.InitCommand
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.api.StatusCommand
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.submodule.SubmoduleWalk.IgnoreSubmoduleMode
import org.eclipse.jgit.treewalk.WorkingTreeIterator
import java.io.File

object GitRepository {
    fun init(dsl: (InitCommandDsl).() -> Unit): Git {
        val cmd = Git.init()
        InitCommandDsl(cmd).apply(dsl)
        return cmd.call()
    }

    fun open(dir : File) : Git = Git.open(dir)
}

fun Git.commit(block : (CommitCommandDsl).() -> Unit) {
    val cmd = commit()
    val dsl = CommitCommandDsl(cmd)
    dsl.apply(block)
    cmd.call()
}

class CommitCommandDsl(private val commitCommand : CommitCommand) {
    fun message(message : String) {
        commitCommand.message = message
    }

    fun defaultClean(value : Boolean) {
        commitCommand.setDefaultClean(value)
    }

    fun commentCharacter(char : Char) {
        commitCommand.setCommentCharacter(char)
    }
}

class InitCommandDsl(private val initCommand: InitCommand) {
    fun directory(dir: File) {
        initCommand.setDirectory(dir)
    }

    fun gitDir(dir: File) {
        initCommand.setGitDir(dir)
    }

    fun bare(value: Boolean) {
        initCommand.setBare(value)
    }
}

fun Git.status(dsl :(StatusCommandDsl).() -> Unit): Status {
    val cmd = status()
    StatusCommandDsl(cmd).apply(dsl)
    return cmd.call()
}

class StatusCommandDsl(private val statusCommand : StatusCommand) {
    fun ignoreSubmodules(mode : IgnoreSubmoduleMode) {
        statusCommand.setIgnoreSubmodules(mode)
    }

    fun addPath(path : String) {
        statusCommand.addPath(path)
    }

    fun workingTreeIterator(iterator: WorkingTreeIterator) {
        statusCommand.setWorkingTreeIt(iterator)
    }

    fun progressMonitor(progressMonitor: ProgressMonitor) {
        statusCommand.setProgressMonitor(progressMonitor)
    }
}