package io.cloudshiftdef.gradle.release.fixture

import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempdir
import java.io.File
import kotlinx.datetime.Clock
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ConfigConstants.CONFIG_USER_SECTION
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion

internal fun TestConfiguration.gradleTestEnvironment(
    block: (TestEnvironmentDsl).() -> Unit
): TestEnvironment {
    val model = TestEnvironmentDsl()
    model.apply(block)

    val workingDir = tempdir("test-repo")

    val git = createGitRepository(workingDir, tempdir("upstream-repo"))
    autoClose(git)
    val ctx = TestEnvironmentContext(workingDir, git)

    writeGitIgnore(workingDir)
    writeGradleProperties(workingDir, model.gradleProperties.properties)
    writeGradleSettings(workingDir, model.gradleSettings)
    writeGradleBuild(workingDir, model.gradleBuild)

    ctx.stageAndCommit("Build setup")
    ctx.git.push().call()
    model.setupSpecBlock?.invoke(ctx)

    val testKitDir = tempdir("testkit")
    val runner = model.gradleRunner.withProjectDir(workingDir).withTestKitDir(testKitDir)
    return TestEnvironment(runner = runner, git = ctx.git, workingDir = workingDir)
}

private fun writeGitIgnore(workingDir: File) {
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

private fun writeGradleProperties(workingDir: File, properties: Map<String, String>) {
    val gradlePropertiesFile = workingDir.resolve("gradle.properties")
    val propText =
        properties.map { "${it.key}=${it.value.replace("=", "\\=")}" }.sorted().joinToString("\n")

    val comment = "# test fixture generated ${Clock.System.now()}\n"
    gradlePropertiesFile.writeText(comment + propText)
}

internal class TestEnvironmentDsl {
    var setupSpecBlock: ((TestEnvironmentContext) -> Unit)? = null
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

    fun setup(block: (TestEnvironmentContext) -> Unit) {
        setupSpecBlock = block
    }
}

internal fun TestEnvironmentDsl.baseReleasePluginConfiguration() {
    gradleBuild {
        script =
            """
                    plugins {
                      $ReleasePluginId
                    }
                """
                .trimIndent()
    }

    testKitRunner { releasePluginConfiguration() }
}

internal class GradleProperties {
    val properties = mutableMapOf<String, String>()

    init {
        "org.gradle.jvmargs" to "-Dfile.encoding=UTF-8"
        "org.gradle.vfs.watch" to "true"
        "org.gradle.warning.mode" to "all"
        "org.gradle.parallel" to "true"
        "org.gradle.configureondemand" to "false"
        "org.gradle.daemon" to "true"
        "org.gradle.caching" to "true"
        "org.gradle.kotlin.dsl.allWarningsAsErrors" to "true"
        "systemProp.file.encoding" to "UTF-8"

        if (GradleVersion.current() >= GradleVersion.version("7.6")) {
            // toolchains introduced in 7.6
            "org.gradle.java.installations.auto-detect" to "false"
        }

        if (GradleVersion.current() >= GradleVersion.version("8.0")) {
            // configuration cache was incubating before 8.0; set it to 8.0 as it was reasonably
            // mature by then
            "org.gradle.unsafe.configuration-cache" to "true"
        }

        if (GradleVersion.current() >= GradleVersion.version("8.2")) {
            // configuration cache became stable in 8.2
            "org.gradle.configuration-cache" to "true"
        }
    }

    infix fun String.to(other: String) {
        properties[this] = other
    }
}

internal class GradleSettings {
    var projectName = "test-project"
    val featurePreviews = mutableListOf("TYPESAFE_PROJECT_ACCESSORS")
    var script: String? = null
}

internal class GradleBuild {
    var script: String? = null
}

internal fun GradleRunner.releasePluginConfiguration() {
    withPluginClasspath()
//    withDebug(true)
    withGradleVersion(System.getProperty("compat.gradle.version"))
    withArguments("release", "--info", "--stacktrace")
}

private val ReleasePluginId = "id(\"io.cloudshiftdev.release\")"

private fun createGitRepository(dir: File, upstreamRepositoryDir: File): Git {
    val upstreamUrl = upstreamRepositoryDir.toURI().toURL().toString().replace("file:/", "file:///")

    // create an upstream repo
    createUpstreamRepo(upstreamRepositoryDir)
    val git = Git.cloneRepository().setURI(upstreamUrl).setDirectory(dir).call()

    configureRepo(git)
    println(
        "CONFIG: ${
            dir.resolve(".git/config")
                .readText()
        }"
    )

    return git
}

private fun configureRepo(git: Git) {
    val config = git.repository.config
    config.setString(CONFIG_USER_SECTION, null, "name", "Testing")
    config.setString(CONFIG_USER_SECTION, null, "email", "testing@example.com")
    config.save()
}

private fun createUpstreamRepo(upstreamRepositoryDir: File) {
    Git.init().setDirectory(upstreamRepositoryDir).setInitialBranch("main").call().use { git ->
        configureRepo(git)
        // dummy file for initial commit
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

internal fun TestEnvironmentContext.testFiles(block: (TestFilesDsl).() -> Unit) {
    val dsl = TestFilesDsl()
    dsl.apply(block)
    dsl.files.forEach {
        val relativeComponent =
            when {
                it.startsWith("/") || it.startsWith("\\") -> it.drop(1)
                else -> it
            }
        val toCreate = workingDir.resolve(relativeComponent)
        println("Test file: $relativeComponent")
        toCreate.writeText("Test Data")
    }
}

internal fun TestEnvironmentContext.stageAndCommit(message: String) {
    stageFiles()
    println("Sample commit: $message")
    git.commit { message(message) }
}

internal data class TestEnvironmentContext(val workingDir: File, val git: Git) {
    fun stageFiles() {
        git.add().addFilepattern(".").call()
    }

    fun removeRepository() {
        workingDir.resolve(".git").deleteRecursively()
        git.close()
    }

    fun createRepository() {
        Git.init().setDirectory(workingDir).call()
    }
}
