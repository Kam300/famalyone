package com.example.familyone.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.familyone.utils.ApiServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class RecognitionResult(
    val memberId: String,
    val memberName: String,
    val confidence: Double,
    val location: FaceLocation
)

data class FaceLocation(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int
)

data class RegisteredFace(
    val memberId: String,
    val memberName: String
)

object FaceRecognitionApi {

    private const val TAG = "FaceRecognitionApi"
    private const val USER_AGENT = "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0"

    // Canonical base URL with /api.
    private var serverUrl = ApiServerConfig.DEFAULT_BASE_URL

    // Mutex for sequential write-like requests.
    private val requestMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val allAddresses = Dns.SYSTEM.lookup(hostname)
                val ipv4Addresses = allAddresses.filter { it is Inet4Address }
                return if (ipv4Addresses.isNotEmpty()) ipv4Addresses else allAddresses
            }
        })
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

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

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRouteFallback(endpoint = "health", method = "GET")
            Log.d(TAG, "Health check via ${response.candidateType}: HTTP ${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed: ${e.message}", e)
            false
        }
    }

    suspend fun registerFace(
        memberId: Long,
        memberName: String,
        photo: Bitmap
    ): Result<String> = requestMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val base64Image = bitmapToBase64(photo)
                val jsonBody = JSONObject().apply {
                    put("member_id", memberId.toString())
                    put("member_name", memberName)
                    put("image", base64Image)
                }

                val response = executeWithRouteFallback(
                    endpoint = "register_face",
                    method = "POST",
                    bodyString = jsonBody.toString()
                )
                if (!response.isSuccessful) {
                    return@withContext Result.failure(httpError("register_face", response))
                }
                val jsonResponse = parseJsonOrThrow(response, "register_face")

                if (jsonResponse.optBoolean("success", false)) {
                    Result.success(jsonResponse.optString("message", "OK"))
                } else {
                    Result.failure(Exception(jsonResponse.optString("error", "Registration failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun recognizeFace(
        photo: Bitmap,
        threshold: Double = 0.6
    ): Result<List<RecognitionResult>> = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(photo)
            val jsonBody = JSONObject().apply {
                put("image", base64Image)
                put("threshold", threshold)
            }

            val response = executeWithRouteFallback(
                endpoint = "recognize_face",
                method = "POST",
                bodyString = jsonBody.toString()
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(httpError("recognize_face", response))
            }
            val jsonResponse = parseJsonOrThrow(response, "recognize_face")

            if (jsonResponse.optBoolean("success", false)) {
                val results = mutableListOf<RecognitionResult>()
                val resultsArray = jsonResponse.optJSONArray("results")
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val result = resultsArray.getJSONObject(i)
                        val location = result.getJSONObject("location")
                        results.add(
                            RecognitionResult(
                                memberId = result.getString("member_id"),
                                memberName = result.getString("member_name"),
                                confidence = result.getDouble("confidence"),
                                location = FaceLocation(
                                    top = location.getInt("top"),
                                    right = location.getInt("right"),
                                    bottom = location.getInt("bottom"),
                                    left = location.getInt("left")
                                )
                            )
                        )
                    }
                }
                Result.success(results)
            } else {
                Result.failure(Exception(jsonResponse.optString("error", "Recognition failed")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteFace(memberId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRouteFallback(
                endpoint = "delete_face/$memberId",
                method = "DELETE"
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(httpError("delete_face/$memberId", response))
            }
            val jsonResponse = parseJsonOrThrow(response, "delete_face/$memberId")

            if (jsonResponse.optBoolean("success", false)) {
                Result.success(jsonResponse.optString("message", "Deleted"))
            } else {
                Result.failure(Exception(jsonResponse.optString("error", "Delete failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAll(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRouteFallback(
                endpoint = "clear_all",
                method = "DELETE"
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(httpError("clear_all", response))
            }
            val jsonResponse = parseJsonOrThrow(response, "clear_all")

            if (jsonResponse.optBoolean("success", false)) {
                Result.success(jsonResponse.optString("message", "Cleared"))
            } else {
                Result.failure(Exception(jsonResponse.optString("error", "Clear failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFaces(): Result<List<RegisteredFace>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRouteFallback(endpoint = "list_faces", method = "GET")
            if (!response.isSuccessful) {
                return@withContext Result.failure(httpError("list_faces", response))
            }

            val jsonResponse = parseJsonOrThrow(response, "list_faces")
            if (!jsonResponse.optBoolean("success", false)) {
                return@withContext Result.failure(Exception(jsonResponse.optString("error", "List failed")))
            }

            val faces = mutableListOf<RegisteredFace>()
            val facesArray = jsonResponse.optJSONArray("faces")
            if (facesArray != null) {
                for (i in 0 until facesArray.length()) {
                    val face = facesArray.getJSONObject(i)
                    faces.add(
                        RegisteredFace(
                            memberId = face.getString("member_id"),
                            memberName = face.getString("member_name")
                        )
                    )
                }
            }
            Result.success(faces)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeWithRouteFallback(
        endpoint: String,
        method: String,
        bodyString: String? = null
    ): HttpResponse {
        val baseCandidates = ApiServerConfig.candidateBaseUrls(serverUrl)
        var lastResponse: HttpResponse? = null
        val attemptedResponses = mutableListOf<HttpResponse>()

        for ((index, baseUrl) in baseCandidates.withIndex()) {
            val candidateType = if (index == 0) "primary" else "legacy"
            val url = "$baseUrl/$endpoint"
            Log.d(TAG, "[$candidateType] $method $url")

            val response = try {
                when (method) {
                    "GET" -> executeGet(url, candidateType)
                    "POST" -> executePost(url, candidateType, bodyString.orEmpty())
                    "DELETE" -> executeDelete(url, candidateType)
                    else -> throw IllegalArgumentException("Unsupported method: $method")
                }
            } catch (e: Exception) {
                // No fallback on network/timeout/unknown errors to avoid duplicate mutations.
                Log.e(TAG, "[$candidateType] request failed: ${e.message}", e)
                throw e
            }

            lastResponse = response
            attemptedResponses.add(response)
            val isRouteMismatch = ApiServerConfig.isRouteMismatch(response.code, response.body)
            if (isRouteMismatch && index < baseCandidates.lastIndex) {
                Log.w(
                    TAG,
                    "[$candidateType] route mismatch (HTTP ${response.code}), fallback to legacy candidate"
                )
                continue
            }

            if (isRouteMismatch) {
                val summary = attemptedResponses.joinToString(" | ") {
                    "${it.candidateType}:HTTP ${it.code} ${it.url}"
                }
                throw Exception(
                    "Route mismatch for /$endpoint on all candidates. Tried: $summary"
                )
            }

            Log.d(TAG, "Using ${response.candidateType} response from ${response.url}")
            return response
        }

        return lastResponse ?: throw IllegalStateException("No URL candidates available")
    }

    private fun executeGet(url: String, candidateType: String): HttpResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return HttpResponse(
                code = response.code,
                body = body,
                isSuccessful = response.isSuccessful,
                url = url,
                candidateType = candidateType
            )
        }
    }

    private fun executeDelete(url: String, candidateType: String): HttpResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .delete()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return HttpResponse(
                code = response.code,
                body = body,
                isSuccessful = response.isSuccessful,
                url = url,
                candidateType = candidateType
            )
        }
    }

    private fun executePost(url: String, candidateType: String, bodyString: String): HttpResponse {
        val bodySizeKb = bodyString.length / 1024
        Log.d(TAG, "POST body size: ${bodySizeKb}KB (${bodyString.length} bytes)")

        val requestBody = bodyString.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Connection", "keep-alive")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            Log.d(TAG, "HTTP ${response.code} (${candidateType}) body: ${responseBody.take(200)}")
            return HttpResponse(
                code = response.code,
                body = responseBody,
                isSuccessful = response.isSuccessful,
                url = url,
                candidateType = candidateType
            )
        }
    }

    private fun parseJsonOrThrow(response: HttpResponse, endpoint: String): JSONObject {
        return try {
            JSONObject(response.body)
        } catch (_: Exception) {
            val bodyPreview = response.body.take(200).ifBlank { "<empty>" }
            throw Exception(
                "Invalid JSON from /$endpoint via ${response.candidateType} (HTTP ${response.code}). Body: $bodyPreview"
            )
        }
    }

    private fun httpError(endpoint: String, response: HttpResponse): Exception {
        val bodyPreview = response.body.take(200).ifBlank { "<empty>" }
        return Exception(
            "HTTP ${response.code} for /$endpoint via ${response.candidateType}. Body: $bodyPreview"
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSize = 256
        val quality = 30

        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
