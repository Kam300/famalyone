package com.example.familyone.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import com.example.familyone.data.FamilyMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


enum class PdfPageFormat(val formatName: String, val displayName: String) {
    A4("A4", "A4 (210x297 мм)"),
    A4_LANDSCAPE("A4_LANDSCAPE", "A4 Альбомная"),
    A3("A3", "A3 (297x420 мм)"),
    A3_LANDSCAPE("A3_LANDSCAPE", "A3 Альбомная")
}

/**
 * Результат экспорта PDF
 */
sealed class ExportResult {
    data class LocalFile(val file: File) : ExportResult()
    data class DriveUrl(val downloadUrl: String, val filename: String) : ExportResult()
}

object PdfExporter {
    
    // URL единого сервера (теперь Face Recognition + PDF на одном порту 5000)
    private const val SERVER_URL = "https://api.totalcode.online"
    
    // OkHttp клиент с большими таймаутами для PDF
    // Используем только HTTP/1.1 — HTTP/2 не работает через Cloudflare Tunnel
    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))  // Отключаем HTTP/2
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    suspend fun exportFamilyTree(
        context: Context,
        members: List<FamilyMember>,
        format: PdfPageFormat = PdfPageFormat.A4_LANDSCAPE,
        serverUrl: String? = null
    ): ExportResult? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = serverUrl ?: SERVER_URL
            android.util.Log.d("PdfExporter", "📄 Начинаем экспорт PDF на сервер: $baseUrl")
            
            // Подготавливаем данные
            val membersJson = JSONArray()
            
            for (member in members) {
                val memberObj = JSONObject().apply {
                    put("id", member.id)
                    put("firstName", member.firstName)
                    put("lastName", member.lastName)
                    put("patronymic", member.patronymic ?: "")
                    put("birthDate", member.birthDate)
                    put("phoneNumber", member.phoneNumber ?: "")
                    put("role", member.role.name)
                    put("fatherId", member.fatherId)
                    put("motherId", member.motherId)
                    
                    // Добавляем фото в base64
                    val photoBase64 = getPhotoBase64(context, member.photoUri)
                    if (photoBase64 != null) {
                        put("photoBase64", photoBase64)
                    }
                }
                membersJson.put(memberObj)
            }
            
            val requestBodyJson = JSONObject().apply {
                put("members", membersJson)
                put("format", format.formatName)
            }
            
            val bodyString = requestBodyJson.toString()
            android.util.Log.d("PdfExporter", "📦 Размер запроса: ${bodyString.length} байт")
            
            // Создаём запрос через OkHttp
            val requestBody = bodyString.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$baseUrl/generate_pdf")
                .header("Accept", "application/json")
                .post(requestBody)
                .build()
            
            android.util.Log.d("PdfExporter", "📡 Отправляем запрос через OkHttp...")
            
            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                android.util.Log.d("PdfExporter", "📨 HTTP код ответа: ${response.code}")
                android.util.Log.d("PdfExporter", "📨 Content-Type: ${response.header("Content-Type")}")
                android.util.Log.d("PdfExporter", "📨 Content-Length: ${response.header("Content-Length")}")
                
                if (response.isSuccessful) {
                    // Читаем JSON ответ
                    val responseString = response.body?.string()
                    android.util.Log.d("PdfExporter", "📥 Получен ответ: ${responseString?.take(200)}...")
                    
                    if (responseString != null) {
                        val jsonResponse = JSONObject(responseString)
                        
                        if (jsonResponse.optBoolean("success", false)) {
                            val storage = jsonResponse.optString("storage", "base64")
                            val serverFilename = jsonResponse.optString("filename", "family_tree.pdf")
                            
                            android.util.Log.d("PdfExporter", "📦 Storage: $storage")
                            
                            if (storage == "google_drive") {
                                // Получаем все URL
                                val downloadPath = jsonResponse.getString("download_url")  // Относительный путь /download_pdf/...
                                val viewUrl = jsonResponse.optString("view_url", "")  // Ссылка на просмотр в Drive
                                
                                // Формируем полный URL для прокси скачивания
                                val proxyUrl = if (downloadPath.startsWith("/")) {
                                    "$baseUrl$downloadPath"  // Добавляем базовый URL сервера
                                } else {
                                    downloadPath
                                }
                                
                                android.util.Log.d("PdfExporter", "☁️ Proxy Download URL: $proxyUrl")
                                android.util.Log.d("PdfExporter", "👁️ View URL: $viewUrl")
                                
                                // Используем view_url для открытия (просмотр в Google Drive)
                                // Это надёжнее чем прямое скачивание через прокси
                                val finalUrl = if (viewUrl.isNotEmpty()) viewUrl else proxyUrl
                                
                                // Возвращаем специальный результат с URL
                                return@withContext ExportResult.DriveUrl(finalUrl, serverFilename)
                            } else {
                                // Base64 fallback
                                val pdfBase64 = jsonResponse.optString("pdf_base64", "")
                                if (pdfBase64.isEmpty()) {
                                    throw Exception("Нет данных PDF")
                                }
                                
                                // Декодируем base64 в байты
                                val pdfBytes = Base64.decode(pdfBase64, Base64.DEFAULT)
                                android.util.Log.d("PdfExporter", "📄 Декодировано ${pdfBytes.size} байт PDF")
                                
                                // Сохраняем PDF
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val exportDir = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    "FamilyTree"
                                )
                                if (!exportDir.exists()) {
                                    exportDir.mkdirs()
                                }
                                
                                val file = File(exportDir, "СемейноеДрево_$timestamp.pdf")
                                
                                FileOutputStream(file).use { output ->
                                    output.write(pdfBytes)
                                }
                                
                                android.util.Log.d("PdfExporter", "✅ PDF сохранён: ${file.absolutePath}")
                                
                                return@withContext ExportResult.LocalFile(file)
                            }
                        } else {
                            val error = jsonResponse.optString("error", "Неизвестная ошибка")
                            android.util.Log.e("PdfExporter", "❌ Ошибка от сервера: $error")
                            throw Exception(error)
                        }
                    } else {
                        android.util.Log.e("PdfExporter", "❌ Пустой ответ")
                        return@withContext null
                    }
                } else {
                    val error = response.body?.string()
                    android.util.Log.e("PdfExporter", "❌ Ошибка сервера: ${response.code} - $error")
                    throw Exception("Ошибка сервера: ${response.code}")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("PdfExporter", "❌ Исключение: ${e.message}", e)
            e.printStackTrace()
            // Если сервер недоступен, используем локальную генерацию
            android.util.Log.d("PdfExporter", "🔄 Переключаемся на локальную генерацию...")
            return@withContext exportLocalPdf(context, members, format)
        }
    }
    
    /**
     * Локальная генерация PDF (резервный вариант)
     */
    private fun exportLocalPdf(
        context: Context,
        members: List<FamilyMember>,
        format: PdfPageFormat
    ): ExportResult? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FamilyTree"
            )
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val file = File(exportDir, "СемейноеДрево_$timestamp.pdf")
            
            // Используем Android PdfDocument
            val pageWidth = if (format == PdfPageFormat.A4_LANDSCAPE || format == PdfPageFormat.A3_LANDSCAPE) 842 else 595
            val pageHeight = if (format == PdfPageFormat.A4_LANDSCAPE || format == PdfPageFormat.A3_LANDSCAPE) 595 else 842
            
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            // Простой текст
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 14f
                isAntiAlias = true
            }
            
            var y = 50f
            canvas.drawText("Family Tree - ${members.size} members", 50f, y, paint)
            y += 30f
            
            for (member in members.take(20)) {
                val text = "${member.lastName} ${member.firstName} - ${member.birthDate}"
                canvas.drawText(text, 50f, y, paint)
                y += 25f
                if (y > pageHeight - 50) break
            }
            
            pdfDocument.finishPage(page)
            
            FileOutputStream(file).use { output ->
                pdfDocument.writeTo(output)
            }
            pdfDocument.close()
            
            ExportResult.LocalFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun getPhotoBase64(context: Context, photoUri: String?): String? {
        if (photoUri.isNullOrEmpty()) return null
        
        return try {
            val bitmap = when {
                photoUri.startsWith("content://") -> {
                    val uri = Uri.parse(photoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                photoUri.startsWith("file://") -> {
                    val path = photoUri.replace("file://", "")
                    BitmapFactory.decodeFile(path)
                }
                else -> {
                    BitmapFactory.decodeFile(photoUri)
                }
            }
            
            if (bitmap != null) {
                // Уменьшаем размер для передачи
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                
                bitmap.recycle()
                scaledBitmap.recycle()
                
                "data:image/jpeg;base64,$base64"
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
