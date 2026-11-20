package com.example.musicapp.data.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Crash-safe file writes. The new content is written to a sibling temp file, flushed to disk,
 * and then renamed over the target, so a crash or a concurrent reader never sees a half-written
 * JSON file (which previously made the app discard the whole library or download list).
 */
object AtomicFiles {
    @Throws(IOException::class)
    fun writeText(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            // Some filesystems refuse to rename over an existing file.
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not replace ${target.name}")
            }
        }
    }
}
