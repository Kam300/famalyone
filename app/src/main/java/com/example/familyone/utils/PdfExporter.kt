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
        format: PdfPageFormat = PdfPageFormat.A4_LANDSCAPE,
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
                put("format", format.formatName)
                put("use_drive", true)
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
            return@withContext exportLocalPdf(context, members, format)
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

            val pageWidth = if (format == PdfPageFormat.A4_LANDSCAPE || format == PdfPageFormat.A3_LANDSCAPE) {
                842
            } else {
                595
            }
            val pageHeight = if (format == PdfPageFormat.A4_LANDSCAPE || format == PdfPageFormat.A3_LANDSCAPE) {
                595
            } else {
                842
            }

            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

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
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
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


