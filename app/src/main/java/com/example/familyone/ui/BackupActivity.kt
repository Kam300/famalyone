package com.example.familyone.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.familyone.R
import com.example.familyone.databinding.ActivityBackupBinding
import com.example.familyone.utils.BackupManager
import com.example.familyone.utils.DriveBackupInfo
import com.example.familyone.utils.GoogleDriveHelper
import com.example.familyone.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BackupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBackupBinding
    private lateinit var googleDriveHelper: GoogleDriveHelper
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = googleDriveHelper.handleSignInResult(result.data)
        if (account != null) {
            toast("✓ Вход выполнен: ${account.email}")
            updateGoogleDriveUI()
        } else {
            toast("Ошибка входа в Google")
        }
    }
    
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importFromFile(it) }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            exportToLocalFile()
        } else {
            toast("Требуется разрешение для сохранения файла")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        googleDriveHelper = GoogleDriveHelper(this)
        
        setupClickListeners()
        updateGoogleDriveUI()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        
        // Local backup
        binding.btnLocalExport.setOnClickListener {
            checkStoragePermissionAndExport()
        }
        
        binding.btnLocalImport.setOnClickListener {
            showImportOptionsDialog()
        }
        
        // Google Drive
        binding.btnGoogleSignIn.setOnClickListener {
            signInLauncher.launch(googleDriveHelper.getSignInIntent())
        }
        
        binding.btnGoogleSignOut.setOnClickListener {
            lifecycleScope.launch {
                googleDriveHelper.signOut()
                updateGoogleDriveUI()
                toast("Выход выполнен")
            }
        }
        
        binding.btnDriveBackup.setOnClickListener {
            uploadToDrive()
        }
        
        binding.btnDriveRestore.setOnClickListener {
            showDriveBackupsDialog()
        }
    }
    
    private fun updateGoogleDriveUI() {
        val account = googleDriveHelper.getSignedInAccount()
        
        if (account != null) {
            binding.layoutGoogleSignedOut.visibility = View.GONE
            binding.layoutGoogleSignedIn.visibility = View.VISIBLE
            binding.tvGoogleEmail.text = "✓ ${account.email}"
        } else {
            binding.layoutGoogleSignedOut.visibility = View.VISIBLE
            binding.layoutGoogleSignedIn.visibility = View.GONE
        }
    }
    
    private fun checkStoragePermissionAndExport() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        exportToLocalFile()
    }
    
    private fun exportToLocalFile() {
        binding.progressLocal.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val result = BackupManager.exportToLocalFile(this@BackupActivity)
            
            binding.progressLocal.visibility = View.GONE
            
            result.fold(
                onSuccess = { path ->
                    MaterialAlertDialogBuilder(this@BackupActivity)
                        .setTitle("✓ Экспорт завершён")
                        .setMessage("Файл сохранён:\n$path")
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Поделиться") { _, _ ->
                            shareBackupFile(path)
                        }
                        .show()
                },
                onFailure = { error ->
                    toast("Ошибка: ${error.message}")
                }
            )
        }
    }
    
    private fun shareBackupFile(path: String) {
        try {
            val file = java.io.File(path)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Поделиться бэкапом"))
        } catch (e: Exception) {
            toast("Ошибка: ${e.message}")
        }
    }
    
    private fun showImportOptionsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Импорт данных")
            .setMessage("Существующие данные будут заменены. Продолжить?")
            .setPositiveButton("Выбрать файл") { _, _ ->
                importFileLauncher.launch("application/json")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun importFromFile(uri: Uri) {
        binding.progressLocal.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val result = BackupManager.importFromLocalFile(
                this@BackupActivity,
                uri,
                clearExisting = true
            )
            
            binding.progressLocal.visibility = View.GONE
            
            result.fold(
                onSuccess = { count ->
                    toast("✓ Импортировано: $count записей")
                },
                onFailure = { error ->
                    toast("Ошибка: ${error.message}")
                }
            )
        }
    }
    
    private fun uploadToDrive() {
        binding.progressDrive.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val jsonData = BackupManager.getBackupData(this@BackupActivity)
                val result = googleDriveHelper.uploadBackup(jsonData)
                
                binding.progressDrive.visibility = View.GONE
                
                result.fold(
                    onSuccess = {
                        toast("✓ Бэкап загружен в Google Drive")
                    },
                    onFailure = { error ->
                        toast("Ошибка: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                binding.progressDrive.visibility = View.GONE
                toast("Ошибка: ${e.message}")
            }
        }
    }
    
    private fun showDriveBackupsDialog() {
        binding.progressDrive.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val result = googleDriveHelper.listBackups()
            
            binding.progressDrive.visibility = View.GONE
            
            result.fold(
                onSuccess = { backups ->
                    if (backups.isEmpty()) {
                        toast("Нет сохранённых бэкапов")
                        return@fold
                    }
                    
                    showBackupSelectionDialog(backups)
                },
                onFailure = { error ->
                    toast("Ошибка: ${error.message}")
                }
            )
        }
    }
    
    private fun showBackupSelectionDialog(backups: List<DriveBackupInfo>) {
        val items = backups.map { backup ->
            "${dateFormat.format(backup.createdTime)}\n${formatFileSize(backup.size)}"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Выберите бэкап")
            .setItems(items) { _, which ->
                restoreFromDrive(backups[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun restoreFromDrive(backup: DriveBackupInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Восстановить?")
            .setMessage("Все текущие данные будут заменены данными из бэкапа от ${dateFormat.format(backup.createdTime)}")
            .setPositiveButton("Восстановить") { _, _ ->
                performDriveRestore(backup.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun performDriveRestore(fileId: String) {
        binding.progressDrive.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val downloadResult = googleDriveHelper.downloadBackup(fileId)
                
                downloadResult.fold(
                    onSuccess = { jsonData ->
                        val restoreResult = BackupManager.restoreFromData(
                            this@BackupActivity,
                            jsonData,
                            clearExisting = true
                        )
                        
                        binding.progressDrive.visibility = View.GONE
                        
                        restoreResult.fold(
                            onSuccess = { count ->
                                toast("✓ Восстановлено: $count записей")
                            },
                            onFailure = { error ->
                                toast("Ошибка восстановления: ${error.message}")
                            }
                        )
                    },
                    onFailure = { error ->
                        binding.progressDrive.visibility = View.GONE
                        toast("Ошибка загрузки: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                binding.progressDrive.visibility = View.GONE
                toast("Ошибка: ${e.message}")
            }
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
