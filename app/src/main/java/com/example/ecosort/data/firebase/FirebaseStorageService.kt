package com.example.ecosort.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageService @Inject constructor() {
    
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val storageRef: StorageReference by lazy { storage.reference }
    
    /**
     * Upload an image to Firebase Storage
     * @param imageUri Local URI of the image to upload
     * @param folderName Folder name in storage (e.g., "marketplace_images")
     * @return Download URL of the uploaded image
     */
    suspend fun uploadImage(imageUri: Uri, folderName: String = "marketplace_images"): Result<String> {
        return try {
            android.util.Log.d("FirebaseStorageService", "Starting image upload...")
            
            // Generate unique filename
            val fileName = "${UUID.randomUUID()}.jpg"
            val imageRef = storageRef.child("$folderName/$fileName")
            
            android.util.Log.d("FirebaseStorageService", "Uploading to: $folderName/$fileName")
            
            // Upload the file
            val uploadTask = imageRef.putFile(imageUri).await()
            android.util.Log.d("FirebaseStorageService", "Upload completed")
            
            // Get download URL
            val downloadUrl = imageRef.downloadUrl.await()
            android.util.Log.d("FirebaseStorageService", "Got download URL: $downloadUrl")
            
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            android.util.Log.e("FirebaseStorageService", "Upload failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete an image from Firebase Storage
     * @param imageUrl The download URL of the image to delete
     * @return Success or failure result
     */
    suspend fun deleteImage(imageUrl: String): Result<Unit> {
        return try {
            val imageRef = storage.getReferenceFromUrl(imageUrl)
            imageRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get a reference to a specific image
     * @param imagePath Path to the image in storage
     * @return StorageReference
     */
    fun getImageReference(imagePath: String): StorageReference {
        return storageRef.child(imagePath)
    }
}
