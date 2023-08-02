package io.cloudshiftdef.gradle.release

import io.cloudshiftdef.gradle.release.fixture.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class TemplatePreReleaseHookTests : FunSpec() {
    init {
        test("Template expanding / copying works") {
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
                    template.writeText("Hello from \$version")

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
}
