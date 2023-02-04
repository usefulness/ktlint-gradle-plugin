package io.github.usefulness.support

import org.gradle.api.file.DirectoryProperty
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

internal fun List<KtlintErrorResult>.writeTo(target: File) {
    target.parentFile?.mkdirs()
    ObjectOutputStream(target.outputStream().buffered())
        .use { it.writeObject(this) }
}

@Suppress("UNCHECKED_CAST")
private fun File.readErrors() = inputStream().buffered()
    .let(::ObjectInputStream)
    .use { it.readObject() as List<KtlintErrorResult> }

internal fun DirectoryProperty.readKtlintErrors() = asFileTree.asSequence()
    .filter { it.exists() }
    .flatMap { it.readErrors() }
    .sortedBy { it.file }
