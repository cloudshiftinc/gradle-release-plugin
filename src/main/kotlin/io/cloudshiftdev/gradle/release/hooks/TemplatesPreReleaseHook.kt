@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release.hooks

import com.google.common.hash.Hashing
import io.cloudshiftdev.gradle.release.tasks.PreReleaseHook
import io.cloudshiftdev.gradle.release.util.ReleasePluginLogger
import io.cloudshiftdev.gradle.release.util.releasePluginError
import io.github.z4kn4fein.semver.Version
import java.io.File
import java.util.*
import javax.inject.Inject
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.gradle.api.file.*
import org.gradle.api.provider.Provider

public fun interface PathTransformer {
    public fun transform(relativePath: RelativePath): RelativePath
}

internal abstract class TemplatesPreReleaseHook
@Inject
constructor(private val templateSpec: TemplateSpec) : PreReleaseHook {

    private val logger = ReleasePluginLogger.getLogger(TemplatesPreReleaseHook::class)

    data class TemplateSpec(
        val source: FileTree,
        val destinationDir: DirectoryProperty,
        val preventTampering: Provider<Boolean>,
        val properties: Provider<Map<String, Any>>,
        val pathTransformer: Provider<PathTransformer>
    )

    override fun validate() {
        checkTampering(templateSpec)
    }

    override fun execute(context: PreReleaseHook.HookContext) {
        processTemplateSpecs(context.incomingVersion)
    }

    private fun processTemplateSpecs(incomingVersion: Version) {
        val engine = VelocityEngine()

        val props = Properties()
        props["runtime.references.strict"] = "true"
        props["runtime.references.strict.escape"] = "true"
        engine.init(props)

        val properties =
            mapOf("version" to incomingVersion.toString(), "versionObj" to incomingVersion) +
                templateSpec.properties.get()

        val propTypes = properties.map { "${it.key}: ${it.value} (type: ${it.value.javaClass})" }
        logger.info("Template properties: $propTypes")

        val context = VelocityContext(properties)

        val actions = mutableListOf<CopyAction>(VelocityTemplateCopyAction(engine, context))

        if (templateSpec.preventTampering.get()) {
            actions.add(WriteChecksumCopyAction())
        }

        val visitor =
            CopyFileVisitor(
                actions,
                templateSpec.destinationDir.get().asFile,
                templateSpec.pathTransformer.get()
            )
        templateSpec.source.excludeChecksumPatterns().visit(visitor)
    }

    private fun checkTampering(templateSpec: TemplateSpec) {
        if (!templateSpec.preventTampering.get()) return
        val visitor =
            CopyFileVisitor(
                listOf(VerifyChecksumCopyAction()),
                templateSpec.destinationDir.get().asFile,
                templateSpec.pathTransformer.get()
            )
        templateSpec.source.excludeChecksumPatterns().visit(visitor)
    }

    private fun FileTree.excludeChecksumPatterns(): FileTree {
        return matching { exclude("**/*.sha256") }
    }

    private class CopyFileVisitor(
        private val actions: List<CopyAction>,
        private val destinationDir: File,
        private val pathTransformer: PathTransformer
    ) : EmptyFileVisitor() {
        override fun visitFile(fileDetails: FileVisitDetails) {
            val relativePath = pathTransformer.transform(fileDetails.relativePath)
            val destFile = relativePath.getFile(destinationDir)
            actions.forEach { it.copy(fileDetails.file, destFile, relativePath) }
        }
    }

    private interface CopyAction {
        fun copy(source: File, target: File, relativePath: RelativePath)
    }

    private class VelocityTemplateCopyAction(
        private val engine: VelocityEngine,
        private val context: VelocityContext
    ) : CopyAction {
        override fun copy(source: File, target: File, relativePath: RelativePath) {
            target.parentFile.mkdirs()
            source.bufferedReader().use { reader ->
                target.bufferedWriter().use { writer ->
                    engine.evaluate(context, writer, relativePath.toString(), reader)
                }
            }
        }
    }

    private class WriteChecksumCopyAction : CopyAction {
        override fun copy(source: File, target: File, relativePath: RelativePath) {
            // put sha256 of expanded content in .sha256 file alongside template, for
            // validation on subsequent releases
            val srcSha256File = source.parentFile.resolve("${source.name}.sha256")
            srcSha256File.writeText(target.sha256())
        }
    }

    private class VerifyChecksumCopyAction : CopyAction {
        override fun copy(source: File, target: File, relativePath: RelativePath) {
            val srcSha256File = source.parentFile.resolve("${source.name}.sha256")
            when {
                !srcSha256File.exists() -> {}
                !target.exists() -> {}
                target.sha256() != srcSha256File.readText() ->
                    releasePluginError(
                        "$target tampered with; please delete and do edits in $source"
                    )
            }
        }
    }
}

private fun File.sha256() = Hashing.sha256().newHasher().putBytes(readBytes()).hash().toString()
