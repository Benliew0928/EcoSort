package com.example.ecosort.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object VideoThumbnailGenerator {
    
    /**
     * Generate a thumbnail for a video URI and save it to cache
     */
    suspend fun generateThumbnail(context: Context, videoUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("VideoThumbnailGenerator", "Generating thumbnail for: $videoUri")
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            // Get thumbnail at 1 second mark
            val thumbnail = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            if (thumbnail != null) {
                // Save thumbnail to cache
                val cacheDir = File(context.cacheDir, "video_thumbnails")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                val thumbnailFile = File(cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(thumbnailFile)
                
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.close()
                
                val thumbnailUri = Uri.fromFile(thumbnailFile).toString()
                Log.d("VideoThumbnailGenerator", "Thumbnail generated: $thumbnailUri")
                return@withContext thumbnailUri
            } else {
                Log.e("VideoThumbnailGenerator", "Failed to generate thumbnail")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("VideoThumbnailGenerator", "Error generating thumbnail", e)
            return@withContext null
        }
    }
    
    /**
     * Generate thumbnail for Firebase Storage video URL
     */
    suspend fun generateThumbnailFromUrl(context: Context, videoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("VideoThumbnailGenerator", "Generating thumbnail for URL: $videoUrl")
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoUrl)
            
            // Get thumbnail at 1 second mark
            val thumbnail = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            if (thumbnail != null) {
                // Save thumbnail to cache
                val cacheDir = File(context.cacheDir, "video_thumbnails")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                val thumbnailFile = File(cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(thumbnailFile)
                
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.close()
                
                val thumbnailUri = Uri.fromFile(thumbnailFile).toString()
                Log.d("VideoThumbnailGenerator", "Thumbnail generated from URL: $thumbnailUri")
                return@withContext thumbnailUri
            } else {
                Log.e("VideoThumbnailGenerator", "Failed to generate thumbnail from URL")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("VideoThumbnailGenerator", "Error generating thumbnail from URL", e)
            return@withContext null
        }
    }
}
