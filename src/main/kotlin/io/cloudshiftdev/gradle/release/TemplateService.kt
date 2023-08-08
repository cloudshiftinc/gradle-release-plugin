package io.cloudshiftdev.gradle.release

import com.github.mustachejava.Binding
import com.github.mustachejava.Code
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import com.github.mustachejava.ObjectHandler
import com.github.mustachejava.TemplateContext
import com.github.mustachejava.reflect.GuardedBinding
import com.github.mustachejava.reflect.MissingWrapper
import com.github.mustachejava.util.Wrapper
import io.cloudshiftdev.gradle.release.util.ReleasePluginLogger
import io.cloudshiftdev.gradle.release.util.releasePluginError
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

public abstract class TemplateService internal constructor() :
    BuildService<TemplateService.Params> {

    internal interface Params : BuildServiceParameters {
        val missingTemplateVariableAction: Property<String>
    }

    private val factory: MustacheFactory

    init {
        val action =
            when (parameters.missingTemplateVariableAction.get()) {
                "ignore" -> MissingTemplateVariableAction.Ignore
                "warning" -> MissingTemplateVariableAction.Warning
                "exception" -> MissingTemplateVariableAction.Exception
                else ->
                    releasePluginError(
                        "Unknown missing template variable action: '${parameters.missingTemplateVariableAction.get()}'"
                    )
            }
        factory = DefaultMustacheFactory()

        val oh = VariableCheckingObjectHandlerWrapper(factory.objectHandler, action)
        factory.objectHandler = oh
    }

    public fun evaluateTemplate(
        templateText: Provider<String>,
        templateName: String,
        context: Any
    ): String {
        val template = factory.compile(StringReader(templateText.get()), templateName)
        val sw = StringWriter()
        template.execute(sw, context).flush()
        return sw.toString()
    }

    public fun evaluateTemplate(templateFile: File, dest: Writer, context: Any) {
        val template =
            templateFile.bufferedReader().use { factory.compile(it, templateFile.toString()) }
        template.execute(dest, context)
    }

    private sealed class MissingTemplateVariableAction {
        abstract fun missingTemplateVariable(file: String?, variableName: String?)

        object Exception : MissingTemplateVariableAction() {
            override fun missingTemplateVariable(file: String?, variableName: String?) {
                throw MissingTemplateVariableException(
                    "Unresolved variable in ${file ?: "<unknown>"}: '$variableName'",
                )
            }
        }

        object Warning : MissingTemplateVariableAction() {
            private val logger = ReleasePluginLogger.getLogger(MissingTemplateVariableAction::class)

            override fun missingTemplateVariable(file: String?, variableName: String?) {
                logger.warn("Missing template variable in ${file ?: "<unknown>"}: '$variableName'")
            }
        }

        object Ignore : MissingTemplateVariableAction() {
            override fun missingTemplateVariable(file: String?, variableName: String?) {
                // EMPTY - no-op
            }
        }
    }

    private class VariableCheckingObjectHandlerWrapper(
        private val delegate: ObjectHandler,
        private val action: MissingTemplateVariableAction
    ) : ObjectHandler by delegate {
        override fun createBinding(name: String?, tc: TemplateContext?, code: Code?): Binding {
            return when (val binding = delegate.createBinding(name, tc, code)) {
                is GuardedBinding ->
                    return object : GuardedBinding(this, name, tc, code) {
                        override fun getWrapper(name: String?, scopes: MutableList<Any>?): Wrapper {
                            return when (val wrapper = super.getWrapper(name, scopes)) {
                                is MissingWrapper -> {
                                    action.missingTemplateVariable(tc?.file(), name)
                                    wrapper
                                }
                                else -> wrapper
                            }
                        }
                    }
                else -> binding
            }
        }
    }

    private class MissingTemplateVariableException(message: String) : RuntimeException(message)
}
