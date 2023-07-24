package cloudshift.gradle.release.tasks

public abstract class CheckRemoteOutstandingCommits : AbstractCheckTask() {
    override val message: String = "local outstanding commits"

    override fun executeCheckCommand() = gitService.get().localStagedFiles()
}
