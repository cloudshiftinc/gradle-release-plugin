import com.gradle.publish.PublishTask

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.0"
    signing
}

gradlePlugin {
    website = "https://github.com/cloudshiftinc/gradle-release-plugin"
    vcsUrl = "https://github.com/cloudshiftinc/gradle-release-plugin"
    plugins {
        create("cloudshiftRelease") {
            id = "io.cloudshiftdev.release-plugin"
            implementationClass = "io.cloudshiftdev.gradle.release.ReleasePlugin"
            displayName = "Gradle Release Plugin"
            description = project.description
            tags = listOf("release", "version", "release management")
        }
    }
}

tasks {
    // from https://github.com/Vampire/setup-wsl/blob/master/gradle/build-logic/src/main/kotlin/net/kautler/github_actions.gradle.kts
    val preprocessWorkflows by registering

    file(".github/workflows").listFiles { _, name -> name.endsWith(".main.kts") }!!
        .forEach { workflowScript ->
            val workflowName = workflowScript.name.removeSuffix(".main.kts")
            val camelCasedWorkflowName = workflowName.replace("""-\w""".toRegex()) {
                it.value.substring(1)
                    .replaceFirstChar(Char::uppercaseChar)
            }
                .replaceFirstChar(Char::uppercaseChar)

            val task = register<Exec>("preprocess${camelCasedWorkflowName}Workflow") {
                inputs.file(workflowScript)
                    .withPropertyName("workflowScript")
                outputs.file(workflowScript.resolveSibling("$workflowName.yaml"))
                    .withPropertyName("workflowFile")
                commandLine(workflowScript.absolutePath)
            }
            preprocessWorkflows {
                dependsOn(task)
            }
        }

//    named("precommit") {
//        dependsOn(preprocessWorkflows)
//    }
}

val ktlint: Configuration by configurations.creating

dependencies {
    implementation(libs.guava)
    implementation(libs.semver)
    ktlint("com.pinterest:ktlint:0.50.0")
}

val ktlintFormat = tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style and format"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
//    args("--format", "**/src/**/*.kt", "**.kts", "!build-logic/build/**")
    args("--format", "**/src/**/*.kt", "**.kts", "!build-logic/build/**", "!dsl/src/**/*.kt")
}


tasks.withType<AbstractArchiveTask>()
    .configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

kotlin {
    explicitApi()
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}


//val noLocalChanges = tasks.register<NoLocalChanges>("noLocalChanges") {
//    group = LifecycleBasePlugin.VERIFICATION_GROUP
//    onlyIf { System.getenv()["CI"] != null }
//    dependsOn(ktlintFormat)
//}

//tasks.named("check") {
//    dependsOn(noLocalChanges)
//}
//
//release {
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
//}
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


signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}

val publishingPredicate =
    provider {
        val ci = System.getenv()["CI"] == "true"
        System.getenv()
            .filter {
                it.key.startsWith("GITHUB_") &&
                        (it.key.contains("REF") || it.key.contains("EVENT"))
            }
            .forEach {
                println("Publishing env: ${it.key} -> ${it.value}")
            }

        val eventName = System.getenv()["GITHUB_EVENT_NAME"]
        val refName = System.getenv()["GITHUB_REF_NAME"]

        val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")
        when {
            !ci || isSnapshot-> false
            eventName == "push" && refName == "main" -> true
            // TODO - handle PR merges
            else -> false
        }
    }

tasks.withType<PublishToMavenRepository>()
    .configureEach {
        onlyIf("Publishing only allowed on CI for non-snapshot releases") {
            publishingPredicate.get()
        }
    }

tasks.withType<PublishTask>().configureEach {
    onlyIf("Publishing only allowed on CI for non-snapshot releases") {
        publishingPredicate.get()
    }
}


