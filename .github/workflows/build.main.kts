#!/usr/bin/env kotlin
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:0.50.0")

import io.github.typesafegithub.workflows.actions.actions.CheckoutV3
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV3
import io.github.typesafegithub.workflows.actions.gradle.GradleBuildActionV2
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile

val operatingSystems = listOf("ubuntu-latest", "macos-latest", "windows-latest")

workflow(
    name = "Build Gradle Release Plugin",
    on = listOf(Push(), PullRequest()),
    sourceFile = __FILE__.toPath(),
    env =
    linkedMapOf(
        "GRADLE_BUILD_ACTION_CACHE_DEBUG_ENABLED" to "false",
        "ORG_GRADLE_PROJECT_signingKey" to expr("secrets.SIGNING_KEY"),
        "ORG_GRADLE_PROJECT_signingPassword" to expr("secrets.SIGNING_PASSWORD"),
        "ORG_GRADLE_PROJECT_sonatypeUsername" to expr("secrets.SONATYPEUSERNAME"),
        "ORG_GRADLE_PROJECT_sonatypePassword" to expr("secrets.SONATYPEPASSWORD"),
        "GRADLE_PUBLISH_KEY" to expr("secrets.PLUGIN_PORTAL_KEY"),
        "GRADLE_PUBLISH_SECRET" to expr("secrets.PLUGIN_PORTAL_SECRET"),
    ),
) {
    val readTestMatrix = job(
        id = "read-test-matrix", name = "Read Test Matrix", runsOn = RunnerType.UbuntuLatest,
    ) {
        run(command = """echo "::set-output name=compat::${'$'}(jq -c . < .github/compatibility-test-matrix.json)"""")
    }
    val test =
        job(
            id = "test",
            name = """Test on ${expr("matrix.os")} ${expr("matrix.compat")}""",
            runsOn = RunnerType.Custom(expr("matrix.os")),
            needs = listOf(readTestMatrix),
            _customArguments =
            mapOf(
                "strategy" to mapOf(
                    "matrix" to mapOf(
                        "os" to operatingSystems,
                        "compat" to expr {
                            fromJSON("needs.read-test-matrix.outputs.compat")
                        },
                    ),
                ),
            ),
        ) {
            uses(name = "Checkout", action = CheckoutV3())
            uses(
                name = "Set up JDK",
                action =
                SetupJavaV3(
                    javaVersion = "17",
                    distribution = SetupJavaV3.Distribution.Temurin,
                    checkLatest = true,
                ),
            )
            uses(
                name = "Compatibility Test os=${expr("matrix.os")} java-version=${expr("matrix.compat.javaVersion")} gradle-version=${expr("matrix.compat.gradleVersion")}",
                action =
                GradleBuildActionV2(
                    gradleVersion = "wrapper",
                    gradleHomeCacheCleanup = true,
                    gradleHomeCacheIncludes = listOf("jdks", "caches", "notifications"),
                    arguments = """build compatibilityTest -Pcompatibility-test.java-version=${expr("matrix.compat.javaVersion")} -Pcompatibility-test.gradle-version=${expr("matrix.compat.gradleVersion")} --info --scan --stacktrace""",
                ),
            )
        }
    job(
        id = "release",
        needs = listOf(test),
        runsOn = RunnerType.UbuntuLatest,
        `if` = "startsWith(github.event.ref, 'refs/tags/v')",
    ) {
        uses(name = "Checkout", action = CheckoutV3())
        uses(
            name = "Set up JDK",
            action =
            SetupJavaV3(
                javaVersion = "17",
                distribution = SetupJavaV3.Distribution.Temurin,
                checkLatest = true,
            ),
        )
        uses(
            name = "Release",
            action =
            GradleBuildActionV2(
                gradleVersion = "wrapper",
                gradleHomeCacheCleanup = true,
                gradleHomeCacheIncludes = listOf("jdks", "caches", "notifications"),
                arguments =
                "build publishPlugins -Pgradle.publish.key=\$GRADLE_PUBLISH_KEY -Pgradle.publish.secret=\$GRADLE_PUBLISH_SECRET --info --scan --stacktrace --no-configuration-cache",
            ),
        )
    }
}
    .writeToFile()
