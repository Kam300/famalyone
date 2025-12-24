package com.example.familyone.ui

import android.app.TimePickerDialog
import android.content.Context

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.familyone.R
import com.example.familyone.databinding.ActivitySettingsBinding

import com.example.familyone.utils.ThemePreferences
import com.example.familyone.utils.toast

import com.example.familyone.workers.NotificationWorker
import kotlinx.coroutines.launch

import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themePrefs: ThemePreferences
    private lateinit var notificationPrefs: android.content.SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        themePrefs = ThemePreferences(this)
        notificationPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        
        updateCurrentThemeText()
        loadNotificationSettings()
        setupClickListeners()
        setupNotificationListeners()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnSelectTheme.setOnClickListener {
            showThemeDialog()
        }
        
        binding.btnSetNotificationTime.setOnClickListener {
            showTimePickerDialog()
        }
        
        // === Сервер распознавания лиц ===
        val savedFaceUrl = notificationPrefs.getString("face_server_url", "http://10.0.2.2:5000")
        binding.etFaceServerUrl.setText(savedFaceUrl)
        
        binding.btnSaveFaceServerUrl.setOnClickListener {
            val url = binding.etFaceServerUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                notificationPrefs.edit().putString("face_server_url", url).apply()
                com.example.familyone.api.FaceRecognitionApi.setServerUrl(url)
                toast("✓ URL сервера распознавания сохранён")
            } else {
                toast("Введите URL сервера")
            }
        }
        
        binding.btnTestFaceServer.setOnClickListener {
            testFaceServer()
        }
        
        // === Сервер PDF ===
        val savedUrl = notificationPrefs.getString("pdf_server_url", "")
        binding.etPdfServerUrl.setText(savedUrl)
        
        binding.btnSaveServerUrl.setOnClickListener {
            val url = binding.etPdfServerUrl.text.toString().trim()
            notificationPrefs.edit().putString("pdf_server_url", url).apply()
            toast("URL сервера PDF сохранён")
        }
    }
    
    private fun testFaceServer() {
        val url = binding.etFaceServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            toast("Введите URL сервера")
            return
        }
        
        binding.tvFaceServerStatus.visibility = android.view.View.VISIBLE
        binding.tvFaceServerStatus.text = "🔄 Проверка подключения..."
        binding.tvFaceServerStatus.setTextColor(getColor(R.color.text_secondary_light))
        
        com.example.familyone.api.FaceRecognitionApi.setServerUrl(url)
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val isConnected = com.example.familyone.api.FaceRecognitionApi.checkHealth()
            
            if (isConnected) {
                binding.tvFaceServerStatus.text = "✓ Сервер доступен!"
                binding.tvFaceServerStatus.setTextColor(getColor(R.color.green_accent))
                
                // Автоматически сохраняем если подключение успешно
                notificationPrefs.edit().putString("face_server_url", url).apply()
            } else {
                binding.tvFaceServerStatus.text = "✗ Сервер недоступен. Проверьте URL и сеть"
                binding.tvFaceServerStatus.setTextColor(getColor(R.color.red_button))
            }
        }
    }
    
    private fun setupNotificationListeners() {
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            notificationPrefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            updateNotificationWorker()
        }
        
        binding.switchBirthdays.setOnCheckedChangeListener { _, isChecked ->
            notificationPrefs.edit().putBoolean("birthday_reminders", isChecked).apply()
        }
        
        binding.switchWeddings.setOnCheckedChangeListener { _, isChecked ->
            notificationPrefs.edit().putBoolean("wedding_reminders", isChecked).apply()
        }
        
        binding.sliderReminderDays.addOnChangeListener { _, value, _ ->
            val days = value.toInt()
            notificationPrefs.edit().putInt("reminder_days_before", days).apply()
            updateReminderDaysText(days)
        }
    }
    
    private fun loadNotificationSettings() {
        binding.switchNotifications.isChecked = notificationPrefs.getBoolean("notifications_enabled", true)
        binding.switchBirthdays.isChecked = notificationPrefs.getBoolean("birthday_reminders", true)
        binding.switchWeddings.isChecked = notificationPrefs.getBoolean("wedding_reminders", true)
        
        val reminderDays = notificationPrefs.getInt("reminder_days_before", 3)
        binding.sliderReminderDays.value = reminderDays.toFloat()
        updateReminderDaysText(reminderDays)
        
        val hour = notificationPrefs.getInt("notification_hour", 9)
        val minute = notificationPrefs.getInt("notification_minute", 0)
        updateNotificationTimeText(hour, minute)
    }
    
    private fun updateReminderDaysText(days: Int) {
        val text = when (days) {
            1 -> "1 день"
            2, 3, 4 -> "$days дня"
            else -> "$days дней"
        }
        binding.tvReminderDaysValue.text = text
    }
    
    private fun updateNotificationTimeText(hour: Int, minute: Int) {
        binding.btnSetNotificationTime.text = String.format("%02d:%02d", hour, minute)
    }
    
    private fun showTimePickerDialog() {
        val hour = notificationPrefs.getInt("notification_hour", 9)
        val minute = notificationPrefs.getInt("notification_minute", 0)
        
        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                notificationPrefs.edit()
                    .putInt("notification_hour", selectedHour)
                    .putInt("notification_minute", selectedMinute)
                    .apply()
                updateNotificationTimeText(selectedHour, selectedMinute)
                updateNotificationWorker()
            },
            hour,
            minute,
            true
        ).show()
    }
    
    private fun updateNotificationWorker() {
        val notificationsEnabled = notificationPrefs.getBoolean("notifications_enabled", true)
        
        if (notificationsEnabled) {
            val notificationWork = PeriodicWorkRequestBuilder<NotificationWorker>(
                24, TimeUnit.HOURS
            ).build()
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "daily_notification_work",
                ExistingPeriodicWorkPolicy.REPLACE,
                notificationWork
            )
        } else {
            WorkManager.getInstance(this).cancelUniqueWork("daily_notification_work")
        }
    }
    
    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.light_theme),
            getString(R.string.dark_theme),
            getString(R.string.system_theme)
        )
        
        val currentTheme = themePrefs.getTheme()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_theme))
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                themePrefs.saveTheme(which)
                applyTheme(which)
                updateCurrentThemeText()
                dialog.dismiss()
                
                // Recreate activity to apply theme
                recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun applyTheme(theme: Int) {
        when (theme) {
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
    
    private fun updateCurrentThemeText() {
        val currentThemeName = when (themePrefs.getTheme()) {
            ThemePreferences.THEME_LIGHT -> getString(R.string.light_theme)
            ThemePreferences.THEME_DARK -> getString(R.string.dark_theme)
            ThemePreferences.THEME_SYSTEM -> getString(R.string.system_theme)
            else -> getString(R.string.system_theme)
        }
        
        binding.tvCurrentTheme.text = currentThemeName
    }
}
