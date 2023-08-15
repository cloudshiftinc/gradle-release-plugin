package io.cloudshiftdev.gradle

import org.ajoberstar.gradle.stutter.StutterExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("org.ajoberstar.stutter")
}

configure<StutterExtension> {
    sparse.set(true)
    matrices {
        // cover off Java LTS releases (8, 11, 17) and leading edge (Gradle 8.3 / Java 20)
        java(8) { compatibleRange("7.0", "8.0") }
        java(11) { compatible("7.0.2", "7.6.2") }
        java(17) { compatibleRange("7.3") }
        java(20) { compatibleRange("8.3") }
    }
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
            named("stutterWriteLocks"),
            named("compatTestJava8Gradle7.0.2"),
            named("compatTestJava20Gradle8.3-rc-4"),
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


