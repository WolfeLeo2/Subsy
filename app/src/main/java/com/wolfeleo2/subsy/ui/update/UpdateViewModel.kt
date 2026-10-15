package com.wolfeleo2.subsy.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.repository.SettingsRepository
import com.wolfeleo2.subsy.data.update.AppUpdate
import com.wolfeleo2.subsy.data.update.UpdateChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** What a check the user asked for found, shown in Settings. */
enum class CheckOutcome { NONE, UP_TO_DATE, FAILED }

data class UpdateUiState(
    val available: AppUpdate? = null,
    val checking: Boolean = false,
    val outcome: CheckOutcome = CheckOutcome.NONE,
    val sheetOpen: Boolean = false,
    val downloading: Boolean = false,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
)

/**
 * Lives as long as the activity, so a download carries on while the sheet is closed or Settings is left. Checks once
 * at startup and opens the sheet if something newer is out.
 */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private var downloadJob: Job? = null

    // Set when the APK is downloaded but Android sent the user to allow installs first.
    private var pendingInstall: File? = null

    init {
        checker.deleteDownloads()
        check(manual = false)
    }

    /** A [manual] check reports "up to date" or a failure; the startup check stays quiet unless it finds an update. */
    fun check(manual: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(checking = manual, outcome = CheckOutcome.NONE) }
            val result = checker.check()
            val found = result.getOrNull()
            val skipped = settings.skippedUpdate()
            _state.update { s ->
                when {
                    found != null -> s.copy(checking = false, available = found, sheetOpen = manual || found.version != skipped)
                    manual -> s.copy(checking = false, outcome = if (result.isSuccess) CheckOutcome.UP_TO_DATE else CheckOutcome.FAILED)
                    else -> s
                }
            }
        }
    }

    fun openSheet() = _state.update { it.copy(sheetOpen = true) }

    fun closeSheet() = _state.update { it.copy(sheetOpen = false) }

    /** "Don't remind me": this version no longer opens the sheet at startup. Settings still offers it. */
    fun skipVersion() {
        _state.value.available?.let { update -> viewModelScope.launch { settings.skipUpdate(update.version) } }
        closeSheet()
    }

    fun startDownload() {
        val update = _state.value.available ?: return
        _state.update { it.copy(downloading = true, error = null, doneBytes = 0, totalBytes = 0) }
        downloadJob =
            viewModelScope.launch {
                runCatching {
                    val file = checker.download(update) { done, total -> _state.update { it.copy(doneBytes = done, totalBytes = total) } }
                    if (checker.install(file)) installed() else pendingInstall = file
                }.onFailure { failure ->
                    // Cancelling the job is the user's choice, not an error.
                    if (failure !is kotlinx.coroutines.CancellationException) _state.update { it.copy(error = failure.message.orEmpty()) }
                }
                _state.update { it.copy(downloading = false) }
            }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.update { it.copy(downloading = false) }
    }

    /** Call when the app comes back to the front: finishes an install that was waiting on the permission. */
    fun retryPendingInstall() {
        val file = pendingInstall ?: return
        if (!checker.canInstallPackages()) return
        pendingInstall = null
        runCatching { checker.install(file) }
            .onSuccess { launched -> if (launched) installed() }
            .onFailure { failure -> _state.update { it.copy(error = failure.message.orEmpty()) } }
    }

    private fun installed() = _state.update { it.copy(available = null, sheetOpen = false) }

    class Factory(
        private val checker: UpdateChecker,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(checker, settings) as T
    }
}
