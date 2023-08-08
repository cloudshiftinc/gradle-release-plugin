package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.hooks.ReplacementsPreReleaseHook

public class ReplacementsPreReleaseHookDsl internal constructor() {
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
        replacements[string] = replacement
    }

    /**  */
    public fun replace(replacement: Pair<String, String>) {
        replacements[replacement.first] = replacement.second
    }

    /**  */
    public fun replace(replacements: Map<String, String>) {
        this.replacements.putAll(replacements)
    }

    internal fun build(): ReplacementsPreReleaseHook.ReplacementSpec {
        return ReplacementsPreReleaseHook.ReplacementSpec(
            includes = includes,
            excludes = excludes,
            replacements = replacements
        )
    }
}
