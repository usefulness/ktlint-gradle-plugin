package io.github.usefulness.support

import java.io.File

internal fun File.getBaselineKey(projectDir: File) = toRelativeString(projectDir).replace(File.separatorChar, '/')
