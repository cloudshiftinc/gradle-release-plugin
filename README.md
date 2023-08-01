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
    id("io.cloudshiftdev.release") version "0.2.0"
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
    releaseCommitMessage = "[Release] - release commit:"
    
    // template for version tag. `$version` is replaced with the release version.
    versionTagTemplate = "v$version"
    
    // template for version tag commit message
    versionTagCommitMessage = "[Release] - creating tag:"
    
    // whether to increment the version after a release
    incrementAfterRelease = true
    
    // template for new version commit message
    newVersionCommitMessage = "[Release] - new version commit:"
    
    // built-in pre-release hook for file processing, useful for updating version references in documentation
    preProcessFiles {
        
        // copy templates from a master location, expanding version references and other properties
        templates("gradle/templates", ".") {
            
            // whether to allow tampering of generated content
            // generated content is protected by creating & checking sha256 fingerprint
            allowTampering()
            
            // patterns to include
            includes("**/*")
            
            // patterns to exclude
            excludes("")
        }
        
        // update files in-place with specified replacements
        replacements {
            
        }
    }
    
    // custom pre-release hook
    preReleaseHook<HookClass>()
}
```

## Test Matrix

This plugin is tested against the below matrix of Java and Gradle versions, on Linux, MacOS and Windows.

Tests are designed to validate against Java LTS versions and leading-edge Java/Gradle versions.

| Java Version | Gradle Version |
| --- | --- |
| Java 8 | Gradle 7.0.2, 7.6.2, 8.0.2, 8.2.1, 8.3-rc-2 |
| Java 11 | Gradle 7.0.2, 7.6.2, 8.0.2, 8.2.1, 8.3-rc-2 |
| Java 17 | Gradle 7.3.3, 7.6.2, 8.0.2, 8.2.1, 8.3-rc-2 |
| Java 20 | Gradle 8.3-rc-2 |

