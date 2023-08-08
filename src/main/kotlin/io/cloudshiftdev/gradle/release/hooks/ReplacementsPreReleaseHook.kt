@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release.hooks

import io.cloudshiftdev.gradle.release.util.ReleasePluginLogger
import java.io.File
import java.util.*
import javax.inject.Inject
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout

internal abstract class ReplacementsPreReleaseHook
@Inject
constructor(
    private val replacementSpecs: List<ReplacementSpec>,
    private val fs: FileSystemOperations,
    private val layout: ProjectLayout
) : PreReleaseHook {

    private val logger = ReleasePluginLogger.getLogger(ReplacementsPreReleaseHook::class)

    data class ReplacementSpec(
        val includes: List<String>,
        val excludes: List<String>,
        val replacements: Map<String, String>
    )

    override fun validate(hookServices: PreReleaseHook.HookServices) {
        // EMPTY
    }

    override fun execute(
        hookServices: PreReleaseHook.HookServices,
        context: PreReleaseHook.HookContext
    ) {
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
}
