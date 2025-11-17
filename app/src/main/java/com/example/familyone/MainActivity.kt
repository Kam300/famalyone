package com.example.familyone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.*
import com.example.familyone.databinding.ActivityMainBinding
import com.example.familyone.ui.*
import com.example.familyone.utils.NotificationHelper
import com.example.familyone.workers.NotificationWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val NOTIFICATION_PERMISSION_CODE = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Инициализация уведомлений
        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermission()
        scheduleNotificationWorker()
        
        setupClickListeners()
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }
    
    private fun scheduleNotificationWorker() {
        // Запускаем в фоновом потоке, чтобы не блокировать UI
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
                
                if (!notificationsEnabled) return@launch
                
                // Получаем время уведомлений из настроек (по умолчанию 9:00)
                val notificationHour = prefs.getInt("notification_hour", 9)
                val notificationMinute = prefs.getInt("notification_minute", 0)
                
                // Вычисляем начальную задержку до следующего запланированного времени
                val currentTime = java.util.Calendar.getInstance()
                val scheduledTime = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, notificationHour)
                    set(java.util.Calendar.MINUTE, notificationMinute)
                    set(java.util.Calendar.SECOND, 0)
                    
                    // Если время уже прошло сегодня, планируем на завтра
                    if (before(currentTime)) {
                        add(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                }
                
                val initialDelay = scheduledTime.timeInMillis - currentTime.timeInMillis
                
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
                
                val notificationWork = PeriodicWorkRequestBuilder<NotificationWorker>(
                    24, TimeUnit.HOURS
                )
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .build()
                
                WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                    "daily_notification_work",
                    ExistingPeriodicWorkPolicy.KEEP,
                    notificationWork
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnAddMember.setOnClickListener {
            startActivity(Intent(this, AddMemberActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
        
        binding.btnViewList.setOnClickListener {
            startActivity(Intent(this, MemberListActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
        
        binding.btnFamilyTree.setOnClickListener {
            startActivity(Intent(this, FamilyTreeActivity::class.java))
            overridePendingTransition(R.anim.scale_in, R.anim.fade_out)
        }
        
        binding.btnExport.setOnClickListener {
            startActivity(Intent(this, ExportActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
        
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
    }
}
