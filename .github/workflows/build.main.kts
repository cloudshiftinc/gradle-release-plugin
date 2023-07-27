#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:0.48.0")

import io.github.typesafegithub.workflows.actions.actions.CheckoutV3
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV3
import io.github.typesafegithub.workflows.actions.gradle.GradleBuildActionV2
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile

workflow(
    name = "Build Gradle Release Plugin",
    on = listOf(Push(), PullRequest()),
    sourceFile = __FILE__.toPath(),
    env = linkedMapOf(
        "GRADLE_BUILD_ACTION_CACHE_DEBUG_ENABLED" to "false",
        "ORG_GRADLE_PROJECT_signingKey" to expr("secrets.SIGNING_KEY"),
        "ORG_GRADLE_PROJECT_signingPassword" to expr("secrets.SIGNING_PASSWORD"),
        "ORG_GRADLE_PROJECT_sonatypeUsername" to expr("secrets.SONATYPEUSERNAME"),
        "ORG_GRADLE_PROJECT_sonatypePassword" to expr("secrets.SONATYPEPASSWORD"),
        "GRADLE_PUBLISH_KEY" to expr("secrets.PLUGIN_PORTAL_KEY"),
        "GRADLE_PUBLISH_SECRET" to expr("secrets.PLUGIN_PORTAL_SECRET"),
    )
) {
    job(id = "build", runsOn = RunnerType.UbuntuLatest) {
        run(name = "Setup Git", command = "git config --global init.defaultBranch main")
        uses(name = "Checkout", action = CheckoutV3())
        uses(
            name = "Set up JDK", action = SetupJavaV3(
                javaVersion = "17",
                distribution = SetupJavaV3.Distribution.Temurin,
                checkLatest = true
            )
        )
        uses(
            name = "Build", action = GradleBuildActionV2(
                gradleVersion = "wrapper",
                gradleHomeCacheCleanup = true,
                gradleHomeCacheIncludes = listOf("jdks", "caches", "notifications"),
                arguments = "build publishPlugins -Pgradle.publish.key=\$GRADLE_PUBLISH_KEY -Pgradle.publish.secret=\$GRADLE_PUBLISH_SECRET --info --scan --stacktrace --no-configuration-cache"
            )
        )
    }
}.writeToFile()

