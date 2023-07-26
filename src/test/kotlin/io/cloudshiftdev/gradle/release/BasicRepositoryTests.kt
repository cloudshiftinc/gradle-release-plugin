package io.cloudshiftdev.gradle.release

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class BasicRepositoryTests : FunSpec() {
    init {
        test("build fails with no git repository") {
            val testEnvironment = gradleTestEnvironment {
                baseReleasePluginConfiguration()
            }

            val buildResult = testEnvironment.runner.run()



            withClue(buildResult.output) {
                // expecting failure as repository doesn't exit
                buildResult.failed().shouldBe(true)
                buildResult.output.shouldContain("Git repository not found")
            }
        }

        test("build fails with empty git repository") {
            val testEnvironment = gradleTestEnvironment {
                baseReleasePluginConfiguration()

                // create empty repository
                gitRepository {}
            }

            val buildResult = testEnvironment.runner.run()

            // expecting failure
            withClue(buildResult.output) {
                // expecting failure as repository is empty
                buildResult.failed().shouldBe(true)

                buildResult.output.shouldContain("Git repository is empty; please commit something first")
            }
        }
    }
}


