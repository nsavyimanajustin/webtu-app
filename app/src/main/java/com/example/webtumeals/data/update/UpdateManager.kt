package com.example.webtumeals.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val hasUpdate: Boolean
)

class UpdateManager(
    private val repoOwner: String = "nsavyimanajustin",
    private val repoName: String = "webtu-app"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdates(currentVersionName: String): AppUpdateInfo = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "WebTUMeals-Android-App")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppUpdateInfo("", "", "", "", "", false)
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                val title = json.optString("name", "Mise à jour WebTU")
                val notes = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "https://github.com/$repoOwner/$repoName/releases")

                val assets = json.optJSONArray("assets")
                var downloadUrl = ""
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                    if (downloadUrl.isBlank() && assets.length() > 0) {
                        downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    }
                }

                val hasUpdate = isNewerVersion(currentVersionName.removePrefix("v").trim(), tagName)
                AppUpdateInfo(
                    latestVersion = tagName,
                    releaseTitle = title,
                    releaseNotes = notes,
                    downloadUrl = downloadUrl.ifBlank { htmlUrl },
                    htmlUrl = htmlUrl,
                    hasUpdate = hasUpdate
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("WebTUMeals", "Could not check updates: ${e.message}")
            AppUpdateInfo("", "", "", "", "", false)
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (latest.isBlank()) return false
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currParts.size, lateParts.size)
        for (i in 0 until length) {
            val c = currParts.getOrElse(i) { 0 }
            val l = lateParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val destFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "webtu-update.apk")
        if (destFile.exists()) destFile.delete()

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "WebTUMeals-Android-App")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Échec de téléchargement (HTTP ${response.code})")
            val body = response.body ?: throw RuntimeException("Fichier vide")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                    output.flush()
                }
            }
        }

        withContext(Dispatchers.Main) {
            installApk(context, destFile)
        }
        true
    }

    fun installApk(context: Context, apkFile: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openBrowserRelease(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
