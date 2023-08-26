package io.cloudshiftdev.gradle

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
}

val ktfmt: Configuration by configurations.creating

dependencies { ktfmt("com.facebook:ktfmt:0.44") }

tasks {
    // ensure Kotlin workflow scripts are executed to keep the generated yaml up-to-date
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

    register("precommit") {
        group = "verification"
        dependsOn(
            preprocessWorkflows,
            named("check"),
            named("compatibilityTestJava8Gradle702"),
            named("compatibilityTestJava20Gradle83"),
        )
    }

    withType<Test>().configureEach {
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

    withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    val ktfmtFormat =
        register<JavaExec>("ktfmt") {
            val ktfmtArgs =
                mutableListOf(
                    "--kotlinlang-style",
                    "--do-not-remove-unused-imports",
                    "${layout.projectDirectory.asFile.absolutePath}/src",
                )
            if (System.getenv()["CI"] != null) ktfmtArgs.add("--set-exit-if-changed")
            group = "formatting"
            description = "Run ktfmt"
            classpath = ktfmt
            mainClass.set("com.facebook.ktfmt.cli.Main")
            args(ktfmtArgs)
        }

    named("check") { dependsOn(ktfmtFormat) }
}


