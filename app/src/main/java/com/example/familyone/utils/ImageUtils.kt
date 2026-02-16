package com.example.familyone.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {
    
    /**
     * Копирует изображение из временного URI в постоянное хранилище приложения.
     * Автоматически применяет правильную ориентацию из EXIF-метаданных.
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        try {
            // Загружаем bitmap из URI
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap == null) return null
            
            // Исправляем ориентацию по EXIF
            val rotatedBitmap = fixOrientation(context, uri, bitmap)
            
            // Создаем папку для изображений
            val imagesDir = File(context.filesDir, "family_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            // Создаем уникальное имя файла
            val fileName = "IMG_${UUID.randomUUID()}.jpg"
            val imageFile = File(imagesDir, fileName)
            
            // Сохраняем bitmap в файл (уже с правильной ориентацией)
            FileOutputStream(imageFile).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            return imageFile.absolutePath
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Исправляет ориентацию изображения на основе EXIF-метаданных.
     * Android камеры часто сохраняют фото повёрнутыми с EXIF-тегом ориентации.
     */
    private fun fixOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()
            
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.preScale(-1f, 1f)
                }
                else -> return bitmap // Ориентация нормальная, возвращаем как есть
            }
            
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            return rotated
        } catch (e: Exception) {
            e.printStackTrace()
            return bitmap // При ошибке возвращаем оригинал
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
