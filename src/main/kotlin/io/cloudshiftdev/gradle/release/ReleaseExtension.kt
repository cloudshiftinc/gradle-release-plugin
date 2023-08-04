@file:Suppress("KDocUnresolvedReference", "LeakingThis")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.hooks.PreReleaseHook
import io.cloudshiftdev.gradle.release.hooks.ReplacementsPreReleaseHook
import io.cloudshiftdev.gradle.release.hooks.TemplatesPreReleaseHook
import javax.inject.Inject
import kotlin.reflect.KClass
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.newInstance

public abstract class ReleaseExtension @Inject constructor(internal val objects: ObjectFactory) {

    /**
     * Whether to do a dry-run - everything except commits / pushes.
     *
     * All checks are performed and modified files are left in-place for inspection.
     *
     * Can be set with the Gradle property `release.dry-run`
     *
     * Default: **false**
     */
    public abstract val dryRun: Property<Boolean>

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

    /**
     * Version segment to bump on release.
     *
     * Valid values are `major`, `minor`, or `patch`
     *
     * Default: **`patch`**
     */
    public abstract val releaseBump: Property<String>

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

    internal val preReleaseHooks = objects.newInstance<PreReleaseHooks>()

    public abstract class PreReleaseHooks @Inject constructor(internal val objects: ObjectFactory) {
        internal abstract val hooks: ListProperty<PreReleaseHook>

        /**
         * Add a pre-release hook of the specified type, with the (optional) constructor parameters.
         *
         * Instances are created with constructor parameters injected, via
         * [ObjectFactory.newInstance].
         */
        public fun <T : PreReleaseHook> hook(type: KClass<T>, vararg parameters: Any) {
            hook(type.java, *parameters)
        }

        public fun <T : PreReleaseHook> hook(type: Class<T>, vararg parameters: Any) {
            val hook = objects.newInstance(type, *parameters)
            hooks.add(hook)
        }

        /**
         * Adds a pre-release hook for processing templates.
         *
         * Commonly used to insert version number into documentation files, such as README.md.
         */
        public fun processTemplates(action: Action<TemplatesPreReleaseHookDsl>) {
            val dsl = objects.newInstance<TemplatesPreReleaseHookDsl>()
            action.execute(dsl)

            hook(TemplatesPreReleaseHook::class, dsl.build())
        }

        public fun processReplacements(action: Action<ReplacementsPreReleaseHookDsl>) {
            val dsl = ReplacementsPreReleaseHookDsl()
            action.execute(dsl)
            hook(ReplacementsPreReleaseHook::class, dsl.build())
        }
    }

    public fun preReleaseHooks(action: Action<PreReleaseHooks>) {
        action.execute(preReleaseHooks)
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
