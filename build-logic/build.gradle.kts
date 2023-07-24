plugins {
    `kotlin-dsl`
}

dependencies {
    repositories {
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }
}

dependencies {

    // workaround for using version catalog in precompiled script plugins
  //  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}