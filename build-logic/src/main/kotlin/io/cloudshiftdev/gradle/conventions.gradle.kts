package io.cloudshiftdev.gradle

import org.ajoberstar.gradle.stutter.StutterExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("org.ajoberstar.stutter")
}

tasks.register("precommit") {
    group = "verification"
    dependsOn(tasks.named("check"))
}

configure<StutterExtension> {
    sparse.set(true)
    matrices {
        // cover off Java LTS releases (8, 11, 17) and leading edge (Gradle 8.3 / Java 20)
        java(8) { compatibleRange("7.0") }
        java(11) { compatibleRange("7.0") }
        java(17) { compatibleRange("7.3") }
        java(20) { compatibleRange("8.3") }
    }
}

// ensure Kotlin workflow scripts are executed to keep the generated yaml up-to-date
tasks {
    // from
    // https://github.com/Vampire/setup-wsl/blob/master/gradle/build-logic/src/main/kotlin/net/kautler/github_actions.gradle.kts
    val preprocessWorkflows by registering

    file(".github/workflows")
        .listFiles { _, name -> name.endsWith(".main.kts") }!!
        .forEach { workflowScript ->
            val workflowName = workflowScript.name.removeSuffix(".main.kts")
            val camelCasedWorkflowName =
                workflowName
                    .replace("""-\w""".toRegex()) {
                        it.value.substring(1).replaceFirstChar(Char::uppercaseChar)
                    }
                    .replaceFirstChar(Char::uppercaseChar)

            val task =
                register<Exec>("preprocess${camelCasedWorkflowName}Workflow") {
                    inputs.file(workflowScript).withPropertyName("workflowScript")
                    outputs
                        .file(workflowScript.resolveSibling("$workflowName.yaml"))
                        .withPropertyName("workflowFile")
                    commandLine(workflowScript.absolutePath)
                }
            preprocessWorkflows { dependsOn(task) }
        }

    named("precommit") { dependsOn(preprocessWorkflows) }
}

tasks.withType<Test>().configureEach {
    systemProperty("kotest.framework.dump.config", "true")
    systemProperty(
        "org.gradle.testkit.dir",
        layout.projectDirectory.dir("build/gradle-testkit").asFile.toString(),
    )

    useJUnitPlatform()

    reports {
        html.required.set(true)
        junitXml.apply { required.set(true) }
    }

    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    testLogging {
        events =
            setOf(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STANDARD_OUT,
                TestLogEvent.STANDARD_ERROR,
            )
        exceptionFormat = TestExceptionFormat.SHORT
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val ktfmt: Configuration by configurations.creating

dependencies { ktfmt("com.facebook:ktfmt:0.44") }

val ktfmtFormat by
    tasks.registering(JavaExec::class) {
        val ktfmtArgs =
            mutableListOf(
                "--kotlinlang-style",
                "--do-not-remove-unused-imports",
                layout.projectDirectory.asFile.absolutePath,
            )
        if (System.getenv()["CI"] != null) ktfmtArgs.add("--set-exit-if-changed")
        group = "formatting"
        description = "Run ktfmt"
        classpath = ktfmt
        mainClass.set("com.facebook.ktfmt.cli.Main")
        args(ktfmtArgs)
    }

val check = tasks.named("check") { dependsOn(ktfmtFormat) }
