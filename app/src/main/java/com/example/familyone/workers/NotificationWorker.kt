package com.example.familyone.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.familyone.receivers.DailyNotificationReceiver

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        // Вызываем логику проверки уведомлений
        val receiver = DailyNotificationReceiver()
        receiver.onReceive(applicationContext, android.content.Intent())
        
        return Result.success()
    }
}
