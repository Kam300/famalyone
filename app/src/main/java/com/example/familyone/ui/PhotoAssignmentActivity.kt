package com.example.familyone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.familyone.R
import com.example.familyone.api.FaceRecognitionApi
import com.example.familyone.data.FamilyDatabase
import com.example.familyone.data.FamilyMember
import com.example.familyone.data.MemberPhoto
import com.example.familyone.databinding.ActivityPhotoAssignmentBinding
import com.example.familyone.utils.UniqueIdHelper
import com.example.familyone.utils.toast
import com.example.familyone.viewmodel.FamilyViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class PhotoAssignmentActivity : AppCompatActivity() {

    companion object {
        private const val MAX_BATCH_PHOTOS = 20
    }

    private lateinit var binding: ActivityPhotoAssignmentBinding
    private lateinit var viewModel: FamilyViewModel
    private lateinit var memberAdapter: MemberSelectionAdapter
    private lateinit var pendingAdapter: PendingPhotoAssignmentAdapter

    private var allMembers: List<FamilyMember> = emptyList()
    private var isServerConnected = false

    private val selectedPhotoUris = mutableListOf<Uri>()
    private val pendingManualPhotos = mutableListOf<PendingManualPhoto>()
    private var activePendingIndex: Int? = null

    private var lastBatchSelectedCount = 0

    private var autoSavedCount = 0
    private var autoDuplicateCount = 0
    private var autoErrorCount = 0

    private var manualSavedCount = 0
    private var manualDuplicateCount = 0
    private var manualErrorCount = 0

    private var noMatchCount = 0
    private var apiErrorCount = 0
    private var decodeErrorCount = 0
    private val autoAssignedByMember = linkedMapOf<String, Int>()

    private enum class PendingReason {
        NO_MATCH,
        API_ERROR,
        DECODE_ERROR
    }

    private enum class SavePhotoResult {
        SAVED,
        DUPLICATE,
        ERROR
    }

    private data class PendingManualPhoto(
        val uri: Uri,
        val reason: PendingReason,
        val error: String? = null
    )

    private data class RecognizedMemberBucket(
        val memberName: String,
        val photoUris: MutableList<Uri>
    )

    private data class BatchRecognitionResult(
        val recognized: LinkedHashMap<String, RecognizedMemberBucket>,
        val pending: MutableList<PendingManualPhoto>
    )

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            handleSelectedImages(uris)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            toast("Требуется разрешение для доступа к фото")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoAssignmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]

        initServerUrl()
        setupRecyclerView()
        setupPendingRecyclerView()
        setupClickListeners()
        loadMembers()
        resetSelectionUi()
        checkServerAndSync()
    }

    private fun initServerUrl() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val serverUrl = com.example.familyone.utils.ApiServerConfig.readUnifiedServerUrl(prefs)
        FaceRecognitionApi.setServerUrl(serverUrl)
    }

    private fun setupRecyclerView() {
        memberAdapter = MemberSelectionAdapter(
            onMemberClick = { member ->
                handleMemberClick(member)
            }
        )

        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(this@PhotoAssignmentActivity)
            adapter = memberAdapter
        }

        memberAdapter.setSelectionEnabled(false)
    }

    private fun setupPendingRecyclerView() {
        pendingAdapter = PendingPhotoAssignmentAdapter { index ->
            activePendingIndex = index
            refreshPendingUi()
            scrollToMembersSection()
        }

        binding.rvPendingManual.apply {
            layoutManager = LinearLayoutManager(this@PhotoAssignmentActivity)
            adapter = pendingAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSelectPhoto.setOnClickListener { checkPermissionAndPickImage() }
        binding.btnAutoRecognize.setOnClickListener {
            when {
                selectedPhotoUris.isEmpty() -> toast("Сначала выберите фото")
                !isServerConnected -> toast("Сервер недоступен")
                else -> autoRecognizeAndAssign()
            }
        }
        binding.btnSyncAll.setOnClickListener { syncAllMembersToServer() }
    }

    private fun loadMembers() {
        viewModel.allMembers.observe(this) { members ->
            allMembers = members
            memberAdapter.submitList(members)

            binding.tvEmptyMembers.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
            binding.rvMembers.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun checkServerAndSync() {
        binding.tvServerStatus.text = "Проверка сервера..."
        binding.tvServerStatus.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            isServerConnected = FaceRecognitionApi.checkHealth()
            if (isServerConnected) {
                binding.tvServerStatus.text = "Сервер подключен"
                binding.tvServerStatus.setTextColor(getColor(R.color.green_accent))
                binding.btnSyncAll.isEnabled = true
                checkAndSyncMembers()
                binding.tvServerStatus.postDelayed({
                    binding.tvServerStatus.visibility = View.GONE
                }, 3000)
            } else {
                binding.tvServerStatus.text = "Сервер недоступен"
                binding.tvServerStatus.setTextColor(getColor(R.color.red_button))
                binding.btnSyncAll.isEnabled = false
            }
            updateActionButtons()
        }
    }
    private fun checkAndSyncMembers() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverFacesResult = FaceRecognitionApi.listFaces()
                val serverFaceIds = serverFacesResult.getOrNull()?.map { it.memberId }?.toSet() ?: emptySet()

                val localRegisteredIds = serverFaceIds.mapNotNull { serverId ->
                    try {
                        UniqueIdHelper.fromServerId(serverId.toLong()).toString()
                    } catch (_: Exception) {
                        null
                    }
                }.toSet()

                withContext(Dispatchers.Main) {
                    memberAdapter.updateRegisteredMembers(localRegisteredIds)
                }

                val database = FamilyDatabase.getDatabase(applicationContext)
                val members = database.familyMemberDao().getAllMembersSync()
                var registeredCount = 0

                for (member in members) {
                    if (member.photoUri.isNullOrEmpty()) continue

                    val serverId = UniqueIdHelper.toServerId(applicationContext, member.id).toString()
                    if (serverId in serverFaceIds) continue

                    val photoFile = File(member.photoUri!!.replace("file://", ""))
                    if (!photoFile.exists()) continue

                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: continue
                    val fullName = "${member.firstName} ${member.lastName}".trim()
                    val result = FaceRecognitionApi.registerFace(
                        UniqueIdHelper.toServerId(applicationContext, member.id),
                        fullName,
                        bitmap
                    )
                    if (result.isSuccess) {
                        registeredCount++
                    }
                    bitmap.recycle()
                }

                if (registeredCount > 0) {
                    val updatedFacesResult = FaceRecognitionApi.listFaces()
                    val updatedServerIds = updatedFacesResult.getOrNull()?.map { it.memberId }?.toSet() ?: emptySet()
                    val updatedLocalIds = updatedServerIds.mapNotNull { serverId ->
                        try {
                            UniqueIdHelper.fromServerId(serverId.toLong()).toString()
                        } catch (_: Exception) {
                            null
                        }
                    }.toSet()

                    withContext(Dispatchers.Main) {
                        memberAdapter.updateRegisteredMembers(updatedLocalIds)
                        toast("Синхронизировано: $registeredCount")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PhotoAssignment", "Sync error", e)
            }
        }
    }

    private fun syncAllMembersToServer() {
        if (!isServerConnected) {
            toast("Сервер недоступен")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvSyncStatus.text = "Синхронизация..."
        binding.tvSyncStatus.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = FamilyDatabase.getDatabase(applicationContext)
                val members = database.familyMemberDao().getAllMembersSync()
                var registeredCount = 0
                var errorCount = 0
                var noPhotoCount = 0

                for ((index, member) in members.withIndex()) {
                    withContext(Dispatchers.Main) {
                        binding.tvSyncStatus.text = "Обработка ${index + 1}/${members.size}..."
                    }

                    if (member.photoUri.isNullOrEmpty()) {
                        noPhotoCount++
                        continue
                    }

                    val photoFile = File(member.photoUri!!.replace("file://", ""))
                    if (!photoFile.exists()) {
                        noPhotoCount++
                        continue
                    }

                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap == null) {
                        errorCount++
                        continue
                    }

                    val fullName = "${member.firstName} ${member.lastName}".trim()
                    val serverId = UniqueIdHelper.toServerId(applicationContext, member.id)
                    val result = FaceRecognitionApi.registerFace(serverId, fullName, bitmap)
                    if (result.isSuccess) registeredCount++ else errorCount++
                    bitmap.recycle()
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSyncStatus.text = buildString {
                        append("Зарегистрировано: $registeredCount")
                        if (noPhotoCount > 0) append("\nБез фото: $noPhotoCount")
                        if (errorCount > 0) append("\nОшибок: $errorCount")
                    }
                    binding.tvSyncStatus.setTextColor(getColor(R.color.green_accent))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSyncStatus.text = "Ошибка: ${e.message}"
                    binding.tvSyncStatus.setTextColor(getColor(R.color.red_button))
                }
            }
        }
    }

    private fun checkPermissionAndPickImage() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openImagePicker()
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
            else -> {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openImagePicker()
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun handleSelectedImages(uris: List<Uri>) {
        resetBatchStateForNewSelection()

        val limitedUris = if (uris.size > MAX_BATCH_PHOTOS) {
            toast("Можно выбрать до $MAX_BATCH_PHOTOS фото. Будут обработаны первые $MAX_BATCH_PHOTOS")
            uris.take(MAX_BATCH_PHOTOS)
        } else {
            uris
        }

        selectedPhotoUris.addAll(limitedUris)
        lastBatchSelectedCount = selectedPhotoUris.size

        if (selectedPhotoUris.isEmpty()) {
            resetSelectionUi()
            return
        }

        binding.cardPhotoPreview.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE

        Glide.with(this)
            .load(selectedPhotoUris.first())
            .centerCrop()
            .into(binding.ivPhotoPreview)

        binding.tvStep1.text = "1. Фото выбраны (${selectedPhotoUris.size})"
        binding.tvStep1.setTextColor(getColor(R.color.green_accent))
        binding.tvStep1.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)

        binding.tvStep2.text = "2. AI + ручное назначение"
        binding.tvStep2.setTextColor(getColor(R.color.purple_button))
        binding.tvStep2.setTypeface(null, android.graphics.Typeface.BOLD)

        binding.tvSyncStatus.visibility = View.VISIBLE
        binding.tvSyncStatus.setTextColor(getColor(R.color.green_accent))
        binding.tvSyncStatus.text = "Выбрано фото: ${selectedPhotoUris.size}. Нажмите AI"

        updateActionButtons()
    }

    private fun autoRecognizeAndAssign() {
        if (selectedPhotoUris.isEmpty() || !isServerConnected) {
            return
        }

        memberAdapter.setSelectionEnabled(false)
        activePendingIndex = null

        binding.progressBar.visibility = View.VISIBLE
        binding.tvSyncStatus.visibility = View.VISIBLE
        binding.tvSyncStatus.setTextColor(getColor(R.color.white))
        binding.tvSyncStatus.text = "Распознавание..."

        binding.btnAutoRecognize.isEnabled = false
        binding.btnSelectPhoto.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                resetCounters()
                val batchResult = processBatchRecognition()

                pendingManualPhotos.clear()
                pendingManualPhotos.addAll(batchResult.pending)
                activePendingIndex = null

                if (batchResult.recognized.isNotEmpty()) {
                    showRecognizedAssignmentsDialog(batchResult.recognized)
                } else {
                    handleManualStageOrFinish()
                }
            } catch (e: Exception) {
                binding.tvSyncStatus.setTextColor(getColor(R.color.red_button))
                binding.tvSyncStatus.text = "Ошибка: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSelectPhoto.isEnabled = true
                updateActionButtons()
            }
        }
    }

    private suspend fun processBatchRecognition(): BatchRecognitionResult {
        val recognized = linkedMapOf<String, RecognizedMemberBucket>()
        val pending = mutableListOf<PendingManualPhoto>()

        for ((index, uri) in selectedPhotoUris.withIndex()) {
            binding.tvSyncStatus.text = "AI: обработка ${index + 1} из ${selectedPhotoUris.size}"

            val bitmap = withContext(Dispatchers.IO) { decodeBitmapFromUri(uri) }
            if (bitmap == null) {
                pending.add(PendingManualPhoto(uri, PendingReason.DECODE_ERROR, "Не удалось открыть фото"))
                decodeErrorCount++
                continue
            }

            val result = FaceRecognitionApi.recognizeFace(bitmap)
            bitmap.recycle()

            if (result.isSuccess) {
                val recognitions = result.getOrDefault(emptyList())
                if (recognitions.isEmpty()) {
                    pending.add(PendingManualPhoto(uri, PendingReason.NO_MATCH))
                    noMatchCount++
                } else {
                    recognitions.forEach { recognition ->
                        val bucket = recognized.getOrPut(recognition.memberId) {
                            RecognizedMemberBucket(recognition.memberName, mutableListOf())
                        }
                        if (bucket.photoUris.none { it.toString() == uri.toString() }) {
                            bucket.photoUris.add(uri)
                        }
                    }
                }
            } else {
                val error = result.exceptionOrNull()
                val errorMessage = error?.message
                val reason = if (isNoMatchError(errorMessage)) {
                    noMatchCount++
                    PendingReason.NO_MATCH
                } else {
                    apiErrorCount++
                    PendingReason.API_ERROR
                }
                pending.add(PendingManualPhoto(uri, reason, errorMessage))
            }
        }

        return BatchRecognitionResult(
            recognized = LinkedHashMap(recognized),
            pending = pending
        )
    }

    private fun showRecognizedAssignmentsDialog(recognizedMap: LinkedHashMap<String, RecognizedMemberBucket>) {
        val entries = recognizedMap.entries.toList()
        val items = entries.map { (_, bucket) ->
            "${bucket.memberName} (${bucket.photoUris.size} фото)"
        }.toTypedArray()

        val checked = BooleanArray(items.size) { true }

        MaterialAlertDialogBuilder(this)
            .setTitle("AI нашел совпадения")
            .setMessage("Выберите, кому прикрепить распознанные фото")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Прикрепить") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    saveRecognizedAssignments(entries, checked)
                    binding.progressBar.visibility = View.GONE
                    handleManualStageOrFinish()
                }
            }
            .setNegativeButton("Пропустить") { _, _ ->
                handleManualStageOrFinish()
            }
            .setCancelable(false)
            .show()
    }

    private suspend fun saveRecognizedAssignments(
        entries: List<Map.Entry<String, RecognizedMemberBucket>>,
        checked: BooleanArray
    ) {
        for (index in entries.indices) {
            if (!checked[index]) {
                continue
            }

            val (serverMemberId, bucket) = entries[index]
            val localMemberId = try {
                UniqueIdHelper.fromServerId(serverMemberId.toLong())
            } catch (_: Exception) {
                autoErrorCount++
                continue
            }
            val memberName = bucket.memberName.ifBlank { "Без имени" }

            for (uri in bucket.photoUris) {
                when (savePhotoUriToMember(localMemberId, memberName, uri)) {
                    SavePhotoResult.SAVED -> {
                        autoSavedCount++
                        autoAssignedByMember[memberName] = (autoAssignedByMember[memberName] ?: 0) + 1
                    }
                    SavePhotoResult.DUPLICATE -> autoDuplicateCount++
                    SavePhotoResult.ERROR -> autoErrorCount++
                }
            }
        }
    }

    private fun buildAutoAssignmentReport(): String {
        if (autoAssignedByMember.isEmpty()) {
            return "AI отчет: новых прикреплений не было"
        }

        val sorted = autoAssignedByMember.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        )

        return buildString {
            append("AI отчет по прикреплению:")
            for ((memberName, count) in sorted) {
                append("\n- ")
                append(memberName)
                append(": ")
                append(count)
                append(" фото")
            }
        }
    }

    private fun handleManualStageOrFinish() {
        if (pendingManualPhotos.isEmpty()) {
            memberAdapter.setSelectionEnabled(false)
            binding.cardPendingManual.visibility = View.GONE
            finishSessionAndResetUi()
            return
        }

        binding.cardPendingManual.visibility = View.VISIBLE
        binding.tvSyncStatus.visibility = View.VISIBLE
        binding.tvSyncStatus.setTextColor(getColor(R.color.green_accent))
        binding.tvSyncStatus.text = buildString {
            append("AI готово. Прикреплено: $autoSavedCount")
            if (autoDuplicateCount > 0) append(", дубликатов: $autoDuplicateCount")
            if (autoErrorCount > 0) append(", ошибок: $autoErrorCount")
            append("\n")
            append(buildAutoAssignmentReport())
            append("\nНеразобранных фото: ${pendingManualPhotos.size}. Выберите фото и члена семьи.")
        }

        memberAdapter.setSelectionEnabled(true)
        refreshPendingUi()
        updateActionButtons()
    }

    private fun refreshPendingUi() {
        if (pendingManualPhotos.isEmpty()) {
            binding.cardPendingManual.visibility = View.GONE
            pendingAdapter.submitList(emptyList())
            return
        }

        binding.cardPendingManual.visibility = View.VISIBLE
        binding.tvPendingManualHint.text = buildPendingHintText()

        val items = pendingManualPhotos.mapIndexed { index, pendingPhoto ->
            PendingPhotoAssignmentAdapter.PendingPhotoUiItem(
                uri = pendingPhoto.uri,
                reasonText = reasonToText(pendingPhoto),
                errorText = pendingPhoto.error,
                isSelected = index == activePendingIndex
            )
        }
        pendingAdapter.submitList(items)
    }

    private fun buildPendingHintText(): String {
        val hintText = if (activePendingIndex == null) {
            "Выберите фото сверху, затем прокрутите ниже до блока \"Члены семьи\" и нажмите нужного человека"
        } else {
            "Фото выбрано. Прокрутите ниже до блока \"Члены семьи\" и нажмите нужного человека"
        }
        return hintText

    }

    private fun scrollToMembersSection() {
        binding.root.post {
            binding.root.smoothScrollTo(0, binding.cardMembers.top)
        }
    }

    private fun handleMemberClick(member: FamilyMember) {
        if (pendingManualPhotos.isEmpty()) {
            toast("Сначала запустите AI-распознавание")
            return
        }

        val currentIndex = activePendingIndex
        if (currentIndex == null || currentIndex !in pendingManualPhotos.indices) {
            toast("Выберите фото из списка неразобранных")
            return
        }

        val pendingPhoto = pendingManualPhotos[currentIndex]
        val memberName = "${member.firstName} ${member.lastName}".trim()

        binding.progressBar.visibility = View.VISIBLE
        memberAdapter.setSelectionEnabled(false)

        CoroutineScope(Dispatchers.Main).launch {
            when (savePhotoUriToMember(member.id, memberName, pendingPhoto.uri)) {
                SavePhotoResult.SAVED -> {
                    manualSavedCount++
                    toast("Фото прикреплено к ${member.firstName}")
                    removePendingPhoto(currentIndex)
                }
                SavePhotoResult.DUPLICATE -> {
                    manualDuplicateCount++
                    toast("Это фото уже прикреплено к ${member.firstName}")
                    removePendingPhoto(currentIndex)
                }
                SavePhotoResult.ERROR -> {
                    manualErrorCount++
                    toast("Ошибка сохранения фото")
                    memberAdapter.setSelectionEnabled(true)
                }
            }

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun removePendingPhoto(index: Int) {
        if (index !in pendingManualPhotos.indices) {
            return
        }

        pendingManualPhotos.removeAt(index)
        activePendingIndex = null

        if (pendingManualPhotos.isEmpty()) {
            memberAdapter.setSelectionEnabled(false)
            binding.cardPendingManual.visibility = View.GONE
            finishSessionAndResetUi()
        } else {
            memberAdapter.setSelectionEnabled(true)
            refreshPendingUi()
            binding.tvSyncStatus.text = buildString {
                append("AI прикреплено: $autoSavedCount")
                if (autoDuplicateCount > 0) append(", дубликатов: $autoDuplicateCount")
                if (autoErrorCount > 0) append(", ошибок: $autoErrorCount")
                append("\n")
                append(buildAutoAssignmentReport())
                append("\nНеразобранных фото: ${pendingManualPhotos.size}. Выберите следующее фото.")
            }
        }

        updateActionButtons()
    }

    private fun finishSessionAndResetUi() {
        binding.tvSyncStatus.visibility = View.VISIBLE
        binding.tvSyncStatus.setTextColor(getColor(R.color.green_accent))
        binding.tvSyncStatus.text = buildFinalSummary()

        selectedPhotoUris.clear()
        pendingManualPhotos.clear()
        activePendingIndex = null

        resetSelectionUi(keepStatus = true)
        updateActionButtons()
    }

    private fun buildFinalSummary(): String {
        return buildString {
            append("Готово. Обработано фото: $lastBatchSelectedCount\n")
            append("AI прикрепил: $autoSavedCount")
            if (autoDuplicateCount > 0) append(", дубликатов: $autoDuplicateCount")
            if (autoErrorCount > 0) append(", ошибок: $autoErrorCount")
            append("\n")
            append(buildAutoAssignmentReport())
            append("\nРучной режим: $manualSavedCount")
            if (manualDuplicateCount > 0) append(", дубликатов: $manualDuplicateCount")
            if (manualErrorCount > 0) append(", ошибок: $manualErrorCount")
            append("\nНе распознано AI: $noMatchCount")
            if (apiErrorCount > 0) append(" | Ошибки API: $apiErrorCount")
            if (decodeErrorCount > 0) append(" | Ошибки чтения: $decodeErrorCount")
        }
    }

    private fun resetCounters() {
        autoSavedCount = 0
        autoDuplicateCount = 0
        autoErrorCount = 0
        autoAssignedByMember.clear()
        manualSavedCount = 0
        manualDuplicateCount = 0
        manualErrorCount = 0
        noMatchCount = 0
        apiErrorCount = 0
        decodeErrorCount = 0
    }

    private fun resetBatchStateForNewSelection() {
        selectedPhotoUris.clear()
        pendingManualPhotos.clear()
        activePendingIndex = null
        resetCounters()
        binding.cardPendingManual.visibility = View.GONE
        pendingAdapter.submitList(emptyList())
        memberAdapter.setSelectionEnabled(false)
    }

    private fun resetSelectionUi(keepStatus: Boolean = false) {
        binding.cardPhotoPreview.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
        if (!keepStatus) {
            binding.tvSyncStatus.visibility = View.GONE
        }
        binding.cardPendingManual.visibility = View.GONE

        binding.tvStep1.text = "1. Выбрать фото (до 20)"
        binding.tvStep1.setTextColor(getColor(R.color.purple_button))
        binding.tvStep1.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.tvStep1.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_photo_library, 0, 0, 0)

        binding.tvStep2.text = "2. AI + ручное назначение"
        binding.tvStep2.setTextColor(getColor(R.color.text_tertiary_light))
        binding.tvStep2.setTypeface(null, android.graphics.Typeface.NORMAL)
    }

    private fun updateActionButtons() {
        binding.btnSelectPhoto.isEnabled = true
        binding.btnAutoRecognize.isEnabled =
            isServerConnected && selectedPhotoUris.isNotEmpty() && pendingManualPhotos.isEmpty()
    }

    private fun reasonToText(photo: PendingManualPhoto): String {
        return when (photo.reason) {
            PendingReason.NO_MATCH -> "Не распознано AI"
            PendingReason.API_ERROR -> "Ошибка API"
            PendingReason.DECODE_ERROR -> "Ошибка чтения фото"
        }
    }

    private fun isNoMatchError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val normalized = message.lowercase(Locale.getDefault())
        return normalized.contains("не распозн") ||
            normalized.contains("не найден") ||
            normalized.contains("no face") ||
            normalized.contains("no faces") ||
            normalized.contains("not recognized")
    }

    private suspend fun savePhotoUriToMember(memberId: Long, memberName: String, uri: Uri): SavePhotoResult {
        return withContext(Dispatchers.IO) {
            val bitmap = decodeBitmapFromUri(uri) ?: return@withContext SavePhotoResult.ERROR
            try {
                savePhotoToMember(memberId, memberName, bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun savePhotoToMember(memberId: Long, memberName: String, bitmap: Bitmap): SavePhotoResult {
        return try {
            val database = FamilyDatabase.getDatabase(applicationContext)
            val imageHash = calculateImageHash(bitmap)
            val existingPhotos = database.memberPhotoDao().getPhotosForMemberSync(memberId)

            for (existingPhoto in existingPhotos) {
                val existingFile = File(existingPhoto.photoUri)
                if (!existingFile.exists()) continue

                val existingBitmap = BitmapFactory.decodeFile(existingFile.absolutePath) ?: continue
                val existingHash = calculateImageHash(existingBitmap)
                existingBitmap.recycle()

                if (existingHash == imageHash) {
                    return SavePhotoResult.DUPLICATE
                }
            }

            val filename = "photo_${memberId}_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val photo = MemberPhoto(
                memberId = memberId,
                photoUri = file.absolutePath,
                dateAdded = System.currentTimeMillis()
            )
            database.memberPhotoDao().insertPhoto(photo)
            SavePhotoResult.SAVED
        } catch (e: Exception) {
            android.util.Log.e("PhotoAssignment", "Save error for $memberName", e)
            SavePhotoResult.ERROR
        }
    }

    private fun calculateImageHash(bitmap: Bitmap): String {
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        var totalBrightness = 0

        for (x in 0 until 8) {
            for (y in 0 until 8) {
                val pixel = smallBitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                totalBrightness += (r + g + b) / 3
            }
        }

        val avgBrightness = totalBrightness / 64
        val hash = StringBuilder()

        for (x in 0 until 8) {
            for (y in 0 until 8) {
                val pixel = smallBitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val brightness = (r + g + b) / 3
                hash.append(if (brightness >= avgBrightness) "1" else "0")
            }
        }

        smallBitmap.recycle()
        return hash.toString()
    }
}
