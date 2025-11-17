package com.example.familyone.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.familyone.R
import com.example.familyone.data.FamilyMember
import com.example.familyone.databinding.ActivityExportBinding
import com.example.familyone.utils.PdfExporter
import com.example.familyone.utils.PdfPageFormat
import com.example.familyone.utils.toast
import com.example.familyone.viewmodel.FamilyViewModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ExportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityExportBinding
    private lateinit var viewModel: FamilyViewModel
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toast(getString(R.string.permission_required))
        } else {
            toast(getString(R.string.permission_storage))
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[FamilyViewModel::class.java]
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnExportJson.setOnClickListener {
            checkPermissionAndExport { members ->
                exportToJson(members)
            }
        }
        
        binding.btnExportCsv.setOnClickListener {
            checkPermissionAndExport { members ->
                exportToCsv(members)
            }
        }
        
        binding.btnExportPdf.setOnClickListener {
            checkPermissionAndExport { members ->
                showPdfFormatDialog(members)
            }
        }
    }
    
    private fun checkPermissionAndExport(exportAction: (List<FamilyMember>) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 and above - scoped storage, no permission needed
            viewModel.getAllMembersSync { members ->
                runOnUiThread {
                    exportAction(members)
                }
            }
        } else {
            // Android 10 and below - need permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.getAllMembersSync { members ->
                    runOnUiThread {
                        exportAction(members)
                    }
                }
            } else {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun exportToJson(members: List<FamilyMember>) {
        try {
            val jsonString = gson.toJson(members)
            val fileName = "family_tree_${getTimestamp()}.json"
            val file = saveToFile(fileName, jsonString)
            
            toast(getString(R.string.export_success) + "\n${file.absolutePath}")
        } catch (e: Exception) {
            toast(getString(R.string.export_error) + ": ${e.message}")
        }
    }
    
    private fun exportToCsv(members: List<FamilyMember>) {
        try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("ID,Имя,Фамилия,Отчество,Пол,Дата рождения,Телефон,Роль,Фото\n")
            
            for (member in members) {
                csvBuilder.append("${member.id},")
                csvBuilder.append("${member.firstName},")
                csvBuilder.append("${member.lastName},")
                csvBuilder.append("${member.patronymic},")
                csvBuilder.append("${member.gender},")
                csvBuilder.append("${member.birthDate},")
                csvBuilder.append("${member.phoneNumber ?: ""},")
                csvBuilder.append("${member.role},")
                csvBuilder.append("${member.photoUri ?: ""}\n")
            }
            
            val fileName = "family_tree_${getTimestamp()}.csv"
            val file = saveToFile(fileName, csvBuilder.toString())
            
            toast(getString(R.string.export_success) + "\n${file.absolutePath}")
        } catch (e: Exception) {
            toast(getString(R.string.export_error) + ": ${e.message}")
        }
    }
    
    private fun showPdfFormatDialog(members: List<FamilyMember>) {
        val formats = PdfPageFormat.values()
        val formatNames = formats.map { it.displayName }.toTypedArray()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Выберите формат PDF")
            .setItems(formatNames) { _, which ->
                exportToPdf(members, formats[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun exportToPdf(members: List<FamilyMember>, format: PdfPageFormat) {
        try {
            if (members.isEmpty()) {
                toast("Нет членов семьи для экспорта")
                return
            }
            
            // Показать прогресс
            toast("Создание PDF...")
            
            // Генерация PDF
            val pdfFile = PdfExporter.exportFamilyTree(this, members, format)
            
            if (pdfFile != null && pdfFile.exists()) {
                toast(getString(R.string.export_success))
                
                // Предложить открыть PDF
                showOpenPdfDialog(pdfFile)
            } else {
                toast(getString(R.string.export_error))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.export_error) + ": ${e.message}")
        }
    }
    
    private fun showOpenPdfDialog(pdfFile: File) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("PDF создан")
            .setMessage("Файл сохранен:\n${pdfFile.name}\n\nОткрыть файл?")
            .setPositiveButton("Открыть") { _, _ ->
                openPdfFile(pdfFile)
            }
            .setNegativeButton("Поделиться") { _, _ ->
                sharePdfFile(pdfFile)
            }
            .setNeutralButton("Закрыть", null)
            .show()
    }
    
    private fun openPdfFile(pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                pdfFile
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            startActivity(Intent.createChooser(intent, "Открыть PDF"))
        } catch (e: Exception) {
            toast("Не удалось открыть PDF: ${e.message}")
        }
    }
    
    private fun sharePdfFile(pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                pdfFile
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            startActivity(Intent.createChooser(intent, "Поделиться PDF"))
        } catch (e: Exception) {
            toast("Не удалось поделиться PDF: ${e.message}")
        }
    }
    
    private fun saveToFile(fileName: String, content: String): File {
        val exportDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "FamilyTree")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FamilyTree")
        }
        
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        val file = File(exportDir, fileName)
        FileWriter(file).use { writer ->
            writer.write(content)
        }
        
        return file
    }
    
    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
}

