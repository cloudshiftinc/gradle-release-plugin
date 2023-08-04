@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release.hooks

import com.github.mustachejava.DefaultMustacheFactory
import com.google.common.hash.Hashing
import com.google.common.io.CharStreams
import io.cloudshiftdev.gradle.release.util.ReleasePluginLogger
import io.cloudshiftdev.gradle.release.util.releasePluginError
import io.github.z4kn4fein.semver.Version
import java.io.File
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RelativePath
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
        val actions = mutableListOf(templateCopyAction())

        if (templateSpec.preventTampering.get()) actions.add(VerifyChecksumCopyAction())

        val visitor =
            CopyFileVisitor(
                actions,
                templateSpec.destinationDir.get().asFile,
                templateSpec.pathTransformer.get(),
            )
        templateSpec.source.excludeChecksumPatterns().visit(visitor)
    }

    private fun templateCopyAction(releaseVersion: Version? = null): CopyAction {
        val effectiveVersion =
            when (releaseVersion) {
                null -> Version.parse("99.99.99")
                else -> releaseVersion
            }

        val properties =
            mapOf(
                "releaseVersion" to effectiveVersion.toString(),
                "releaseVersionObj" to effectiveVersion
            ) + templateSpec.properties.get()

        val propTypes = properties.map { "${it.key}: ${it.value} (${it.value.javaClass})" }
        logger.info("Template properties: $propTypes")

        return MustacheTemplateCopyAction(properties, releaseVersion == null)
    }

    override fun execute(context: PreReleaseHook.HookContext) {
        val incomingVersion = context.incomingVersion

        val actions = mutableListOf(templateCopyAction(incomingVersion))

        if (templateSpec.preventTampering.get()) {
            actions.add(WriteChecksumCopyAction())
        }

        val visitor =
            CopyFileVisitor(
                actions,
                templateSpec.destinationDir.get().asFile,
                templateSpec.pathTransformer.get(),
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

    private class MustacheTemplateCopyAction(
        private val context: Map<String, Any>,
        private val dryRun: Boolean
    ) : CopyAction {
        private val mustacheFactory = DefaultMustacheFactory()

        override fun copy(source: File, target: File, relativePath: RelativePath) {
            val template =
                source.bufferedReader().use { mustacheFactory.compile(it, source.toString()) }

            when (dryRun) {
                true -> CharStreams.nullWriter()
                else -> {
                    target.parentFile.mkdirs()
                    target.bufferedWriter()
                }
            }.use { template.execute(it, context) }
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
                        "$target tampered with; please delete and do edits in $source",
                    )
            }
        }
    }
}

private fun File.sha256() = Hashing.sha256().newHasher().putBytes(readBytes()).hash().toString()
