package io.cloudshiftdev.gradle.release.util

internal fun releasePluginError(msg: String): Nothing {
    error("[${PluginSpec.Id}] $msg")
}

internal fun warningOrError(error: Boolean, msg: String) {
    when (error) {
        true -> releasePluginError(msg)
        else -> ReleasePluginLogger.getLogger(PluginSpec::class).warn(msg)
    }
}
