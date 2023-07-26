package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.jgit.commit
import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempdir
import kotlinx.datetime.Clock
import org.eclipse.jgit.api.Git
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File


fun TestConfiguration.gradleTestEnvironment(block: (TestEnvironmentDsl).() -> Unit): TestEnvironment {
    val model = TestEnvironmentDsl()
    model.apply(block)

    val workingDir = tempdir("test-repo")

    val git = createGitRepository(workingDir, tempdir("upstream-repo"))
    autoClose(git)
    val ctx = GitContext(workingDir, git)

    writeGitIgnore(workingDir)
    writeGradleProperties(workingDir, model.gradleProperties.properties)
    writeGradleSettings(workingDir, model.gradleSettings)
    writeGradleBuild(workingDir, model.gradleBuild)

    ctx.stageAndCommit("Build setup")
    ctx.git.push().call()
    model.gitSpecBlock?.invoke(ctx)

    val testKitDir = tempdir("testkit")
    val runner = model.gradleRunner.withProjectDir(workingDir)
        .withTestKitDir(testKitDir)
    return TestEnvironment(runner = runner, git = ctx.git, workingDir = workingDir)
}


fun writeGitIgnore(workingDir: File) {
    val gitIgnoreFile = workingDir.resolve(".gitignore")
    val toIgnore = listOf(".gradle/", "build/")
    gitIgnoreFile.writeText(toIgnore.joinToString("\n"))
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

data class TestEnvironment(val runner: GradleRunner, val git: Git, val workingDir: File)

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
    var gitSpecBlock: ((GitContext) -> Unit)? = null
    val gradleProperties = GradleProperties()
    val gradleSettings = GradleSettings()
    val gradleBuild = GradleBuild()
    val gradleRunner = GradleRunner.create()!!

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

    fun gitRepository(block: (GitContext) -> Unit) {
        gitSpecBlock = block
    }
}


fun TestEnvironmentDsl.baseReleasePluginConfiguration() {
    gradleBuild {
        script = """
                    plugins {
                      $ReleasePluginId
                    }
                """.trimIndent()
    }

    testKitRunner {
        releasePluginConfiguration()
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
    withDebug(true)
    withArguments("release", "--info", "--stacktrace")
}

val ReleasePluginId = "id(\"${PluginSpec.Id}\")"

fun createGitRepository(dir: File, upstreamRepositoryDir: File): Git {
    val upstreamUrl = upstreamRepositoryDir.toURI().toURL().toString().replace("file:/", "file:///")

    // create an upstream repo
    createUpstreamRepo(upstreamRepositoryDir)
    val git = Git.cloneRepository()
        .setURI(upstreamUrl)
        .setDirectory(dir)
        .call()
    println("CONFIG: ${dir.resolve(".git/config").readText()}")

    return git
}

private fun createUpstreamRepo(upstreamRepositoryDir: File) {
    Git.init().setDirectory(upstreamRepositoryDir).call().use { git ->
        upstreamRepositoryDir.resolve(".gitinit")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Initial commit").call()
    }
}

fun BuildResult.failed() = output.lines().any { it.startsWith("BUILD FAILED in") }

class TestFilesDsl {
    val files = mutableListOf<String>()
    fun files(vararg files: String) {
        this.files.addAll(files.toList())
    }
}

class SampleCommitDsl {
    val files = mutableListOf<String>()
    fun files(vararg files: String) {
        this.files.addAll(files.toList())
    }
}

fun GitContext.testFiles(block: (TestFilesDsl).() -> Unit) {
    val dsl = TestFilesDsl()
    dsl.apply(block)
    dsl.files.forEach {
        val relativeComponent = when {
            it.startsWith("/") || it.startsWith("\\") -> it.drop(1)
            else -> it
        }
        val toCreate = workingDir.resolve(relativeComponent)
        println("Test file: $relativeComponent")
        toCreate.writeText("Test Data")
    }
}

fun GitContext.stageAndCommit(message: String) {
    stageFiles()
    println("Sample commit: $message")
    git.commit {
        message(message)
    }
}

data class GitContext(val workingDir: File, val git: Git) {
    fun stageFiles() {
        git.add().addFilepattern(".").call()
    }
}


