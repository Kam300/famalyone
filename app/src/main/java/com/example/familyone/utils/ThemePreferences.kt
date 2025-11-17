package com.example.familyone.utils

import android.content.Context
import android.content.SharedPreferences

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_THEME = "selected_theme"
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
    }
    
    fun saveTheme(theme: Int) {
        prefs.edit().putInt(KEY_THEME, theme).apply()
    }
    
    fun getTheme(): Int {
        return prefs.getInt(KEY_THEME, THEME_SYSTEM)
    }
}

