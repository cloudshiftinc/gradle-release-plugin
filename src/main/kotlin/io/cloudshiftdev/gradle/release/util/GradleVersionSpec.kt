package io.cloudshiftdev.gradle.release.util

import org.gradle.util.GradleVersion

// https://docs.gradle.org/8.3-rc-2/userguide/compatibility.html

internal data class GradleVersionSpec(
    val gradleVersion: GradleVersion,
    val javaVersionRange: IntRange,
    val kotlinLanguageVersion: KotlinLanguageVersion
) : Comparable<GradleVersionSpec> {
    override fun compareTo(other: GradleVersionSpec): Int {
        return this.gradleVersion.compareTo(other.gradleVersion)
    }
}

internal enum class JavaVersion(version: Int) : Comparable<JavaVersion> {
    Version8(8),
    Version11(11),
    Version19(19),
    Version20(20)
}

internal enum class KotlinLanguageVersion(version: String) : Comparable<KotlinLanguageVersion> {
    Version1_3("1.3"),
    Version1_4("1.4"),
    Version1_8("1.8"),
    Version1_9("1.9"),
    Version2_0("2.0")
}
