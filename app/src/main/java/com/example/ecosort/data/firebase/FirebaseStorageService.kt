package com.example.ecosort.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageService @Inject constructor() {
    
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val communityImagesRef: StorageReference by lazy { 
        storage.reference.child("community_images") 
    }
    
    private val communityVideosRef: StorageReference by lazy { 
        storage.reference.child("community_videos") 
    }
    
    /**
     * Upload an image to Firebase Storage and return the download URL
     */
    suspend fun uploadCommunityImage(imageUri: Uri, fileName: String): Result<String> {
        return try {
            android.util.Log.d("FirebaseStorageService", "Uploading image: $fileName")
            android.util.Log.d("FirebaseStorageService", "Image URI: $imageUri")
            android.util.Log.d("FirebaseStorageService", "URI scheme: ${imageUri.scheme}")
            
            val imageRef = communityImagesRef.child(fileName)
            val uploadTask = imageRef.putFile(imageUri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()
            
            android.util.Log.d("FirebaseStorageService", "Image uploaded successfully: $downloadUrl")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            android.util.Log.e("FirebaseStorageService", "Error uploading image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a video to Firebase Storage and return the download URL
     */
    suspend fun uploadCommunityVideo(videoUri: Uri, fileName: String): Result<String> {
        return try {
            android.util.Log.d("FirebaseStorageService", "Uploading video: $fileName")
            android.util.Log.d("FirebaseStorageService", "Video URI: $videoUri")
            android.util.Log.d("FirebaseStorageService", "URI scheme: ${videoUri.scheme}")
            
            val videoRef = communityVideosRef.child(fileName)
            val uploadTask = videoRef.putFile(videoUri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()
            
            android.util.Log.d("FirebaseStorageService", "Video uploaded successfully: $downloadUrl")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            android.util.Log.e("FirebaseStorageService", "Error uploading video", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an image from Firebase Storage
     */
    suspend fun deleteCommunityImage(imageUrl: String): Result<Unit> {
        return try {
            val imageRef = storage.getReferenceFromUrl(imageUrl)
            imageRef.delete().await()
            android.util.Log.d("FirebaseStorageService", "Image deleted successfully: $imageUrl")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseStorageService", "Error deleting image", e)
            Result.failure(e)
        }
    }
}