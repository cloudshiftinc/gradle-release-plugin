package io.cloudshiftdev.gradle

import org.ajoberstar.gradle.stutter.StutterGradleVersions
import org.ajoberstar.gradle.stutter.StutterMatrix
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.NamedDomainObjectContainerScope

fun NamedDomainObjectContainerScope<StutterMatrix>.java(
    version: Int,
    block: StutterGradleVersions.() -> Unit
) {
    create("java$version") {
        javaToolchain { languageVersion.set(JavaLanguageVersion.of(version)) }
        gradleVersions { this.apply(block) }
    }
}
