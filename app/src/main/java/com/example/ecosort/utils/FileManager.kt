package com.example.ecosort.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class FileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FileManager"
    }
    
    /**
     * Copy a file from cache to persistent storage
     * This ensures files persist even when cache is cleared
     */
    fun copyToPersistentStorage(sourceFile: File, subDirectory: String = "chat_media"): File? {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val persistentDir = File(externalFilesDir, subDirectory)
            if (!persistentDir.exists()) {
                persistentDir.mkdirs()
            }
            
            val persistentFile = File(persistentDir, sourceFile.name)
            
            // Copy file content
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(persistentFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Copied file from ${sourceFile.absolutePath} to ${persistentFile.absolutePath}")
            persistentFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file to persistent storage", e)
            null
        }
    }
    
    /**
     * Copy a file from URI to persistent storage
     * This is used for gallery images
     */
    fun copyUriToPersistentStorage(uri: Uri, fileName: String, subDirectory: String = "chat_media"): File? {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val persistentDir = File(externalFilesDir, subDirectory)
            if (!persistentDir.exists()) {
                persistentDir.mkdirs()
            }
            
            val persistentFile = File(persistentDir, fileName)
            
            // Copy from URI to persistent file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(persistentFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Copied URI $uri to ${persistentFile.absolutePath}")
            persistentFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying URI to persistent storage", e)
            null
        }
    }
    
    /**
     * Get a file from persistent storage
     * Returns null if file doesn't exist
     */
    fun getPersistentFile(fileName: String, subDirectory: String = "chat_media"): File? {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val persistentDir = File(externalFilesDir, subDirectory)
            val file = File(persistentDir, fileName)
            
            if (file.exists()) {
                Log.d(TAG, "Found persistent file: ${file.absolutePath}")
                file
            } else {
                Log.w(TAG, "Persistent file not found: ${file.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting persistent file", e)
            null
        }
    }
    
    /**
     * Clean up old files from persistent storage
     * Keeps files for 7 days
     */
    fun cleanupOldPersistentFiles(subDirectory: String = "chat_media") {
        try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val persistentDir = File(externalFilesDir, subDirectory)
            
            if (persistentDir.exists()) {
                val currentTime = System.currentTimeMillis()
                val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days
                
                persistentDir.listFiles()?.forEach { file ->
                    if (currentTime - file.lastModified() > maxAge) {
                        file.delete()
                        Log.d(TAG, "Deleted old persistent file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up persistent files", e)
        }
    }
}
