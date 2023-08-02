@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release.hooks

import com.google.common.hash.Hashing
import io.cloudshiftdev.gradle.release.tasks.PreReleaseHook
import io.cloudshiftdev.gradle.release.util.ReleasePluginLogger
import io.cloudshiftdev.gradle.release.util.releasePluginError
import io.github.z4kn4fein.semver.Version
import org.gradle.api.Transformer
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import java.io.File
import javax.inject.Inject

internal abstract class TemplatesPreReleaseHook
@Inject
constructor(
    private val templateSpec: TemplateSpec,
    private val fs: FileSystemOperations,
) : PreReleaseHook {

    private val logger = ReleasePluginLogger.getLogger(TemplatesPreReleaseHook::class)

    data class TemplateSpec(
        val source: FileTree,
        val destinationDir: DirectoryProperty,
        val preventTampering: Provider<Boolean>,
        val properties: Provider<Map<String, String>>,
        val rename: Provider<Transformer<String?, String>>
    )

    override fun validate() {
        checkTampering(templateSpec)
    }

    override fun execute(context: PreReleaseHook.HookContext) {
        processTemplateSpecs(context.incomingVersion)
    }

    private fun processTemplateSpecs(incomingVersion: Version) {
        fs.copy {
            apply(templateSpec.toCopySpec())
            val properties =
                mapOf("version" to incomingVersion.toString()) + templateSpec.properties.get()
            expand(properties) {
                // expand content literally (don't process escape sequences)
                this.escapeBackslash.set(false)
            }
            eachFile { logger.info("Processing template $path") }
        }
        if (templateSpec.preventTampering.get()) {
            fs.copy {
                apply(templateSpec.toCopySpec())
                eachFile {
                    exclude()
                    // put sha256 of expanded content in .sha256 file alongside template, for
                    // validation on subsequent releases
                    val srcSha256File = file.parentFile.resolve("${file.name}.sha256")
                    val destFile = templateSpec.destinationDir.get().asFile.resolve(path)
                    srcSha256File.writeText(destFile.sha256())
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
