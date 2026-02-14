package com.example.familyone.api

import android.graphics.Bitmap
import android.util.Base64
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

    // Публичный API сервер
    private var serverUrl = "https://totalcode.indevs.in"

    // Mutex для последовательных запросов
    private val requestMutex = Mutex()

    // Настройка OkHttpClient с большими таймаутами, принудительным IPv4 и HTTP/1.1
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1)) // Принудительно используем HTTP/1.1, так как HTTP/2 глючит через туннель
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                // Cloudflare возвращает и IPv6 и IPv4.
                // IPv6 часто глючит через VPN/Туннели, поэтому принудительно берем только IPv4
                val allAddresses = Dns.SYSTEM.lookup(hostname)
                val ipv4Addresses = allAddresses.filter { it is Inet4Address }
                return if (ipv4Addresses.isNotEmpty()) ipv4Addresses else allAddresses
            }
        })
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun setServerUrl(url: String) {
        var finalUrl = url.trimEnd('/')
        val isLocalAddress = finalUrl.contains("localhost") ||
                finalUrl.contains("10.0.2.2") ||
                finalUrl.contains("127.0.0.1") ||
                finalUrl.matches(Regex(".*192\\.168\\.\\d+\\.\\d+.*"))

        if (!isLocalAddress && finalUrl.startsWith("http://")) {
            finalUrl = finalUrl.replace("http://", "https://")
            android.util.Log.w("FaceRecognitionApi", "⚠️ Автоматически заменён http:// на https:// для внешнего сервера")
        }
        serverUrl = finalUrl
        android.util.Log.d("FaceRecognitionApi", "🌐 URL сервера установлен: $serverUrl")
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/health")
                .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

                val response = makePostRequest("$serverUrl/register_face", jsonBody)

                if (response.getBoolean("success")) {
                    Result.success(response.getString("message"))
                } else {
                    Result.failure(Exception(response.getString("error")))
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
            android.util.Log.d("FaceRecognitionApi", "🔄 Конвертируем изображение в Base64...")
            val base64Image = bitmapToBase64(photo)

            val jsonBody = JSONObject().apply {
                put("image", base64Image)
                put("threshold", threshold)
            }

            android.util.Log.d("FaceRecognitionApi", "📡 Отправляем POST запрос (OkHttp) на: $serverUrl/recognize_face")
            val response = makePostRequest("$serverUrl/recognize_face", jsonBody)

            if (response.getBoolean("success")) {
                val results = mutableListOf<RecognitionResult>()
                val resultsArray = response.getJSONArray("results")

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
                Result.success(results)
            } else {
                Result.failure(Exception(response.getString("error")))
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceRecognitionApi", "❌ Исключение при распознавании", e)
            Result.failure(e)
        }
    }

    suspend fun deleteFace(memberId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/delete_face/$memberId")
                .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)

                if (jsonResponse.getBoolean("success")) {
                    Result.success(jsonResponse.getString("message"))
                } else {
                    Result.failure(Exception(jsonResponse.getString("error")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAll(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/clear_all")
                .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)

                if (jsonResponse.getBoolean("success")) {
                    Result.success(jsonResponse.getString("message"))
                } else {
                    Result.failure(Exception(jsonResponse.getString("error")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFaces(): Result<List<RegisteredFace>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/list_faces")
                .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.getBoolean("success")) {
                        val faces = mutableListOf<RegisteredFace>()
                        val facesArray = jsonResponse.getJSONArray("faces")
                        for (i in 0 until facesArray.length()) {
                            val face = facesArray.getJSONObject(i)
                            faces.add(RegisteredFace(face.getString("member_id"), face.getString("member_name")))
                        }
                        Result.success(faces)
                    } else {
                        Result.failure(Exception(jsonResponse.getString("error")))
                    }
                } else {
                    Result.failure(Exception("HTTP Error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makePostRequest(urlString: String, jsonBody: JSONObject): JSONObject {
        val bodyString = jsonBody.toString()
        val bodySizeKb = bodyString.length / 1024
        android.util.Log.d("FaceRecognitionApi", "🌐 URL: $urlString")
        android.util.Log.d("FaceRecognitionApi", "📦 Размер тела запроса: ${bodySizeKb}KB (${bodyString.length} bytes)")

        val requestBody = bodyString.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
            .header("Connection", "keep-alive")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        android.util.Log.d("FaceRecognitionApi", "⏳ Отправляем данные (OkHttp)...")

        client.newCall(request).execute().use { response ->
            android.util.Log.d("FaceRecognitionApi", "📨 HTTP код ответа: ${response.code}")

            val responseString = response.body?.string() ?: ""
            android.util.Log.d("FaceRecognitionApi", "📄 Ответ: ${responseString.take(200)}...")

            if (!response.isSuccessful) {
                android.util.Log.e("FaceRecognitionApi", "❌ Ошибка HTTP ${response.code}: $responseString")
            }

            return JSONObject(responseString)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Уменьшаем размер для прохождения через Cloudflare Tunnel
        val MAX_SIZE = 256  // Уменьшено с 480
        val QUALITY = 30    // Уменьшено с 50

        val scaledBitmap = if (bitmap.width > MAX_SIZE || bitmap.height > MAX_SIZE) {
            val ratio = MAX_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            android.util.Log.d("FaceRecognitionApi", "📐 Масштабирование: ${bitmap.width}x${bitmap.height} → ${newWidth}x${newHeight}")
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, outputStream)
        val byteArray = outputStream.toByteArray()
        
        android.util.Log.d("FaceRecognitionApi", "📷 Размер изображения после сжатия: ${byteArray.size / 1024}KB")

        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
