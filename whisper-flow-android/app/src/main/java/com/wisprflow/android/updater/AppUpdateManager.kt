package com.wisprflow.android.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.wisprflow.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val version: String, val notes: String, val downloadUrl: String) : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0
)

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        // Default GitHub repo endpoint or custom server endpoint
        var GITHUB_REPO = "sndp-dev/whisper-flow" // Customizable by user
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus

    suspend fun checkForUpdates(repo: String = GITHUB_REPO) = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Checking
        val url = "https://api.github.com/repos/$repo/releases/latest"

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _updateStatus.value = UpdateStatus.Error("No release found (HTTP ${response.code})")
                    return@withContext
                }

                val body = response.body?.string() ?: ""
                val release = json.decodeFromString<GitHubRelease>(body)

                val latestTag = release.tag_name.removePrefix("v").trim()
                val currentVersion = "1.0.0" // App version

                // Find APK asset
                val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: release.assets.firstOrNull()

                if (apkAsset != null && latestTag != currentVersion) {
                    _updateStatus.value = UpdateStatus.Available(
                        version = release.tag_name,
                        notes = release.body,
                        downloadUrl = apkAsset.browser_download_url
                    )
                } else {
                    _updateStatus.value = UpdateStatus.UpToDate
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed checking for update", e)
            _updateStatus.value = UpdateStatus.Error(e.message ?: "Failed to check for updates")
        }
    }

    suspend fun downloadAndInstall(downloadUrl: String) = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Downloading(0f)

        try {
            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _updateStatus.value = UpdateStatus.Error("Download failed: HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body ?: run {
                    _updateStatus.value = UpdateStatus.Error("Empty download response")
                    return@withContext
                }

                val contentLength = body.contentLength()
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updateDir, "whisper_flow_update.apk")

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead: Long = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalRead.toFloat() / contentLength)
                                _updateStatus.value = UpdateStatus.Downloading(progress)
                            }
                        }
                        output.flush()
                    }
                }

                _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)

                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            _updateStatus.value = UpdateStatus.Error("Download failed: ${e.message}")
        }
    }

    fun installApk(apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            _updateStatus.value = UpdateStatus.Error("Failed to open APK installer: ${e.message}")
        }
    }
}
