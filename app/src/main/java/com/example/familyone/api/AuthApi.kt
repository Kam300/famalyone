package com.example.familyone.api

import com.example.familyone.utils.ApiServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AuthSnapshot(
    val displayName: String,
    val email: String?,
    val preferredAuthProvider: String?,
    val yandexConfigured: Boolean,
    val yandexConnected: Boolean,
    val yandexDisplayName: String?,
    val yandexEmail: String?
)

object AuthApi {

    private const val USER_AGENT = "FamilyOneAuth/1.0"
    private const val DEVICE_HEADER = "X-FamilyOne-Device"

    private var serverUrl = ApiServerConfig.DEFAULT_BASE_URL
    private var deviceId: String = ""

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    fun setServerUrl(url: String) {
        serverUrl = ApiServerConfig.normalizeBaseUrl(url)
    }

    fun setDeviceId(rawDeviceId: Long) {
        deviceId = rawDeviceId.toString()
    }

    fun buildYandexMobileStartUrl(appRedirectUri: String): String {
        val baseUrl = ApiServerConfig.normalizeBaseUrl(serverUrl)
        val params = linkedMapOf(
            "device_id" to deviceId,
            "app_redirect_uri" to appRedirectUri
        )
        return "$baseUrl/v2/auth/yandex/mobile/start?${buildQuery(params)}"
    }

    suspend fun bootstrap(displayName: String? = null): Result<AuthSnapshot> = withContext(Dispatchers.IO) {
        try {
            if (deviceId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Device ID не настроен"))
            }

            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                if (!displayName.isNullOrBlank()) {
                    put("displayName", displayName)
                }
            }

            val response = executeJsonWithRouteFallback(
                endpoint = "v2/auth/bootstrap",
                method = "POST",
                body = payload.toString()
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code} for auth/bootstrap: ${response.body.take(300)}")
                )
            }

            val json = JSONObject(response.body)
            Result.success(parseSnapshot(json))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
        val isSuccessful: Boolean
    )

    private fun executeJsonWithRouteFallback(
        endpoint: String,
        method: String,
        body: String
    ): HttpResponse {
        val baseCandidates = ApiServerConfig.candidateBaseUrls(serverUrl)
        var lastResponse: HttpResponse? = null

        for ((index, baseUrl) in baseCandidates.withIndex()) {
            val url = "$baseUrl/$endpoint"
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .applyDeviceHeader()

            when (method) {
                "POST" -> requestBuilder.post(body.toRequestBody(jsonMediaType))
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val candidateResponse = HttpResponse(
                    code = response.code,
                    body = bodyString,
                    isSuccessful = response.isSuccessful
                )
                lastResponse = candidateResponse
                val shouldTryFallback =
                    !response.isSuccessful &&
                        index < baseCandidates.lastIndex &&
                        ApiServerConfig.isRouteMismatch(response.code, bodyString)

                if (shouldTryFallback) {
                    return@use
                }

                return candidateResponse
            }
        }

        return lastResponse ?: throw IllegalStateException("No auth route candidates available")
    }

    private fun parseSnapshot(json: JSONObject): AuthSnapshot {
        val providers = json.optJSONObject("providers")
        val yandexConfigured = providers
            ?.optJSONObject("yandex")
            ?.optBoolean("configured", false)
            ?: false

        val auth = json.optJSONObject("auth")
        val user = auth?.optJSONObject("user")
        val identities = user?.optJSONArray("providers")

        var yandexDisplayName: String? = null
        var yandexEmail: String? = null
        var yandexConnected = false

        if (identities != null) {
            for (index in 0 until identities.length()) {
                val identity = identities.optJSONObject(index) ?: continue
                if (identity.optString("provider") == "yandex") {
                    yandexConnected = true
                    yandexDisplayName = identity.optString("displayName").ifBlank { null }
                    yandexEmail = identity.optString("email").ifBlank { null }
                    break
                }
            }
        }

        return AuthSnapshot(
            displayName = user?.optString("displayName").orEmpty(),
            email = user?.optString("email")?.ifBlank { null },
            preferredAuthProvider = user?.optString("preferredAuthProvider")?.ifBlank { null },
            yandexConfigured = yandexConfigured,
            yandexConnected = yandexConnected,
            yandexDisplayName = yandexDisplayName,
            yandexEmail = yandexEmail
        )
    }

    private fun buildQuery(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, Charsets.UTF_8.name())}=" +
                java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
        }
    }

    private fun Request.Builder.applyDeviceHeader(): Request.Builder {
        if (deviceId.isNotBlank()) {
            addHeader(DEVICE_HEADER, deviceId)
        }
        return this
    }
}
