package com.jetbrains.edu.learning.courseFormat

import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

fun isBinary(contentType: String): Boolean {
  return contentType.startsWith("image") ||
         contentType.startsWith("audio") ||
         contentType.startsWith("video") ||
         contentType.startsWith("application")
}

/**
 * Note: this method works as expected only for paths on local file system as it uses Path under the hood
 * So it doesn't work properly in tests where in-memory file system is used
 */
fun mimeFileType(path: String): String? {
  return try {
    Files.probeContentType(Paths.get(path))
  }
  catch (e: IOException) {
    LOG.error(e)
    null
  }
}

fun exceedsBase64ContentLimit(base64text: String): Boolean {
  return base64text.toByteArray(StandardCharsets.UTF_16).size > getBinaryFileLimit()
}

fun getBinaryFileLimit(): Int {
  return 1024 * 1024
}

fun getExtension(fileName: String): String {
  val index = fileName.lastIndexOf('.')
  return if (index < 0) "" else fileName.substring(index + 1)
}

private val LOG = Logger.getInstance("fileUtils")
