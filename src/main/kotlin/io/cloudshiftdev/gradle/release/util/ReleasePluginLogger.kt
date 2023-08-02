package io.cloudshiftdev.gradle.release.util

import kotlin.reflect.KClass
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

internal class ReleasePluginLogger(private val delegate: Logger) : Logger by delegate {
    companion object {
        fun wrapLogger(logger: Logger): Logger {
            return when (logger) {
                is ReleasePluginLogger -> logger
                else -> ReleasePluginLogger(logger)
            }
        }

        fun getLogger(kClass: KClass<*>): Logger {
            return wrapLogger(Logging.getLogger(kClass.java))
        }
    }

    private fun annotatedMessage(msg: String?) = "[${PluginSpec.Id}] $msg"

    override fun trace(p0: String?, p1: Throwable?) {
        delegate.trace(annotatedMessage(p0), p1)
    }

    override fun trace(p0: String?, p1: Any?, p2: Any?) {
        delegate.trace(annotatedMessage(p0), p1, p2)
    }

    override fun trace(p0: String?, vararg p1: Any?) {
        delegate.trace(annotatedMessage(p0), *p1)
    }

    override fun trace(p0: String?) {
        delegate.trace(annotatedMessage(p0))
    }

    override fun trace(p0: String?, p1: Any?) {
        delegate.trace(annotatedMessage(p0), p1)
    }

    override fun debug(p0: String?, p1: Throwable?) {
        delegate.debug(annotatedMessage(p0), p1)
    }

    override fun debug(p0: String?, p1: Any?, p2: Any?) {
        delegate.debug(annotatedMessage(p0), p1, p2)
    }

    override fun debug(message: String?, vararg objects: Any?) {
        delegate.debug(annotatedMessage(message), *objects)
    }

    override fun debug(p0: String?, p1: Any?) {
        delegate.debug(annotatedMessage(p0), p1)
    }

    override fun debug(p0: String?) {
        delegate.debug(annotatedMessage(p0))
    }

    override fun info(p0: String?) {
        delegate.info(annotatedMessage(p0))
    }

    override fun info(message: String?, vararg objects: Any?) {
        delegate.info(annotatedMessage(message), *objects)
    }

    override fun warn(p0: String?, p1: Throwable?) {
        delegate.warn(annotatedMessage(p0), p1)
    }

    override fun warn(p0: String?, vararg p1: Any?) {
        delegate.warn(annotatedMessage(p0), *p1)
    }

    override fun warn(p0: String?, p1: Any?) {
        delegate.warn(annotatedMessage(p0), p1)
    }

    override fun warn(p0: String?) {
        delegate.warn(annotatedMessage(p0))
    }

    override fun warn(p0: String?, p1: Any?, p2: Any?) {
        delegate.warn(annotatedMessage(p0), p1, p2)
    }

    override fun error(p0: String?, p1: Throwable?) {
        delegate.error(annotatedMessage(p0), p1)
    }

    override fun error(p0: String?) {
        delegate.error(annotatedMessage(p0))
    }

    override fun lifecycle(message: String?, throwable: Throwable?) {
        delegate.lifecycle(annotatedMessage(message), throwable)
    }

    override fun lifecycle(message: String?) {
        delegate.lifecycle(annotatedMessage(message))
    }

    override fun lifecycle(message: String?, vararg objects: Any?) {
        delegate.lifecycle(annotatedMessage(message), *objects)
    }

    override fun quiet(message: String?, throwable: Throwable?) {
        delegate.quiet(annotatedMessage(message), throwable)
    }

    override fun quiet(message: String?, vararg objects: Any?) {
        delegate.quiet(annotatedMessage(message), *objects)
    }

    override fun quiet(message: String?) {
        delegate.quiet(annotatedMessage(message))
    }

    override fun log(level: LogLevel?, message: String?) {
        delegate.log(level, annotatedMessage(message))
    }

    override fun log(level: LogLevel?, message: String?, throwable: Throwable?) {
        delegate.log(level, annotatedMessage(message), throwable)
    }

    override fun log(level: LogLevel?, message: String?, vararg objects: Any?) {
        delegate.log(level, annotatedMessage(message), *objects)
    }
}
