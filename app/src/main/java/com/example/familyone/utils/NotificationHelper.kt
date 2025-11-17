package com.example.familyone.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.familyone.MainActivity
import com.example.familyone.R


object NotificationHelper {
    
    private const val CHANNEL_ID = "important_dates_channel"
    private const val NOTIFICATION_ID_BASE = 1000
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showBirthdayNotification(context: Context, memberName: String, memberId: Long) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 
                memberId.toInt(), 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_family_tree)
                .setContentTitle(context.getString(R.string.birthday_notification_title))
                .setContentText(context.getString(R.string.birthday_notification_text, memberName))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(context)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(NOTIFICATION_ID_BASE + memberId.toInt(), notification)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID_BASE + memberId.toInt(), notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun showWeddingAnniversaryNotification(
        context: Context, 
        memberName: String, 
        years: Int, 
        memberId: Long
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 
                (10000 + memberId.toInt()), 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_family_tree)
                .setContentTitle(context.getString(R.string.wedding_notification_title))
                .setContentText(context.getString(R.string.wedding_notification_text, memberName, years))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(context)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(NOTIFICATION_ID_BASE + 10000 + memberId.toInt(), notification)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID_BASE + 10000 + memberId.toInt(), notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun showUpcomingBirthdayNotification(
        context: Context, 
        memberName: String, 
        daysUntil: Int, 
        memberId: Long
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 
                (20000 + memberId.toInt()), 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_family_tree)
                .setContentTitle(context.getString(R.string.upcoming_birthday_title))
                .setContentText(context.getString(R.string.upcoming_birthday_text, memberName, daysUntil))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(context)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(NOTIFICATION_ID_BASE + 20000 + memberId.toInt(), notification)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID_BASE + 20000 + memberId.toInt(), notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun showUpcomingWeddingNotification(
        context: Context, 
        memberName: String, 
        daysUntil: Int, 
        memberId: Long
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 
                (30000 + memberId.toInt()), 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_family_tree)
                .setContentTitle(context.getString(R.string.upcoming_wedding_title))
                .setContentText(context.getString(R.string.upcoming_wedding_text, memberName, daysUntil))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(context)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(NOTIFICATION_ID_BASE + 30000 + memberId.toInt(), notification)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID_BASE + 30000 + memberId.toInt(), notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
