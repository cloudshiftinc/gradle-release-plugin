@file:Suppress("UnstableApiUsage")

package io.cloudshiftdev.gradle.release

import io.cloudshiftdev.gradle.release.fixture.ScriptLanguage
import io.cloudshiftdev.gradle.release.fixture.baseReleasePluginConfiguration
import io.cloudshiftdev.gradle.release.fixture.failed
import io.cloudshiftdev.gradle.release.fixture.gradleTestEnvironment
import io.cloudshiftdev.gradle.release.fixture.stageAndCommit
import io.cloudshiftdev.gradle.release.fixture.unpushedCommits
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class TemplatePreReleaseHookTests : FunSpec() {
    init {
        context("Release w/ template works") {
            withData(nameFn = { it.toString() }, ScriptLanguage.Kotlin, ScriptLanguage.Groovy) {
                val testEnvironment = gradleTestEnvironment {
                    baseReleasePluginConfiguration()
                    gradleBuild {
                        scriptLanguage = it
                        script =
                            """
                    plugins {
                      id("io.cloudshiftdev.release")
                    }
                    
                    release {
                        preReleaseHooks {
                            processTemplates {
                                from(fileTree("templates"))
                                into(project.layout.projectDirectory)
                            }
                        }
                    }
                """
                                .trimIndent()
                    }

                    gradleProperties { "version" to "0.3.0-SNAPSHOT" }

                    setup {
                        val templatesDir = it.workingDir.resolve("templates")
                        templatesDir.mkdirs()
                        val template = templatesDir.resolve("README.md")
                        template.writeText("Hello from {{releaseVersion}}")

                        it.stageAndCommit("Setup templates")
                        it.push()
                    }
                }

                val buildResult = testEnvironment.runner.run()

                withClue(buildResult.output) {
                    buildResult.failed().shouldBe(false)

                    val readMe = testEnvironment.workingDir.resolve("README.md")
                    readMe.shouldExist()
                    readMe.readText().shouldBe("Hello from 0.3.0")

                    val sha256 = testEnvironment.workingDir.resolve("templates/README.md.sha256")
                    sha256.shouldNotBeEmpty()

                    // check that everything was pushed
                    testEnvironment.unpushedCommits().isEmpty()
                }
            }
        }

        test("Advanced mustache template works") {
            val testEnvironment = gradleTestEnvironment {
                baseReleasePluginConfiguration()
                gradleBuild {
                    script =
                        """
                    plugins {
                      id("io.cloudshiftdev.release")
                    }
                    
                    release {
                        preReleaseHooks {
                            processTemplates {
                                from(fileTree("templates"))
                                into(project.layout.projectDirectory)
                                propertyFrom("compatTestMatrix", provider {
                                    // same syntax as stutter.lockfile, irl we'd read from that file
                                    listOf(
                                        "java11=7.0.2,7.6.2,8.0.2,8.2.1,8.3-rc-3",
                                        "java17=7.3.3,7.6.2,8.0.2,8.2.1,8.3-rc-3",
                                        "java20=8.3-rc-3",
                                        "java8=7.0.2,7.6.2,8.0.2,8.2.1,8.3-rc-3"
                                    ).map { 
                                        val pieces = it.split("=")
                                        pieces[0].removePrefix("java").toInt() to pieces[1].split(",").joinToString(", ")
                                    }.sortedBy { it.first }
                                })
                            }
                        }
                    }
                """
                            .trimIndent()
                }

                gradleProperties { "version" to "0.3.0-SNAPSHOT" }

                setup {
                    val templatesDir = it.workingDir.resolve("templates")
                    templatesDir.mkdirs()
                    val template = templatesDir.resolve("README.md")
                    template.writeText(
                        """
                        | Java version | Gradle Version |
                        | --- | --- |
                        {{#compatTestMatrix}}
                        | Java {{first}} | Gradle {{second}} |
                        {{/compatTestMatrix}}
                    """
                            .trimIndent(),
                    )

                    it.stageAndCommit("Setup templates")
                    it.push()
                }
            }

            val buildResult = testEnvironment.runner.run()

            withClue(buildResult.output) {
                buildResult.failed().shouldBe(false)

                val readMe = testEnvironment.workingDir.resolve("README.md")
                readMe.shouldExist()
                readMe
                    .readText()
                    .shouldBe(
                        """
                | Java version | Gradle Version |
                | --- | --- |
                | Java 8 | Gradle 7.0.2, 7.6.2, 8.0.2, 8.2.1, 8.3-rc-3 |
                | Java 11 | Gradle 7.0.2, 7.6.2, 8.0.2, 8.2.1, 8.3-rc-3 |
                | Java 17 | Gradle 7.3.3, 7.6.2, 8.0.2, 8.2.1, 8.3-rc-3 |
                | Java 20 | Gradle 8.3-rc-3 |
                """
                            .trimIndent() + "\n"
                    )

                val sha256 = testEnvironment.workingDir.resolve("templates/README.md.sha256")
                sha256.shouldNotBeEmpty()

                // check that everything was pushed
                testEnvironment.unpushedCommits().isEmpty()
            }
        }
    }
}
