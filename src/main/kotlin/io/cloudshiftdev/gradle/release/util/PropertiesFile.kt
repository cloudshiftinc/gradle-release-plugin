package io.cloudshiftdev.gradle.release.util

import java.io.File

internal object PropertiesFile {
    fun loadProperty(propertyName: String, file: File): String? {
        return file.useLines { seq ->
            seq.filter { !it.isNonPropertyLine() }
                .mapNotNull {
                    val pieces = it.split("=", limit = 2)
                    when {
                        pieces.size != 2 -> null
                        pieces[0].trim() != propertyName -> null
                        else -> pieces[1].trim()
                    }
                }
                .firstOrNull()
        }
    }

    fun updateProperty(propertyName: String, propertyValue: String, file: File) {
        val lines =
            file.useLines { seq ->
                seq.map {
                        if (it.isNonPropertyLine()) return@map it
                        val pieces = it.split("=", limit = 2)
                        when {
                            pieces.size != 2 -> it
                            pieces[0].trim() != propertyName -> it
                            else -> "$propertyName = $propertyValue"
                        }
                    }
                    .joinToString("\n")
            }

        file.writeText(lines)
    }

    private fun String.isNonPropertyLine(): Boolean {
        return isBlank() || trim().startsWith("#")
    }
}
