package io.cloudshiftdev.gradle.release.util

import io.cloudshiftdev.gradle.release.PluginSpec
import org.gradle.api.logging.Logging

internal fun releasePluginError(msg: String): Nothing {
    error("[plugin:io.cloudshiftdev.release] $msg")
}

internal fun warningOrError(error: Boolean, msg: String) {
    when (error) {
        true -> releasePluginError(msg)
        else -> Logging.getLogger(PluginSpec.Id).warn(msg)
    }
}
