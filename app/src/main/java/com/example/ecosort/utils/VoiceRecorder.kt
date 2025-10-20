package com.example.ecosort.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null
    
    fun startRecording(): Result<File> {
        return try {
            if (isRecording) {
                return Result.failure(Exception("Already recording"))
            }
            
            // Create output file
            val cacheDir = context.cacheDir
            val audioDir = File(cacheDir, "voice_messages")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            outputFile = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            
            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile!!.absolutePath)
                
                prepare()
                start()
            }
            
            isRecording = true
            Log.d("VoiceRecorder", "Started recording to: ${outputFile!!.absolutePath}")
            Result.success(outputFile!!)
            
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error starting recording", e)
            stopRecording()
            Result.failure(e)
        }
    }
    
    fun stopRecording(): Long {
        return try {
            if (!isRecording) {
                return 0L
            }
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            val duration = getAudioDuration(outputFile)
            Log.d("VoiceRecorder", "Stopped recording. Duration: ${duration}ms")
            duration
            
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error stopping recording", e)
            0L
        }
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun getCurrentOutputFile(): File? = outputFile
    
    private fun getAudioDuration(file: File?): Long {
        return try {
            if (file == null || !file.exists()) {
                Log.e("VoiceRecorder", "File is null or doesn't exist")
                return 0L
            }
            
            Log.d("VoiceRecorder", "Getting duration for file: ${file.absolutePath}, size: ${file.length()} bytes")
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            
            if (durationStr != null) {
                val durationMs = durationStr.toLong()
                Log.d("VoiceRecorder", "Actual audio duration: ${durationMs}ms")
                durationMs
            } else {
                Log.e("VoiceRecorder", "Could not extract duration from audio file")
                // Fallback: estimate based on file size for AAC audio
                val fileSizeKB = file.length() / 1024
                val estimatedDurationMs = fileSizeKB * 50 // More accurate estimate for AAC: 1KB ≈ 50ms
                Log.d("VoiceRecorder", "Using estimated duration: ${estimatedDurationMs}ms")
                estimatedDurationMs.coerceIn(1000, 300000) // Between 1s and 5min
            }
            
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error getting audio duration", e)
            0L
        }
    }
    
    fun cleanup() {
        try {
            if (isRecording) {
                stopRecording()
            }
            outputFile?.delete()
            outputFile = null
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error during cleanup", e)
        }
    }
}
