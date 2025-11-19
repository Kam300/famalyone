package com.example.familyone.utils

import android.content.Context
import android.content.SharedPreferences

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
            // Генерируем уникальный ID на основе времени
            deviceId = System.currentTimeMillis() % 1000000 // Последние 6 цифр
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
