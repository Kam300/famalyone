package com.example.familyone.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.bumptech.glide.Glide
import com.example.familyone.R
import com.example.familyone.data.MemberPhoto
import com.example.familyone.databinding.ActivityMemberProfileBinding
import com.example.familyone.utils.ImageUtils
import com.example.familyone.utils.toast
import com.example.familyone.utils.toLocalizedString
import com.example.familyone.viewmodel.FamilyViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MemberProfileActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMemberProfileBinding
    private lateinit var viewModel: FamilyViewModel
    private lateinit var photoAdapter: PhotoGalleryAdapter
    private var memberId: Long = -1
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { addPhotoToGallery(it) }
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
        binding = ActivityMemberProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        memberId = intent.getLongExtra("MEMBER_ID", -1)
        if (memberId == -1L) {
            toast("Ошибка: член семьи не найден")
            finish()
            return
        }
        
        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]
        
        setupRecyclerView()
        setupClickListeners()
        loadMemberData()
        loadPhotos()
    }
    
    private fun setupRecyclerView() {
        photoAdapter = PhotoGalleryAdapter(
            onPhotoClick = { photo -> showPhotoDialog(photo) },
            onPhotoLongClick = { photo -> showDeletePhotoDialog(photo) }
        )
        
        binding.rvPhotos.apply {
            layoutManager = GridLayoutManager(this@MemberProfileActivity, 3)
            adapter = photoAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnAddPhoto.setOnClickListener {
            checkPermissionAndPickImage()
        }
        
        binding.btnEdit.setOnClickListener {
            val intent = Intent(this, AddMemberActivity::class.java)
            intent.putExtra("MEMBER_ID", memberId)
            startActivity(intent)
        }
    }
    
    private fun loadMemberData() {
        viewModel.getMemberById(memberId) { member ->
            member?.let {
                runOnUiThread {
                    binding.tvName.text = "${it.firstName} ${it.lastName}"
                    binding.tvRole.text = it.role.toLocalizedString(this)
                    binding.tvBirthDate.text = "Дата рождения: ${it.birthDate}"
                    
                    it.patronymic?.let { patronymic ->
                        binding.tvPatronymic.text = patronymic
                        binding.tvPatronymic.visibility = View.VISIBLE
                    }
                    
                    it.phoneNumber?.let { phone ->
                        binding.tvPhone.text = "📱 $phone"
                        binding.tvPhone.visibility = View.VISIBLE
                        
                        // Показываем кнопки связи
                        binding.layoutContactButtons.visibility = View.VISIBLE
                        setupContactButtons(phone)
                    }
                    
                    it.weddingDate?.let { wedding ->
                        binding.tvWedding.text = "💍 Свадьба: $wedding"
                        binding.tvWedding.visibility = View.VISIBLE
                    }
                    
                    // Загружаем фото профиля
                    it.photoUri?.let { uri ->
                        val photoPath = uri.replace("file://", "")
                        Glide.with(this)
                            .load(java.io.File(photoPath))
                            .placeholder(R.mipmap.ic_launcher)
                            .error(R.mipmap.ic_launcher)
                            .centerCrop()
                            .into(binding.ivProfilePhoto)
                    }
                }
            }
        }
    }
    
    private fun loadPhotos() {
        viewModel.getPhotosForMember(memberId).observe(this) { photos ->
            if (photos.isEmpty()) {
                binding.tvEmptyGallery.visibility = View.VISIBLE
                binding.rvPhotos.visibility = View.GONE
            } else {
                binding.tvEmptyGallery.visibility = View.GONE
                binding.rvPhotos.visibility = View.VISIBLE
                photoAdapter.submitList(photos)
            }
            
            binding.tvPhotoCount.text = "Фотографий: ${photos.size}"
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
    
    private fun addPhotoToGallery(uri: Uri) {
        val savedPath = ImageUtils.saveImageToInternalStorage(this, uri)
        if (savedPath != null) {
            val photo = MemberPhoto(
                memberId = memberId,
                photoUri = "file://$savedPath"
            )
            
            viewModel.insertPhoto(photo) {
                runOnUiThread {
                    toast("Фото добавлено")
                }
            }
        } else {
            toast("Ошибка сохранения фото")
        }
    }
    
    private fun showPhotoDialog(photo: MemberPhoto) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_photo_view, null)
        val imageView = dialogView.findViewById<android.widget.ImageView>(R.id.ivPhoto)
        
        val photoPath = photo.photoUri.replace("file://", "")
        Glide.with(this)
            .load(java.io.File(photoPath))
            .into(imageView)
        
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .setNegativeButton("Удалить") { _, _ ->
                showDeletePhotoDialog(photo)
            }
            .show()
    }
    
    private fun showDeletePhotoDialog(photo: MemberPhoto) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить фото?")
            .setMessage("Это действие нельзя отменить")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deletePhoto(photo) {
                    runOnUiThread {
                        toast("Фото удалено")
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    
    private fun setupContactButtons(phoneNumber: String) {
        // WhatsApp
        binding.btnWhatsApp.setOnClickListener {
            openWhatsApp(phoneNumber)
        }
        
        // Telegram
        binding.btnTelegram.setOnClickListener {
            openTelegram(phoneNumber)
        }
        
        // Позвонить
        binding.btnCall.setOnClickListener {
            makePhoneCall(phoneNumber)
        }
    }
    
    private fun openWhatsApp(phoneNumber: String) {
        try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/$cleanNumber")
            startActivity(intent)
        } catch (e: Exception) {
            toast("WhatsApp не установлен")
        }
    }
    
    private fun openTelegram(phoneNumber: String) {
        try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://t.me/$cleanNumber")
            startActivity(intent)
        } catch (e: Exception) {
            toast("Telegram не установлен")
        }
    }
    
    private fun makePhoneCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        startActivity(intent)
    }
}
