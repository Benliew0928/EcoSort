package com.example.ecosort.ml

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * Google ML Kit implementation of ObjectDetectorService
 * This is used for the Google Play Store build variant
 */
class GoogleMLKitDetector : ObjectDetectorService {
    
    private var objectDetector: ObjectDetector? = null
    
    override fun initialize() {
        try {
            // Close any existing detector
            objectDetector?.close()
            
            // Create Google ML Kit detector with stream mode
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification() // Enable classification for labels
                .enableMultipleObjects() // Detect multiple objects
                .build()
            
            objectDetector = ObjectDetection.getClient(options)
            
            Log.d("GoogleMLKitDetector", "Google ML Kit detector initialized successfully")
        } catch (e: Exception) {
            Log.e("GoogleMLKitDetector", "Failed to initialize Google ML Kit detector: ${e.message}", e)
            throw e
        }
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun detectObjects(imageProxy: ImageProxy, rotation: Int): Task<List<DetectedObjectInfo>> {
        val detector = objectDetector
        
        if (detector == null) {
            Log.e("GoogleMLKitDetector", "Detector not initialized")
            return Tasks.forException(IllegalStateException("Detector not initialized"))
        }
        
        return try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                Tasks.forException(IllegalStateException("Media image is null"))
            } else {
                // Create InputImage from camera frame
                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                
                // Process the image and convert results
                detector.process(inputImage)
                    .continueWith { task ->
                        if (task.isSuccessful) {
                            val results = task.result ?: emptyList()
                            // Convert Google ML Kit DetectedObject to our common format
                            results.map { googleObject ->
                                DetectedObjectInfo(
                                    boundingBox = googleObject.boundingBox,
                                    trackingId = googleObject.trackingId,
                                    labels = googleObject.labels.map { label ->
                                        DetectedObjectInfo.Label(
                                            text = label.text,
                                            confidence = label.confidence,
                                            index = label.index
                                        )
                                    }
                                )
                            }
                        } else {
                            Log.e("GoogleMLKitDetector", "Detection failed: ${task.exception?.message}")
                            emptyList()
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("GoogleMLKitDetector", "Error during detection: ${e.message}", e)
            Tasks.forException(e)
        }
    }
    
    override fun stop() {
        try {
            objectDetector?.close()
            objectDetector = null
            Log.d("GoogleMLKitDetector", "Google ML Kit detector stopped")
        } catch (e: Exception) {
            Log.e("GoogleMLKitDetector", "Error stopping detector: ${e.message}", e)
        }
    }
    
    override fun isReady(): Boolean {
        return objectDetector != null
    }
}

