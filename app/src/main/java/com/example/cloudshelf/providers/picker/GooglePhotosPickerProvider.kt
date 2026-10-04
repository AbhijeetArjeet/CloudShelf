package com.example.cloudshelf.providers.picker

import com.example.cloudshelf.model.SourceType
import com.example.cloudshelf.providers.MediaSourceProvider
import com.example.cloudshelf.providers.ScanSummary
import com.example.cloudshelf.providers.SourceItemRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class PickerSessionInfo(
    val sessionId: String,
    val pickerUri: String,
    val expireTime: String?
)

class GooglePhotosPickerProvider(
    private val accessToken: String,
    private val accountEmail: String
) : MediaSourceProvider {

    override val sourceType: SourceType = SourceType.GOOGLE_PHOTOS_PICKER

    companion object {
        private const val API_BASE = "https://photospicker.googleapis.com/v1"
    }

    suspend fun createSession(): PickerSessionInfo = withContext(Dispatchers.IO) {
        val url = URL("$API_BASE/sessions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.use { it.write("{}".toByteArray()) }
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw RuntimeException("Failed to create Google Photos Picker session: HTTP $responseCode - $errorBody")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)
        PickerSessionInfo(
            sessionId = json.getString("id"),
            pickerUri = json.getString("pickerUri"),
            expireTime = json.optString("expireTime")
        )
    }

    suspend fun isMediaSelectionComplete(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$API_BASE/sessions/$sessionId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (conn.responseCode !in 200..299) return@withContext false
        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)
        json.optBoolean("mediaItemsSet", false)
    }

    suspend fun listPickedMedia(sessionId: String): List<SourceItemRef> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SourceItemRef>()
        var pageToken: String? = null

        do {
            val endpoint = buildString {
                append("$API_BASE/mediaItems?sessionId=$sessionId&pageSize=100")
                if (!pageToken.isNullOrEmpty()) {
                    append("&pageToken=$pageToken")
                }
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            if (conn.responseCode !in 200..299) break

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val mediaArray = json.optJSONArray("mediaItems")

            if (mediaArray != null) {
                for (i in 0 until mediaArray.length()) {
                    val itemObj = mediaArray.getJSONObject(i)
                    val id = itemObj.getString("id")
                    val mediaFile = itemObj.optJSONObject("mediaFile")
                    val baseUrl = mediaFile?.optString("baseUrl") ?: ""
                    val filename = mediaFile?.optString("filename") ?: "photo_$id.jpg"
                    val mimeType = mediaFile?.optString("mimeType") ?: "image/jpeg"

                    items.add(
                        SourceItemRef(
                            identifier = "$id#$baseUrl",
                            filename = filename,
                            mimeType = mimeType,
                            estimatedSizeBytes = 5L * 1024 * 1024 // Estimated ~5MB per cloud photo
                        )
                    )
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (!pageToken.isNullOrEmpty())

        items
    }

    override suspend fun scanSource(): ScanSummary = withContext(Dispatchers.IO) {
        // Picker scans dynamically after user selection
        ScanSummary(
            sourceType = SourceType.GOOGLE_PHOTOS_PICKER,
            sourceName = "Google Photos ($accountEmail)",
            totalItems = 0,
            photoCount = 0,
            videoCount = 0,
            totalSizeBytes = 0L,
            estimatedBatches = 0,
            items = emptyList()
        )
    }

    override suspend fun openItemStream(itemRef: SourceItemRef): InputStream = withContext(Dispatchers.IO) {
        val parts = itemRef.identifier.split("#", limit = 2)
        val baseUrl = if (parts.size == 2) parts[1] else itemRef.identifier
        
        // Google media baseUrl download append =d for full download
        val downloadUrl = if (baseUrl.contains("googleusercontent.com")) "$baseUrl=d" else baseUrl
        val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30000
            readTimeout = 60000
        }
        if (conn.responseCode !in 200..299) {
            throw RuntimeException("Failed to download media item: HTTP ${conn.responseCode}")
        }
        conn.inputStream
    }
}
