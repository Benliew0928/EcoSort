package com.example.ecosort.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.ecosort.data.firebase.ProfileImageStorageService
import com.example.ecosort.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileImageManager @Inject constructor(
    private val profileImageStorageService: ProfileImageStorageService,
    private val userRepository: UserRepository
) {
    
    companion object {
        private const val MAX_IMAGE_SIZE = 1024 // Max width/height in pixels
        private const val COMPRESSION_QUALITY = 85 // JPEG compression quality (0-100)
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB max file size
    }
    
    /**
     * Process and upload profile image
     */
    suspend fun uploadProfileImage(
        context: Context,
        userId: Long,
        imageUri: Uri,
        oldImageUrl: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("ProfileImageManager", "Processing profile image for user: $userId")
            
            // Step 1: Validate and process the image
            val processedImageUri = processImage(context, imageUri) ?: return@withContext Result.failure(
                Exception("Failed to process image")
            )
            
            // Step 2: Upload to Firebase Storage
            val uploadResult = profileImageStorageService.updateProfileImage(
                userId, 
                processedImageUri, 
                oldImageUrl
            )
            
            if (uploadResult.isFailure) {
                return@withContext Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }
            
            val newImageUrl = uploadResult.getOrNull() ?: return@withContext Result.failure(Exception("Failed to get image URL"))
            
            // Step 3: Update user profile in database
            val updateResult = userRepository.updateProfileImage(userId, newImageUrl)
            if (updateResult is com.example.ecosort.data.model.Result.Error) {
                Log.e("ProfileImageManager", "Failed to update user profile with new image URL")
                // Try to delete the uploaded image since database update failed
                profileImageStorageService.deleteProfileImage(newImageUrl)
                return@withContext Result.failure(updateResult.exception)
            }
            
            // Step 4: Recalculate profile completion
            userRepository.calculateProfileCompletion(userId)
            
            Log.d("ProfileImageManager", "Profile image uploaded and updated successfully")
            Result.success(newImageUrl)
            
        } catch (e: Exception) {
            Log.e("ProfileImageManager", "Error uploading profile image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete profile image
     */
    suspend fun deleteProfileImage(userId: Long, imageUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("ProfileImageManager", "Deleting profile image for user: $userId")
            
            // Delete from Firebase Storage
            val deleteResult = profileImageStorageService.deleteProfileImage(imageUrl)
            if (deleteResult.isFailure) {
                return@withContext Result.failure(deleteResult.exceptionOrNull() ?: Exception("Delete failed"))
            }
            
            // Update user profile in database
            val updateResult = userRepository.updateProfileImage(userId, null)
            if (updateResult is com.example.ecosort.data.model.Result.Error) {
                Log.e("ProfileImageManager", "Failed to update user profile after image deletion")
                return@withContext Result.failure(updateResult.exception)
            }
            
            // Recalculate profile completion
            userRepository.calculateProfileCompletion(userId)
            
            Log.d("ProfileImageManager", "Profile image deleted successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e("ProfileImageManager", "Error deleting profile image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Process image: resize, compress, and save to temporary file
     */
    private suspend fun processImage(context: Context, imageUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            // Read the original image
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (originalBitmap == null) {
                Log.e("ProfileImageManager", "Failed to decode image")
                return@withContext null
            }
            
            // Check file size
            val originalSize = getImageSize(context, imageUri)
            if (originalSize > MAX_FILE_SIZE) {
                Log.w("ProfileImageManager", "Image too large: ${originalSize} bytes")
            }
            
            // Resize image if needed
            val resizedBitmap = resizeImage(originalBitmap)
            
            // Compress and save to temporary file
            val tempFile = createTempImageFile(context)
            val outputStream = FileOutputStream(tempFile)
            
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Recycle bitmaps to free memory
            originalBitmap.recycle()
            resizedBitmap.recycle()
            
            // Create URI for the processed image
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            
        } catch (e: Exception) {
            Log.e("ProfileImageManager", "Error processing image", e)
            null
        }
    }
    
    /**
     * Resize image to fit within MAX_IMAGE_SIZE while maintaining aspect ratio
     */
    private fun resizeImage(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }
        
        val scale = minOf(
            MAX_IMAGE_SIZE.toFloat() / width,
            MAX_IMAGE_SIZE.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Get image file size in bytes
     */
    private fun getImageSize(context: Context, uri: Uri): Long {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            bytes?.size?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e("ProfileImageManager", "Error getting image size", e)
            0L
        }
    }
    
    /**
     * Create temporary file for processed image
     */
    private fun createTempImageFile(context: Context): File {
        val tempDir = File(context.cacheDir, "profile_images")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        return File.createTempFile(
            "profile_${System.currentTimeMillis()}",
            ".jpg",
            tempDir
        )
    }
    
    /**
     * Clean up temporary files
     */
    fun cleanupTempFiles(context: Context) {
        try {
            val tempDir = File(context.cacheDir, "profile_images")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("profile_")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileImageManager", "Error cleaning up temp files", e)
        }
    }
}
