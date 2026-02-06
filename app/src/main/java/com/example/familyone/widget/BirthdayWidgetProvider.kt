package com.example.familyone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.familyone.MainActivity
import com.example.familyone.R
import com.example.familyone.data.FamilyDatabase
import com.example.familyone.ui.MemberProfileActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Widget provider for displaying upcoming birthdays
 */
class BirthdayWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_OPEN_MEMBER = "com.example.familyone.ACTION_OPEN_MEMBER"
        const val EXTRA_MEMBER_ID = "member_id"
        
        /**
         * Force update all widgets
         */
        fun updateWidgets(context: Context) {
            val intent = Intent(context, BirthdayWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val widgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = widgetManager.getAppWidgetIds(
                ComponentName(context, BirthdayWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            context.sendBroadcast(intent)
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_OPEN_MEMBER) {
            val memberId = intent.getLongExtra(EXTRA_MEMBER_ID, -1L)
            if (memberId != -1L) {
                val profileIntent = Intent(context, MemberProfileActivity::class.java).apply {
                    putExtra("MEMBER_ID", memberId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(profileIntent)
            }
        }
    }
    
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = FamilyDatabase.getDatabase(context)
                val allMembers = database.familyMemberDao().getAllMembersSync()
                
                // Calculate days until birthday for each member
                val today = Calendar.getInstance()
                val upcomingBirthdays = allMembers.mapNotNull { member ->
                    try {
                        val parts = member.birthDate.split(".")
                        if (parts.size >= 2) {
                            val day = parts[0].toInt()
                            val month = parts[1].toInt()
                            
                            val birthdayThisYear = Calendar.getInstance().apply {
                                set(Calendar.DAY_OF_MONTH, day)
                                set(Calendar.MONTH, month - 1)
                                set(Calendar.YEAR, today.get(Calendar.YEAR))
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            
                            // If birthday passed this year, use next year
                            if (birthdayThisYear.before(today) && 
                                !(birthdayThisYear.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR))) {
                                birthdayThisYear.add(Calendar.YEAR, 1)
                            }
                            
                            val todayStart = today.clone() as Calendar
                            todayStart.set(Calendar.HOUR_OF_DAY, 0)
                            todayStart.set(Calendar.MINUTE, 0)
                            todayStart.set(Calendar.SECOND, 0)
                            todayStart.set(Calendar.MILLISECOND, 0)
                            
                            val diffMillis = birthdayThisYear.timeInMillis - todayStart.timeInMillis
                            val daysUntil = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                            
                            BirthdayInfo(
                                memberId = member.id,
                                name = "${member.firstName} ${member.lastName}",
                                daysUntil = daysUntil
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                    .filter { it.daysUntil >= 0 && it.daysUntil <= 30 } // Next 30 days
                    .sortedBy { it.daysUntil }
                    .take(5) // Show max 5
                
                withContext(Dispatchers.Main) {
                    val views = RemoteViews(context.packageName, R.layout.widget_birthday)
                    
                    // Set up click to open app
                    val openAppIntent = Intent(context, MainActivity::class.java)
                    val openAppPendingIntent = PendingIntent.getActivity(
                        context, 0, openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widgetContainer, openAppPendingIntent)
                    
                    // Clear previous items
                    views.removeAllViews(R.id.widgetList)
                    
                    if (upcomingBirthdays.isEmpty()) {
                        // Show empty state
                        val emptyView = RemoteViews(context.packageName, R.layout.widget_birthday_item)
                        emptyView.setTextViewText(R.id.tvName, "Нет ближайших дней рождения")
                        emptyView.setTextViewText(R.id.tvDays, "")
                        views.addView(R.id.widgetList, emptyView)
                    } else {
                        // Add birthday items
                        upcomingBirthdays.forEach { birthday ->
                            val itemView = RemoteViews(context.packageName, R.layout.widget_birthday_item)
                            itemView.setTextViewText(R.id.tvName, birthday.name)
                            
                            val daysText = when (birthday.daysUntil) {
                                0 -> "🎂 Сегодня!"
                                1 -> "Завтра"
                                in 2..4 -> "через ${birthday.daysUntil} дня"
                                else -> "через ${birthday.daysUntil} дн."
                            }
                            itemView.setTextViewText(R.id.tvDays, daysText)
                            
                            // Set up click to open member profile
                            val memberIntent = Intent(context, BirthdayWidgetProvider::class.java).apply {
                                action = ACTION_OPEN_MEMBER
                                putExtra(EXTRA_MEMBER_ID, birthday.memberId)
                            }
                            val memberPendingIntent = PendingIntent.getBroadcast(
                                context, 
                                birthday.memberId.toInt(),
                                memberIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            itemView.setOnClickPendingIntent(R.id.itemContainer, memberPendingIntent)
                            
                            views.addView(R.id.widgetList, itemView)
                        }
                    }
                    
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private data class BirthdayInfo(
        val memberId: Long,
        val name: String,
        val daysUntil: Int
    )
}
