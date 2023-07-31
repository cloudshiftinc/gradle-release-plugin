import com.gradle.publish.PublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.0"
    signing
    //    id("io.cloudshiftdev.release") version "0.1.20"
    //    alias(libs.plugins.release)
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

gradlePlugin {
    website.set("https://github.com/cloudshiftinc/gradle-release-plugin")
    vcsUrl.set("https://github.com/cloudshiftinc/gradle-release-plugin")
    plugins {
        create("cloudshiftRelease") {
            id = "io.cloudshiftdev.release"
            implementationClass = "io.cloudshiftdev.gradle.release.ReleasePlugin"
            displayName = "Gradle Release Plugin"
            description = project.description
            tags.set(listOf("release", "version", "release management"))
        }
    }
}

tasks {
    // from
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

    //    named("precommit") {
    //        dependsOn(preprocessWorkflows)
    //    }
}

tasks.withType<ValidatePlugins>().configureEach {
    enableStricterValidation.set(true)
    failOnWarning.set(true)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

tasks.named<KotlinCompile>("compileKotlin") {
    kotlinOptions {
        // language version must match the earliest supported Gradle version
        apiVersion = "1.4"
        languageVersion = "1.4"
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.semver)

    // testing libraries
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.jupiter.engine)

    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.framework.datatest)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)

    testImplementation(libs.jetbrains.kotlinx.datetime)
    testImplementation(libs.jgit)
}

tasks.named<Test>("test") { useJUnitPlatform() }

val ktfmt: Configuration by configurations.creating

dependencies { ktfmt("com.facebook:ktfmt:0.44") }

val ktfmtFormat by
    tasks.registering(JavaExec::class) {
        val ktfmtArgs =
            mutableListOf(
                "--kotlinlang-style",
                "--do-not-remove-unused-imports",
                layout.projectDirectory.asFile.absolutePath
            )
        if (System.getenv()["CI"] != null) ktfmtArgs.add("--set-exit-if-changed")
        group = "formatting"
        description = "Run ktfmt"
        classpath = ktfmt
        mainClass.set("com.facebook.ktfmt.cli.Main")
        //        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
        args(ktfmtArgs)
    }

val check = tasks.named("check") { dependsOn(ktfmtFormat) }

tasks.register("precommit") {
    group = "verification"
    dependsOn(check)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

kotlin {
    explicitApi()
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(11)) }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
    isRequired = !isSnapshot
}

val publishingPredicate = provider {
    val ci = System.getenv()["CI"] == "true"
    System.getenv()
        .filter {
            it.key.startsWith("GITHUB_") && (it.key.contains("REF") || it.key.contains("EVENT"))
        }
        .forEach { println("Publishing env: ${it.key} -> ${it.value}") }

    val eventName = System.getenv()["GITHUB_EVENT_NAME"]
    val refName = System.getenv()["GITHUB_REF_NAME"]

    when {
        !ci || isSnapshot -> false
        eventName == "push" && refName == "main" -> true
        // TODO - handle PR merges
        else -> false
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf("Publishing only allowed on CI for non-snapshot releases") { publishingPredicate.get() }
}

tasks.withType<PublishTask>().configureEach {
    onlyIf("Publishing only allowed on CI for non-snapshot releases") { publishingPredicate.get() }
}
