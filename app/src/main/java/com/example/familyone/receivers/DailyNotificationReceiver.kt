package com.example.familyone.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.familyone.data.FamilyDatabase
import com.example.familyone.utils.NotificationHelper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DailyNotificationReceiver : BroadcastReceiver() {
    
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        // Используем goAsync для асинхронной работы
        val pendingResult = goAsync()
        
        // Проверяем разрешение на уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingResult?.finish()
                return
            }
        }
        
        // Проверяем настройки уведомлений
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        val birthdayReminders = prefs.getBoolean("birthday_reminders", true)
        val weddingReminders = prefs.getBoolean("wedding_reminders", true)
        val reminderDaysBefore = prefs.getInt("reminder_days_before", 3)
        
        if (!notificationsEnabled) {
            pendingResult?.finish()
            return
        }
        
        // Выполняем работу в фоновом потоке с GlobalScope для корректной работы с goAsync
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val database = FamilyDatabase.getDatabase(context)
                val members = database.familyMemberDao().getAllMembersSync()
                
                val today = Calendar.getInstance()
                val todayDayMonth = "${today.get(Calendar.DAY_OF_MONTH)}.${today.get(Calendar.MONTH) + 1}"
                
                members.forEach { member ->
                    // Проверяем день рождения
                    if (birthdayReminders) {
                        checkBirthday(context, member.firstName, member.lastName, member.birthDate, member.id, todayDayMonth, reminderDaysBefore)
                    }
                    
                    // Проверяем годовщину свадьбы
                    if (weddingReminders && member.weddingDate != null) {
                        checkWeddingAnniversary(context, member.firstName, member.lastName, member.weddingDate!!, member.id, todayDayMonth, reminderDaysBefore)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Завершаем BroadcastReceiver - безопасная проверка на null
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun checkBirthday(
        context: Context,
        firstName: String,
        lastName: String,
        birthDate: String,
        memberId: Long,
        todayDayMonth: String,
        reminderDaysBefore: Int
    ) {
        try {
            val parts = birthDate.split(".")
            if (parts.size >= 2) {
                val birthDayMonth = "${parts[0]}.${parts[1]}"
                val memberName = "$firstName $lastName"
                
                if (birthDayMonth == todayDayMonth) {
                    // Сегодня день рождения
                    NotificationHelper.showBirthdayNotification(context, memberName, memberId)
                } else {
                    // Проверяем приближающийся день рождения
                    val daysUntil = calculateDaysUntil(parts[0].toInt(), parts[1].toInt())
                    if (daysUntil in 1..reminderDaysBefore) {
                        NotificationHelper.showUpcomingBirthdayNotification(context, memberName, daysUntil, memberId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun checkWeddingAnniversary(
        context: Context,
        firstName: String,
        lastName: String,
        weddingDate: String,
        memberId: Long,
        todayDayMonth: String,
        reminderDaysBefore: Int
    ) {
        try {
            val parts = weddingDate.split(".")
            if (parts.size >= 3) {
                val weddingDayMonth = "${parts[0]}.${parts[1]}"
                val memberName = "$firstName $lastName"
                
                if (weddingDayMonth == todayDayMonth) {
                    // Сегодня годовщина
                    val weddingYear = parts[2].toInt()
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    val years = currentYear - weddingYear
                    
                    NotificationHelper.showWeddingAnniversaryNotification(context, memberName, years, memberId)
                } else {
                    // Проверяем приближающуюся годовщину
                    val daysUntil = calculateDaysUntil(parts[0].toInt(), parts[1].toInt())
                    if (daysUntil in 1..reminderDaysBefore) {
                        NotificationHelper.showUpcomingWeddingNotification(context, memberName, daysUntil, memberId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun calculateDaysUntil(day: Int, month: Int): Int {
        val today = Calendar.getInstance()
        val targetDate = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.MONTH, month - 1)
            set(Calendar.YEAR, today.get(Calendar.YEAR))
            
            // Если дата уже прошла в этом году, берем следующий год
            if (before(today)) {
                add(Calendar.YEAR, 1)
            }
        }
        
        val diffInMillis = targetDate.timeInMillis - today.timeInMillis
        return (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
    }
}
