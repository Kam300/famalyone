package com.example.familyone.ui

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.familyone.R
import com.example.familyone.databinding.ActivitySettingsBinding
import com.example.familyone.utils.DataImportExport
import com.example.familyone.utils.ThemePreferences
import com.example.familyone.utils.toast
import com.example.familyone.viewmodel.FamilyViewModel
import com.example.familyone.workers.NotificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themePrefs: ThemePreferences
    private lateinit var notificationPrefs: android.content.SharedPreferences
    private lateinit var viewModel: FamilyViewModel
    
    private val importLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { importData(it) }
    }
    
    private val exportLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportData(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        themePrefs = ThemePreferences(this)
        notificationPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]
        
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
        
        binding.btnImportData.setOnClickListener {
            importLauncher.launch("application/json")
        }
        
        binding.btnExportData.setOnClickListener {
            exportLauncher.launch("family_data_${System.currentTimeMillis()}.json")
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
        
        binding.tvCurrentTheme.text = "Текущая тема: $currentThemeName"
    }



    
    private fun importData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    DataImportExport.importFromJson(this@SettingsActivity, uri)
                }
                
                result.onSuccess { importMembers ->
                    if (importMembers.isEmpty()) {
                        toast("Файл не содержит данных")
                        return@onSuccess
                    }
                    
                    android.util.Log.d("Import", "Starting import of ${importMembers.size} members")
                    importMembers.forEach { member ->
                        android.util.Log.d("Import", "Member: ${member.firstName} ${member.lastName}, ID: ${member.id}, Father: ${member.fatherId}, Mother: ${member.motherId}")
                    }
                    
                    // Получаем существующих членов семьи ОДИН РАЗ
                    val existingMembers = withContext(Dispatchers.IO) {
                        viewModel.getAllMembersSync()
                    }
                    
                    var addedCount = 0
                    var skippedCount = 0
                    val duplicates = mutableListOf<String>()
                    
                    // Карта для сопоставления старых ID с новыми
                    val idMapping = mutableMapOf<Long, Long>()
                    
                    // ШАГ 1: Добавляем всех членов БЕЗ связей
                    importMembers.forEach { member ->
                        val isDuplicate = existingMembers.any { existing ->
                            existing.firstName == member.firstName &&
                            existing.lastName == member.lastName &&
                            existing.birthDate == member.birthDate
                        }
                        
                        if (isDuplicate) {
                            skippedCount++
                            duplicates.add("${member.firstName} ${member.lastName}")
                        } else {
                            // Сохраняем старый ID
                            val oldId = member.id
                            
                            // Добавляем члена БЕЗ связей
                            val memberWithoutLinks = member.copy(
                                id = 0, // Auto-generate
                                fatherId = null,
                                motherId = null
                            )
                            
                            // Вставляем и получаем новый ID
                            val newId = withContext(Dispatchers.IO) {
                                viewModel.insertMemberSync(memberWithoutLinks)
                            }
                            
                            // Сохраняем маппинг старого ID на новый
                            idMapping[oldId] = newId
                            android.util.Log.d("Import", "Mapped: old ID $oldId -> new ID $newId (${member.firstName} ${member.lastName})")
                            addedCount++
                        }
                    }
                    
                    android.util.Log.d("Import", "ID Mapping complete: ${idMapping.size} entries")
                    
                    // ШАГ 2: Обновляем связи с родителями
                    var linksUpdated = 0
                    var linksFailed = 0
                    
                    importMembers.forEach { member ->
                        val isDuplicate = existingMembers.any { existing ->
                            existing.firstName == member.firstName &&
                            existing.lastName == member.lastName &&
                            existing.birthDate == member.birthDate
                        }
                        
                        if (!isDuplicate && (member.fatherId != null || member.motherId != null)) {
                            val newId = idMapping[member.id]
                            
                            // Проверяем, есть ли родители в маппинге
                            val newFatherId = member.fatherId?.let { oldFatherId ->
                                idMapping[oldFatherId] ?: run {
                                    // Родитель не найден в импортируемых данных
                                    android.util.Log.w("Import", "Father ID $oldFatherId not found for ${member.firstName} ${member.lastName}")
                                    null
                                }
                            }
                            
                            val newMotherId = member.motherId?.let { oldMotherId ->
                                idMapping[oldMotherId] ?: run {
                                    // Родитель не найден в импортируемых данных
                                    android.util.Log.w("Import", "Mother ID $oldMotherId not found for ${member.firstName} ${member.lastName}")
                                    null
                                }
                            }
                            
                            if (newId != null && (newFatherId != null || newMotherId != null)) {
                                withContext(Dispatchers.IO) {
                                    viewModel.updateMemberParents(newId, newFatherId, newMotherId)
                                }
                                linksUpdated++
                            } else if (newId != null) {
                                linksFailed++
                            }
                        }
                    }
                    
                    android.util.Log.d("Import", "Links updated: $linksUpdated, failed: $linksFailed")
                    
                    // Формируем сообщение о результате
                    val message = buildString {
                        append("Добавлено: $addedCount\n")
                        append("Связей восстановлено: $linksUpdated\n")
                        if (linksFailed > 0) {
                            append("⚠️ Связей не восстановлено: $linksFailed\n")
                            append("(родители отсутствуют в файле)\n")
                        }
                        if (skippedCount > 0) {
                            append("\nПропущено дубликатов: $skippedCount\n")
                            if (duplicates.size <= 5) {
                                append("\nДубликаты:\n")
                                duplicates.forEach { append("• $it\n") }
                            } else {
                                append("\nДубликаты:\n")
                                duplicates.take(5).forEach { append("• $it\n") }
                                append("...и ещё ${duplicates.size - 5}")
                            }
                        }
                    }
                    
                    toast("Импорт завершён: добавлено $addedCount")
                    
                    // Показываем диалог с результатом ОДИН РАЗ
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Импорт завершён")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                        
                }.onFailure { error ->
                    toast("Ошибка импорта: ${error.message}")
                    
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Ошибка импорта")
                        .setMessage("Не удалось импортировать данные:\n${error.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                toast("Ошибка: ${e.message}")
            }
        }
    }
    
    private fun exportData(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Получаем членов семьи ОДИН РАЗ синхронно
                val members = withContext(Dispatchers.IO) {
                    viewModel.getAllMembersSync()
                }
                
                if (members.isEmpty()) {
                    toast("Нет данных для экспорта")
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val jsonString = DataImportExport.exportToJson(members)
                    
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray())
                    }
                }
                
                toast("Экспортировано ${members.size} членов семьи")
                
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Экспорт завершён")
                    .setMessage("Успешно экспортировано ${members.size} членов семьи")
                    .setPositiveButton("OK", null)
                    .show()
                    
            } catch (e: Exception) {
                toast("Ошибка экспорта: ${e.message}")
                
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Ошибка экспорта")
                    .setMessage("Не удалось экспортировать данные:\n${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
