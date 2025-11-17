package com.example.familyone.utils

import android.content.Context
import android.content.SharedPreferences

enum class TreeTemplate {
    MODERN,     // Текущий стиль с карточками
    CLASSIC,    // Классический стиль древа
    PRINT_A4    // Шаблон для печати на А4
}

object TreeTemplateManager {
    private const val PREFS_NAME = "tree_template_prefs"
    private const val KEY_TEMPLATE = "selected_template"
    
    fun saveTemplate(context: Context, template: TreeTemplate) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TEMPLATE, template.name).apply()
    }
    
    fun getTemplate(context: Context): TreeTemplate {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val templateName = prefs.getString(KEY_TEMPLATE, TreeTemplate.MODERN.name)
        return try {
            TreeTemplate.valueOf(templateName ?: TreeTemplate.MODERN.name)
        } catch (e: IllegalArgumentException) {
            TreeTemplate.MODERN
        }
    }
}

