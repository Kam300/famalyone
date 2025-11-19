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
                com.bumptech.glide.Glide.with(this)
                    .load(java.io.File(savedPath))
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .centerCrop()
                    .into(binding.ivPhotoPreview)
                binding.ivPhotoPreview.visibility = View.VISIBLE
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
        
        return isValid
    }
    
    private fun saveMember() {
        if (!validateInputs()) {
            return
        }
        
        val member = FamilyMember(
            id = editingMemberId ?: 0,
            firstName = binding.etFirstName.text.toString().trim(),
            lastName = binding.etLastName.text.toString().trim(),
            patronymic = binding.etPatronymic.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            gender = selectedGender!!,
            birthDate = birthDate,
            phoneNumber = binding.etPhoneNumber.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
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
                    
                    it.photoUri?.let { uriString ->
                        selectedPhotoUri = Uri.parse(uriString)
                        // Загружаем изображение через Glide для надежности
                        val photoPath = uriString.replace("file://", "")
                        com.bumptech.glide.Glide.with(this@AddMemberActivity)
                            .load(java.io.File(photoPath))
                            .placeholder(R.mipmap.ic_launcher)
                            .error(R.mipmap.ic_launcher)
                            .into(binding.ivPhotoPreview)
                        binding.ivPhotoPreview.visibility = View.VISIBLE
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
        val serverUrl = prefs.getString("face_server_url", "http://10.0.2.2:5000") ?: "http://10.0.2.2:5000"
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
