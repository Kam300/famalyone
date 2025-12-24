package com.example.familyone.api

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
    
    // URL сервера - измените на свой
    private var serverUrl = "http://10.201.148.53:5000" // Для эмулятора Android
    // Для реального устройства используйте IP компьютера: "http://192.168.1.100:5000"
    
    fun setServerUrl(url: String) {
        serverUrl = url.trimEnd('/')
    }
    
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun registerFace(
        memberId: Long,
        memberName: String,
        photo: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
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
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun recognizeFace(
        photo: Bitmap,
        threshold: Double = 0.6
    ): Result<List<RecognitionResult>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FaceRecognitionApi", "🔄 Конвертируем изображение в Base64...")
            val base64Image = bitmapToBase64(photo)
            android.util.Log.d("FaceRecognitionApi", "✓ Base64 размер: ${base64Image.length} символов")
            
            val jsonBody = JSONObject().apply {
                put("image", base64Image)
                put("threshold", threshold)
            }
            
            android.util.Log.d("FaceRecognitionApi", "📡 Отправляем POST запрос на: $serverUrl/recognize_face")
            val response = makePostRequest("$serverUrl/recognize_face", jsonBody)
            android.util.Log.d("FaceRecognitionApi", "📥 Получен ответ: $response")
            
            if (response.getBoolean("success")) {
                val results = mutableListOf<RecognitionResult>()
                val resultsArray = response.getJSONArray("results")
                android.util.Log.d("FaceRecognitionApi", "✅ Найдено лиц: ${resultsArray.length()}")
                
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
                val error = response.getString("error")
                android.util.Log.e("FaceRecognitionApi", "❌ Ошибка сервера: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceRecognitionApi", "❌ Исключение при распознавании", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun deleteFace(memberId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/delete_face/$memberId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            
            connection.disconnect()
            
            val jsonResponse = JSONObject(response)
            
            if (jsonResponse.getBoolean("success")) {
                Result.success(jsonResponse.getString("message"))
            } else {
                Result.failure(Exception(jsonResponse.getString("error")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun clearAll(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/clear_all")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            
            connection.disconnect()
            
            val jsonResponse = JSONObject(response)
            
            if (jsonResponse.getBoolean("success")) {
                Result.success(jsonResponse.getString("message"))
            } else {
                Result.failure(Exception(jsonResponse.getString("error")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun listFaces(): Result<List<RegisteredFace>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/list_faces")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(response)
                
                if (jsonResponse.getBoolean("success")) {
                    val faces = mutableListOf<RegisteredFace>()
                    val facesArray = jsonResponse.getJSONArray("faces")
                    
                    for (i in 0 until facesArray.length()) {
                        val face = facesArray.getJSONObject(i)
                        faces.add(
                            RegisteredFace(
                                memberId = face.getString("member_id"),
                                memberName = face.getString("member_name")
                            )
                        )
                    }
                    
                    Result.success(faces)
                } else {
                    Result.failure(Exception(jsonResponse.getString("error")))
                }
            } else {
                Result.failure(Exception("HTTP Error: $responseCode"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private fun makePostRequest(urlString: String, jsonBody: JSONObject): JSONObject {
        android.util.Log.d("FaceRecognitionApi", "🌐 URL: $urlString")
        android.util.Log.d("FaceRecognitionApi", "📦 JSON размер: ${jsonBody.toString().length} байт")
        
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        
        android.util.Log.d("FaceRecognitionApi", "⏳ Отправляем данные...")
        
        // Отправляем данные
        connection.outputStream.use { os ->
            val input = jsonBody.toString().toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }
        
        android.util.Log.d("FaceRecognitionApi", "✓ Данные отправлены, ждем ответ...")
        
        // Читаем ответ
        val responseCode = connection.responseCode
        android.util.Log.d("FaceRecognitionApi", "📨 HTTP код ответа: $responseCode")
        
        val response = if (responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            android.util.Log.e("FaceRecognitionApi", "❌ Ошибка HTTP $responseCode: $errorResponse")
            errorResponse
        }
        
        connection.disconnect()
        
        android.util.Log.d("FaceRecognitionApi", "📄 Ответ сервера: ${response.take(200)}...")
        
        return JSONObject(response)
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
