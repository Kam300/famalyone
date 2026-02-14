package com.example.familyone

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.familyone.utils.ApiServerConfig
import com.example.familyone.utils.ThemePreferences

class FamilyTreeApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        migrateServerUrlPrefs()
        
        // Apply saved theme
        val themePrefs = ThemePreferences(this)
        when (themePrefs.getTheme()) {
            ThemePreferences.THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            ThemePreferences.THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            ThemePreferences.THEME_SYSTEM -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun migrateServerUrlPrefs() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val normalizedUrl = ApiServerConfig.readUnifiedServerUrl(prefs)
        ApiServerConfig.writeUnifiedServerUrl(prefs, normalizedUrl)
    }
}

