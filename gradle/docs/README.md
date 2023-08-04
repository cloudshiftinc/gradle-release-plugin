gradle-release-plugin
====
*Gradle release / version management plugin*

![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.cloudshiftdev.release?style=plastic)
![GitHub](https://img.shields.io/github/license/cloudshiftinc/gradle-release-plugin)

# Introduction

This plugin is designed to automate the tasks of releasing a new version of your application.
It automates the tedious tasks of incrementing the version, performing any related updates to documentation,
tagging the repository, while providing hooks for extensibility.

# Getting Started

Install the plugin:

```kotlin
plugins {
    id("io.cloudshiftdev.release") version "{{releaseVersion}}"
}
```

The plugin is currently compatible with:

| Plugin version | Gradle version                                                                                                                                                                             |  
| --- |--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| < 1.0 | 7.0.x - 7.2.x (Java 8 -16)<br/>7.3.x - 7.5.x (Java 8 - 17)<br/>7.6.x - 8.2.x (Java 8 - 19)<br/>8.3.x (Java 8 - 20) |

*See the [Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html#java) for what Java versions are supported for running Gradle (note: the Java version
used to run Gradle is different than specified Toolchain version, if any).*

# Usage

After installing the plugin simply run `./gradlew release` to execute the release process.

## Configuration

Configuration is via the `release` extension installed by the plugin; the default configuration is below.

It is not necessary to configure the `release` extension if the defaults are satisfactory.

Default configuration:

*when using Gradle Kotlin DSL on Gradle < 8.2 replace assignment `=` with `.set`*

{{=<% %>=}}
```kotlin
release {
    versionProperties {
        // properties file where the version property resides
        propertiesFile = file("gradle.properties")
        
        // property name holding the version
        propertyName = "version"
    }
    
    preReleaseChecks {
        // regex for branches which releases must be done off of.  Set to empty string to ignore.
        releaseBranchPattern = "main"

        // whether to fail the release if there are unstaged files
        failOnUnstagedFiles = true

        // whether to fail the release if there are staged files
        failOnStagedFiles = true

        // whether to fail the release if there are commits to be pushed
        failOnPushNeeded = true

        // whether to fail the release if there are commits to be pulled
        failOnPullNeeded = true
    }
    
    gitSettings {
        // Whether to sign git tags
        signTag = false
        
        // list of options used for git commit, e.g. '-s'
        commitOptions = emptyList()
        
        // list of options used for git push, e.g. '--no-verify'
        pushOptions = emptyList()
    }
    
    // template for release commit message
    releaseCommitMessage = "[Release] - release commit: {{preReleaseVersion}} -> {{releaseVersion}}"
    
    // template for version tag.
    versionTagTemplate = "v{{releaseVersion}}"
    
    // template for version tag commit message
    versionTagCommitMessage = "[Release] - creating tag: {{preReleaseVersion}} -> {{releaseVersion}}"
    
    // whether to increment the version after a release
    incrementAfterRelease = true
    
    // template for new version commit message
    newVersionCommitMessage = "[Release] - new version commit: {{releaseVersion}} -> {{nextPreReleaseVersion}}"

    // which version segment is incremented for the next pre-release version
    releaseBump = "patch"
    
    preReleaseHooks {
        // copy Mustache templates from a master location, processing version references and other properties
        //
        // See the 'Templates' section below for details on which properties are exposed for use in templates
        //
        // other expansion properties can be added via `property` or `properties`
        // note that properties can be providers for lazy evaluation
        //
        // this can be repeated multiple times for different sources/targets etc as required
        processTemplates {
            from(fileTree("gradle/docs") { include("**/*.md") })
            into(project.layout.projectDirectory)
        }
        
        // update files in-place with specified replacements
        replacements {

        }

        // custom pre-release hook
        hook<HookClass>()
    }
}
```
<%={{ }}=%>

# Templates

This plugin uses [Mustache](https://mustache.github.io) templates.

The following template contexts are exposed for use in templates:

| Use Case                       | Template Context                                                                                                                                         |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Pre-release template hook      | `releaseVersion`: version being released                                                                                                                 |
| Release commit / tag templates | `preReleaseVersion`: current version prior to release<br/>`releaseVersion`: version being released                                                       |
| Post release commit template   | `preReleaseVersion`: current version prior to release<br/>`releaseVersion`: version being released<br/>`nextPreReleaseVersion`: next pre-release version |


## Controlling the release version

By default a release will increment the `patch` segment of the version, e.g. `1.2.2-SNAPSHOT` releases `1.2.2` with the next version being `1.2.3-SNAPSHOT`

If you wish to...

### Adjust the next version post-release

`./gradlew release -Prelease.bump=major/minor/patch`

| `release.bump` | Current Version | Release Version | Next Version |
|----------------| --- | --- | --- |
| `major`        | 1.2.2-SNAPSHOT | 1.2.2 | 2.0.0-SNAPSHOT |
| `minor`        | 1.2.2-SNAPSHOT | 1.2.2 | 1.3.0-SNAPSHOT |
| `patch`        | 1.2.2-SNAPSHOT | 1.2.2 | 1.2.3-SNAPSHOT |

`patch` is the default behaviour when `release.bump` is not specified.

`release.bump` can be adjusted persistently in the `release` extension; using the `release.bump` property
overrides any other setting.

### Specify the release version

`./gradlew release -Prelease.version=2.0.0`

| Current Version | `release.version` | Next Version   |
| --- |-------------------|----------------|
| 1.2.2-SNAPSHOT | 2.0.0             | 2.0.1-SNAPSHOT |

This can be combined with `release.bump` to set the next version:

`./gradlew release -Prelease.version=2.0.0 -Prelease.bump=major/minor/patch`

| `release.bump` | Current Version | `release.version` | Next Version   |
|----------------| --- |-------------------|----------------|
| `major`         | 1.2.2-SNAPSHOT   | 2.0.0             | 3.0.0-SNAPSHOT |
| `minor` | 1.2.2-SNAPSHOT   | 2.0.0             | 2.1.0-SNAPSHOT |
| `patch` | 1.2.2-SNAPSHOT   | 2.0.0             | 2.0.1-SNAPSHOT |

For full control the next version can be explicitly set (it _must_ be a SNAPSHOT version):

`./gradlew release -Prelease.version=2.0.0 -Prelease.next-version=2.3.0-SNAPSHOT`

| Current Version | `release.version` | `release.next-version` |
| --- |-------------------|------------------------|
| 1.2.2-SNAPSHOT | 2.0.0             | 2.3.0-SNAPSHOT         |

## Adjusting the current version

The current version can be adjusted by either editing the properties file or running the `setCurrentVersion` task:

`./gradlew setCurrentVersion --version=1.3.0-SNAPSHOT`

The specified version _must_ be a SNAPSHOT version.

| Current Version | `--version`    | Now-current version |
| --- |----------------|---------------------|
| 1.2.2-SNAPSHOT | 1.3.0-SNAPSHOT | 1.3.0-SNAPSHOT     |


# Compatibility Test Matrix

This plugin is tested against the below matrix of Java and Gradle versions, on Linux, MacOS and Windows.

Tests are designed to validate against Java LTS versions and leading-edge Java/Gradle versions.

| Java version | Gradle Version |
| --- | --- |
#foreach( ${record} in ${compatTestMatrix} )
| Java ${record.first} | Gradle ${record.second} |
#end

