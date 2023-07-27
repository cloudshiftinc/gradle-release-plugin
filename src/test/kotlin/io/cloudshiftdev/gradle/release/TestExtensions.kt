package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.jgit.commit
import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempdir
import kotlinx.datetime.Clock
import org.eclipse.jgit.api.Git
import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Properties


fun TestConfiguration.gradleTestEnvironment(block: (TestEnvironmentDsl).() -> Unit): TestEnvironment {
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
    ctx.git.push()
        .call()
    model.setupSpecBlock?.invoke(ctx)

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
    val upstreamUrl = upstreamRepositoryDir.toURI()
        .toURL()
        .toString()
        .replace("file:/", "file:///")

    // create an upstream repo
    createUpstreamRepo(upstreamRepositoryDir)
    val git = Git.cloneRepository()
        .setURI(upstreamUrl)
        .setDirectory(dir)
        .call()
    println(
        "CONFIG: ${
            dir.resolve(".git/config")
                .readText()
        }"
    )

    return git
}

private fun createUpstreamRepo(upstreamRepositoryDir: File) {
    Git.init()
        .setDirectory(upstreamRepositoryDir)
        .call()
        .use { git ->
            upstreamRepositoryDir.resolve(".gitinit")
            git.add()
                .addFilepattern(".")
                .call()
            git.commit()
                .setMessage("Initial commit")
                .call()
        }
}

fun BuildResult.failed() = output.lines()
    .any { it.startsWith("BUILD FAILED in") }

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

fun TestEnvironmentContext.testFiles(block: (TestFilesDsl).() -> Unit) {
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

fun TestEnvironmentContext.stageAndCommit(message: String) {
    stageFiles()
    println("Sample commit: $message")
    git.commit {
        message(message)
    }
}

data class TestEnvironmentContext(val workingDir: File, val git: Git) {
    fun stageFiles() {
        git.add()
            .addFilepattern(".")
            .call()
    }

    fun removeRepository() {
        workingDir.resolve(".git")
            .deleteRecursively()
        git.close()
    }

    fun createRepository() {
        Git.init()
            .setDirectory(workingDir)
            .call()
    }
}

fun TestEnvironment.gitLog(refSpec : String = "") : List<String> {
    return execGit("log", "--oneline", "--no-color", refSpec).lines.map {
        val pieces = it.split(" ", limit = 2)
        when(pieces.size) {
            2 -> pieces[1]
            else -> error("Invalid git log output: '$it'")
        }
    }
}

fun TestEnvironment.unpushedCommits() : List<String> = gitLog("origin/main..HEAD")

fun TestEnvironment.gitTags() : List<String> {
    return execGit("tag").lines
}

fun TestEnvironment.currentVersion() : String {
    return workingDir.resolve("gradle.properties").bufferedReader().use {
        val props = Properties()

        props.load(it)
        props["version"] as String
    }
}

fun TestEnvironment.execGit(vararg args : String) : ExecOutput {
    val commandLine = mutableListOf("git")
    commandLine.addAll(args.toList())

    val builder = ProcessBuilder(commandLine.filter { it.isNotBlank() })
    builder.directory(workingDir)

    val env = builder.environment()

    env.clear()
    env["GIT_TRACE"] = "1"

    val process = builder.start()
    val br = BufferedReader(InputStreamReader(process.inputStream))
    var line: String?
    val sb = StringBuilder()
    while (br.readLine()
            .also { line = it } != null
    ) sb.append(line + "\n")

    val br2 = BufferedReader(InputStreamReader(process.errorStream))
    var line2: String?
    val sb2 = StringBuilder()
    while (br2.readLine()
            .also { line2 = it } != null
    ) sb2.append(line2 + "\n")

    return when (val exitCode = process.waitFor()) {
        0 -> ExecOutput(sb.toString())
        else -> {
            val standardOutput = sb.toString()
            val stdErr = sb2.toString()
            var msg = "Error executing $commandLine; exit code $exitCode"
            if (standardOutput.isNotBlank()) msg = "$msg\n$standardOutput"
            if (stdErr.isNotBlank()) msg = "$msg\n$stdErr"

            throw GradleException(msg)
        }
    }
}

data class ExecOutput(val output : String) {
    val lines = output.lines().filter { it.isNotBlank() }
}


