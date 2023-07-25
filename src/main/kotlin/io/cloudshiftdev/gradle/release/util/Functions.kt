package io.cloudshiftdev.gradle.release.util


internal fun releasePluginError(msg: String) {
    error("[plugin:io.cloudshiftdev.release] $msg")
}