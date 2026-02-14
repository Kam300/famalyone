package com.example.familyone.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.familyone.R
import com.example.familyone.data.FamilyMember
import com.example.familyone.data.FamilyRole
import com.example.familyone.data.Gender
import com.example.familyone.databinding.ActivityAddMemberBinding
import com.example.familyone.utils.toast
import com.example.familyone.utils.toLocalizedString
import com.example.familyone.viewmodel.FamilyViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddMemberActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAddMemberBinding
    private lateinit var viewModel: FamilyViewModel
    private var selectedPhotoUri: Uri? = null
    private var selectedGender: Gender? = null
    private var selectedRole: FamilyRole? = null
    private var birthDate: String = ""
    private var weddingDate: String = ""
    private var editingMemberId: Long? = null
    private var selectedFatherId: Long? = null
    private var selectedMotherId: Long? = null
    private var allMembers: List<FamilyMember> = emptyList()
    
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Сохраняем изображение в постоянное хранилище
            val savedPath = com.example.familyone.utils.ImageUtils.saveImageToInternalStorage(this, it)
            if (savedPath != null) {
                selectedPhotoUri = Uri.parse("file://$savedPath")
                showPhotoPreview(savedPath)
                toast("✓ Фото добавлено")
            } else {
                toast("Ошибка сохранения фото")
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            toast(getString(R.string.permission_storage))
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMemberBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]
        
        editingMemberId = intent.getLongExtra("MEMBER_ID", -1L).takeIf { it != -1L }
        
        setupGenderDropdown()
        setupRoleDropdown()
        setupDatePicker()
        setupClickListeners()
        loadAllMembers()
        
        if (editingMemberId != null) {
            loadMemberData(editingMemberId!!)
        }
    }
    
    private fun setupGenderDropdown() {
        val genders = listOf(
            Gender.MALE to getString(R.string.male),
            Gender.FEMALE to getString(R.string.female)
        )
        
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            genders.map { it.second }
        )
        
        binding.actvGender.setAdapter(adapter)
        binding.actvGender.setOnItemClickListener { _, _, position, _ ->
            selectedGender = genders[position].first
        }
    }
    
    private fun setupRoleDropdown() {
        val roles = FamilyRole.values().map { role ->
            role to role.toLocalizedString(this)
        }
        
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            roles.map { it.second }
        )
        
        binding.actvRole.setAdapter(adapter)
        binding.actvRole.setOnItemClickListener { _, _, position, _ ->
            selectedRole = roles[position].first
            updateWeddingDateVisibility()
        }
    }
    
    private fun setupDatePicker() {
        binding.etBirthDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    birthDate = dateFormat.format(calendar.time)
                    binding.etBirthDate.setText(birthDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        
        binding.etWeddingDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    weddingDate = dateFormat.format(calendar.time)
                    binding.etWeddingDate.setText(weddingDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }
    
    private fun updateWeddingDateVisibility() {
        // Показываем поле даты свадьбы для всех ролей кроме детей
        val childRoles = listOf(
            FamilyRole.SON, 
            FamilyRole.DAUGHTER, 
            FamilyRole.GRANDSON, 
            FamilyRole.GRANDDAUGHTER,
            FamilyRole.NEPHEW,
            FamilyRole.NIECE
        )
        
        val shouldShow = selectedRole != null && selectedRole !in childRoles
        binding.tilWeddingDate.visibility = if (shouldShow) View.VISIBLE else View.GONE
        binding.tvWeddingDateLabel.visibility = if (shouldShow) View.VISIBLE else View.GONE
        
        // Очищаем дату свадьбы если скрываем поле
        if (!shouldShow) {
            weddingDate = ""
            binding.etWeddingDate.setText("")
        }
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnSelectPhoto.setOnClickListener {
            checkPermissionAndPickImage()
        }
        
        binding.btnSave.setOnClickListener {
            saveMember()
        }
        
        binding.btnDeleteAll.setOnClickListener {
            showDeleteAllConfirmation()
        }
        
        // Кнопка удаления фото
        binding.fabDeletePhoto.setOnClickListener {
            showDeletePhotoConfirmation()
        }
        
        // Кнопка полноэкранного просмотра
        binding.fabFullscreen.setOnClickListener {
            showFullscreenPhoto()
        }
        
        // Клик по превью для полноэкранного просмотра
        binding.ivPhotoPreview.setOnClickListener {
            showFullscreenPhoto()
        }
        
        // Добавляем форматирование номера телефона при вводе
        setupPhoneNumberFormatting()
    }
    
    /**
     * Показывает предпросмотр фото
     */
    private fun showPhotoPreview(photoPath: String) {
        com.bumptech.glide.Glide.with(this)
            .load(java.io.File(photoPath))
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .centerCrop()
            .into(binding.ivPhotoPreview)
        
        binding.framePhotoPreview.visibility = View.VISIBLE
        binding.layoutNoPhoto.visibility = View.GONE
        binding.btnSelectPhoto.text = "Изменить фото"
    }
    
    /**
     * Скрывает предпросмотр фото
     */
    private fun hidePhotoPreview() {
        binding.framePhotoPreview.visibility = View.GONE
        binding.layoutNoPhoto.visibility = View.VISIBLE
        binding.btnSelectPhoto.text = getString(R.string.select_photo)
        selectedPhotoUri = null
    }
    
    /**
     * Показывает диалог подтверждения удаления фото
     */
    private fun showDeletePhotoConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить фото?")
            .setMessage("Вы уверены, что хотите удалить выбранное фото?")
            .setPositiveButton("Удалить") { _, _ ->
                // Удаляем файл если он был сохранен
                selectedPhotoUri?.let { uri ->
                    val path = uri.toString().replace("file://", "")
                    com.example.familyone.utils.ImageUtils.deleteImageFromInternalStorage(path)
                }
                hidePhotoPreview()
                toast("Фото удалено")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    /**
     * Показывает фото в полноэкранном режиме
     */
    private fun showFullscreenPhoto() {
        selectedPhotoUri?.let { uri ->
            val photoPath = uri.toString().replace("file://", "")
            
            val dialogView = layoutInflater.inflate(R.layout.dialog_photo_view, null)
            val imageView = dialogView.findViewById<android.widget.ImageView>(R.id.ivPhoto)
            
            // Загружаем фото
            com.bumptech.glide.Glide.with(this)
                .load(java.io.File(photoPath))
                .into(imageView)
            
            // Создаем диалог с фото
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Закрыть", null)
                .create()
            
            // Закрытие по клику на фото
            imageView.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        }
    }
    
    /**
     * Настраивает автоматическое форматирование номера телефона при вводе
     */
    private fun setupPhoneNumberFormatting() {
        binding.etPhoneNumber.addTextChangedListener(object : android.text.TextWatcher {
            private var isFormatting = false
            private var deletingHyphen = false
            private var hyphenStart = 0
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isFormatting) return
                
                // Проверяем, удаляется ли дефис или пробел
                if (count == 1 && after == 0 && s != null) {
                    val char = s[start]
                    if (char == '-' || char == ' ' || char == '(' || char == ')') {
                        deletingHyphen = true
                        hyphenStart = start
                    }
                }
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isFormatting || s == null) return
                
                isFormatting = true
                
                // Убираем ошибку при вводе
                binding.tilPhoneNumber.error = null
                
                // Получаем только цифры и +
                val digits = s.toString().replace(Regex("[^+\\d]"), "")
                
                // Форматируем номер
                val formatted = formatPhoneForDisplay(digits)
                
                if (formatted != s.toString()) {
                    s.replace(0, s.length, formatted)
                }
                
                isFormatting = false
            }
        })
    }
    
    /**
     * Форматирует номер для отображения при вводе
     */
    private fun formatPhoneForDisplay(digits: String): String {
        if (digits.isEmpty()) return ""
        
        return when {
            // Российский формат +7
            digits.startsWith("+7") -> {
                val rest = digits.substring(2)
                buildString {
                    append("+7")
                    if (rest.isNotEmpty()) {
                        append(" (")
                        append(rest.take(3))
                        if (rest.length > 3) {
                            append(") ")
                            append(rest.substring(3).take(3))
                            if (rest.length > 6) {
                                append("-")
                                append(rest.substring(6).take(2))
                                if (rest.length > 8) {
                                    append("-")
                                    append(rest.substring(8).take(2))
                                }
                            }
                        }
                    }
                }
            }
            // Российский формат 8
            digits.startsWith("8") && digits.length > 1 -> {
                val rest = digits.substring(1)
                buildString {
                    append("8")
                    if (rest.isNotEmpty()) {
                        append(" (")
                        append(rest.take(3))
                        if (rest.length > 3) {
                            append(") ")
                            append(rest.substring(3).take(3))
                            if (rest.length > 6) {
                                append("-")
                                append(rest.substring(6).take(2))
                                if (rest.length > 8) {
                                    append("-")
                                    append(rest.substring(8).take(2))
                                }
                            }
                        }
                    }
                }
            }
            // Международный формат
            digits.startsWith("+") -> digits
            // Просто цифры
            else -> digits
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
    
    private fun validateInputs(): Boolean {
        var isValid = true
        
        if (binding.etFirstName.text.isNullOrBlank()) {
            binding.tilFirstName.error = getString(R.string.error_empty_field)
            isValid = false
        } else {
            binding.tilFirstName.error = null
        }
        
        if (binding.etLastName.text.isNullOrBlank()) {
            binding.tilLastName.error = getString(R.string.error_empty_field)
            isValid = false
        } else {
            binding.tilLastName.error = null
        }
        
        // Отчество теперь необязательное
        binding.tilPatronymic.error = null
        
        if (selectedGender == null) {
            toast(getString(R.string.error_empty_field))
            isValid = false
        }
        
        if (birthDate.isEmpty()) {
            binding.tilBirthDate.error = getString(R.string.error_invalid_date)
            isValid = false
        } else {
            binding.tilBirthDate.error = null
        }
        
        if (selectedRole == null) {
            toast(getString(R.string.error_empty_field))
            isValid = false
        }
        
        // Валидация номера телефона (если введен)
        val phoneNumber = binding.etPhoneNumber.text?.toString()?.trim()
        if (!phoneNumber.isNullOrEmpty() && !isValidPhoneNumber(phoneNumber)) {
            binding.tilPhoneNumber.error = "Неверный формат номера"
            isValid = false
        } else {
            binding.tilPhoneNumber.error = null
        }
        
        return isValid
    }
    
    /**
     * Проверяет корректность формата номера телефона
     * Поддерживаемые форматы:
     * - +7XXXXXXXXXX (российский)
     * - 8XXXXXXXXXX (российский)
     * - +XXXXXXXXXXX (международный)
     * - Минимум 10 цифр
     */
    private fun isValidPhoneNumber(phone: String): Boolean {
        // Убираем все символы кроме цифр и +
        val cleanPhone = phone.replace(Regex("[^+\\d]"), "")
        
        // Проверяем минимальную длину (10 цифр)
        val digitsOnly = cleanPhone.replace("+", "")
        if (digitsOnly.length < 10) {
            return false
        }
        
        // Проверяем максимальную длину (15 цифр по стандарту E.164)
        if (digitsOnly.length > 15) {
            return false
        }
        
        // Проверяем формат
        return when {
            // Российский формат +7
            cleanPhone.startsWith("+7") -> digitsOnly.length == 11
            // Российский формат 8
            cleanPhone.startsWith("8") && !cleanPhone.startsWith("+") -> digitsOnly.length == 11
            // Международный формат
            cleanPhone.startsWith("+") -> digitsOnly.length in 10..15
            // Просто цифры (локальный номер)
            else -> digitsOnly.length in 10..15
        }
    }
    
    /**
     * Форматирует номер телефона для сохранения
     */
    private fun formatPhoneNumber(phone: String): String {
        val cleanPhone = phone.replace(Regex("[^+\\d]"), "")
        
        // Если начинается с 8, заменяем на +7
        return if (cleanPhone.startsWith("8") && cleanPhone.length == 11) {
            "+7${cleanPhone.substring(1)}"
        } else if (!cleanPhone.startsWith("+") && cleanPhone.length >= 10) {
            "+$cleanPhone"
        } else {
            cleanPhone
        }
    }
    
    private fun saveMember() {
        if (!validateInputs()) {
            return
        }
        
        // Форматируем номер телефона перед сохранением
        val phoneNumber = binding.etPhoneNumber.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val formattedPhone = phoneNumber?.let { formatPhoneNumber(it) }
        
        val member = FamilyMember(
            id = editingMemberId ?: 0,
            firstName = binding.etFirstName.text.toString().trim(),
            lastName = binding.etLastName.text.toString().trim(),
            patronymic = binding.etPatronymic.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            gender = selectedGender!!,
            birthDate = birthDate,
            phoneNumber = formattedPhone,
            role = selectedRole!!,
            photoUri = selectedPhotoUri?.toString(),
            fatherId = selectedFatherId,
            motherId = selectedMotherId,
            weddingDate = weddingDate.takeIf { it.isNotEmpty() }
        )
        
        if (editingMemberId != null) {
            viewModel.updateMember(member) {
                runOnUiThread {
                    showSuccessDialog(member, true)
                    // Регистрируем лицо на сервере если есть фото
                    selectedPhotoUri?.let { uri ->
                        registerFaceOnServer(member, uri)
                    }
                }
            }
        } else {
            viewModel.insertMember(member) { insertedId ->
                runOnUiThread {
                    // Обновляем ID члена семьи после вставки
                    val memberWithId = member.copy(id = insertedId)
                    showSuccessDialog(memberWithId, false)
                    // Регистрируем лицо на сервере если есть фото
                    selectedPhotoUri?.let { uri ->
                        registerFaceOnServer(memberWithId, uri)
                    }
                }
            }
        }
    }
    
    private fun showSuccessDialog(member: FamilyMember, isUpdate: Boolean) {
        val message = if (isUpdate) {
            getString(R.string.member_updated_message)
        } else {
            getString(R.string.member_added_message, member.firstName + " " + member.lastName)
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.member_added_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }
    
    private fun showDeleteAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirmation))
            .setMessage(getString(R.string.delete_all_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                viewModel.deleteAllMembers {
                    runOnUiThread {
                        toast(getString(R.string.ok))
                        finish()
                    }
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }
    
    private fun loadMemberData(memberId: Long) {
        viewModel.getMemberById(memberId) { member ->
            member?.let {
                runOnUiThread {
                    binding.etFirstName.setText(it.firstName)
                    binding.etLastName.setText(it.lastName)
                    binding.etPatronymic.setText(it.patronymic)
                    binding.actvGender.setText(it.gender.toLocalizedString(this), false)
                    selectedGender = it.gender
                    binding.etBirthDate.setText(it.birthDate)
                    birthDate = it.birthDate
                    binding.actvRole.setText(it.role.toLocalizedString(this), false)
                    selectedRole = it.role
                    updateWeddingDateVisibility()
                    binding.etPhoneNumber.setText(it.phoneNumber)
                    
                    // Загружаем дату свадьбы если есть
                    it.weddingDate?.let { wedding ->
                        weddingDate = wedding
                        binding.etWeddingDate.setText(wedding)
                    }
                    
                    // Загружаем выбранных родителей
                    selectedFatherId = it.fatherId
                    selectedMotherId = it.motherId
                    
                    // Обновляем текст кнопок если родители выбраны
                    it.fatherId?.let { fatherId ->
                        viewModel.getMemberById(fatherId) { father ->
                            father?.let { f ->
                                runOnUiThread {
                                    binding.btnSelectFather.text = "Отец: ${f.firstName} ${f.lastName}"
                                }
                            }
                        }
                    }
                    
                    it.motherId?.let { motherId ->
                        viewModel.getMemberById(motherId) { mother ->
                            mother?.let { m ->
                                runOnUiThread {
                                    binding.btnSelectMother.text = "Мать: ${m.firstName} ${m.lastName}"
                                }
                            }
                        }
                    }
                    
                    // Загружаем фото с новым предпросмотром
                    it.photoUri?.let { uriString ->
                        selectedPhotoUri = Uri.parse(uriString)
                        val photoPath = uriString.replace("file://", "")
                        showPhotoPreview(photoPath)
                    }
                }
            }
        }
    }
    
    private fun loadAllMembers() {
        viewModel.allMembers.observe(this) { members ->
            allMembers = members
            setupParentSelectors()
        }
    }
    
    private fun setupParentSelectors() {
        // Фильтруем только взрослых для выбора родителей
        val potentialFathers = allMembers.filter { 
            it.gender == Gender.MALE && 
            it.id != editingMemberId &&
            (it.role == FamilyRole.FATHER || it.role == FamilyRole.GRANDFATHER || it.role == FamilyRole.UNCLE)
        }
        
        val potentialMothers = allMembers.filter { 
            it.gender == Gender.FEMALE && 
            it.id != editingMemberId &&
            (it.role == FamilyRole.MOTHER || it.role == FamilyRole.GRANDMOTHER || it.role == FamilyRole.AUNT)
        }
        
        // Настроить селектор отца
        if (potentialFathers.isNotEmpty()) {
            binding.btnSelectFather.visibility = View.VISIBLE
            binding.btnSelectFather.setOnClickListener {
                showParentSelector("Выберите отца", potentialFathers) { selectedMember ->
                    selectedFatherId = selectedMember.id
                    binding.btnSelectFather.text = "Отец: ${selectedMember.firstName} ${selectedMember.lastName}"
                }
            }
        }
        
        // Настроить селектор матери
        if (potentialMothers.isNotEmpty()) {
            binding.btnSelectMother.visibility = View.VISIBLE
            binding.btnSelectMother.setOnClickListener {
                showParentSelector("Выберите мать", potentialMothers) { selectedMember ->
                    selectedMotherId = selectedMember.id
                    binding.btnSelectMother.text = "Мать: ${selectedMember.firstName} ${selectedMember.lastName}"
                }
            }
        }
    }
    
    private fun showParentSelector(
        title: String,
        members: List<FamilyMember>,
        onSelect: (FamilyMember) -> Unit
    ) {
        val names = members.map { "${it.firstName} ${it.lastName} (${it.role.toLocalizedString(this)})" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names) { _, which ->
                onSelect(members[which])
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Очистить") { _, _ ->
                if (title.contains("отца")) {
                    selectedFatherId = null
                    binding.btnSelectFather.text = "Выбрать отца"
                } else {
                    selectedMotherId = null
                    binding.btnSelectMother.text = "Выбрать мать"
                }
            }
            .show()
    }


    
    private fun registerFaceOnServer(member: FamilyMember, photoUri: Uri) {
        android.util.Log.d("AddMember", "📸 Регистрируем лицо для: ${member.firstName} ${member.lastName} (ID: ${member.id})")
        
        // Инициализируем URL сервера из настроек
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val serverUrl = com.example.familyone.utils.ApiServerConfig.readUnifiedServerUrl(prefs)
        com.example.familyone.api.FaceRecognitionApi.setServerUrl(serverUrl)
        
        // Создаем уникальный ID для сервера (device_id + member_id)
        val uniqueServerId = getUniqueServerId(member.id)
        android.util.Log.d("AddMember", "🔑 Уникальный ID для сервера: $uniqueServerId")
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
                android.util.Log.d("AddMember", "✓ Bitmap загружен: ${bitmap.width}x${bitmap.height}")
                
                val result = com.example.familyone.api.FaceRecognitionApi.registerFace(
                    uniqueServerId,
                    "${member.firstName} ${member.lastName}",
                    bitmap
                )
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    result.onSuccess { message ->
                        android.util.Log.d("AddMember", "✅ Лицо зарегистрировано: $message")
                        toast("✓ Лицо зарегистрировано для распознавания")
                    }
                    result.onFailure { error ->
                        android.util.Log.e("AddMember", "❌ Ошибка регистрации: ${error.message}", error)
                        showPhotoErrorDialog(error.message ?: "Неизвестная ошибка")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AddMember", "❌ Исключение при регистрации", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    toast("⚠️ Ошибка: ${e.message}")
                }
            }
        }
    }


    
    private fun getUniqueServerId(memberId: Long): Long {
        return com.example.familyone.utils.UniqueIdHelper.toServerId(this, memberId)
    }
    
    private fun showPhotoErrorDialog(errorMessage: String) {
        val message = when {
            errorMessage.contains("Route mismatch", ignoreCase = true) || errorMessage.contains("HTTP 405", ignoreCase = true) || errorMessage.contains("HTTP 404", ignoreCase = true) ->
                "������ �� ������������ ������� ����������� ���� �� �������� URL.\n\n��������� ����� ������� � ���������� (������������� .../api) ��� ��������� ������ � endpoint'�� /register_face."
            errorMessage.contains("несколько лиц", ignoreCase = true) -> 
                "На выбранном фото обнаружено несколько лиц.\n\nДля регистрации в системе распознавания используйте фото с одним человеком."
            errorMessage.contains("не найдено лиц", ignoreCase = true) || errorMessage.contains("no faces", ignoreCase = true) ->
                "На выбранном фото не обнаружено лиц.\n\nПопробуйте выбрать другое фото с четким изображением лица."
            else ->
                "Не удалось зарегистрировать лицо для распознавания.\n\n$errorMessage"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Ошибка регистрации лица")
            .setMessage(message)
            .setPositiveButton("Выбрать другое фото") { _, _ ->
                // Открываем выбор фото заново
                checkPermissionAndPickImage()
            }
            .setNegativeButton("Пропустить") { dialog, _ ->
                dialog.dismiss()
                toast("Член семьи сохранен без регистрации лица")
            }
            .setCancelable(false)
            .show()
    }
}
