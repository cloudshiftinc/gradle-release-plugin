package io.cloudshiftdev.gradle.release

import kotlinx.datetime.Clock
import org.gradle.testkit.runner.GradleRunner
import java.io.File


fun gradleTestEnvironment(tempDirFactory: () -> File, block: (TestEnvironmentDsl).() -> Unit): GradleRunner {
    val model = TestEnvironmentDsl()
    model.apply(block)

    val workingDir = tempDirFactory()
    writeGradleProperties(workingDir, model.gradleProperties.properties)
    writeGradleSettings(workingDir, model.gradleSettings)
    writeGradleBuild(workingDir, model.gradleBuild)
    model.workingDirCallback?.let { it(workingDir) }

    val testKitDir = tempDirFactory()
    val runner = model.gradleRunner
    return runner.withProjectDir(workingDir).withTestKitDir(testKitDir)
}

private fun writeGradleBuild(workingDir: File, gradleBuild: GradleBuild) {
    val gradleBuildFile = workingDir.resolve("build.gradle.kts")
    gradleBuildFile.writeText(gradleBuild.script ?: "")
}

private fun writeGradleSettings(workingDir: File, gradleSettings: GradleSettings) {
    val gradleSettingsFile = workingDir.resolve("setting.gradle.kts")
    val lines = mutableListOf<String>()
    lines.add("# test fixture generated ${Clock.System.now()}")
    lines.add("rootProject.name = \"${gradleSettings.projectName}\"")
    gradleSettings.featurePreviews.mapTo(lines) { "enableFeaturePrefix(\"$it\")" }

    val text = lines.joinToString(separator = "\n") + "\n" + (gradleSettings.script ?: "")
    gradleSettingsFile.writeText(text)
}

fun dumpDir(workingDir: File): String {
    return workingDir.walkTopDown()
        .mapNotNull {
            val relative = it.relativeTo(workingDir)
            when {
                it.isDirectory -> null
                else -> relative.name
            }
        }
        .joinToString(separator = "\n") { "    $it" }
}

private fun writeGradleProperties(workingDir: File, properties: Map<String, String>) {
    val gradlePropertiesFile = workingDir.resolve("gradle.properties")
    val propText = properties.map { "${it.key}=${it.value.replace("=", "\\=")}" }
        .sorted()
        .joinToString("\n")

    val comment = "# test fixture generated ${Clock.System.now()}\n"
    gradlePropertiesFile.writeText(comment + propText)
}

class TestEnvironmentDsl {
    val gradleProperties = GradleProperties()
    val gradleSettings = GradleSettings()
    val gradleBuild = GradleBuild()
    val gradleRunner = GradleRunner.create()!!
    var workingDirCallback : ((File) -> Unit)? = null
    fun gradleProperties(block: (GradleProperties).() -> Unit) {
        gradleProperties.apply(block)
    }

    fun gradleSettings(block: (GradleSettings).() -> Unit) {
        gradleSettings.apply(block)
    }

    fun gradleBuild(block: (GradleBuild).() -> Unit) {
        gradleBuild.apply(block)
    }

    fun testKitRunner(block: (GradleRunner).() -> Unit) {
        gradleRunner.apply(block)
    }

    fun withWorkingDir(block : (File) -> Unit) {
        workingDirCallback = block
    }
}

class GradleProperties {
    val properties = mutableMapOf<String, String>()

    init {
        "org.gradle.jvmargs" to "-Dfile.encoding=UTF-8"
        "org.gradle.vfs.watch" to "true"
        "org.gradle.warning.mode" to "all"
        "org.gradle.parallel" to "true"
        "org.gradle.configureondemand" to "false"
        "org.gradle.daemon" to "true"
        "org.gradle.caching" to "true"
        "org.gradle.configuration-cache" to "true"
        "org.gradle.kotlin.dsl.allWarningsAsErrors" to "true"
        "systemProp.file.encoding" to "UTF-8"
        "org.gradle.java.installations.auto-detect" to "false"
    }

    infix fun String.to(other: String) {
        properties[this] = other
    }
}

class GradleSettings {
    var projectName = "test-project"
    val featurePreviews = mutableListOf("TYPESAFE_PROJECT_ACCESSORS")
    var script: String? = null
}

class GradleBuild {

    var script: String? = null

}


fun GradleRunner.releasePluginConfiguration() {
    withPluginClasspath()
    withArguments("release", "--info", "--stacktrace")
}

val ReleasePluginId = "id(\"io.cloudshiftdev.release\")"

fun createGitRepository(dir : File ) {
    val result = ProcessBuilder("git", "init")
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .directory(dir)
        .start()
        .waitFor()

    if(result != 0) error("Unable to create git repository")
}