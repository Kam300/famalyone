package com.example.familyone.api

import android.util.Log
import com.example.familyone.utils.ApiServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class BackupRemoteMeta(
    val schemaVersion: Int,
    val exists: Boolean,
    val createdAtUtc: String?,
    val sizeBytes: Long?,
    val membersCount: Int?,
    val memberPhotosCount: Int?,
    val assetsCount: Int?,
    val compression: String?,
    val checksumSha256: String?
)

object BackupApi {

    private const val TAG = "BackupApi"
    private const val USER_AGENT = "FamilyOneBackup/1.0"
    private val zipMediaType = "application/zip".toMediaType()

    // Canonical base URL with /api.
    private var serverUrl = ApiServerConfig.DEFAULT_BASE_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private data class HttpResponse(
        val code: Int,
        val body: String,
        val isSuccessful: Boolean,
        val url: String,
        val candidateType: String
    )

    fun setServerUrl(url: String) {
        val normalized = ApiServerConfig.normalizeBaseUrl(url)
        serverUrl = normalized
        Log.d(TAG, "Normalized base URL: $normalized")
    }

    suspend fun getMeta(idToken: String): Result<BackupRemoteMeta> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRouteFallback(
                endpoint = "backup/meta",
                method = "GET",
                idToken = idToken
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(httpError("backup/meta", response))
            }
            val json = parseJsonOrThrow(response, "backup/meta")
            Result.success(parseMeta(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBackup(idToken: String, archiveFile: File): Result<BackupRemoteMeta> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists()) {
                return@withContext Result.failure(Exception("Archive file not found"))
            }

            val response = executeUploadWithRouteFallback(
                endpoint = "backup/upload",
                idToken = idToken,
                archiveFile = archiveFile
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(httpError("backup/upload", response))
            }
            val json = parseJsonOrThrow(response, "backup/upload")
            Result.success(parseMeta(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(idToken: String, destinationFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val baseCandidates = ApiServerConfig.candidateBaseUrls(serverUrl)
            var lastError: Exception? = null

            for ((index, baseUrl) in baseCandidates.withIndex()) {
                val candidateType = if (index == 0) "primary" else "legacy"
                val url = "$baseUrl/backup/download"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer $idToken")
                    .addHeader("User-Agent", USER_AGENT)
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body ?: return@use
                            destinationFile.parentFile?.mkdirs()
                            FileOutputStream(destinationFile).use { output ->
                                responseBody.byteStream().copyTo(output)
                            }
                            return@withContext Result.success(destinationFile)
                        }

                        val bodyString = response.body?.string().orEmpty()
                        if (index < baseCandidates.lastIndex &&
                            ApiServerConfig.isRouteMismatch(response.code, bodyString)
                        ) {
                            Log.w(TAG, "[$candidateType] route mismatch for download, trying fallback")
                            return@use
                        }

                        return@withContext Result.failure(
                            Exception(buildHttpErrorMessage("backup/download", response.code, bodyString))
                        )
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (index == baseCandidates.lastIndex) {
                        break
                    }
                }
            }

            Result.failure(lastError ?: Exception("Download failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(idToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRouteFallback(
                endpoint = "backup",
                method = "DELETE",
                idToken = idToken
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(httpError("backup", response))
            }
            val json = parseJsonOrThrow(response, "backup")
            val deleted = json.optBoolean("deleted", false)
            Result.success(deleted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeWithRouteFallback(
        endpoint: String,
        method: String,
        idToken: String
    ): HttpResponse {
        val baseCandidates = ApiServerConfig.candidateBaseUrls(serverUrl)
        var lastResponse: HttpResponse? = null

        for ((index, baseUrl) in baseCandidates.withIndex()) {
            val candidateType = if (index == 0) "primary" else "legacy"
            val url = "$baseUrl/$endpoint"
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("User-Agent", USER_AGENT)

            when (method) {
                "GET" -> requestBuilder.get()
                "DELETE" -> requestBuilder.delete()
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }

            try {
                var shouldTryFallback = false
                val result: HttpResponse
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    result = HttpResponse(
                        code = response.code,
                        body = bodyString,
                        isSuccessful = response.isSuccessful,
                        url = url,
                        candidateType = candidateType
                    )
                    lastResponse = result

                    if (!response.isSuccessful &&
                        index < baseCandidates.lastIndex &&
                        ApiServerConfig.isRouteMismatch(response.code, bodyString)
                    ) {
                        Log.w(TAG, "[$candidateType] route mismatch on $endpoint, trying fallback")
                        shouldTryFallback = true
                    }
                }

                if (shouldTryFallback) {
                    continue
                }

                return result
            } catch (e: Exception) {
                if (index == baseCandidates.lastIndex) {
                    throw e
                }
            }
        }

        return lastResponse ?: throw Exception("No route candidates available for $endpoint")
    }

    private fun executeUploadWithRouteFallback(
        endpoint: String,
        idToken: String,
        archiveFile: File
    ): HttpResponse {
        val baseCandidates = ApiServerConfig.candidateBaseUrls(serverUrl)
        var lastResponse: HttpResponse? = null

        for ((index, baseUrl) in baseCandidates.withIndex()) {
            val candidateType = if (index == 0) "primary" else "legacy"
            val url = "$baseUrl/$endpoint"

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "backup_file",
                    archiveFile.name,
                    archiveFile.asRequestBody(zipMediaType)
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .post(multipartBody)
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("User-Agent", USER_AGENT)
                .build()

            try {
                var shouldTryFallback = false
                val result: HttpResponse
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    result = HttpResponse(
                        code = response.code,
                        body = bodyString,
                        isSuccessful = response.isSuccessful,
                        url = url,
                        candidateType = candidateType
                    )
                    lastResponse = result

                    if (!response.isSuccessful &&
                        index < baseCandidates.lastIndex &&
                        ApiServerConfig.isRouteMismatch(response.code, bodyString)
                    ) {
                        Log.w(TAG, "[$candidateType] route mismatch on upload, trying fallback")
                        shouldTryFallback = true
                    }
                }

                if (shouldTryFallback) {
                    continue
                }

                return result
            } catch (e: Exception) {
                // No fallback for network errors to avoid duplicate upload.
                throw e
            }
        }

        return lastResponse ?: throw Exception("No route candidates available for $endpoint")
    }

    private fun parseJsonOrThrow(response: HttpResponse, endpoint: String): JSONObject {
        return try {
            JSONObject(response.body)
        } catch (e: Exception) {
            throw Exception(
                "Invalid JSON response for $endpoint " +
                    "(HTTP ${response.code}, ${response.candidateType}, URL=${response.url})"
            )
        }
    }

    private fun parseMeta(json: JSONObject): BackupRemoteMeta {
        return BackupRemoteMeta(
            schemaVersion = json.optInt("schemaVersion", 1),
            exists = json.optBoolean("exists", true),
            createdAtUtc = json.optString("createdAtUtc").ifBlank { null },
            sizeBytes = if (json.has("sizeBytes")) json.optLong("sizeBytes") else null,
            membersCount = if (json.has("membersCount")) json.optInt("membersCount") else null,
            memberPhotosCount = if (json.has("memberPhotosCount")) json.optInt("memberPhotosCount") else null,
            assetsCount = if (json.has("assetsCount")) json.optInt("assetsCount") else null,
            compression = json.optString("compression").ifBlank { null },
            checksumSha256 = json.optString("checksumSha256").ifBlank { null }
        )
    }

    private fun httpError(endpoint: String, response: HttpResponse): Exception {
        return Exception(buildHttpErrorMessage(endpoint, response.code, response.body))
    }

    private fun buildHttpErrorMessage(endpoint: String, code: Int, body: String): String {
        val bodyText = body.trim()
        val snippet = if (bodyText.length > 600) bodyText.take(600) + "..." else bodyText
        return "HTTP $code for $endpoint: $snippet"
    }
}
