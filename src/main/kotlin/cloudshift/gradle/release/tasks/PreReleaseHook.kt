package cloudshift.gradle.release.tasks

import io.github.z4kn4fein.semver.Version
import java.io.File

public interface PreReleaseHook {
    public fun execute(context: HookContext)

    public data class HookContext(val previousVersion: Version, val incomingVersion: Version, val workingDirectory: File)
}
