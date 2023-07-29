import com.gradle.publish.PublishTask

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.0"
 //   signing
    id("io.cloudshiftdev.release") version "0.1.18"
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

gradlePlugin {
    website = "https://github.com/cloudshiftinc/gradle-release-plugin"
    vcsUrl = "https://github.com/cloudshiftinc/gradle-release-plugin"
    plugins {
        create("cloudshiftRelease") {
            id = "io.cloudshiftdev.release"
            implementationClass = "io.cloudshiftdev.gradle.release.ReleasePlugin"
            displayName = "Gradle Release Plugin"
            description = project.description
            tags = listOf("release", "version", "release management")
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

val ktfmt by configurations.creating

dependencies { ktfmt("com.facebook:ktfmt:0.44") }

val ktfmtFormat by
    tasks.registering(JavaExec::class) {
        val ktfmtArgs =
            mutableListOf("--kotlinlang-style", layout.projectDirectory.asFile.absolutePath)
      //  if (System.getenv()["CI"] != null) ktfmtArgs.add("--set-exit-if-changed")
        group = "formatting"
        description = "Run ktfmt"
        classpath = ktfmt
        mainClass.set("com.facebook.ktfmt.cli.Main")
        //        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
        args(ktfmtArgs)
    }

tasks.register("precommit") {
    group = "verification"
    dependsOn(tasks.named("check"))
}

//tasks.withType<AbstractArchiveTask>().configureEach {
//    isPreserveFileTimestamps = false
//    isReproducibleFileOrder = true
//}

kotlin {
    explicitApi()
    jvmToolchain { languageVersion = JavaLanguageVersion.of(11) }
}

// val noLocalChanges = tasks.register<NoLocalChanges>("noLocalChanges") {
//    group = LifecycleBasePlugin.VERIFICATION_GROUP
//    onlyIf { System.getenv()["CI"] != null }
//    dependsOn(ktlintFormat)
// }

// tasks.named("check") {
//    dependsOn(noLocalChanges)
// }
//
// release {
//    checks {
//        failOnPushNeeded = false
//        failOnPullNeeded = false
//        failOnStagedFiles = false
//        failOnUnstagedFiles = false
//    }
//    preProcessFiles {
//        templates(sourceDir = "gradle/templates", destinationDir = layout.projectDirectory)
//        replacements {
//            includes("README.MD")
//        }
//    }
// }
/*

// NOTE: _always_ use providers for name, description due to the use of afterEvaluate in the java-gradle-plugin
publishing.publications.withType<MavenPublication>() {

        pluginManager.withPlugin("java-base") {
            versionMapping {
                usage("java-api") { fromResolutionOf("runtimeClasspath") }
                usage("java-runtime") { fromResolutionResult() }
            }
        }

        pom {
            name = provider { project.name }
            description = provider { project.description }
            url = provider { "https://github.com/cloudshiftinc/gradle-release-plugin" }
            licenses {
                license {
                    name = "Apache License, version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }

            scm {
                connection = "scm:git:git://github.com/cloudshiftinc/gradle-release-plugin.git/"
                developerConnection = "scm:git:ssh://github.com:cloudshiftinc/gradle-release-plugin.git"
                url = "https://github.com/cloudshiftinc/gradle-release-plugin"
            }

            developers {
                developer {
                    name = "Chris Lee"
                    email = "chris@cloudshiftconsulting.com"
                }
            }
        }
    }
*/

//signing {
//    val signingKey: String? by project
//    val signingPassword: String? by project
//    useInMemoryPgpKeys(signingKey, signingPassword)
//    sign(publishing.publications)
//    isRequired = !isSnapshot
//}

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
