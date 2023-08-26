plugins { `kotlin-dsl` }

dependencies {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencies {
    // workaround for using version catalog in precompiled script plugins
    //  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

}

fun DependencyHandlerScope.plugin(id: String, version: String) = "$id:$id.gradle.plugin:$version"

fun DependencyHandlerScope.plugin(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
