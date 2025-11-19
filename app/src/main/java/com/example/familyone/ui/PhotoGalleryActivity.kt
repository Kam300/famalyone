package com.example.familyone.ui

import android.Manifest
import android.content.Intent
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
import androidx.recyclerview.widget.GridLayoutManager
import com.example.familyone.R
import com.example.familyone.api.FaceRecognitionApi
import com.example.familyone.databinding.ActivityPhotoGalleryBinding
import com.example.familyone.utils.toast
import com.example.familyone.viewmodel.FamilyViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PhotoGalleryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPhotoGalleryBinding
    private lateinit var viewModel: FamilyViewModel
    private var selectedBitmap: Bitmap? = null
    private val selectedPhotos = mutableListOf<Bitmap>()
    private val processedPhotoHashes = mutableSetOf<String>()
    
    // Для хранения результатов распознавания
    data class PhotoRecognitionResult(
        val bitmap: Bitmap,
        val recognitions: List<com.example.familyone.api.RecognitionResult>
    )
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        android.util.Log.d("PhotoGallery", "📥 Выбрано фото: ${uris.size}")
        if (uris.isNotEmpty()) {
            handleSelectedImages(uris)
        } else {
            android.util.Log.w("PhotoGallery", "⚠️ Фото не выбраны")
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
        binding = ActivityPhotoGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]
        
        // Инициализируем URL сервера
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val serverUrl = prefs.getString("face_server_url", "http://10.0.2.2:5000") ?: "http://10.0.2.2:5000"
        FaceRecognitionApi.setServerUrl(serverUrl)
        
        setupClickListeners()
        checkServerConnection()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnUploadPhoto.setOnClickListener {
            checkPermissionAndPickImage()
        }
        
        binding.btnSettings.setOnClickListener {
            showServerSettingsDialog()
        }
    }
    
    private fun checkServerConnection() {
        binding.tvStatus.text = "Проверка подключения..."
        binding.tvStatus.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            val isConnected = FaceRecognitionApi.checkHealth()
            
            if (isConnected) {
                binding.tvStatus.text = "✓ Сервер подключен"
                binding.tvStatus.setTextColor(getColor(R.color.green_accent))
                binding.btnUploadPhoto.isEnabled = true
                
                // Скрываем статус через 2 секунды
                binding.tvStatus.postDelayed({
                    binding.tvStatus.visibility = View.GONE
                }, 2000)
            } else {
                binding.tvStatus.text = "✗ Сервер недоступен. Проверьте настройки"
                binding.tvStatus.setTextColor(getColor(R.color.red_button))
                binding.btnUploadPhoto.isEnabled = false
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
        android.util.Log.d("PhotoGallery", "📸 Обрабатываем ${uris.size} фото")
        
        selectedPhotos.clear()
        var loadedCount = 0
        var duplicateCount = 0
        
        try {
            for (uri in uris) {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                val hash = calculateImageHash(bitmap)
                
                // Проверяем дубликаты среди уже загруженных фото
                if (hash in processedPhotoHashes) {
                    android.util.Log.w("PhotoGallery", "⚠️ Пропускаем дубликат фото")
                    duplicateCount++
                    bitmap.recycle()
                    continue
                }
                
                processedPhotoHashes.add(hash)
                selectedPhotos.add(bitmap)
                loadedCount++
            }
            
            android.util.Log.d("PhotoGallery", "✓ Загружено: $loadedCount, пропущено дубликатов: $duplicateCount")
            
            if (selectedPhotos.isEmpty()) {
                toast("⚠️ Все выбранные фото уже были загружены")
                return
            }
            
            // Показываем первое фото как превью
            selectedBitmap = selectedPhotos.first()
            
            // Показываем превью и карточку
            binding.cardPreview.visibility = View.VISIBLE
            binding.ivPreview.setImageBitmap(selectedBitmap)
            binding.ivPreview.visibility = View.VISIBLE
            binding.btnRecognize.visibility = View.VISIBLE
            
            // Обновляем текст кнопки
            val buttonText = if (selectedPhotos.size == 1) {
                "Распознать лица"
            } else {
                "Распознать лица (${selectedPhotos.size} фото)"
            }
            binding.btnRecognize.text = buttonText
            
            // Показываем информацию
            if (duplicateCount > 0) {
                toast("✓ Загружено: $loadedCount фото, пропущено дубликатов: $duplicateCount")
            } else {
                toast("✓ Загружено фото: $loadedCount")
            }
            
            android.util.Log.d("PhotoGallery", "✓ UI обновлен, кнопка распознавания видна")
            
            binding.btnRecognize.setOnClickListener {
                android.util.Log.d("PhotoGallery", "🔘 Нажата кнопка 'Распознать лица' для ${selectedPhotos.size} фото")
                recognizeMultiplePhotos()
            }
            
        } catch (e: IOException) {
            android.util.Log.e("PhotoGallery", "❌ Ошибка загрузки изображений", e)
            toast("Ошибка загрузки изображений")
            e.printStackTrace()
        }
    }
    
    private fun recognizeMultiplePhotos() {
        if (selectedPhotos.isEmpty()) {
            toast("⚠️ Нет загруженных фото")
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRecognize.isEnabled = false
        binding.tvResult.text = "Распознавание ${selectedPhotos.size} фото..."
        binding.tvResult.visibility = View.VISIBLE
        
        android.util.Log.d("PhotoGallery", "🔍 Начинаем распознавание ${selectedPhotos.size} фото...")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Сохраняем результаты: memberId -> (memberName, список bitmap)
                val allResults = mutableMapOf<String, Pair<String, MutableList<Bitmap>>>()
                var processedCount = 0
                var totalFacesFound = 0
                
                for (bitmap in selectedPhotos) {
                    processedCount++
                    binding.tvResult.text = "Обработано $processedCount из ${selectedPhotos.size}..."
                    
                    android.util.Log.d("PhotoGallery", "📤 Отправляем фото $processedCount на сервер...")
                    val result = FaceRecognitionApi.recognizeFace(bitmap)
                    
                    result.onSuccess { recognitions ->
                        android.util.Log.d("PhotoGallery", "✅ Фото $processedCount: найдено ${recognitions.size} лиц")
                        totalFacesFound += recognitions.size
                        
                        for (recognition in recognitions) {
                            val memberId = recognition.memberId
                            if (!allResults.containsKey(memberId)) {
                                allResults[memberId] = Pair(recognition.memberName, mutableListOf())
                            }
                            allResults[memberId]?.second?.add(bitmap)
                        }
                    }
                    
                    result.onFailure { error ->
                        android.util.Log.e("PhotoGallery", "❌ Ошибка распознавания фото $processedCount: ${error.message}", error)
                    }
                }
                
                // Показываем результаты
                if (allResults.isEmpty()) {
                    binding.tvResult.text = "Лица не распознаны"
                    binding.tvResult.setTextColor(getColor(R.color.red_button))
                    toast("⚠️ На фото не найдено знакомых лиц")
                } else {
                    binding.tvResult.text = "✓ Найдено: $totalFacesFound лиц, ${allResults.size} человек"
                    binding.tvResult.setTextColor(getColor(R.color.green_accent))
                    showMultipleRecognitionResults(allResults)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PhotoGallery", "❌ Исключение: ${e.message}", e)
                binding.tvResult.text = "Ошибка: ${e.message}"
                binding.tvResult.setTextColor(getColor(R.color.red_button))
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnRecognize.isEnabled = true
            }
        }
    }
    
    private fun recognizeFaces(bitmap: Bitmap) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRecognize.isEnabled = false
        binding.tvResult.text = "Распознавание..."
        binding.tvResult.visibility = View.VISIBLE
        
        android.util.Log.d("PhotoGallery", "🔍 Начинаем распознавание лиц...")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                android.util.Log.d("PhotoGallery", "📤 Отправляем запрос на сервер...")
                val result = FaceRecognitionApi.recognizeFace(bitmap)
                
                result.onSuccess { recognitions ->
                    android.util.Log.d("PhotoGallery", "✅ Получен ответ: ${recognitions.size} лиц")
                    if (recognitions.isEmpty()) {
                        binding.tvResult.text = "Лица не распознаны"
                        binding.tvResult.setTextColor(getColor(R.color.red_button))
                    } else {
                        showRecognitionResults(recognitions)
                    }
                }
                
                result.onFailure { error ->
                    android.util.Log.e("PhotoGallery", "❌ Ошибка распознавания: ${error.message}", error)
                    binding.tvResult.text = "Ошибка: ${error.message}"
                    binding.tvResult.setTextColor(getColor(R.color.red_button))
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PhotoGallery", "❌ Исключение: ${e.message}", e)
                binding.tvResult.text = "Ошибка: ${e.message}"
                binding.tvResult.setTextColor(getColor(R.color.red_button))
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnRecognize.isEnabled = true
            }
        }
    }
    
    private fun showMultipleRecognitionResults(results: Map<String, Pair<String, List<Bitmap>>>) {
        val message = buildString {
            append("🎯 Распознано людей: ${results.size}\n\n")
            results.entries.forEachIndexed { index, (memberId, pair) ->
                val (memberName, bitmaps) = pair
                append("${index + 1}. $memberName\n")
                append("   Найдено фото: ${bitmaps.size}\n\n")
            }
        }
        
        binding.tvResult.text = message
        binding.tvResult.setTextColor(getColor(R.color.green_accent))
        
        // Показываем диалог
        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 Результаты распознавания")
            .setMessage(message)
            .setPositiveButton("Прикрепить фото") { _, _ ->
                showAttachMultiplePhotosDialog(results)
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }
    
    private fun showAttachMultiplePhotosDialog(results: Map<String, Pair<String, List<Bitmap>>>) {
        val items = results.map { (memberId, pair) ->
            val (memberName, bitmaps) = pair
            "$memberName (${bitmaps.size} фото)"
        }.toTypedArray()
        
        val checkedItems = BooleanArray(results.size) { true } // По умолчанию все выбраны
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Выберите кому прикрепить фото")
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Прикрепить") { _, _ ->
                attachMultiplePhotosToMembers(results, checkedItems)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun attachMultiplePhotosToMembers(
        results: Map<String, Pair<String, List<Bitmap>>>,
        checkedItems: BooleanArray
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var totalSaved = 0
            var totalSkipped = 0
            
            results.entries.forEachIndexed { index, (memberId, pair) ->
                if (checkedItems[index]) {
                    val (memberName, bitmaps) = pair
                    
                    for (bitmap in bitmaps) {
                        val wasSaved = savePhotoToMemberWithResult(
                            memberId.toLong(),
                            memberName,
                            bitmap
                        )
                        if (wasSaved) totalSaved++ else totalSkipped++
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                when {
                    totalSaved > 0 && totalSkipped == 0 -> {
                        toast("✓ Прикреплено фото: $totalSaved")
                    }
                    totalSaved > 0 && totalSkipped > 0 -> {
                        toast("✓ Прикреплено: $totalSaved, пропущено дубликатов: $totalSkipped")
                    }
                    totalSkipped > 0 -> {
                        toast("ℹ️ Все фото уже были прикреплены")
                    }
                    else -> {
                        toast("⚠️ Не удалось прикрепить фото")
                    }
                }
                
                // Очищаем выбранные фото после прикрепления
                selectedPhotos.forEach { it.recycle() }
                selectedPhotos.clear()
                processedPhotoHashes.clear()
                
                // Скрываем превью
                binding.cardPreview.visibility = View.GONE
                binding.ivPreview.visibility = View.GONE
                binding.btnRecognize.visibility = View.GONE
                binding.tvResult.visibility = View.GONE
            }
        }
    }
    
    private fun showRecognitionResults(results: List<com.example.familyone.api.RecognitionResult>) {
        val message = buildString {
            append("🎯 Найдено лиц: ${results.size}\n\n")
            results.forEachIndexed { index, result ->
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
        
        binding.tvResult.text = message
        binding.tvResult.setTextColor(getColor(R.color.green_accent))
        
        // Показываем диалог с результатами
        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 Результаты распознавания")
            .setMessage(message)
            .setPositiveButton("Прикрепить фото") { dialog, _ ->
                dialog.dismiss()
                // Предлагаем прикрепить фото к члену семьи
                if (results.size == 1) {
                    showAttachPhotoDialog(results[0])
                } else if (results.size > 1) {
                    showMultipleAttachDialog(results)
                }
            }
            .setNeutralButton("Подробнее") { _, _ ->
                showDetailedResults(results)
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }
    
    private fun showDetailedResults(results: List<com.example.familyone.api.RecognitionResult>) {
        val details = buildString {
            append("📊 Подробные результаты:\n\n")
            results.forEach { result ->
                append("👤 ${result.memberName}\n")
                append("🎯 Уверенность: ${(result.confidence * 100).toInt()}%\n")
                append("📍 Позиция: (${result.location.left}, ${result.location.top})\n\n")
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Детальная информация")
            .setMessage(details)
            .setPositiveButton("Понятно", null)
            .show()
    }
    
    private fun showAttachPhotoDialog(result: com.example.familyone.api.RecognitionResult) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Прикрепить фото?")
            .setMessage("Прикрепить это фото к ${result.memberName}?")
            .setPositiveButton("Да") { _, _ ->
                selectedBitmap?.let { bitmap ->
                    // Конвертируем серверный ID обратно в локальный
                    val localMemberId = com.example.familyone.utils.UniqueIdHelper.fromServerId(result.memberId.toLong())
                    savePhotoToMember(localMemberId, result.memberName, bitmap)
                }
            }
            .setNegativeButton("Нет", null)
            .show()
    }
    
    private fun showMultipleAttachDialog(results: List<com.example.familyone.api.RecognitionResult>) {
        val names = results.map { it.memberName }.toTypedArray()
        val checkedItems = BooleanArray(results.size) { true } // По умолчанию все выбраны
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Прикрепить фото к членам семьи")
            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Прикрепить") { _, _ ->
                selectedBitmap?.let { bitmap ->
                    val selectedCount = checkedItems.count { it }
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        var savedCount = 0
                        var skippedCount = 0
                        
                        results.forEachIndexed { index, result ->
                            if (checkedItems[index]) {
                                // Конвертируем серверный ID обратно в локальный
                                val localMemberId = com.example.familyone.utils.UniqueIdHelper.fromServerId(result.memberId.toLong())
                                val wasSaved = savePhotoToMemberWithResult(
                                    localMemberId, 
                                    result.memberName, 
                                    bitmap
                                )
                                if (wasSaved) savedCount++ else skippedCount++
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            when {
                                savedCount > 0 && skippedCount == 0 -> {
                                    val word = when (savedCount) {
                                        1 -> "человеку"
                                        2, 3, 4 -> "людям"
                                        else -> "людям"
                                    }
                                    toast("✓ Фото прикреплено к $savedCount $word")
                                }
                                savedCount > 0 && skippedCount > 0 -> {
                                    toast("✓ Прикреплено: $savedCount, пропущено дубликатов: $skippedCount")
                                }
                                skippedCount > 0 -> {
                                    toast("ℹ️ Все выбранные фото уже прикреплены")
                                }
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun savePhotoToMember(memberId: Long, memberName: String, bitmap: Bitmap, showToast: Boolean = true) {
        android.util.Log.d("PhotoGallery", "💾 Сохраняем фото для члена ID: $memberId ($memberName)")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = com.example.familyone.data.FamilyDatabase.getDatabase(applicationContext)
                
                // Вычисляем хеш изображения для проверки дубликатов
                val imageHash = calculateImageHash(bitmap)
                android.util.Log.d("PhotoGallery", "🔑 Хеш изображения: $imageHash")
                
                // Проверяем существующие фото этого члена семьи
                val existingPhotos = database.memberPhotoDao().getPhotosForMemberSync(memberId)
                
                for (existingPhoto in existingPhotos) {
                    val existingFile = java.io.File(existingPhoto.photoUri)
                    if (existingFile.exists()) {
                        val existingBitmap = android.graphics.BitmapFactory.decodeFile(existingFile.absolutePath)
                        if (existingBitmap != null) {
                            val existingHash = calculateImageHash(existingBitmap)
                            existingBitmap.recycle()
                            
                            if (existingHash == imageHash) {
                                android.util.Log.w("PhotoGallery", "⚠️ Фото уже существует для $memberName")
                                withContext(Dispatchers.Main) {
                                    if (showToast) {
                                        toast("ℹ️ Это фото уже прикреплено к $memberName")
                                    }
                                }
                                return@launch
                            }
                        }
                    }
                }
                
                // Сохраняем bitmap во внутреннее хранилище
                val filename = "photo_${memberId}_${System.currentTimeMillis()}.jpg"
                val file = java.io.File(filesDir, filename)
                
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                android.util.Log.d("PhotoGallery", "✓ Файл сохранен: ${file.absolutePath}")
                
                // Создаем запись в базе данных
                val photo = com.example.familyone.data.MemberPhoto(
                    memberId = memberId,
                    photoUri = file.absolutePath,
                    dateAdded = System.currentTimeMillis()
                )
                
                database.memberPhotoDao().insertPhoto(photo)
                
                android.util.Log.d("PhotoGallery", "✅ Фото добавлено в базу данных для $memberName")
                
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        toast("✓ Фото прикреплено к $memberName")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PhotoGallery", "❌ Ошибка сохранения фото для $memberName", e)
                withContext(Dispatchers.Main) {
                    toast("⚠️ Ошибка сохранения фото: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun savePhotoToMemberWithResult(memberId: Long, memberName: String, bitmap: Bitmap): Boolean {
        return try {
            val database = com.example.familyone.data.FamilyDatabase.getDatabase(applicationContext)
            
            // Вычисляем хеш изображения для проверки дубликатов
            val imageHash = calculateImageHash(bitmap)
            
            // Проверяем существующие фото этого члена семьи
            val existingPhotos = database.memberPhotoDao().getPhotosForMemberSync(memberId)
            
            for (existingPhoto in existingPhotos) {
                val existingFile = java.io.File(existingPhoto.photoUri)
                if (existingFile.exists()) {
                    val existingBitmap = android.graphics.BitmapFactory.decodeFile(existingFile.absolutePath)
                    if (existingBitmap != null) {
                        val existingHash = calculateImageHash(existingBitmap)
                        existingBitmap.recycle()
                        
                        if (existingHash == imageHash) {
                            android.util.Log.w("PhotoGallery", "⚠️ Фото уже существует для $memberName")
                            return false
                        }
                    }
                }
            }
            
            // Сохраняем bitmap во внутреннее хранилище
            val filename = "photo_${memberId}_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(filesDir, filename)
            
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Создаем запись в базе данных
            val photo = com.example.familyone.data.MemberPhoto(
                memberId = memberId,
                photoUri = file.absolutePath,
                dateAdded = System.currentTimeMillis()
            )
            
            database.memberPhotoDao().insertPhoto(photo)
            android.util.Log.d("PhotoGallery", "✅ Фото добавлено в базу данных для $memberName")
            
            true
        } catch (e: Exception) {
            android.util.Log.e("PhotoGallery", "❌ Ошибка сохранения фото для $memberName", e)
            false
        }
    }
    
    private fun calculateImageHash(bitmap: Bitmap): String {
        // Уменьшаем изображение до 8x8 для быстрого сравнения (perceptual hash)
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        
        // Вычисляем среднюю яркость
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
        
        // Создаем хеш на основе сравнения с средней яркостью
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
        
        // Конвертируем бинарную строку в hex для компактности
        return hash.toString().chunked(4).joinToString("") { 
            Integer.parseInt(it, 2).toString(16)
        }
    }
    
    private fun showServerSettingsDialog() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val currentUrl = prefs.getString("face_server_url", "http://192.168.1.178:5000") ?: "http://192.168.1.178:5000"
        
        val input = android.widget.EditText(this)
        input.setText(currentUrl)
        input.hint = "http://192.168.1.178:5000"
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Настройки сервера")
            .setMessage("Введите URL сервера распознавания лиц:")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString("face_server_url", newUrl).apply()
                    FaceRecognitionApi.setServerUrl(newUrl)
                    toast("URL сохранен")
                    checkServerConnection()
                }
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("По умолчанию") { _, _ ->
                val defaultUrl = "http://192.168.1.178:5000"
                prefs.edit().putString("face_server_url", defaultUrl).apply()
                FaceRecognitionApi.setServerUrl(defaultUrl)
                toast("Установлен URL по умолчанию")
                checkServerConnection()
            }
            .show()
    }
}
