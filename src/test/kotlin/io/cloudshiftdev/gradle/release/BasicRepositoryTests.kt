package io.cloudshiftdev.gradle.release

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

class BasicRepositoryTests : FunSpec() {
    init {
        test("build fails with no git repository") {
            val runner = gradleTestEnvironment({ tempdir() }) {
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

            val output = runner.run()

            // expecting all tasks to have failed as repo does not exist
            withClue(output.output) {
                output.tasks.all { it.outcome == TaskOutcome.FAILED }
                    .shouldBe(true)
                output.output.shouldContain("not a git repository")
            }
        }

        test("build fails with empty git repository") {
            val runner = gradleTestEnvironment({ tempdir() }) {
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

                withWorkingDir {
                    createGitRepository(it)
                }
            }

            val output = runner.run()

            // expecting failure
            withClue(output.output) {
                output.tasks.all { it.outcome == TaskOutcome.FAILED }
                    .shouldBe(true)
                output.output.shouldContain("Git repository is empty; please commit something first")
            }
        }
    }
}


