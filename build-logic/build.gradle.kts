plugins { `kotlin-dsl` }

dependencies {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencies {
    implementation(plugin("org.ajoberstar.stutter", "0.7.2"))
    // workaround for using version catalog in precompiled script plugins
    //  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

fun DependencyHandlerScope.plugin(id: String, version: String) = "$id:$id.gradle.plugin:$version"

fun DependencyHandlerScope.plugin(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
