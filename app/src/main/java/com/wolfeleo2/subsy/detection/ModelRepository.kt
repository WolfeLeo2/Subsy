package com.wolfeleo2.subsy.detection

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.wolfeleo2.subsy.worker.ModelDownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** One file of a detector export (eval/reference.py + eval/export_app.py), as published. */
data class ModelFile(
    val name: String,
    val bytes: Long,
    val sha256: String,
)

/**
 * The detector export the app downloads: GitHub release assets on the app's own repo. A new export is a new tag with
 * new hashes; the four files only work together (see ARCHITECTURE.md, "The model files").
 */
object DetectorRelease {
    const val TAG = "detector-v1"
    const val BASE_URL = "https://github.com/WolfeLeo2/Subsy/releases/download/$TAG/"

    val files =
        listOf(
            ModelFile("spec.json", 45_046, "6d58c3235e5f92ecbc22cc76d249b80796bbfd17fd6682ca4e672c6501e7ed8c"),
            ModelFile("vocab.tsv", 4_513_843, "1c7e797eee200481ee4d17495e9e317f1f021b232160fe28cda9cd6afc683822"),
            ModelFile("heads.onnx", 5_903_679, "ffe263ec85b3094f6ceaf24ef633c45e4073245426db041f0c0cf475c945527b"),
            ModelFile("encoder.int8.onnx", 87_137_095, "48069a71f125dbb1bf860db258313fd6807f39a2e13e75da6b5be75d47cbed36"),
        )

    val totalBytes = files.sumOf { it.bytes }
}

sealed interface DetectorState {
    /** Not downloaded and not downloading. */
    data object Absent : DetectorState

    /** Queued until Wi-Fi (or storage) allows, or between retries. */
    data object Waiting : DetectorState

    data class Downloading(
        val doneBytes: Long,
    ) : DetectorState

    data object Ready : DetectorState

    /** Gave up after retries or a file failed its checksum; starting again resumes what is on disk. */
    data object Failed : DetectorState
}

/**
 * The on-device detector files: downloaded only after the user opts in, kept in noBackupFilesDir (never in cloud
 * backups), deleted when they opt out.
 */
class ModelRepository(
    private val context: Context,
) {
    private val root = File(context.noBackupFilesDir, "detector")

    /** Where this release's files live. A file is only renamed into place after its checksum matched. */
    val dir = File(root, DetectorRelease.TAG)

    private val workManager get() = WorkManager.getInstance(context)

    fun isReady(): Boolean = DetectorRelease.files.all { File(dir, it.name).length() == it.bytes }

    val state: Flow<DetectorState> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            val info = infos.firstOrNull()
            when {
                isReady() -> DetectorState.Ready
                // Succeeded but the files are gone means they were removed since.
                info == null || info.state == WorkInfo.State.CANCELLED || info.state == WorkInfo.State.SUCCEEDED ->
                    DetectorState.Absent
                info.state == WorkInfo.State.RUNNING ->
                    DetectorState.Downloading(info.progress.getLong(ModelDownloadWorker.PROGRESS_BYTES, 0))
                info.state == WorkInfo.State.FAILED -> DetectorState.Failed
                else -> DetectorState.Waiting
            }
        }

    /** Starts or resumes the download on an unmetered network. A no-op while one is queued or running. */
    fun download() {
        root.listFiles()?.filter { it != dir }?.forEach { it.deleteRecursively() } // older releases
        val request =
            OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                ).build()
        // A failed or finished run is replaced, so turning detection on again retries.
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Stops any download and deletes every detector file. */
    fun remove() {
        workManager.cancelUniqueWork(WORK_NAME)
        root.deleteRecursively()
    }

    private companion object {
        const val WORK_NAME = "detector-download"
    }
}
