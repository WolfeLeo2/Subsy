package com.wolfeleo2.subsy.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wolfeleo2.subsy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

/** A GitHub release newer than the installed app, with the APK built for this phone's CPU. */
data class AppUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    /** GitHub's SHA-256 of the asset, when it reports one. */
    val sha256: String?,
)

/**
 * Checks the app's GitHub releases for a newer version, downloads the matching APK and hands it to Android's
 * installer. Android refuses to install it unless it is signed with the same key as the installed app.
 */
class UpdateChecker(
    private val context: Context,
) {
    private val dir get() = File(context.filesDir, "updates")

    /** The latest release if it is newer than this build; null if not. A failure (offline, rate limited) is a failed Result. */
    suspend fun check(): Result<AppUpdate?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject(get(RELEASES_URL))
                val version = json.getString("tag_name").removePrefix("v")
                if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) return@runCatching null
                val assets = json.getJSONArray("assets")
                val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name").endsWith(".apk") }
                // One APK per CPU: take the first of this phone's ABIs, in its order of preference, that has one.
                val apk = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.getString("name").contains(abi) } }
                apk?.let {
                    AppUpdate(
                        version = version,
                        notes = json.optString("body"),
                        apkUrl = it.getString("browser_download_url"),
                        sha256 = it.optString("digest").removePrefix("sha256:").takeIf { d -> d.length == SHA256_HEX_LENGTH },
                    )
                }
            }
        }

    /** Downloads [update] into the app's files; [onProgress] gets bytes read and the total (0 when unknown). */
    suspend fun download(
        update: AppUpdate,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            // filesDir, not cacheDir: the system empties the cache first when storage runs low, which would remove the
            // APK between this write and the install (the user may spend minutes in the "unknown sources" screen).
            dir.deleteRecursively()
            dir.mkdirs()
            val file = File(dir, "subsy-${update.version}.apk")
            val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            val total = connection.contentLengthLong
            // Staging and optimising an install need more than the APK's own size; fail clearly instead of at the installer.
            if (total > 0 && dir.usableSpace < total * 2) throw IOException("Not enough free space for the update")
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var read = 0L
                    var reported = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (read - reported >= PROGRESS_STEP_BYTES) {
                            reported = read
                            onProgress(read, total)
                        }
                    }
                    // A dropped connection can end the stream cleanly, leaving a truncated file the installer rejects.
                    if (total > 0 && read != total) throw IOException("Incomplete download")
                    onProgress(read, total)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256 != null && !actual.equals(update.sha256, ignoreCase = true)) {
                file.delete()
                throw IOException("The download didn't match its checksum")
            }
            file
        }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Starts Android's installer for [apk], or sends the user to the "allow from this source" screen first. True when
     * the installer started; false means keep [apk] and call again once they have granted it.
     */
    fun install(apk: File): Boolean {
        // The permission detour can leave the file waiting for minutes; make sure it survived.
        if (!isIntactApk(apk)) {
            apk.delete()
            throw IOException("The update file was incomplete or removed. Try again.")
        }
        if (!canInstallPackages()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    /** Removes APKs left from earlier updates; run at startup, when no install is waiting. */
    fun deleteDownloads() {
        dir.deleteRecursively()
    }

    private fun isIntactApk(apk: File): Boolean =
        runCatching {
            ZipFile(apk).use { it.getEntry("AndroidManifest.xml") != null }
        }.getOrDefault(false)

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/WolfeLeo2/Subsy/releases/latest"
        const val TIMEOUT_MS = 15_000
        const val DOWNLOAD_READ_TIMEOUT_MS = 60_000
        const val BUFFER_BYTES = 8_192
        const val PROGRESS_STEP_BYTES = 128 * 1024L
        const val SHA256_HEX_LENGTH = 64
    }
}

/** "1.10.0" is newer than "1.9.0": compared number by number, not as text. Anything non-numeric counts as 0. */
internal fun compareVersions(
    a: String,
    b: String,
): Int {
    val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diff = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
