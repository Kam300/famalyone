package com.example.familyone.utils

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object ApiServerConfig {

    const val DEFAULT_BASE_URL = "https://totalcode.indevs.in/api"

    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_FACE_SERVER_URL = "face_server_url"
    private const val KEY_PDF_SERVER_URL = "pdf_server_url"

    fun normalizeBaseUrl(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) {
            return DEFAULT_BASE_URL
        }

        val withScheme = if (hasScheme(raw)) {
            raw
        } else {
            if (looksLikeLocalHost(raw)) "http://$raw" else "https://$raw"
        }

        val parsed = runCatching { URI(withScheme) }.getOrNull()
            ?: return DEFAULT_BASE_URL
        val host = parsed.host ?: return DEFAULT_BASE_URL

        val isLocalHost = isLocalHost(host)
        val scheme = parsed.scheme?.lowercase() ?: if (isLocalHost) "http" else "https"
        val normalizedScheme = if (scheme == "http" && !isLocalHost) "https" else scheme

        val normalizedPath = normalizePath(parsed.path.orEmpty())
        val authority = buildAuthority(host, parsed.port)

        return "$normalizedScheme://$authority$normalizedPath"
    }

    fun candidateBaseUrls(input: String): List<String> {
        val primary = normalizeBaseUrl(input)
        val urls = linkedSetOf(primary)
        if (primary.endsWith("/api")) {
            urls.add(primary.removeSuffix("/api"))
        }
        return urls.toList()
    }

    fun readUnifiedServerUrl(prefs: SharedPreferences): String {
        val savedUrl = prefs.getString(KEY_SERVER_URL, null)
            ?: prefs.getString(KEY_FACE_SERVER_URL, null)
            ?: prefs.getString(KEY_PDF_SERVER_URL, null)
            ?: DEFAULT_BASE_URL
        return normalizeBaseUrl(savedUrl)
    }

    fun writeUnifiedServerUrl(prefs: SharedPreferences, url: String) {
        val normalized = normalizeBaseUrl(url)
        prefs.edit()
            .putString(KEY_SERVER_URL, normalized)
            .putString(KEY_FACE_SERVER_URL, normalized)
            .putString(KEY_PDF_SERVER_URL, normalized)
            .apply()
    }

    fun isRouteMismatch(code: Int, body: String?): Boolean {
        if (code == 404 || code == 405 || code == 501) {
            return true
        }

        if (body.isNullOrBlank()) {
            return false
        }

        val lowered = body.lowercase()
        val hasRouteMarkers = lowered.contains("not found") || lowered.contains("404")
        if (!hasRouteMarkers) {
            return false
        }

        return !isJsonPayload(body)
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank() || path == "/") {
            return "/api"
        }

        val trimmed = path.trimEnd('/')
        return if (trimmed.isBlank()) "/api" else trimmed
    }

    private fun hasScheme(value: String): Boolean {
        return Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value)
    }

    private fun looksLikeLocalHost(value: String): Boolean {
        val host = runCatching { URI("http://$value").host }.getOrNull()
            ?: value.substringBefore('/').substringBefore(':')
        return isLocalHost(host)
    }

    private fun isLocalHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "localhost" ||
            normalized == "127.0.0.1" ||
            normalized == "10.0.2.2" ||
            Regex("^192\\.168\\.\\d+\\.\\d+$").matches(normalized)
    }

    private fun buildAuthority(host: String, port: Int): String {
        val hostPart = if (host.contains(":") && !host.startsWith("[")) {
            "[$host]"
        } else {
            host
        }
        return if (port != -1) "$hostPart:$port" else hostPart
    }

    private fun isJsonPayload(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return false
        }

        if (trimmed.startsWith("{")) {
            return runCatching { JSONObject(trimmed) }.isSuccess
        }

        if (trimmed.startsWith("[")) {
            return runCatching { JSONArray(trimmed) }.isSuccess
        }

        return false
    }
}
