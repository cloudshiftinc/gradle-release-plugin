plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0-rc-1" // only on root project
}

gradlePlugin {
    plugins {
        create("cloudshiftRelease") {
            id = "io.cloudshiftdev.release-plugin"
            implementationClass = "cloudshift.gradle.release.ReleasePlugin"
        }
    }
}

nexusPublishing {
    this.repositories {
        sonatype { // only for users registered in Sonatype after 24 Feb 2021
            nexusUrl = uri("https://s01.oss.sonatype.org/service/local/")
            snapshotRepositoryUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
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

java {
    withJavadocJar()
    withSourcesJar()
    consistentResolution {
        useCompileClasspathVersions()
    }
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

publishing.publications.withType<MavenPublication>().configureEach {
/*
name.set("gradle-release-plugin")
                description.set("Gradle release/version management plugin")
                url.set("https://github.com/cloudshiftinc/gradle-release-plugin")

                licenses {
                    license {
                        name.set("Apache License, version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/cloudshiftinc/gradle-release-plugin.git/")
                    developerConnection.set("scm:git:ssh://github.com:cloudshiftinc/gradle-release-plugin.git")
                    url.set("https://github.com/cloudshiftinc/gradle-release-plugin")
                }

                developers {
                    developer {
                        id.set("cloudshiftchris")
                        name.set("Chris Lee")
                        email.set("chris@cloudshiftconsulting.com")
                    }
                }
 */
    pom {
        name = "gradle-release-plugin"
        description = "Gradle release/version management plugin"
        url = "https://github.com/cloudshiftinc/gradle-release-plugin"
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

        when {
            !ci -> false
            eventName == "push" && refName == "main" -> true
            // TODO - handle PR merges
            else -> false
        }
    }

tasks.withType<PublishToMavenRepository>().configureEach {
        onlyIf("Publishing only allowed on CI") {
            publishingPredicate.get()
        }
    }

/*
For a push on main:

Publishing env: GITHUB_REF_TYPE -> branch
Publishing env: GITHUB_REF -> refs/heads/main
Publishing env: GITHUB_BASE_REF ->
Publishing env: GITHUB_EVENT_NAME -> push
Publishing env: GITHUB_WORKFLOW_REF -> cloudshiftinc/awscdk-dsl-kotlin/.github/workflows/build.yaml@refs/heads/main
Publishing env: GITHUB_REF_NAME -> main
Publishing env: GITHUB_HEAD_REF ->

 */

