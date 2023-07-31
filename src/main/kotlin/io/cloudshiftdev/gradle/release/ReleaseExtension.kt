@file:Suppress("KDocUnresolvedReference")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.hooks.PreProcessFilesHook
import io.cloudshiftdev.gradle.release.tasks.PreReleaseHook
import javax.inject.Inject
import kotlin.reflect.KClass
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.newInstance

public abstract class ReleaseExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Template for release commit message.
     *
     * Default: **"`[Release] - release commit:`"**
     */
    public abstract val releaseCommitMessage: Property<String>

    /**
     * Template for version tag. `$version` is replaced with the release version.
     *
     * Default: **"`v$version`"**
     */
    public abstract val versionTagTemplate: Property<String>

    /**
     * Template for version tag commit message.
     *
     * Default: **"`[Release] - creating tag:`"**
     */
    public abstract val versionTagCommitMessage: Property<String>

    /**
     * Whether to increment the version after a release.
     *
     * Default: **true**
     */
    public abstract val incrementAfterRelease: Property<Boolean>

    /**
     * Template for new version commit message.
     *
     * Default: **"`[Release] - new version commit:`"**
     */
    public abstract val newVersionCommitMessage: Property<String>

    internal abstract val preReleaseHooks: ListProperty<PreReleaseHookSpec<*>>

    internal val versionProperties = objects.newInstance<VersionProperties>()

    /** Configure where the version property lives. */
    public fun versionProperties(action: Action<VersionProperties>) {
        action.execute(versionProperties)
    }

    public abstract class VersionProperties {
        /**
         * Properties file where the version property resides.
         *
         * Default: **gradle.properties**
         */
        public abstract val propertiesFile: RegularFileProperty

        /**
         * Property name holding the version.
         *
         * Default: **version**
         */
        public abstract val propertyName: Property<String>
    }

    /**
     * Add a pre-release hook of the specified type, with the (optional) constructor parameters.
     *
     * Instances are created dynamically at runtime with constructor parameters injected, via
     * [ObjectFactory.newInstance].
     */
    public inline fun <reified T : PreReleaseHook> preReleaseHook(vararg parameters: Any) {
        preReleaseHook(T::class, *parameters)
    }

    /**
     * Add a pre-release hook of the specified type, with the (optional) constructor parameters.
     *
     * Instances are created dynamically at runtime with constructor parameters injected, via
     * [ObjectFactory.newInstance].
     */
    public fun <T : PreReleaseHook> preReleaseHook(type: KClass<T>, vararg parameters: Any) {
        preReleaseHooks.add(PreReleaseHookSpec(clazz = type.java, parameters))
    }

    public fun <T : PreReleaseHook> preReleaseHook(type: Class<T>, vararg parameters: Any) {
        preReleaseHooks.add(PreReleaseHookSpec(clazz = type, parameters))
    }

    internal val gitSettings: GitSettings = objects.newInstance<GitSettings>()

    public fun gitSettings(action: Action<GitSettings>) {
        action.execute(gitSettings)
    }

    public abstract class GitSettings {

        /**
         * Whether to sign git tags.
         *
         * Default: **false**
         */
        public abstract val signTag: Property<Boolean>

        /**
         * List of options to use during a commit, e.g. '-s'
         *
         * Default: **<empty>**
         */
        public abstract val commitOptions: ListProperty<String>

        /**
         * List of options to use during a push, e.g. '--no-verify'
         *
         * Default: **<empty>**
         */
        public abstract val pushOptions: ListProperty<String>
    }

    internal val preReleaseChecks: PreReleaseChecks = objects.newInstance<PreReleaseChecks>()

    public fun preReleaseChecks(action: Action<PreReleaseChecks>) {
        action.execute(preReleaseChecks)
    }

    // properties annotated w/ @get:Input as this object is passed into [DefaultPreReleaseChecks]
    public abstract class PreReleaseChecks {
        /**
         * Whether to fail the release if there are untracked (unstaged) files.
         *
         * Default: **true**
         */
        @get:Input public abstract val failOnUntrackedFiles: Property<Boolean>

        /**
         * Whether to fail the release if there are uncommitted (staged) files.
         *
         * Default: **true**
         */
        @get:Input public abstract val failOnUncommittedFiles: Property<Boolean>

        /**
         * Whether to fail the release if there are commits to be pushed.
         *
         * Default: **true**
         */
        @get:Input public abstract val failOnPushNeeded: Property<Boolean>

        /**
         * Whether to fail the release if there are commits to be pulled.
         *
         * Default: **true**
         */
        @get:Input public abstract val failOnPullNeeded: Property<Boolean>

        /**
         * Regex for branches which releases must be done off of. Set to empty string to ignore.
         *
         * Default: **main**
         */
        @get:Input public abstract val releaseBranchPattern: Property<String>
    }
}

internal class PreReleaseHookSpec<T : PreReleaseHook>(
    val clazz: Class<T>,
    val parameters: Array<out Any>
)

/**
 * Adds a pre-release hook for processing files, either via templates or in-line token replacements.
 *
 * Commonly used to insert version number into documentation files, such as README.md.
 */
public fun ReleaseExtension.preProcessFiles(action: Action<PreProcessFilesDsl>) {
    val dsl = PreProcessFilesDsl()
    action.execute(dsl)

    val templateSpecs = dsl.templateSpecs
    val replacementSpecs = dsl.replacementSpecs

    preReleaseHook<PreProcessFilesHook>(templateSpecs, replacementSpecs)
}

public class PreProcessFilesDsl {

    internal val templateSpecs = mutableListOf<PreProcessFilesHook.TemplateSpec>()
    internal val replacementSpecs = mutableListOf<PreProcessFilesHook.ReplacementSpec>()

    /**  */
    public fun templates(sourceDir: Any, destinationDir: Any, action: Action<TemplateDsl>) {
        val dsl = TemplateDsl(sourceDir, destinationDir)
        action.execute(dsl)
        templateSpecs.add(dsl.build())
    }

    /**  */
    public fun replacements(action: Action<ReplacementDsl>) {
        val dsl = ReplacementDsl()
        action.execute(dsl)
        replacementSpecs.add(dsl.build())
    }

    public class TemplateDsl(private val sourceDir: Any, private val destinationDir: Any) {
        private var preventTampering: Boolean = true
        private val includes = mutableListOf<String>()
        private val excludes = mutableListOf<String>()
        private val properties = mutableMapOf<String, String>()

        /**  */
        public fun includes(vararg pattern: String) {
            includes.addAll(pattern)
        }

        /**  */
        public fun excludes(vararg pattern: String) {
            excludes.addAll(pattern)
        }

        /**
         * By default, files generated from templates will fail the build if they have been tampered
         * with.
         *
         * Call `allowTampering` to disable this check.
         */
        public fun allowTampering() {
            preventTampering = false
        }

        internal fun build(): PreProcessFilesHook.TemplateSpec {
            return PreProcessFilesHook.TemplateSpec(
                templateDir = sourceDir,
                destinationDir = destinationDir,
                preventTampering = preventTampering,
                includes = includes,
                excludes = excludes,
                properties = properties
            )
        }
    }

    public class ReplacementDsl {
        private val includes = mutableListOf<String>()
        private val excludes = mutableListOf<String>()
        private val replacements = mutableMapOf<String, String>()

        /**  */
        public fun includes(vararg pattern: String) {
            includes.addAll(pattern)
        }

        /**  */
        public fun excludes(vararg pattern: String) {
            excludes.addAll(pattern)
        }

        /**  */
        public fun replace(string: String, replacement: String) {
            replacements.put(string, replacement)
        }

        /**  */
        public fun replace(replacement: Pair<String, String>) {
            replacements.put(replacement.first, replacement.second)
        }

        /**  */
        public fun replace(replacements: Map<String, String>) {
            this.replacements.putAll(replacements)
        }

        internal fun build(): PreProcessFilesHook.ReplacementSpec {
            return PreProcessFilesHook.ReplacementSpec(
                includes = includes,
                excludes = excludes,
                replacements = replacements
            )
        }
    }
}
