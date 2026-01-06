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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

enum class PdfPageFormat(val formatName: String, val displayName: String) {
    A4("A4", "A4 (210x297 мм)"),
    A4_LANDSCAPE("A4_LANDSCAPE", "A4 Альбомная"),
    A3("A3", "A3 (297x420 мм)"),
    A3_LANDSCAPE("A3_LANDSCAPE", "A3 Альбомная")
}

object PdfExporter {
    
    // URL единого сервера (теперь Face Recognition + PDF на одном порту 5000)
    private const val SERVER_URL = "http://10.0.2.2:5000"
    
    suspend fun exportFamilyTree(
        context: Context,
        members: List<FamilyMember>,
        format: PdfPageFormat = PdfPageFormat.A4_LANDSCAPE,
        serverUrl: String? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = serverUrl ?: SERVER_URL
            
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
            
            val requestBody = JSONObject().apply {
                put("members", membersJson)
                put("format", format.formatName)
            }
            
            // Отправляем запрос
            val url = URL("$baseUrl/generate_pdf")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }
            
            // Отправляем данные
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            // Проверяем ответ
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
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
                
                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                
                return@withContext file
            } else {
                // Читаем ошибку
                val error = connection.errorStream?.bufferedReader()?.readText()
                throw Exception("Ошибка сервера: ${connection.responseCode} - $error")
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Если сервер недоступен, используем локальную генерацию
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
    ): File? {
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
            
            file
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
