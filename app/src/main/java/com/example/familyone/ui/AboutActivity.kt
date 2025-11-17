package com.example.familyone.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.familyone.R
import com.example.familyone.databinding.ActivityAboutBinding
import com.example.familyone.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class AboutActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAboutBinding
    
    // Replace with your actual links
    private val githubRepoUrl = "https://github.com/kam300/family-tree"
    private val telegramChannelUrl = "https://t.me/toatalCode"

    private val websiteUrl = "https://yourwebsite.com"
    private val tutorialUrl = "https://yourwebsite.com/tutorial"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
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
            openUrl(tutorialUrl)
        }
    }
    
    private fun checkForUpdates() {
        toast("Проверка обновлений...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Simple version check - in a real app, you would parse the GitHub API response
                // For now, we'll just open the releases page
                withContext(Dispatchers.Main) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@AboutActivity)
                        .setTitle("Проверка обновлений")
                        .setMessage("Текущая версия: 1.3.0.0\n\nПроверить последнюю версию на GitHub?")
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            openUrl("$githubRepoUrl/releases")
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast("Ошибка проверки обновлений: ${e.message}")
                }
            }
        }
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

