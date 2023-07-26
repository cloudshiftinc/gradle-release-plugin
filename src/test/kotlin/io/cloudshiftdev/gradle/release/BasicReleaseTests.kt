package io.cloudshiftdev.gradle.release

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class BasicReleaseTests : FunSpec() {
    init {
        test("Release works") {
            val testEnvironment = gradleTestEnvironment {
                baseReleasePluginConfiguration()

                gradleProperties {
                    "version" to "0.3.0"
                }

                gitRepository { ctx ->
//                    ctx.testFiles {
//                        files("abc.txt", "foo.txt")
//                    }
//                    ctx.stageAndCommit("Sample commit")
                }
            }

            val buildResult = testEnvironment.runner.run()

            println(buildResult.output)
            withClue(buildResult.output) {
                buildResult.failed()
                    .shouldBe(false)
            }
        }

        test("abc") {
            val commandWithArgs = listOf("/opt/homebrew/bin/git", "rev-list", "--count", "HEAD..@{upstream}", "--")
            val builder = ProcessBuilder(commandWithArgs)
            builder.directory(File("/Users/chrislee/tmp/repo1"))

            val env = builder.environment()
            env.clear()
            env["GIT_TRACE"] = "1"

            val process = builder.start()
            val br = BufferedReader(InputStreamReader(process.errorStream))
            var line: String?
            val sb = StringBuilder()
            while (br.readLine().also { line = it } != null) sb.append(line)


            val exitCode = process.waitFor()

            println("Executed ${commandWithArgs}; exit code = $exitCode")
            println("stderr>>> ${sb.toString()}")
            exitCode.shouldBe(0)
        }

    }
}


