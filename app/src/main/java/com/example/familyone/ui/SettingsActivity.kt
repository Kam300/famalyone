package com.example.familyone.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.familyone.R
import com.example.familyone.api.FaceRecognitionApi
import com.example.familyone.databinding.ActivitySettingsBinding
import com.example.familyone.utils.ApiServerConfig
import com.example.familyone.utils.BiometricHelper
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
        setupBiometricToggle()
    }

    private fun setupBiometricToggle() {
        binding.switchBiometric.isChecked = BiometricHelper.isEnabled(this)

        val canAuth = BiometricHelper.canAuthenticate(this)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            binding.tvBiometricStatus.visibility = View.VISIBLE
            binding.tvBiometricStatus.text = BiometricHelper.getStatusMessage(this)

            if (
                canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                canAuth == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
            ) {
                binding.switchBiometric.isEnabled = false
            }
        }

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (BiometricHelper.isBiometricAvailable(this)) {
                    BiometricHelper.showPrompt(
                        activity = this,
                        title = getString(R.string.biometric_title),
                        subtitle = "Подтвердите для включения защиты",
                        negativeButtonText = getString(R.string.biometric_cancel),
                        onSuccess = {
                            BiometricHelper.setEnabled(this, true)
                            toast("✓ Биометрическая защита включена")
                        },
                        onError = { error ->
                            binding.switchBiometric.isChecked = false
                            toast("Ошибка: $error")
                        },
                        onCancel = {
                            binding.switchBiometric.isChecked = false
                        }
                    )
                } else {
                    binding.switchBiometric.isChecked = false
                    binding.tvBiometricStatus.visibility = View.VISIBLE
                    binding.tvBiometricStatus.text = BiometricHelper.getStatusMessage(this)
                    toast(BiometricHelper.getStatusMessage(this))
                }
            } else {
                BiometricHelper.setEnabled(this, false)
                toast("Биометрическая защита отключена")
            }
        }
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

        binding.btnOpenBackup.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }

        val savedServerUrl = ApiServerConfig.readUnifiedServerUrl(notificationPrefs)
        binding.etFaceServerUrl.setText(savedServerUrl)
        binding.etPdfServerUrl.setText(savedServerUrl)

        binding.btnSaveFaceServerUrl.setOnClickListener {
            val rawUrl = binding.etFaceServerUrl.text.toString().trim()
            if (rawUrl.isEmpty()) {
                toast("Введите URL сервера")
                return@setOnClickListener
            }

            ApiServerConfig.writeUnifiedServerUrl(notificationPrefs, rawUrl)
            val normalized = ApiServerConfig.readUnifiedServerUrl(notificationPrefs)
            binding.etFaceServerUrl.setText(normalized)
            binding.etPdfServerUrl.setText(normalized)
            FaceRecognitionApi.setServerUrl(normalized)
            toast("✓ URL сервера сохранен (Face + PDF)")
        }

        binding.btnTestFaceServer.setOnClickListener {
            testFaceServer()
        }

        binding.btnSaveServerUrl.setOnClickListener {
            val rawUrl = binding.etPdfServerUrl.text.toString().trim()
            if (rawUrl.isEmpty()) {
                toast("Введите URL сервера")
                return@setOnClickListener
            }

            ApiServerConfig.writeUnifiedServerUrl(notificationPrefs, rawUrl)
            val normalized = ApiServerConfig.readUnifiedServerUrl(notificationPrefs)
            binding.etFaceServerUrl.setText(normalized)
            binding.etPdfServerUrl.setText(normalized)
            FaceRecognitionApi.setServerUrl(normalized)
            toast("✓ URL сервера сохранен (Face + PDF)")
        }
    }

    private fun testFaceServer() {
        val url = binding.etFaceServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            toast("Введите URL сервера")
            return
        }

        binding.tvFaceServerStatus.visibility = View.VISIBLE
        binding.tvFaceServerStatus.text = "🔄 Проверка подключения..."
        binding.tvFaceServerStatus.setTextColor(getColor(R.color.text_secondary_light))

        FaceRecognitionApi.setServerUrl(url)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val isConnected = FaceRecognitionApi.checkHealth()

            if (isConnected) {
                binding.tvFaceServerStatus.text = "✓ Сервер доступен!"
                binding.tvFaceServerStatus.setTextColor(getColor(R.color.green_accent))

                ApiServerConfig.writeUnifiedServerUrl(notificationPrefs, url)
                val normalized = ApiServerConfig.readUnifiedServerUrl(notificationPrefs)
                binding.etFaceServerUrl.setText(normalized)
                binding.etPdfServerUrl.setText(normalized)
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
            val notificationWork = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS).build()
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
                recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun applyTheme(theme: Int) {
        when (theme) {
            ThemePreferences.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemePreferences.THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ThemePreferences.THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
