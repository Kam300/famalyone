package com.example.familyone.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.familyone.R
import com.example.familyone.api.BackupApi
import com.example.familyone.api.BackupRemoteMeta
import com.example.familyone.api.FaceRecognitionApi
import com.example.familyone.databinding.ActivityBackupBinding
import com.example.familyone.utils.ApiServerConfig
import com.example.familyone.utils.BackupArchiveBuildResult
import com.example.familyone.utils.BackupArchiveManager
import com.example.familyone.utils.FaceSyncManager
import com.example.familyone.utils.GoogleAuthManager
import com.example.familyone.utils.UniqueIdHelper
import com.example.familyone.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private lateinit var googleAuthManager: GoogleAuthManager
    private var currentMeta: BackupRemoteMeta? = null

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signInResult = googleAuthManager.handleSignInResult(result.data)
        signInResult.fold(
            onSuccess = { account ->
                toast("Вход выполнен: ${account.email ?: "Google"}")
                updateAuthUi()
                refreshRemoteMeta()
            },
            onFailure = { error ->
                toast("Ошибка входа: ${error.message}")
                updateAuthUi()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        googleAuthManager = GoogleAuthManager(this)

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val serverUrl = ApiServerConfig.readUnifiedServerUrl(prefs)
        BackupApi.setServerUrl(serverUrl)
        BackupApi.setBackupDeviceId(UniqueIdHelper.getDeviceId(this))
        FaceRecognitionApi.setServerUrl(serverUrl)

        setupClickListeners()
        updateAuthUi()
        if (googleAuthManager.getSignedInAccount() != null) {
            refreshRemoteMeta()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.btnLocalExport.setOnClickListener {
            startActivity(Intent(this, ExportActivity::class.java))
        }

        binding.btnGoogleSignIn.setOnClickListener {
            if (!googleAuthManager.isConfigured()) {
                toast("Не настроен google_web_client_id")
                return@setOnClickListener
            }
            signInLauncher.launch(googleAuthManager.getSignInIntent())
        }

        binding.btnGoogleSignOut.setOnClickListener {
            lifecycleScope.launch {
                val signOutResult = googleAuthManager.signOut()
                currentMeta = null
                updateAuthUi()
                updateMetaUi(null)
                if (signOutResult.isSuccess) {
                    toast("Выход выполнен")
                } else {
                    toast("Ошибка выхода: ${signOutResult.exceptionOrNull()?.message}")
                }
            }
        }

        binding.btnDriveRefresh.setOnClickListener {
            refreshRemoteMeta()
        }

        binding.btnDriveBackup.setOnClickListener {
            uploadBackupToServer()
        }

        binding.btnDriveRestore.setOnClickListener {
            confirmAndRestoreFromServer()
        }

        binding.btnDriveDelete.setOnClickListener {
            confirmAndDeleteRemoteBackup()
        }
    }

    private fun updateAuthUi() {
        val account = googleAuthManager.getSignedInAccount()
        val isSignedIn = account != null

        binding.layoutGoogleSignedOut.visibility = if (isSignedIn) View.GONE else View.VISIBLE
        binding.layoutGoogleSignedIn.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        binding.tvGoogleEmail.text = account?.email ?: ""

        val buttonsEnabled = isSignedIn
        binding.btnDriveBackup.isEnabled = buttonsEnabled
        binding.btnDriveRestore.isEnabled = buttonsEnabled
        binding.btnDriveDelete.isEnabled = buttonsEnabled
        binding.btnDriveRefresh.isEnabled = buttonsEnabled

        if (!isSignedIn) {
            binding.tvDriveStatus.text = "Облако не подключено"
            binding.tvDriveStatus.setTextColor(getColor(R.color.text_secondary_light))
        }
    }

    private fun updateMetaUi(meta: BackupRemoteMeta?) {
        currentMeta = meta

        if (meta == null) {
            binding.tvDriveStatus.text = "Нет данных о backup"
            binding.tvDriveStatus.setTextColor(getColor(R.color.text_secondary_light))
            binding.btnDriveRestore.isEnabled = false
            binding.btnDriveDelete.isEnabled = false
            return
        }

        if (!meta.exists) {
            binding.tvDriveStatus.text = "В облаке еще нет backup"
            binding.tvDriveStatus.setTextColor(getColor(R.color.text_secondary_light))
            binding.btnDriveRestore.isEnabled = false
            binding.btnDriveDelete.isEnabled = false
            return
        }

        val sizeText = meta.sizeBytes?.let { formatFileSize(it) } ?: "-"
        val dateText = meta.createdAtUtc ?: "-"
        val members = meta.membersCount ?: 0
        val photos = meta.memberPhotosCount ?: 0
        val assets = meta.assetsCount ?: 0
        binding.tvDriveStatus.text = "Backup: $sizeText\nДата: $dateText\nПрофили: $members, фото: $photos, assets: $assets"
        binding.tvDriveStatus.setTextColor(getColor(R.color.green_accent))
        binding.btnDriveRestore.isEnabled = true
        binding.btnDriveDelete.isEnabled = true
    }

    private fun refreshRemoteMeta() {
        lifecycleScope.launch {
            binding.progressDrive.visibility = View.VISIBLE
            val tokenResult = googleAuthManager.getValidIdToken()
            if (tokenResult.isFailure) {
                binding.progressDrive.visibility = View.GONE
                updateMetaUi(null)
                toast(tokenResult.exceptionOrNull()?.message ?: "Не удалось получить Google токен")
                return@launch
            }

            val metaResult = BackupApi.getMeta(tokenResult.getOrThrow())
            binding.progressDrive.visibility = View.GONE
            metaResult.fold(
                onSuccess = { meta ->
                    updateMetaUi(meta)
                },
                onFailure = { error ->
                    toast("Ошибка получения статуса: ${error.message}")
                    updateMetaUi(null)
                }
            )
        }
    }

    private fun uploadBackupToServer() {
        lifecycleScope.launch {
            binding.progressDrive.visibility = View.VISIBLE

            val tokenResult = googleAuthManager.getValidIdToken()
            if (tokenResult.isFailure) {
                binding.progressDrive.visibility = View.GONE
                toast(tokenResult.exceptionOrNull()?.message ?: "Не удалось получить Google токен")
                return@launch
            }

            val buildResult = BackupArchiveManager.createArchive(
                context = this@BackupActivity,
                maxSizeBytes = 250L * 1024L * 1024L
            )
            if (buildResult.isFailure) {
                binding.progressDrive.visibility = View.GONE
                toast("Ошибка сборки backup: ${buildResult.exceptionOrNull()?.message}")
                return@launch
            }

            val archive = buildResult.getOrThrow()
            val uploadResult = BackupApi.uploadBackup(
                idToken = tokenResult.getOrThrow(),
                archiveFile = archive.archiveFile
            )

            archive.archiveFile.delete()
            binding.progressDrive.visibility = View.GONE

            uploadResult.fold(
                onSuccess = { meta ->
                    updateMetaUi(meta)
                    showUploadSuccessDialog(archive, meta)
                },
                onFailure = { error ->
                    toast("Ошибка загрузки: ${error.message}")
                }
            )
        }
    }

    private fun showUploadSuccessDialog(
        localArchive: BackupArchiveBuildResult,
        remoteMeta: BackupRemoteMeta
    ) {
        val savedSize = remoteMeta.sizeBytes?.let { formatFileSize(it) } ?: formatFileSize(localArchive.sizeBytes)
        MaterialAlertDialogBuilder(this)
            .setTitle("Backup загружен")
            .setMessage(
                "Размер: $savedSize\n" +
                    "Профили: ${remoteMeta.membersCount ?: localArchive.membersCount}\n" +
                    "Фото: ${remoteMeta.memberPhotosCount ?: localArchive.memberPhotosCount}\n" +
                    "Assets: ${remoteMeta.assetsCount ?: localArchive.assetsCount}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmAndRestoreFromServer() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Восстановить из облака?")
            .setMessage("Будет выполнено слияние данных с дедупликацией. Продолжить?")
            .setPositiveButton("Восстановить") { _, _ ->
                restoreFromServer()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun restoreFromServer() {
        lifecycleScope.launch {
            binding.progressDrive.visibility = View.VISIBLE

            val tokenResult = googleAuthManager.getValidIdToken()
            if (tokenResult.isFailure) {
                binding.progressDrive.visibility = View.GONE
                toast(tokenResult.exceptionOrNull()?.message ?: "Не удалось получить Google токен")
                return@launch
            }

            val destination = File(cacheDir, "remote_backup_${System.currentTimeMillis()}.zip")
            val downloadResult = BackupApi.downloadBackup(tokenResult.getOrThrow(), destination)
            if (downloadResult.isFailure) {
                binding.progressDrive.visibility = View.GONE
                destination.delete()
                toast("Ошибка скачивания: ${downloadResult.exceptionOrNull()?.message}")
                return@launch
            }

            val restoreResult = BackupArchiveManager.restoreFromArchive(this@BackupActivity, destination)
            destination.delete()

            if (restoreResult.isFailure) {
                binding.progressDrive.visibility = View.GONE
                toast("Ошибка восстановления: ${restoreResult.exceptionOrNull()?.message}")
                return@launch
            }

            val restoreReport = restoreResult.getOrThrow()
            val aiSyncReport = FaceSyncManager.syncProfilePhotos(this@BackupActivity)
            binding.progressDrive.visibility = View.GONE
            refreshRemoteMeta()
            showRestoreReportDialog(restoreReport, aiSyncReport)
        }
    }

    private fun showRestoreReportDialog(
        restoreReport: com.example.familyone.utils.BackupRestoreReport,
        aiSyncReport: com.example.familyone.utils.FaceSyncReport
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Восстановление завершено")
            .setMessage(
                "Профили добавлено: ${restoreReport.membersInserted}\n" +
                    "Профили сопоставлено: ${restoreReport.membersMatched}\n" +
                    "Фото добавлено: ${restoreReport.photosAdded}\n" +
                    "Дубликатов пропущено: ${restoreReport.photosSkippedDuplicates}\n" +
                    "Ошибок: ${restoreReport.errors}\n\n" +
                    "AI sync:\n" +
                    "Зарегистрировано: ${aiSyncReport.registered}\n" +
                    "Пропущено: ${aiSyncReport.skipped}\n" +
                    "Ошибок: ${aiSyncReport.failed}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmAndDeleteRemoteBackup() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить backup из облака?")
            .setMessage("Удаляется только удаленный backup. Локальные данные не изменятся.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteRemoteBackup()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteRemoteBackup() {
        lifecycleScope.launch {
            binding.progressDrive.visibility = View.VISIBLE

            val tokenResult = googleAuthManager.getValidIdToken()
            if (tokenResult.isFailure) {
                binding.progressDrive.visibility = View.GONE
                toast(tokenResult.exceptionOrNull()?.message ?: "Не удалось получить Google токен")
                return@launch
            }

            val result = BackupApi.deleteBackup(tokenResult.getOrThrow())
            binding.progressDrive.visibility = View.GONE
            result.fold(
                onSuccess = { deleted ->
                    if (deleted) {
                        toast("Backup удален")
                    } else {
                        toast("Удалять было нечего")
                    }
                    updateMetaUi(BackupRemoteMeta(1, false, null, null, null, null, null, null, null))
                },
                onFailure = { error ->
                    toast("Ошибка удаления: ${error.message}")
                }
            )
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
