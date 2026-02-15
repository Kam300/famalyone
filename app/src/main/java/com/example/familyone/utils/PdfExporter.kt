package com.example.familyone.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
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
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PdfPageFormat(val formatName: String, val displayName: String) {
    A4("A4", "A4 (210x297 мм)"),
    A4_LANDSCAPE("A4_LANDSCAPE", "A4 Альбомная"),
    A3("A3", "A3 (297x420 мм)"),
    A3_LANDSCAPE("A3_LANDSCAPE", "A3 Альбомная")
}

data class PdfSettings(
    val format: PdfPageFormat = PdfPageFormat.A4_LANDSCAPE,
    val showPhotos: Boolean = true,
    val showDates: Boolean = true,
    val showPatronymic: Boolean = true,
    val title: String = "Семейное Древо",
    val photoQuality: String = "medium" // low, medium, high
)

sealed class ExportResult {
    data class LocalFile(val file: File) : ExportResult()
    data class DriveUrl(val downloadUrl: String, val filename: String) : ExportResult()
}

object PdfExporter {

    private const val TAG = "PdfExporter"
    private const val SERVER_URL = ApiServerConfig.DEFAULT_BASE_URL

    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun exportFamilyTree(
        context: Context,
        members: List<FamilyMember>,
        pdfSettings: PdfSettings = PdfSettings(),
        serverUrl: String? = null
    ): ExportResult? = withContext(Dispatchers.IO) {
        try {
            val configuredUrl = serverUrl ?: SERVER_URL
            val normalizedBaseUrl = ApiServerConfig.normalizeBaseUrl(configuredUrl)
            val candidateBaseUrls = ApiServerConfig.candidateBaseUrls(normalizedBaseUrl)

            Log.d(TAG, "Starting PDF export. Normalized base URL: $normalizedBaseUrl")

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

                    val photoBase64 = getPhotoBase64(context, member.photoUri)
                    if (photoBase64 != null) {
                        put("photoBase64", photoBase64)
                    }
                }
                membersJson.put(memberObj)
            }

            val requestBodyJson = JSONObject().apply {
                put("members", membersJson)
                put("format", pdfSettings.format.formatName)
                put("use_drive", true)
                put("show_photos", pdfSettings.showPhotos)
                put("show_dates", pdfSettings.showDates)
                put("show_patronymic", pdfSettings.showPatronymic)
                put("title", pdfSettings.title)
                put("photo_quality", pdfSettings.photoQuality)
            }

            val bodyString = requestBodyJson.toString()
            Log.d(TAG, "Request body size: ${bodyString.length} bytes")

            for ((index, baseUrl) in candidateBaseUrls.withIndex()) {
                val candidateType = if (index == 0) "primary" else "legacy"
                val endpointUrl = "$baseUrl/generate_pdf"
                Log.d(TAG, "[$candidateType] POST $endpointUrl")

                val requestBody = bodyString.toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(endpointUrl)
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                var responseCode = 0
                var responseBody = ""
                var isSuccessful = false
                client.newCall(request).execute().use { response ->
                    responseCode = response.code
                    responseBody = response.body?.string().orEmpty()
                    isSuccessful = response.isSuccessful
                }
                val routeMismatch = ApiServerConfig.isRouteMismatch(responseCode, responseBody)
                val shouldTryLegacy = routeMismatch && index < candidateBaseUrls.lastIndex

                if (!isSuccessful) {
                    if (shouldTryLegacy) {
                        Log.w(
                            TAG,
                            "[$candidateType] route mismatch (HTTP $responseCode), fallback to legacy candidate"
                        )
                    } else {
                        throw Exception("Server error: $responseCode ${responseBody.take(200)}")
                    }
                } else {
                    val jsonResponse = try {
                        JSONObject(responseBody)
                    } catch (_: Exception) {
                        if (shouldTryLegacy) {
                            Log.w(TAG, "[$candidateType] non-JSON route mismatch response, fallback to legacy")
                            null
                        } else {
                            throw Exception("Invalid JSON response from PDF server")
                        }
                    }

                    if (jsonResponse != null) {
                        if (!jsonResponse.optBoolean("success", false)) {
                            val error = jsonResponse.optString("error", "Unknown PDF server error")
                            throw Exception(error)
                        }

                        val storage = jsonResponse.optString("storage", "base64")
                        val serverFilename = jsonResponse.optString("filename", "family_tree.pdf")
                        Log.d(TAG, "[$candidateType] storage=$storage")

                        if (storage == "google_drive") {
                            val downloadPath = jsonResponse.getString("download_url")
                            val viewUrl = jsonResponse.optString("view_url", "")
                            val origin = originFromBaseUrl(baseUrl)
                            val proxyUrl = toAbsoluteUrl(origin, downloadPath)
                            val finalUrl = if (viewUrl.isNotBlank()) viewUrl else proxyUrl
                            return@withContext ExportResult.DriveUrl(finalUrl, serverFilename)
                        }

                        val pdfBase64 = jsonResponse.optString("pdf_base64", "")
                        if (pdfBase64.isBlank()) {
                            throw Exception("Empty PDF payload")
                        }

                        val pdfBytes = Base64.decode(pdfBase64, Base64.DEFAULT)
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
                        return@withContext ExportResult.LocalFile(file)
                    }
                }
            }

            throw Exception("No suitable URL candidate for /generate_pdf")
        } catch (e: Exception) {
            Log.e(TAG, "PDF export failed, switching to local fallback: ${e.message}", e)
            return@withContext exportLocalPdf(context, members, pdfSettings.format)
        }
    }

    private fun originFromBaseUrl(baseUrl: String): String {
        val parsed = URI(baseUrl)
        val host = parsed.host ?: throw IllegalArgumentException("Invalid base URL host")
        val hostPart = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
        val authority = if (parsed.port != -1) "$hostPart:${parsed.port}" else hostPart
        return "${parsed.scheme}://$authority"
    }

    private fun toAbsoluteUrl(origin: String, downloadPath: String): String {
        return when {
            downloadPath.startsWith("http://") || downloadPath.startsWith("https://") -> downloadPath
            downloadPath.startsWith("/") -> "$origin$downloadPath"
            else -> "$origin/$downloadPath"
        }
    }

    private fun exportLocalPdf(
        context: Context,
        members: List<FamilyMember>,
        format: PdfPageFormat = PdfPageFormat.A4_LANDSCAPE
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

            val pageWidth = if (format == PdfPageFormat.A4_LANDSCAPE || format == PdfPageFormat.A3_LANDSCAPE) 842 else 595
            val pageHeight = if (format == PdfPageFormat.A4_LANDSCAPE || format == PdfPageFormat.A3_LANDSCAPE) 595 else 842

            val pdfDocument = android.graphics.pdf.PdfDocument()
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
            }

            val headerPaint = android.graphics.Paint(paint).apply {
                textSize = 22f
                isFakeBoldText = true
                color = android.graphics.Color.parseColor("#3D2B1F")
            }
            val namePaint = android.graphics.Paint(paint).apply {
                textSize = 13f
                isFakeBoldText = true
                color = android.graphics.Color.parseColor("#3D2B1F")
            }
            val detailPaint = android.graphics.Paint(paint).apply {
                textSize = 11f
                color = android.graphics.Color.parseColor("#666050")
            }
            val rolePaint = android.graphics.Paint(paint).apply {
                textSize = 12f
                color = android.graphics.Color.parseColor("#338050")
            }

            // Группировка по ролям
            val roleGroups = members.groupBy { it.role.name }
            val orderedGroups = listOf(
                "GRANDFATHER" to "Бабушки и Дедушки", "GRANDMOTHER" to "Бабушки и Дедушки",
                "FATHER" to "Родители", "MOTHER" to "Родители",
                "SON" to "Дети", "DAUGHTER" to "Дети",
                "BROTHER" to "Дети", "SISTER" to "Дети"
            )

            // Собираем по поколениям
            val generations = linkedMapOf<String, MutableList<FamilyMember>>()
            for ((role, genName) in orderedGroups) {
                roleGroups[role]?.let { list ->
                    generations.getOrPut(genName) { mutableListOf() }.addAll(list)
                }
            }
            // Оставшиеся
            val usedRoles = orderedGroups.map { it.first }.toSet()
            val otherMembers = members.filter { it.role.name !in usedRoles }
            if (otherMembers.isNotEmpty()) {
                generations.getOrPut("Другие") { mutableListOf() }.addAll(otherMembers)
            }

            val cardW = 140f
            val cardH = 80f
            val gapX = 16f
            val gapY = 20f
            var pageNum = 1
            var currentPage: android.graphics.pdf.PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var y = 0f

            fun startNewPage() {
                currentPage?.let { pdfDocument.finishPage(it) }
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage!!.canvas
                // Фон
                canvas!!.drawColor(android.graphics.Color.parseColor("#F8F4E8"))
                y = 50f
                // Заголовок
                val titleText = if (pageNum == 1) "Семейное Древо" else "Семейное Древо (стр. $pageNum)"
                val tw = headerPaint.measureText(titleText)
                canvas!!.drawText(titleText, (pageWidth - tw) / 2, y, headerPaint)
                y += 40f
                pageNum++
            }

            startNewPage()

            for ((genName, genMembers) in generations) {
                // Метка поколения
                if (y + cardH + 30 > pageHeight - 40) {
                    startNewPage()
                }
                val labelPaint = android.graphics.Paint(paint).apply {
                    textSize = 14f
                    isFakeBoldText = true
                    color = android.graphics.Color.parseColor("#6B4226")
                }
                val lw = labelPaint.measureText(genName)
                canvas!!.drawText(genName, (pageWidth - lw) / 2, y, labelPaint)
                y += 22f

                // Карточки в ряды
                val maxPerRow = ((pageWidth - 60) / (cardW + gapX)).toInt().coerceAtLeast(1)
                val rows = genMembers.chunked(maxPerRow)

                for (row in rows) {
                    if (y + cardH + gapY > pageHeight - 40) {
                        startNewPage()
                    }
                    val totalW = row.size * cardW + (row.size - 1) * gapX
                    var x = (pageWidth - totalW) / 2

                    for (member in row) {
                        // Тень
                        val shadowPaint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#B8B0A0") }
                        canvas!!.drawRoundRect(x + 2, y + 2, x + cardW + 2, y + cardH + 2, 8f, 8f, shadowPaint)
                        // Фон карточки
                        val cardBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#FAF6EE") }
                        canvas!!.drawRoundRect(x, y, x + cardW, y + cardH, 8f, 8f, cardBg)
                        // Рамка
                        val borderPaint = android.graphics.Paint().apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.5f
                            color = android.graphics.Color.parseColor("#998060")
                        }
                        canvas!!.drawRoundRect(x, y, x + cardW, y + cardH, 8f, 8f, borderPaint)

                        // Имя
                        val name = "${member.lastName} ${member.firstName}"
                        val nw = namePaint.measureText(name)
                        canvas!!.drawText(name, x + (cardW - nw) / 2, y + 22f, namePaint)

                        // Роль
                        val roleText = getRoleName(member.role.name)
                        val rw = rolePaint.measureText(roleText)
                        canvas!!.drawText(roleText, x + (cardW - rw) / 2, y + 40f, rolePaint)

                        // Дата
                        val dw = detailPaint.measureText(member.birthDate)
                        canvas!!.drawText(member.birthDate, x + (cardW - dw) / 2, y + 56f, detailPaint)

                        x += cardW + gapX
                    }
                    y += cardH + gapY
                }
                y += 10f
            }

            currentPage?.let { pdfDocument.finishPage(it) }
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

    private fun getRoleName(role: String): String {
        return when (role) {
            "GRANDFATHER" -> "Дедушка"
            "GRANDMOTHER" -> "Бабушка"
            "FATHER" -> "Отец"
            "MOTHER" -> "Мать"
            "SON" -> "Сын"
            "DAUGHTER" -> "Дочь"
            "BROTHER" -> "Брат"
            "SISTER" -> "Сестра"
            "UNCLE" -> "Дядя"
            "AUNT" -> "Тётя"
            "NEPHEW" -> "Племянник"
            "NIECE" -> "Племянница"
            "GRANDSON" -> "Внук"
            "GRANDDAUGHTER" -> "Внучка"
            else -> "Родственник"
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
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

                bitmap.recycle()
                scaledBitmap.recycle()

                "data:image/jpeg;base64,$base64"
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
