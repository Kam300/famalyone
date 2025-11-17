package com.example.familyone.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    
    /**
     * Копирует изображение из временного URI в постоянное хранилище приложения
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        try {
            // Загружаем bitmap из URI
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            
            // Создаем папку для изображений
            val imagesDir = File(context.filesDir, "family_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            // Создаем уникальное имя файла
            val fileName = "IMG_${UUID.randomUUID()}.jpg"
            val imageFile = File(imagesDir, fileName)
            
            // Сохраняем bitmap в файл
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            return imageFile.absolutePath
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Удаляет изображение из внутреннего хранилища
     */
    fun deleteImageFromInternalStorage(imagePath: String?): Boolean {
        if (imagePath.isNullOrEmpty()) return false
        
        return try {
            val file = File(imagePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

