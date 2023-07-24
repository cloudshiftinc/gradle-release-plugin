plugins {
    `kotlin-dsl`
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0-rc-1" // only on root project
}

gradlePlugin {
    plugins {
        create("cloudshiftRelease") {
            id = "cloudshift.release-plugin"
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


tasks.withType<AbstractArchiveTask>().configureEach {
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
