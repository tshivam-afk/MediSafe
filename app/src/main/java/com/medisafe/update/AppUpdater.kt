package com.medisafe.update

import android.content.Context
import com.medisafe.BuildConfig
import com.medisafe.MediSafeApp
import com.medisafe.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val release: AppRelease) : UpdateState()
    data object UpToDate : UpdateState()
    data class Downloading(
        val release: AppRelease,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class Installing(val release: AppRelease) : UpdateState()
    data class Failed(val message: String, val release: AppRelease?) : UpdateState()
}

class AppUpdater(
    private val appContext: Context,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null
    private var dismissedTag: String? = null

    fun currentVersion(): String {
        val installed = preferences.installedReleaseTag
        val build = BuildConfig.VERSION_NAME
        return if (!installed.isNullOrBlank() && AppVersion.isNewer(installed, build)) installed else build
    }

    fun check(manual: Boolean) {
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.Checking -> return
            else -> Unit
        }
        if (!manual) {
            if (!consumeColdStart()) return
            if (!preferences.autoUpdateEnabled) return
        }
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Checking
            val result = withContext(Dispatchers.IO) {
                runCatching { GitHubUpdateSource.fetchLatest() }
            }
            result.fold(
                onSuccess = { release ->
                    val local = currentVersion()
                    if (!AppVersion.isNewer(release.tag, local)) {
                        _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
                    } else if (!manual && dismissedTag == release.tag) {
                        _state.value = UpdateState.Idle
                    } else {
                        _state.value = UpdateState.Available(release)
                    }
                },
                onFailure = { error ->
                    val message = error.message?.takeIf { it.isNotBlank() }
                        ?: "Couldn't check for updates."
                    _state.value = if (manual) {
                        UpdateState.Failed(message, null)
                    } else {
                        UpdateState.Idle
                    }
                }
            )
        }
    }

    fun downloadAndInstall() {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release
            is UpdateState.Downloading -> current.release
            else -> null
        } ?: return
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0L, release.apkSizeBytes)
            val file = withContext(Dispatchers.IO) { runCatching { downloadApk(release) } }
            file.fold(
                onSuccess = { apk ->
                    _state.value = UpdateState.Installing(release)
                    runCatching { ApkInstaller.install(appContext, apk, release.tag) }
                        .onFailure { error ->
                            _state.value = UpdateState.Failed(
                                error.message?.takeIf { it.isNotBlank() }
                                    ?: "Couldn't start the installer.",
                                release
                            )
                        }
                },
                onFailure = { error ->
                    _state.value = UpdateState.Failed(
                        error.message?.takeIf { it.isNotBlank() } ?: "Download failed.",
                        release
                    )
                }
            )
        }
    }

    fun dismiss() {
        val tag = when (val current = _state.value) {
            is UpdateState.Available -> current.release.tag
            is UpdateState.Downloading -> current.release.tag
            is UpdateState.Installing -> current.release.tag
            is UpdateState.Failed -> current.release?.tag
            else -> null
        }
        if (tag != null) dismissedTag = tag
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun consumeTransient() {
        val current = _state.value
        if (current is UpdateState.UpToDate ||
            (current is UpdateState.Failed && current.release == null)
        ) {
            _state.value = UpdateState.Idle
        }
    }

    private fun consumeColdStart(): Boolean {
        val app = appContext.applicationContext as? MediSafeApp ?: return false
        synchronized(app) {
            if (!app.pendingColdStartUpdateCheck) return false
            app.pendingColdStartUpdateCheck = false
            return true
        }
    }

    private suspend fun downloadApk(release: AppRelease): File {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "MediSafe-update.apk")
        if (dest.exists()) dest.delete()
        val conn = GitHubUpdateSource.openDownload(release.apkUrl)
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("Download failed ($code).")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        _state.value = UpdateState.Downloading(release, copied, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (dest.length() < 64) {
            dest.delete()
            error("Downloaded APK is empty.")
        }
        return dest
    }
}
