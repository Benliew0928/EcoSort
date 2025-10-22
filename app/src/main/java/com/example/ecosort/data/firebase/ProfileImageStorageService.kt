package com.example.ecosort.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileImageStorageService @Inject constructor() {
    
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val profileImagesRef: StorageReference by lazy { 
        storage.reference.child("profile_images") 
    }
    
    /**
     * Upload profile image to Firebase Storage
     */
    suspend fun uploadProfileImage(userId: Long, imageUri: Uri): Result<String> {
        return try {
            android.util.Log.d("ProfileImageStorageService", "Uploading profile image for user: $userId")
            
            // Create unique filename
            val fileName = "profile_${userId}_${System.currentTimeMillis()}.jpg"
            val imageRef = profileImagesRef.child(fileName)
            
            // Upload the image
            val uploadTask = imageRef.putFile(imageUri).await()
            
            // Get download URL
            val downloadUrl = imageRef.downloadUrl.await()
            val urlString = downloadUrl.toString()
            
            android.util.Log.d("ProfileImageStorageService", "Profile image uploaded successfully: $urlString")
            Result.success(urlString)
        } catch (e: Exception) {
            android.util.Log.e("ProfileImageStorageService", "Error uploading profile image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete profile image from Firebase Storage
     */
    suspend fun deleteProfileImage(imageUrl: String): Result<Unit> {
        return try {
            if (imageUrl.startsWith("https://firebasestorage.googleapis.com/")) {
                val imageRef = storage.getReferenceFromUrl(imageUrl)
                imageRef.delete().await()
                android.util.Log.d("ProfileImageStorageService", "Profile image deleted successfully")
                Result.success(Unit)
            } else {
                android.util.Log.w("ProfileImageStorageService", "Invalid image URL, skipping deletion")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileImageStorageService", "Error deleting profile image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update profile image (delete old, upload new)
     */
    suspend fun updateProfileImage(userId: Long, newImageUri: Uri, oldImageUrl: String?): Result<String> {
        return try {
            // Delete old image if it exists
            if (!oldImageUrl.isNullOrBlank()) {
                val deleteResult = deleteProfileImage(oldImageUrl)
                if (deleteResult.isFailure) {
                    android.util.Log.w("ProfileImageStorageService", "Failed to delete old image: ${deleteResult.exceptionOrNull()?.message}")
                    // Continue with upload even if delete fails
                }
            }
            
            // Upload new image
            uploadProfileImage(userId, newImageUri)
        } catch (e: Exception) {
            android.util.Log.e("ProfileImageStorageService", "Error updating profile image", e)
            Result.failure(e)
        }
    }
}
