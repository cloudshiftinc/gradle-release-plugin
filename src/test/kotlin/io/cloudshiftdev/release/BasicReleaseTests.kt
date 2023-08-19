package io.cloudshiftdev.release

import io.cloudshiftdev.release.fixture.ScriptLanguage
import io.cloudshiftdev.release.fixture.baseReleasePluginConfiguration
import io.cloudshiftdev.release.fixture.currentVersion
import io.cloudshiftdev.release.fixture.failed
import io.cloudshiftdev.release.fixture.gitLog
import io.cloudshiftdev.release.fixture.gitTags
import io.cloudshiftdev.release.fixture.gradleTestEnvironment
import io.cloudshiftdev.release.fixture.unpushedCommits
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe

class BasicReleaseTests : FunSpec() {
    init {
        context("Release works") {
            withData(nameFn = { it.toString() }, ScriptLanguage.Kotlin, ScriptLanguage.Groovy) {
                val testEnvironment = gradleTestEnvironment {
                    baseReleasePluginConfiguration()
                    gradleBuild { scriptLanguage = it }

                    gradleProperties { "version" to "0.3.0-SNAPSHOT" }
                }

                val buildResult = testEnvironment.runner.run()

                withClue(buildResult.output) {
                    buildResult.failed().shouldBe(false)

                    // check that expected commits are present
                    testEnvironment
                        .gitLog()
                        .reversed()
                        .shouldContainInOrder(
                            "Build setup",
                            "[Release] - release commit: 0.3.0-SNAPSHOT -> 0.3.0",
                            "[Release] - new version commit: 0.3.0 -> 0.3.1-SNAPSHOT",
                        )

                    // check that expected tag is present
                    testEnvironment.gitTags().shouldContainInOrder("v0.3.0")

                    // check that properties file updated
                    testEnvironment.currentVersion().shouldBe("0.3.1-SNAPSHOT")

                    // check that everything was pushed
                    testEnvironment.unpushedCommits().isEmpty()
                }
            }
        }
    }
}
