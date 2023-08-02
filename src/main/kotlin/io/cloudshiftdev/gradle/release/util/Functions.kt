package io.cloudshiftdev.gradle.release.util

import org.gradle.api.logging.Logging

internal fun releasePluginError(msg: String): Nothing {
    error("[${PluginSpec.Id}] $msg")
}

internal fun warningOrError(error: Boolean, msg: String) {
    when (error) {
        true -> releasePluginError(msg)
        else -> Logging.getLogger(PluginSpec.Id).warn("[${PluginSpec.Id}] $msg")
    }
}
