package com.example.familyone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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

/**
 * Удобный экран для привязки фотографий к членам семьи
 * Позволяет выбрать фото и вручную или автоматически привязать к членам семьи
 */
class PhotoAssignmentActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPhotoAssignmentBinding
    private lateinit var viewModel: FamilyViewModel
    private lateinit var memberAdapter: MemberSelectionAdapter
    
    private var selectedBitmap: Bitmap? = null
    private var selectedUri: Uri? = null
    private var allMembers: List<FamilyMember> = emptyList()
    private var isServerConnected = false
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
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
        setupClickListeners()
        loadMembers()
        checkServerAndSync()
    }
    
    private fun initServerUrl() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val serverUrl = prefs.getString("face_server_url", "https://api.totalcode.online") ?: "https://api.totalcode.online"
        FaceRecognitionApi.setServerUrl(serverUrl)
    }

    private fun setupRecyclerView() {
        memberAdapter = MemberSelectionAdapter(
            onMemberClick = { member ->
                if (selectedBitmap != null) {
                    showAssignConfirmDialog(member)
                } else {
                    toast("Сначала выберите фото")
                }
            }
        )
        
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(this@PhotoAssignmentActivity)
            adapter = memberAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnSelectPhoto.setOnClickListener {
            checkPermissionAndPickImage()
        }
        
        binding.btnAutoRecognize.setOnClickListener {
            if (selectedBitmap != null && isServerConnected) {
                autoRecognizeAndAssign()
            } else if (!isServerConnected) {
                toast("Сервер недоступен")
            } else {
                toast("Сначала выберите фото")
            }
        }
        
        binding.btnSyncAll.setOnClickListener {
            syncAllMembersToServer()
        }
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
        binding.tvServerStatus.text = "🔄 Проверка сервера..."
        binding.tvServerStatus.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            isServerConnected = FaceRecognitionApi.checkHealth()
            
            if (isServerConnected) {
                binding.tvServerStatus.text = "✓ Сервер подключен"
                binding.tvServerStatus.setTextColor(getColor(R.color.green_accent))
                binding.btnAutoRecognize.isEnabled = true
                binding.btnSyncAll.isEnabled = true
                
                // Автоматическая синхронизация при подключении
                checkAndSyncMembers()
                
                binding.tvServerStatus.postDelayed({
                    binding.tvServerStatus.visibility = View.GONE
                }, 3000)
            } else {
                binding.tvServerStatus.text = "✗ Сервер недоступен"
                binding.tvServerStatus.setTextColor(getColor(R.color.red_button))
                binding.btnAutoRecognize.isEnabled = false
                binding.btnSyncAll.isEnabled = false
            }
        }
    }
    
    /**
     * Проверяет и синхронизирует всех членов семьи с сервером
     * Регистрирует тех, кто еще не зарегистрирован
     */
    private fun checkAndSyncMembers() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Получаем список зарегистрированных лиц на сервере
                val serverFacesResult = FaceRecognitionApi.listFaces()
                val serverFaceIds = serverFacesResult.getOrNull()?.map { it.memberId }?.toSet() ?: emptySet()
                
                android.util.Log.d("PhotoAssignment", "📋 На сервере зарегистрировано: ${serverFaceIds.size} лиц")
                android.util.Log.d("PhotoAssignment", "📋 Server IDs: $serverFaceIds")
                
                // Конвертируем server IDs в local IDs для сопоставления с адаптером
                val localRegisteredIds = serverFaceIds.mapNotNull { serverId ->
                    try {
                        UniqueIdHelper.fromServerId(serverId.toLong()).toString()
                    } catch (e: Exception) {
                        android.util.Log.w("PhotoAssignment", "⚠️ Не удалось конвертировать serverId: $serverId")
                        null
                    }
                }.toSet()
                
                android.util.Log.d("PhotoAssignment", "📋 Local IDs: $localRegisteredIds")
                
                // Обновляем UI адаптера с информацией о зарегистрированных членах (local IDs)
                withContext(Dispatchers.Main) {
                    memberAdapter.updateRegisteredMembers(localRegisteredIds)
                }
                
                // Получаем всех членов семьи с фото
                val database = FamilyDatabase.getDatabase(applicationContext)
                val members = database.familyMemberDao().getAllMembersSync()
                
                var registeredCount = 0
                var skippedCount = 0
                
                for (member in members) {
                    if (member.photoUri.isNullOrEmpty()) {
                        skippedCount++
                        continue
                    }
                    
                    val serverId = UniqueIdHelper.toServerId(applicationContext, member.id).toString()
                    
                    if (serverId in serverFaceIds) {
                        android.util.Log.d("PhotoAssignment", "✓ ${member.firstName} уже зарегистрирован")
                        continue
                    }
                    
                    // Регистрируем на сервере
                    val photoFile = File(member.photoUri!!.replace("file://", ""))
                    if (photoFile.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                        if (bitmap != null) {
                            val fullName = "${member.firstName} ${member.lastName}"
                            val result = FaceRecognitionApi.registerFace(
                                UniqueIdHelper.toServerId(applicationContext, member.id),
                                fullName,
                                bitmap
                            )
                            
                            result.onSuccess {
                                registeredCount++
                                android.util.Log.d("PhotoAssignment", "✅ Зарегистрирован: $fullName")
                            }
                            
                            result.onFailure { error ->
                                android.util.Log.e("PhotoAssignment", "❌ Ошибка регистрации $fullName: ${error.message}")
                            }
                            
                            bitmap.recycle()
                        }
                    }
                }
                
                // Обновляем статусы после регистрации
                if (registeredCount > 0) {
                    val updatedFacesResult = FaceRecognitionApi.listFaces()
                    val updatedServerIds = updatedFacesResult.getOrNull()?.map { it.memberId }?.toSet() ?: emptySet()
                    
                    // Конвертируем в local IDs
                    val updatedLocalIds = updatedServerIds.mapNotNull { serverId ->
                        try {
                            UniqueIdHelper.fromServerId(serverId.toLong()).toString()
                        } catch (e: Exception) { null }
                    }.toSet()
                    
                    withContext(Dispatchers.Main) {
                        memberAdapter.updateRegisteredMembers(updatedLocalIds)
                        toast("✓ Синхронизировано: $registeredCount членов семьи")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PhotoAssignment", "❌ Ошибка синхронизации", e)
            }
        }
    }

    /**
     * Принудительная синхронизация всех членов семьи
     */
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
                    
                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap == null) {
                        errorCount++
                        continue
                    }
                    
                    val fullName = "${member.firstName} ${member.lastName}"
                    val serverId = UniqueIdHelper.toServerId(applicationContext, member.id)
                    
                    val result = FaceRecognitionApi.registerFace(serverId, fullName, bitmap)
                    
                    result.onSuccess {
                        registeredCount++
                    }
                    
                    result.onFailure {
                        errorCount++
                    }
                    
                    bitmap.recycle()
                }
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    val message = buildString {
                        append("✓ Зарегистрировано: $registeredCount\n")
                        if (noPhotoCount > 0) append("⚠️ Без фото: $noPhotoCount\n")
                        if (errorCount > 0) append("❌ Ошибок: $errorCount")
                    }
                    
                    binding.tvSyncStatus.text = message
                    binding.tvSyncStatus.setTextColor(getColor(R.color.green_accent))
                    
                    toast("Синхронизация завершена")
                }
                
                // Обновляем статусы в адаптере после синхронизации
                val updatedFacesResult = FaceRecognitionApi.listFaces()
                val updatedServerIds = updatedFacesResult.getOrNull()?.map { it.memberId }?.toSet() ?: emptySet()
                
                // Конвертируем в local IDs
                val updatedLocalIds = updatedServerIds.mapNotNull { serverId ->
                    try {
                        UniqueIdHelper.fromServerId(serverId.toLong()).toString()
                    } catch (e: Exception) { null }
                }.toSet()
                
                withContext(Dispatchers.Main) {
                    memberAdapter.updateRegisteredMembers(updatedLocalIds)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSyncStatus.text = "❌ Ошибка: ${e.message}"
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
    
    private fun handleSelectedImage(uri: Uri) {
        try {
            selectedUri = uri
            selectedBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            
            // Показываем превью, скрываем пустое состояние
            binding.cardPhotoPreview.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.ivPhotoPreview)
            
            // Обновляем пошаговый индикатор
            binding.tvStep1.setTextColor(getColor(R.color.green_accent))
            binding.tvStep1.text = "Фото выбрано"
            binding.tvStep1.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)
            binding.tvStep2.setTextColor(getColor(R.color.purple_button))
            binding.tvStep2.setTypeface(null, android.graphics.Typeface.BOLD)
            
        } catch (e: Exception) {
            toast("Ошибка загрузки фото")
            e.printStackTrace()
        }
    }

    /**
     * Автоматическое распознавание и привязка фото
     */
    private fun autoRecognizeAndAssign() {
        val bitmap = selectedBitmap ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        binding.tvSyncStatus.text = "Распознавание..."
        binding.tvSyncStatus.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = FaceRecognitionApi.recognizeFace(bitmap)
                
                result.onSuccess { recognitions ->
                    binding.progressBar.visibility = View.GONE
                    
                    if (recognitions.isEmpty()) {
                        binding.tvSyncStatus.text = "⚠️ Лица не распознаны"
                        binding.tvSyncStatus.setTextColor(getColor(R.color.red_button))
                        toast("На фото не найдено знакомых лиц")
                    } else {
                        showRecognitionResultsDialog(recognitions)
                    }
                }
                
                result.onFailure { error ->
                    binding.progressBar.visibility = View.GONE
                    binding.tvSyncStatus.text = "❌ ${error.message}"
                    binding.tvSyncStatus.setTextColor(getColor(R.color.red_button))
                }
                
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvSyncStatus.text = "❌ Ошибка: ${e.message}"
                binding.tvSyncStatus.setTextColor(getColor(R.color.red_button))
            }
        }
    }
    
    private fun showRecognitionResultsDialog(recognitions: List<com.example.familyone.api.RecognitionResult>) {
        val message = buildString {
            append("🎯 Найдено: ${recognitions.size} человек\n\n")
            recognitions.forEachIndexed { index, result ->
                val confidence = (result.confidence * 100).toInt()
                val emoji = when {
                    confidence >= 90 -> "✅"
                    confidence >= 70 -> "⚠️"
                    else -> "❓"
                }
                append("$emoji ${index + 1}. ${result.memberName}\n")
                append("   Уверенность: $confidence%\n\n")
            }
        }
        
        binding.tvSyncStatus.text = message
        binding.tvSyncStatus.setTextColor(getColor(R.color.green_accent))
        
        val names = recognitions.map { it.memberName }.toTypedArray()
        val checkedItems = BooleanArray(recognitions.size) { true }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 Результаты распознавания")
            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Прикрепить") { _, _ ->
                assignPhotoToRecognizedMembers(recognitions, checkedItems)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun assignPhotoToRecognizedMembers(
        recognitions: List<com.example.familyone.api.RecognitionResult>,
        checkedItems: BooleanArray
    ) {
        val bitmap = selectedBitmap ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            var savedCount = 0
            
            recognitions.forEachIndexed { index, result ->
                if (checkedItems[index]) {
                    val localMemberId = UniqueIdHelper.fromServerId(result.memberId.toLong())
                    val saved = savePhotoToMember(localMemberId, result.memberName, bitmap)
                    if (saved) savedCount++
                }
            }
            
            withContext(Dispatchers.Main) {
                if (savedCount > 0) {
                    toast("✓ Фото прикреплено к $savedCount членам семьи")
                    clearSelection()
                } else {
                    toast("⚠️ Не удалось прикрепить фото")
                }
            }
        }
    }
    
    private fun showAssignConfirmDialog(member: FamilyMember) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Прикрепить фото?")
            .setMessage("Прикрепить выбранное фото к ${member.firstName} ${member.lastName}?")
            .setPositiveButton("Да") { _, _ ->
                assignPhotoToMember(member)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun assignPhotoToMember(member: FamilyMember) {
        val bitmap = selectedBitmap ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            val saved = savePhotoToMember(member.id, "${member.firstName} ${member.lastName}", bitmap)
            
            withContext(Dispatchers.Main) {
                if (saved) {
                    toast("✓ Фото прикреплено к ${member.firstName}")
                    clearSelection()
                } else {
                    toast("⚠️ Это фото уже прикреплено")
                }
            }
        }
    }
    
    private suspend fun savePhotoToMember(memberId: Long, memberName: String, bitmap: Bitmap): Boolean {
        return try {
            val database = FamilyDatabase.getDatabase(applicationContext)
            
            // Проверяем дубликаты
            val imageHash = calculateImageHash(bitmap)
            val existingPhotos = database.memberPhotoDao().getPhotosForMemberSync(memberId)
            
            for (existingPhoto in existingPhotos) {
                val existingFile = File(existingPhoto.photoUri)
                if (existingFile.exists()) {
                    val existingBitmap = android.graphics.BitmapFactory.decodeFile(existingFile.absolutePath)
                    if (existingBitmap != null) {
                        val existingHash = calculateImageHash(existingBitmap)
                        existingBitmap.recycle()
                        
                        if (existingHash == imageHash) {
                            return false // Дубликат
                        }
                    }
                }
            }
            
            // Сохраняем фото
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
            true
            
        } catch (e: Exception) {
            android.util.Log.e("PhotoAssignment", "Ошибка сохранения фото", e)
            false
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
    
    private fun clearSelection() {
        selectedBitmap?.recycle()
        selectedBitmap = null
        selectedUri = null
        
        // Скрываем превью, показываем пустое состояние
        binding.cardPhotoPreview.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.tvSyncStatus.visibility = View.GONE
        
        // Сбрасываем пошаговый индикатор
        binding.tvStep1.text = "1. Выбрать фото"
        binding.tvStep1.setTextColor(getColor(R.color.purple_button))
        binding.tvStep1.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.tvStep1.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_photo_library, 0, 0, 0)
        binding.tvStep2.text = "2. Выбрать человека"
        binding.tvStep2.setTextColor(getColor(R.color.text_tertiary_light))
        binding.tvStep2.setTypeface(null, android.graphics.Typeface.NORMAL)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        selectedBitmap?.recycle()
    }
}
