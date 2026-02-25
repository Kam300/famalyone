package com.example.familyone.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.Locale

object UniqueIdHelper {
    
    private const val PREFS_NAME = "app_settings"
    private const val KEY_DEVICE_ID = "device_id"
    
    /**
     * Получает или создает уникальный ID устройства
     */
    fun getDeviceId(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getLong(KEY_DEVICE_ID, 0L)
        
        if (deviceId == 0L) {
            // Стабильный ID на базе ANDROID_ID, чтобы не менять server IDs после переустановки.
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?.trim()
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }

            deviceId = if (androidId.isNotEmpty()) {
                val normalized = androidId.lowercase(Locale.US)
                val positiveHash = normalized.hashCode().toLong() and 0x7fffffffL
                (positiveHash % 900000L) + 100000L
            } else {
                // Fallback для редких случаев, когда ANDROID_ID недоступен.
                (System.currentTimeMillis() % 900000L) + 100000L
            }

            prefs.edit().putLong(KEY_DEVICE_ID, deviceId).apply()
            android.util.Log.d("UniqueIdHelper", "🆔 Создан device ID: $deviceId")
        }
        
        return deviceId
    }
    
    /**
     * Создает уникальный ID для сервера из локального ID члена семьи
     * Формат: device_id * 1000000 + member_id
     */
    fun toServerId(context: Context, memberId: Long): Long {
        val deviceId = getDeviceId(context)
        return deviceId * 1000000 + memberId
    }
    
    /**
     * Извлекает локальный ID члена семьи из серверного ID
     */
    fun fromServerId(serverId: Long): Long {
        return serverId % 1000000
    }
    
    /**
     * Извлекает device ID из серверного ID
     */
    fun getDeviceIdFromServerId(serverId: Long): Long {
        return serverId / 1000000
    }
}
