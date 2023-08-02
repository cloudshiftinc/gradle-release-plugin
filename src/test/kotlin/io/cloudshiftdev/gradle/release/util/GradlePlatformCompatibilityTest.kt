package io.cloudshiftdev.gradle.release.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.string.shouldContain
import org.gradle.api.JavaVersion
import org.gradle.util.GradleVersion

class GradlePlatformCompatibilityTest :
    FunSpec({
        context("Gradle compatibility check works") {
            withData(
                nameFn = { it.toString() },
                GradleVersion.version("7.0"),
                GradleVersion.version("7.1"),
                GradleVersion.version("7.5"),
                GradleVersion.version("7.6"),
                GradleVersion.version("8.0"),
                GradleVersion.version("8.1"),
                GradleVersion.version("8.2"),
                GradleVersion.version("8.3-rc-2"),
                GradleVersion.version("8.3")
            ) {
                gradlePlatformCompatibility.isCompatible(it, JavaVersion.VERSION_1_8)
            }
        }

        context("Gradle compatibility check fails") {
            withData(
                nameFn = { it.toString() },
                GradleVersion.version("1.0"),
                GradleVersion.version("2.0"),
                GradleVersion.version("3.0"),
                GradleVersion.version("4.0"),
                GradleVersion.version("5.0"),
                GradleVersion.version("6.0"),
                GradleVersion.version("10.0"),
                GradleVersion.version("11.0")
            ) {
                val e =
                    shouldThrow<IllegalStateException> {
                        gradlePlatformCompatibility.isCompatible(it, JavaVersion.VERSION_1_8)
                    }
                e.message.shouldContain("Incompatible with Gradle ")
            }
        }

        context("Java compatibility check works") {
            withData(
                nameFn = { it.toString() },
                JavaVersion.VERSION_1_8,
                JavaVersion.VERSION_1_9,
                JavaVersion.VERSION_1_10,
                JavaVersion.VERSION_11,
                JavaVersion.VERSION_12,
                JavaVersion.VERSION_13,
                JavaVersion.VERSION_14,
                JavaVersion.VERSION_15,
                JavaVersion.VERSION_16,
                JavaVersion.VERSION_17,
                JavaVersion.VERSION_18,
                JavaVersion.VERSION_19,
                JavaVersion.VERSION_20,
            ) {
                gradlePlatformCompatibility.isCompatible(GradleVersion.version("8.3-rc-2"), it)
            }
        }

        context("Java compatibility check fails") {
            withData(
                nameFn = { it.toString() },
                JavaVersion.VERSION_1_1,
                JavaVersion.VERSION_1_2,
                JavaVersion.VERSION_1_3,
                JavaVersion.VERSION_1_4,
                JavaVersion.VERSION_1_5,
                JavaVersion.VERSION_1_6,
                JavaVersion.VERSION_1_7,
                JavaVersion.VERSION_21,
                JavaVersion.VERSION_22,
                JavaVersion.VERSION_23,
                JavaVersion.VERSION_24,
            ) {
                val e =
                    shouldThrow<java.lang.IllegalStateException> {
                        gradlePlatformCompatibility.isCompatible(GradleVersion.version("8.3"), it)
                    }
                e.message.shouldContain("Incompatible Java version ")
            }
        }
    })
