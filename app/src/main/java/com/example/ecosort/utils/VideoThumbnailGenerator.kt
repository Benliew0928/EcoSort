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
     * For Firebase URLs, we'll use a fallback approach since MediaMetadataRetriever has limitations with remote URLs
     */
    suspend fun generateThumbnailFromUrl(context: Context, videoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("VideoThumbnailGenerator", "Generating thumbnail for URL: $videoUrl")
            Log.d("VideoThumbnailGenerator", "URL type: ${if (videoUrl.startsWith("http")) "HTTP" else if (videoUrl.startsWith("content://")) "CONTENT" else if (videoUrl.startsWith("file://")) "FILE" else "UNKNOWN"}")
            
            // For Firebase Storage URLs, try to generate thumbnail but be prepared for failure
            if (videoUrl.contains("firebasestorage.googleapis.com")) {
                Log.d("VideoThumbnailGenerator", "Firebase Storage URL detected - attempting thumbnail generation")
                Log.d("VideoThumbnailGenerator", "Note: MediaMetadataRetriever may have limitations with Firebase URLs")
            }
            
            val retriever = MediaMetadataRetriever()
            
            // Try different methods based on URL type
            when {
                videoUrl.startsWith("http") -> {
                    Log.d("VideoThumbnailGenerator", "Setting data source for HTTP URL")
                    try {
                        retriever.setDataSource(videoUrl)
                    } catch (e: Exception) {
                        Log.e("VideoThumbnailGenerator", "Failed to set data source for HTTP URL", e)
                        retriever.release()
                        return@withContext null
                    }
                }
                videoUrl.startsWith("content://") -> {
                    Log.d("VideoThumbnailGenerator", "Setting data source for content URI")
                    try {
                        retriever.setDataSource(context, Uri.parse(videoUrl))
                    } catch (e: Exception) {
                        Log.e("VideoThumbnailGenerator", "Failed to set data source for content URI", e)
                        retriever.release()
                        return@withContext null
                    }
                }
                videoUrl.startsWith("file://") -> {
                    Log.d("VideoThumbnailGenerator", "Setting data source for file URI")
                    try {
                        retriever.setDataSource(videoUrl)
                    } catch (e: Exception) {
                        Log.e("VideoThumbnailGenerator", "Failed to set data source for file URI", e)
                        retriever.release()
                        return@withContext null
                    }
                }
                else -> {
                    Log.d("VideoThumbnailGenerator", "Setting data source for unknown URI type")
                    try {
                        retriever.setDataSource(videoUrl)
                    } catch (e: Exception) {
                        Log.e("VideoThumbnailGenerator", "Failed to set data source for unknown URI", e)
                        retriever.release()
                        return@withContext null
                    }
                }
            }
            
            // Get thumbnail at 1 second mark
            Log.d("VideoThumbnailGenerator", "Extracting frame at 1 second mark")
            val thumbnail = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            if (thumbnail != null) {
                Log.d("VideoThumbnailGenerator", "Thumbnail extracted successfully, size: ${thumbnail.width}x${thumbnail.height}")
                
                // Save thumbnail to cache
                val cacheDir = File(context.cacheDir, "video_thumbnails")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                    Log.d("VideoThumbnailGenerator", "Created cache directory: ${cacheDir.absolutePath}")
                }
                
                val thumbnailFile = File(cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(thumbnailFile)
                
                val compressed = thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.close()
                
                if (compressed) {
                    val thumbnailUri = Uri.fromFile(thumbnailFile).toString()
                    Log.d("VideoThumbnailGenerator", "Thumbnail saved successfully: $thumbnailUri")
                    Log.d("VideoThumbnailGenerator", "File size: ${thumbnailFile.length()} bytes")
                    return@withContext thumbnailUri
                } else {
                    Log.e("VideoThumbnailGenerator", "Failed to compress thumbnail")
                    return@withContext null
                }
            } else {
                Log.e("VideoThumbnailGenerator", "Failed to extract thumbnail from video")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("VideoThumbnailGenerator", "Error generating thumbnail from URL: $videoUrl", e)
            Log.e("VideoThumbnailGenerator", "Exception type: ${e.javaClass.simpleName}")
            Log.e("VideoThumbnailGenerator", "Exception message: ${e.message}")
            return@withContext null
        }
    }
}
