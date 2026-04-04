package com.example.familyone.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.familyone.R
import com.example.familyone.api.AuthApi
import com.example.familyone.api.AuthSnapshot
import com.example.familyone.api.BackupApi
import com.example.familyone.api.BackupRemoteMeta
import com.example.familyone.api.FaceRecognitionApi
import com.example.familyone.databinding.ActivityBackupBinding
import com.example.familyone.utils.ApiServerConfig
import com.example.familyone.utils.BackupArchiveBuildResult
import com.example.familyone.utils.BackupArchiveManager
import com.example.familyone.utils.BackupRestoreReport
import com.example.familyone.utils.FaceSyncManager
import com.example.familyone.utils.FaceSyncReport
import com.example.familyone.utils.UniqueIdHelper
import com.example.familyone.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private var currentMeta: BackupRemoteMeta? = null
    private var authSnapshot: AuthSnapshot? = null
    private lateinit var serverUrl: String
    private var deviceId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        serverUrl = ApiServerConfig.readUnifiedServerUrl(prefs)
        deviceId = UniqueIdHelper.getDeviceId(this)

        BackupApi.setServerUrl(serverUrl)
        BackupApi.setBackupDeviceId(deviceId)
        FaceRecognitionApi.setServerUrl(serverUrl)
        AuthApi.setServerUrl(serverUrl)
        AuthApi.setDeviceId(deviceId)

        binding.tvServerInfo.text =
            "Сервер: $serverUrl\nУстройство: $deviceId\nРежим: серверная резервная копия"
        binding.tvDriveStatus.text = "Проверяем наличие резервной копии на сервере..."
        binding.tvDriveStatus.setTextColor(getColor(R.color.text_secondary_light))

        setupClickListeners()
        handleAuthCallbackResult(intent)
        refreshAuthState()
        refreshRemoteMeta()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallbackResult(intent)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.btnLocalExport.setOnClickListener {
            startActivity(Intent(this, ExportActivity::class.java))
        }

        binding.btnYandexConnect.setOnClickListener {
            startYandexConnectFlow()
        }

        binding.btnAuthRefresh.setOnClickListener {
            refreshAuthState()
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

    private fun handleAuthCallbackResult(intent: Intent?) {
        val targetIntent = intent ?: return
        val status = targetIntent.getStringExtra(YandexAuthCallbackActivity.EXTRA_AUTH_STATUS).orEmpty()
        if (status.isBlank()) {
            return
        }

        val message = targetIntent.getStringExtra(YandexAuthCallbackActivity.EXTRA_AUTH_MESSAGE).orEmpty()
        targetIntent.removeExtra(YandexAuthCallbackActivity.EXTRA_AUTH_STATUS)
        targetIntent.removeExtra(YandexAuthCallbackActivity.EXTRA_AUTH_MESSAGE)
        targetIntent.removeExtra(YandexAuthCallbackActivity.EXTRA_AUTH_PROVIDER)

        if (status == "success") {
            toast(message.ifBlank { "Яндекс ID подключен" })
        } else {
            toast(message.ifBlank { "Не удалось завершить вход через Яндекс ID" })
        }

        refreshAuthState()
        refreshRemoteMeta()
    }

    private fun startYandexConnectFlow() {
        val currentAuth = authSnapshot
        if (currentAuth != null && !currentAuth.yandexConfigured) {
            toast("Яндекс ID пока не настроен на сервере")
            return
        }

        val connectUrl = AuthApi.buildYandexMobileStartUrl(YandexAuthCallbackActivity.CALLBACK_URI)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(connectUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        runCatching { startActivity(browserIntent) }
            .onFailure { error ->
                toast("Не удалось открыть Яндекс ID: ${error.message}")
            }
    }

    private fun refreshAuthState() {
        lifecycleScope.launch {
            setAuthBusy(true)
            val result = AuthApi.bootstrap(displayName = "Android-клиент Семейного древа")
            setAuthBusy(false)

            result.fold(
                onSuccess = { snapshot ->
                    updateAuthUi(snapshot)
                },
                onFailure = { error ->
                    updateAuthUi(null)
                    toast("Ошибка получения статуса аккаунта: ${error.message}")
                }
            )
        }
    }

    private fun updateAuthUi(snapshot: AuthSnapshot?) {
        authSnapshot = snapshot

        if (snapshot == null) {
            binding.tvAuthStatus.text =
                "Не удалось получить статус переносимой учётной записи.\nПроверьте доступ к серверу и повторите попытку."
            binding.tvAuthStatus.setTextColor(getColor(R.color.text_secondary_light))
            binding.btnYandexConnect.text = "Подключить Яндекс ID"
            binding.btnYandexConnect.isEnabled = true
            return
        }

        if (!snapshot.yandexConfigured) {
            binding.tvAuthStatus.text =
                "Яндекс ID ещё не настроен на сервере приложения «Семейное древо».\nПока доступна только локальная сессия этого устройства."
            binding.tvAuthStatus.setTextColor(getColor(R.color.text_secondary_light))
            binding.btnYandexConnect.text = "Яндекс ID недоступен"
            binding.btnYandexConnect.isEnabled = false
            return
        }

        if (snapshot.yandexConnected) {
            val name = snapshot.yandexDisplayName ?: snapshot.displayName.ifBlank { "Пользователь Семейного древа" }
            val email = snapshot.yandexEmail ?: snapshot.email ?: "email не указан"
            binding.tvAuthStatus.text =
                "Яндекс ID подключен\n" +
                    "Имя: $name\n" +
                    "Email: $email\n" +
                    "Теперь эту учётную запись можно использовать на других устройствах."
            binding.tvAuthStatus.setTextColor(getColor(R.color.green_accent))
            binding.btnYandexConnect.text = "Яндекс ID подключен"
            binding.btnYandexConnect.isEnabled = false
            return
        }

        binding.tvAuthStatus.text =
            "Переносимая учётная запись пока не подключена.\n" +
                "Подключите Яндекс ID, чтобы использовать один server backup между Android, web и другими устройствами."
        binding.tvAuthStatus.setTextColor(getColor(R.color.text_secondary_light))
        binding.btnYandexConnect.text = "Подключить Яндекс ID"
        binding.btnYandexConnect.isEnabled = true
    }

    private fun setAuthBusy(isBusy: Boolean) {
        binding.progressAuth.visibility = if (isBusy) View.VISIBLE else View.GONE
        binding.btnAuthRefresh.isEnabled = !isBusy
        binding.btnYandexConnect.isEnabled = !isBusy && authSnapshot?.yandexConnected != true
    }

    private fun setBackupBusy(isBusy: Boolean) {
        binding.progressDrive.visibility = if (isBusy) View.VISIBLE else View.GONE
        binding.btnDriveRefresh.isEnabled = !isBusy
        binding.btnDriveBackup.isEnabled = !isBusy
        binding.btnDriveRestore.isEnabled = !isBusy && currentMeta?.exists == true
        binding.btnDriveDelete.isEnabled = !isBusy && currentMeta?.exists == true
    }

    private fun updateMetaUi(meta: BackupRemoteMeta?) {
        currentMeta = meta

        if (meta == null) {
            binding.tvDriveStatus.text =
                "Не удалось получить статус server backup.\nПроверьте подключение к серверу и повторите попытку."
            binding.tvDriveStatus.setTextColor(getColor(R.color.text_secondary_light))
            binding.btnDriveRestore.isEnabled = false
            binding.btnDriveDelete.isEnabled = false
            return
        }

        if (!meta.exists) {
            binding.tvDriveStatus.text =
                "На сервере пока нет резервной копии.\nНажмите «Создать и загрузить backup», чтобы сохранить текущие данные."
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

        binding.tvDriveStatus.text =
            "Backup найден на сервере\n" +
                "Размер: $sizeText\n" +
                "Дата: $dateText\n" +
                "Профили: $members\n" +
                "Фото: $photos\n" +
                "Assets: $assets"
        binding.tvDriveStatus.setTextColor(getColor(R.color.green_accent))
        binding.btnDriveRestore.isEnabled = true
        binding.btnDriveDelete.isEnabled = true
    }

    private fun refreshRemoteMeta() {
        lifecycleScope.launch {
            setBackupBusy(true)
            val metaResult = BackupApi.getMeta("")
            setBackupBusy(false)
            metaResult.fold(
                onSuccess = { meta ->
                    updateMetaUi(meta)
                },
                onFailure = { error ->
                    updateMetaUi(null)
                    toast("Ошибка получения статуса backup: ${error.message}")
                }
            )
        }
    }

    private fun uploadBackupToServer() {
        lifecycleScope.launch {
            setBackupBusy(true)

            val buildResult = BackupArchiveManager.createArchive(
                context = this@BackupActivity,
                maxSizeBytes = 250L * 1024L * 1024L
            )
            if (buildResult.isFailure) {
                setBackupBusy(false)
                toast("Ошибка сборки backup: ${buildResult.exceptionOrNull()?.message}")
                return@launch
            }

            val archive = buildResult.getOrThrow()
            val uploadResult = BackupApi.uploadBackup(
                idToken = "",
                archiveFile = archive.archiveFile
            )

            archive.archiveFile.delete()
            setBackupBusy(false)

            uploadResult.fold(
                onSuccess = { meta ->
                    updateMetaUi(meta)
                    showUploadSuccessDialog(archive, meta)
                },
                onFailure = { error ->
                    toast("Ошибка загрузки backup: ${error.message}")
                }
            )
        }
    }

    private fun showUploadSuccessDialog(
        localArchive: BackupArchiveBuildResult,
        remoteMeta: BackupRemoteMeta
    ) {
        val savedSize =
            remoteMeta.sizeBytes?.let { formatFileSize(it) } ?: formatFileSize(localArchive.sizeBytes)
        MaterialAlertDialogBuilder(this)
            .setTitle("Backup загружен на сервер")
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
            .setTitle("Восстановить данные с сервера?")
            .setMessage("Будет выполнено слияние данных с дедупликацией. Продолжить?")
            .setPositiveButton("Восстановить") { _, _ ->
                restoreFromServer()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun restoreFromServer() {
        lifecycleScope.launch {
            setBackupBusy(true)

            val destination = File(cacheDir, "remote_backup_${System.currentTimeMillis()}.zip")
            val downloadResult = BackupApi.downloadBackup("", destination)
            if (downloadResult.isFailure) {
                setBackupBusy(false)
                destination.delete()
                toast("Ошибка скачивания backup: ${downloadResult.exceptionOrNull()?.message}")
                return@launch
            }

            val restoreResult = BackupArchiveManager.restoreFromArchive(this@BackupActivity, destination)
            destination.delete()

            if (restoreResult.isFailure) {
                setBackupBusy(false)
                toast("Ошибка восстановления backup: ${restoreResult.exceptionOrNull()?.message}")
                return@launch
            }

            val restoreReport = restoreResult.getOrThrow()
            val aiSyncReport = FaceSyncManager.syncProfilePhotos(this@BackupActivity)
            setBackupBusy(false)
            refreshRemoteMeta()
            showRestoreReportDialog(restoreReport, aiSyncReport)
        }
    }

    private fun showRestoreReportDialog(
        restoreReport: BackupRestoreReport,
        aiSyncReport: FaceSyncReport
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
            .setTitle("Удалить backup с сервера?")
            .setMessage("Удалится только удалённая резервная копия. Локальные данные на телефоне не изменятся.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteRemoteBackup()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteRemoteBackup() {
        lifecycleScope.launch {
            setBackupBusy(true)

            val result = BackupApi.deleteBackup("")
            setBackupBusy(false)
            result.fold(
                onSuccess = { deleted ->
                    if (deleted) {
                        toast("Backup удалён с сервера")
                    } else {
                        toast("На сервере нечего было удалять")
                    }
                    updateMetaUi(BackupRemoteMeta(1, false, null, null, null, null, null, null, null))
                },
                onFailure = { error ->
                    toast("Ошибка удаления backup: ${error.message}")
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
