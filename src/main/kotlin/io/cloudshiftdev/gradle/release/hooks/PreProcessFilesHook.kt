@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release.hooks

import com.google.common.hash.Hashing
import io.cloudshiftdev.gradle.release.tasks.PreReleaseHook
import io.cloudshiftdev.gradle.release.util.releasePluginError
import io.github.z4kn4fein.semver.Version
import java.io.File
import java.util.*
import javax.inject.Inject
import org.gradle.api.Transformer
import org.gradle.api.file.*
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider

internal abstract class PreProcessFilesHook
@Inject
constructor(
    private val templateSpecs: List<TemplateSpec>,
    private val replacementSpecs: List<ReplacementSpec>,
    private val fs: FileSystemOperations,
    private val layout: ProjectLayout
) : PreReleaseHook {

    private val logger = Logging.getLogger(PreProcessFilesHook::class.java)

    data class TemplateSpec(
        val source: FileTree,
        val destinationDir: DirectoryProperty,
        val preventTampering: Provider<Boolean>,
        val properties: Provider<Map<String, String>>,
        val rename: Provider<Transformer<String?, String>>
    )

    data class ReplacementSpec(
        val includes: List<String>,
        val excludes: List<String>,
        val replacements: Map<String, String>
    )

    override fun validate() {
        templateSpecs.forEach(::checkTampering)
    }

    override fun execute(context: PreReleaseHook.HookContext) {
        processTemplateSpecs(context.incomingVersion)
        processReplacementSpecs(context.workingDirectory)
    }

    private fun processReplacementSpecs(workingDir: File) {
        replacementSpecs.forEach { replacementSpec ->
            if (replacementSpec.includes.isEmpty()) return@forEach
            val tempDir = workingDir.resolve(UUID.randomUUID().toString())
            fs.copy {
                from(layout.projectDirectory) {
                    include(replacementSpec.includes)
                    exclude(replacementSpec.excludes)
                }
                into(tempDir)

                //                expand(replacementSpec.replacements)
                // TODO - perform replacements during copy
                eachFile { logger.info("Processing replacement in $path") }
            }

            // overwrite original files with processed one
            fs.copy {
                from(tempDir)
                into(layout.projectDirectory)
            }
        }
    }

    private fun processTemplateSpecs(incomingVersion: Version) {
        templateSpecs.forEach { templateSpec ->
            fs.copy {
                apply(templateSpec.toCopySpec())
                eachFile {
                    val properties =
                        mapOf("version" to incomingVersion.toString()) +
                            templateSpec.properties.get()
                    expand(properties) {
                        // expand content literally (don't process escape sequences)
                        this.escapeBackslash.set(false)
                    }
                    if (templateSpec.preventTampering.get()) {
                        // put sha256 of expanded content in .sha256 file alongside template, for
                        // validation on
                        // subsequent releases
                        val srcSha256File = file.parentFile.resolve("${file.name}.sha256")
                        val destFile = templateSpec.destinationDir.get().asFile.resolve(path)
                        srcSha256File.writeText(destFile.sha256())
                    }
                }
            }
        }
    }

    private fun checkTampering(templateSpec: TemplateSpec) {
        if (!templateSpec.preventTampering.get()) return
        fs.copy {
            apply(templateSpec.toCopySpec())
            eachFile {
                val destFile = templateSpec.destinationDir.get().asFile.resolve(path)
                val srcSha256File = file.parentFile.resolve("${file.name}.sha256")
                when {
                    !srcSha256File.exists() -> {}
                    !destFile.exists() -> {}
                    destFile.sha256() != srcSha256File.readText() ->
                        releasePluginError(
                            "$destFile tampered with; please delete and do edits in $file"
                        )
                }
                exclude()
            }
        }
    }

    private fun TemplateSpec.toCopySpec(): CopySpec.() -> Unit {
        val sourceFiles = source.matching { exclude("**/*.sha256") }

        val copySpec: CopySpec.() -> Unit = {
            from(sourceFiles)
            into(destinationDir)
            rename(rename.get())
        }
        return copySpec
    }

    private fun File.sha256() = Hashing.sha256().newHasher().putBytes(readBytes()).hash().toString()
}
