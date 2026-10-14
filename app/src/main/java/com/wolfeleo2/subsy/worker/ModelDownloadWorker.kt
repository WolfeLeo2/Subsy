package com.wolfeleo2.subsy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wolfeleo2.subsy.SubsyApplication
import com.wolfeleo2.subsy.detection.DetectorRelease
import com.wolfeleo2.subsy.detection.ModelFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the detector files into [com.wolfeleo2.subsy.detection.ModelRepository.dir]. Each file streams into
 * `<name>.part`, resuming with an HTTP range after an interruption, and is renamed into place only once its size and
 * SHA-256 match [DetectorRelease]. Network errors retry with WorkManager's backoff.
 *
 * ponytail: no foreground service. WorkManager stops a worker after 10 minutes and reschedules it, and the range
 * resume keeps the bytes, so a slow link finishes over several runs. Add setForeground if that proves too slow.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val dir = (applicationContext as SubsyApplication).modelRepository.dir.apply { mkdirs() }
            var finished = 0L
            for (file in DetectorRelease.files) {
                val target = File(dir, file.name)
                if (target.length() != file.bytes) {
                    try {
                        fetch(file, target) { bytes -> report(finished + bytes) }
                    } catch (_: IOException) {
                        // Includes a checksum mismatch (the partial file is dropped and starts over). A wrong hash
                        // in the app would fail every time, so give up eventually.
                        return@withContext if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                    }
                }
                finished += file.bytes
            }
            Result.success()
        }

    private var lastReported = 0L

    private suspend fun report(doneBytes: Long) {
        if (doneBytes - lastReported < REPORT_EVERY_BYTES) return
        lastReported = doneBytes
        setProgress(workDataOf(PROGRESS_BYTES to doneBytes))
    }

    private suspend fun fetch(
        file: ModelFile,
        target: File,
        onBytes: suspend (Long) -> Unit,
    ) {
        val part = File(target.path + ".part")
        if (part.length() > file.bytes) part.delete()
        if (part.length() < file.bytes) {
            val connection = URL(DetectorRelease.BASE_URL + file.name).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (part.length() > 0) connection.setRequestProperty("Range", "bytes=${part.length()}-")
            try {
                val append =
                    when (connection.responseCode) {
                        HttpURLConnection.HTTP_PARTIAL -> true
                        HttpURLConnection.HTTP_OK -> false // the server ignored the range: start over
                        else -> throw IOException("HTTP ${connection.responseCode} for ${file.name}")
                    }
                connection.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        var written = part.length()
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onBytes(written)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (part.length() != file.bytes || sha256(part) != file.sha256) {
            part.delete()
            throw CorruptFile()
        }
        if (!part.renameTo(target)) throw IOException("Could not move ${file.name} into place")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class CorruptFile : IOException("checksum mismatch")

    companion object {
        const val PROGRESS_BYTES = "bytes"
        private const val MAX_ATTEMPTS = 5
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val REPORT_EVERY_BYTES = 1L shl 20
    }
}
