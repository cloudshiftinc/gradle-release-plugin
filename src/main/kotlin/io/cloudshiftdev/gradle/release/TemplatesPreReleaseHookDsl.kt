@file:Suppress("LeakingThis")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.hooks.TemplatesPreReleaseHook
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

@DslMarker public annotation class TemplatesPreReleaseHookDslMarker

@TemplatesPreReleaseHookDslMarker
public abstract class TemplatesPreReleaseHookDsl {
    internal abstract var from: ConfigurableFileTree
    internal abstract val destinationDir: DirectoryProperty
    internal abstract val properties: MapProperty<String, String>
    internal abstract val preventTampering: Property<Boolean>
    internal abstract val rename: Property<Transformer<String?, String>>

    init {
        preventTampering.convention(true)
        rename.convention(Transformer { it })
    }

    public fun from(fileTree: ConfigurableFileTree) {
        this.from = fileTree
    }

    public fun into(directory: Directory) {
        destinationDir.set(directory)
    }

    public fun rename(transformer: Transformer<String?, String>) {
        this.rename.set(transformer)
    }

    public fun property(key: String, value: String) {
        properties.put(key, value)
    }

    public fun property(key: String, value: Provider<String>) {
        properties.put(key, value)
    }

    public fun properties(map: Map<String, String>) {
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
            rename = rename
        )
    }
}
