package io.cloudshiftdev.gradle.release.util

import org.gradle.api.JavaVersion
import org.gradle.util.GradleVersion

internal val gradlePlatformCompatibility = gradlePlatformCompatibility {
    compatible("7.0", "8.4")
    javaCompatibility("7.0", "7.3", 8..16)
    javaCompatibility("7.3", "7.5", 8..17)
    javaCompatibility("7.5", "7.6", 8..18)
    javaCompatibility("7.6", "8.3", 8..19)
    javaCompatibility("8.3", "8.4", 8..20)
}

internal class GradlePlatformCompatibility {
    private val gradleCompatible: MutableList<GradleVersionRange> = mutableListOf()
    private val gradleIncompatible: MutableList<GradleVersionRange> = mutableListOf()
    private val gradleJavaCompatibility: MutableList<GradleJavaCompatibility> = mutableListOf()

    fun isCompatible(gradleVersion: GradleVersion, javaVersion: JavaVersion) {
        sanityCheck()
        val gradleCompatibleVersions = gradleCompatible.joinToString(", ")
        when {
            gradleIncompatible.any { gradleVersion in it } ->
                releasePluginError(
                    "Incompatible with $gradleVersion; compatible versions $gradleCompatibleVersions"
                )
            gradleCompatible.none { gradleVersion in it } ->
                releasePluginError(
                    "Incompatible with $gradleVersion; compatible versions $gradleCompatibleVersions"
                )
            gradleJavaCompatibility.none {
                gradleVersion in it.gradleVersionRange &&
                    javaVersion.majorVersion.toInt() in it.javaVersionRange
            } ->
                releasePluginError(
                    "Incompatible Java version $javaVersion; compatible versions $gradleJavaCompatibility"
                )
        }
    }

    private fun sanityCheck() {
        rangesDoNotOverlap(gradleCompatible)
        rangesDoNotOverlap(gradleJavaCompatibility.map { it.gradleVersionRange })

        check(
            gradleJavaCompatibility
                .map { it.gradleVersionRange }
                .any { javaGradleRange ->
                    gradleCompatible.any { javaGradleRange.startingInclusive in it }
                }
        ) {
            "Java Gradle range must be in supported versions $gradleJavaCompatibility"
        }
    }

    private fun rangesDoNotOverlap(ranges: List<GradleVersionRange>) {
        // check that ranges are not overlapping
        ranges.forEach { outer ->
            ranges.forEach { inner ->
                if (outer != inner) {
                    check(outer.startingInclusive !in inner) {
                        "Ranges must not overlap; $outer / $inner"
                    }
                }
            }
        }
    }

    fun compatible(startingInclusive: String, endingExclusive: String) {
        gradleCompatible.add(GradleVersionRange.from(startingInclusive, endingExclusive))
    }

    fun notCompatible(startingInclusive: String, endingExclusive: String) {
        gradleIncompatible.add(GradleVersionRange.from(startingInclusive, endingExclusive))
    }

    fun javaCompatibility(
        startingInclusive: String,
        endingExclusive: String,
        javaVersionRange: IntRange
    ) {
        gradleJavaCompatibility.add(
            GradleJavaCompatibility(
                GradleVersionRange.from(startingInclusive, endingExclusive),
                javaVersionRange
            )
        )
    }

    private class GradleVersionRange
    private constructor(val startingInclusive: GradleVersion, val endingExclusive: GradleVersion) {
        init {
            check(endingExclusive >= startingInclusive) {
                "Invalid range $startingInclusive ..< $endingExclusive"
            }
        }

        operator fun contains(version: GradleVersion): Boolean {
            return version >= startingInclusive && version < endingExclusive
        }

        companion object {
            fun from(startingInclusive: String, endingExclusive: String): GradleVersionRange {
                return GradleVersionRange(
                    GradleVersion.version(startingInclusive),
                    GradleVersion.version(endingExclusive)
                )
            }
        }

        override fun toString(): String {
            return "[$startingInclusive,$endingExclusive)"
        }
    }

    private data class GradleJavaCompatibility(
        val gradleVersionRange: GradleVersionRange,
        val javaVersionRange: IntRange
    )
}

private fun gradlePlatformCompatibility(
    block: GradlePlatformCompatibility.() -> Unit
): GradlePlatformCompatibility {
    val dsl = GradlePlatformCompatibility()
    dsl.apply(block)
    return dsl
}
