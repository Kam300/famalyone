package com.example.familyone.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.familyone.R
import com.example.familyone.databinding.ActivityAboutBinding
import com.example.familyone.utils.UpdateChecker
import com.example.familyone.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAboutBinding
    private var downloadId: Long = -1
    
    // Replace with your actual links
    private val githubRepoUrl = "https://github.com/Kam300/URL"
    private val telegramChannelUrl = "https://t.me/TotalC0de"
    private val websiteUrl = "https://yourwebsite.com"
    private val tutorialUrl = "https://yourwebsite.com/tutorial"
    
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                showInstallDialog()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        registerDownloadReceiver()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnCheckUpdates.setOnClickListener {
            checkForUpdates()
        }
        
        binding.btnTelegram.setOnClickListener {
            openUrl(telegramChannelUrl)
        }

        
        binding.btnTutorial.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        
        binding.btnPhotoGallery.setOnClickListener {
            startActivity(Intent(this, PhotoGalleryActivity::class.java))
        }
    }
    
    private fun checkForUpdates() {
        binding.btnCheckUpdates.isEnabled = false
        binding.btnCheckUpdates.text = "Проверка..."
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val updateInfo = UpdateChecker.checkForUpdates(this@AboutActivity)
                
                if (updateInfo != null) {
                    if (updateInfo.isUpdateAvailable) {
                        showUpdateDialog(updateInfo)
                    } else {
                        showNoUpdateDialog(updateInfo.version)
                    }
                } else {
                    toast("Не удалось проверить обновления")
                }
            } catch (e: Exception) {
                toast("Ошибка: ${e.message}")
                e.printStackTrace()
            } finally {
                binding.btnCheckUpdates.isEnabled = true
                binding.btnCheckUpdates.text = getString(R.string.check_updates)
            }
        }
    }
    
    private fun showUpdateDialog(updateInfo: com.example.familyone.utils.UpdateInfo) {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Доступно обновление! 🎉")
            .setMessage(
                "Текущая версия: $currentVersion\n" +
                "Новая версия: ${updateInfo.version}\n\n" +
                "Хотите загрузить обновление?"
            )
            .setPositiveButton("Загрузить") { _, _ ->
                downloadUpdate(updateInfo.downloadUrl)
            }
            .setNegativeButton("Позже", null)
            .setNeutralButton("GitHub") { _, _ ->
                openUrl(githubRepoUrl)
            }
            .show()
    }
    
    private fun showNoUpdateDialog(latestVersion: String) {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Обновлений нет ✓")
            .setMessage(
                "У вас установлена последняя версия!\n\n" +
                "Текущая версия: $currentVersion\n" +
                "Последняя версия: $latestVersion"
            )
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun downloadUpdate(downloadUrl: String) {
        try {
            toast("Начинается загрузка...")
            downloadId = UpdateChecker.downloadUpdate(this, downloadUrl)
        } catch (e: Exception) {
            toast("Ошибка загрузки: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun showInstallDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Загрузка завершена")
            .setMessage("Обновление загружено. Установить сейчас?")
            .setPositiveButton("Установить") { _, _ ->
                UpdateChecker.installUpdate(this, downloadId)
            }
            .setNegativeButton("Позже", null)
            .show()
    }
    
    private fun showContactOptions() {
        val options = listOf(
            ContactOption(getString(R.string.telegram_app), R.drawable.ic_telegram),
            ContactOption(getString(R.string.whatsapp_app), R.drawable.ic_whatsapp),
            ContactOption(getString(R.string.call), R.drawable.ic_phone)
        )
        
        val adapter = ContactDialogAdapter(this, options)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.contact_via))
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> openTelegram()
                    1 -> openWhatsApp()
                    2 -> makeCall()
                }
            }
            .show()
    }
    
    private fun openTelegram() {
        // Replace with actual telegram username or phone number
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/yourusername"))
            startActivity(intent)
        } catch (e: Exception) {
            toast("Telegram не установлен")
        }
    }
    
    private fun openWhatsApp() {
        // Replace with actual WhatsApp number (format: country code + number, no + or spaces)
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/1234567890")
            startActivity(intent)
        } catch (e: Exception) {
            toast("WhatsApp не установлен")
        }
    }
    
    private fun makeCall() {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:+1234567890")
            startActivity(intent)
        } catch (e: Exception) {
            toast("Невозможно совершить звонок")
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            toast("Невозможно открыть ссылку")
        }
    }
}

