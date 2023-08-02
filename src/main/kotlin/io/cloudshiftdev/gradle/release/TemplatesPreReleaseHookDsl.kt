@file:Suppress("LeakingThis")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.hooks.PathTransformer
import io.cloudshiftdev.gradle.release.hooks.TemplatesPreReleaseHook
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

@DslMarker public annotation class TemplatesPreReleaseHookDslMarker

@TemplatesPreReleaseHookDslMarker
public abstract class TemplatesPreReleaseHookDsl {
    internal abstract var from: ConfigurableFileTree
    internal abstract val destinationDir: DirectoryProperty
    internal abstract val properties: MapProperty<String, Any>
    internal abstract val preventTampering: Property<Boolean>
    internal abstract val pathTransformer: Property<PathTransformer>

    init {
        preventTampering.convention(true)
        pathTransformer.convention(PathTransformer { it })
    }

    public fun from(fileTree: ConfigurableFileTree) {
        this.from = fileTree
    }

    public fun into(directory: Directory) {
        destinationDir.set(directory)
    }

    public fun pathTransformer(transformer: PathTransformer) {
        this.pathTransformer.set(transformer)
    }

    public fun property(key: String, value: Any) {
        properties.put(key, value)
    }

    public fun properties(map: Map<String, Any>) {
        properties.putAll(map)
    }

    /**
     * By default, files generated from templates will fail the build if they have been tampered
     * with.
     *
     * Call `allowTampering` to disable this check.
     */
    public fun allowTampering() {
        preventTampering.set(false)
    }

    internal fun build(): TemplatesPreReleaseHook.TemplateSpec {
        return TemplatesPreReleaseHook.TemplateSpec(
            source = from,
            destinationDir = destinationDir,
            preventTampering = preventTampering,
            properties = properties,
            pathTransformer = pathTransformer
        )
    }
}
