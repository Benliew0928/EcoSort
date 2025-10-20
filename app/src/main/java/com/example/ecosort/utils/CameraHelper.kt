package com.example.ecosort.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CameraHelper(private val context: Context) {
    
    var currentPhotoPath: String? = null
    
    fun createImageFile(): File? {
        return try {
            val cacheDir = context.cacheDir
            val imageDir = File(cacheDir, "chat_images")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }
            
            val imageFile = File(imageDir, "image_${System.currentTimeMillis()}.jpg")
            currentPhotoPath = imageFile.absolutePath
            Log.d("CameraHelper", "Created image file: ${imageFile.absolutePath}")
            imageFile
        } catch (e: Exception) {
            Log.e("CameraHelper", "Error creating image file", e)
            null
        }
    }
    
    fun compressImage(originalFile: File, maxSizeKB: Int = 500): File? {
        return try {
            val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
            if (bitmap == null) {
                Log.e("CameraHelper", "Could not decode bitmap from file")
                return null
            }
            
            // Calculate compression ratio
            val originalSizeKB = originalFile.length() / 1024
            val compressionRatio = if (originalSizeKB > maxSizeKB) {
                maxSizeKB.toFloat() / originalSizeKB.toFloat()
            } else {
                1.0f
            }
            
            // Create compressed file
            val compressedFile = File(originalFile.parent, "compressed_${originalFile.name}")
            val outputStream = FileOutputStream(compressedFile)
            
            val quality = (compressionRatio * 100).toInt().coerceIn(10, 100)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.close()
            
            Log.d("CameraHelper", "Compressed image: ${originalSizeKB}KB -> ${compressedFile.length() / 1024}KB")
            compressedFile
            
        } catch (e: Exception) {
            Log.e("CameraHelper", "Error compressing image", e)
            null
        }
    }
    
    fun getImageFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("CameraHelper", "Could not open input stream for URI: $uri")
                return null
            }
            
            val imageFile = createImageFile()
            if (imageFile == null) {
                inputStream.close()
                return null
            }
            
            val outputStream = FileOutputStream(imageFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            
            Log.d("CameraHelper", "Saved image from URI to: ${imageFile.absolutePath}")
            imageFile
            
        } catch (e: Exception) {
            Log.e("CameraHelper", "Error getting image from URI", e)
            null
        }
    }
    
    fun cleanupOldFiles() {
        try {
            val cacheDir = context.cacheDir
            val imageDir = File(cacheDir, "chat_images")
            val voiceDir = File(cacheDir, "voice_messages")
            
            // Clean up files older than 24 hours
            val currentTime = System.currentTimeMillis()
            val maxAge = 24 * 60 * 60 * 1000L // 24 hours
            
            listOf(imageDir, voiceDir).forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (currentTime - file.lastModified() > maxAge) {
                            file.delete()
                            Log.d("CameraHelper", "Deleted old file: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraHelper", "Error cleaning up old files", e)
        }
    }
}
