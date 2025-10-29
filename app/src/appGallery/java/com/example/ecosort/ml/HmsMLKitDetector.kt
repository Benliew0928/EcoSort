package com.example.ecosort.ml

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.objects.MLObject
import com.huawei.hms.mlsdk.objects.MLObjectAnalyzer
import com.huawei.hms.mlsdk.objects.MLObjectAnalyzerFactory
import com.huawei.hms.mlsdk.objects.MLObjectAnalyzerSetting
import java.util.concurrent.Executors

/**
 * Huawei HMS ML Kit implementation of ObjectDetectorService
 * This is used for the AppGallery build variant
 */
class HmsMLKitDetector : ObjectDetectorService {
    
    private var objectAnalyzer: MLObjectAnalyzer? = null
    private val executor = Executors.newSingleThreadExecutor()
    
    override fun initialize() {
        try {
            // Stop any existing analyzer
            objectAnalyzer?.stop()
            
            // Create HMS ML Kit analyzer with video mode (equivalent to stream mode)
            val settings = MLObjectAnalyzerSetting.Factory()
                .setAnalyzerType(MLObjectAnalyzerSetting.TYPE_VIDEO) // Stream mode equivalent
                .allowMultiResults() // Detect multiple objects
                .allowClassification() // Enable classification
                .create()
            
            objectAnalyzer = MLObjectAnalyzerFactory.getInstance()
                .getLocalObjectAnalyzer(settings)
            
            Log.d("HmsMLKitDetector", "HMS ML Kit detector initialized successfully")
        } catch (e: Exception) {
            Log.e("HmsMLKitDetector", "Failed to initialize HMS ML Kit detector: ${e.message}", e)
            throw e
        }
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun detectObjects(imageProxy: ImageProxy, rotation: Int): Task<List<DetectedObjectInfo>> {
        val analyzer = objectAnalyzer
        
        if (analyzer == null) {
            Log.e("HmsMLKitDetector", "Analyzer not initialized")
            return Tasks.forException(IllegalStateException("Analyzer not initialized"))
        }
        
        return try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                Tasks.forException(IllegalStateException("Media image is null"))
            } else {
                // Create MLFrame from camera frame
                val mlFrame = MLFrame.fromMediaImage(mediaImage, rotation)
                
                // Create a TaskCompletionSource to convert HMS callback to Task
                val taskSource = com.google.android.gms.tasks.TaskCompletionSource<List<DetectedObjectInfo>>()
                
                // Process asynchronously
                executor.execute {
                    try {
                        val task = analyzer.asyncAnalyseFrame(mlFrame)
                        task.addOnSuccessListener { hmsResults ->
                            // Convert HMS MLObject to our common format
                            val convertedResults = hmsResults.map { mlObject ->
                                convertHmsObjectToCommon(mlObject)
                            }
                            taskSource.setResult(convertedResults)
                        }.addOnFailureListener { e ->
                            Log.e("HmsMLKitDetector", "HMS detection failed: ${e.message}", e)
                            taskSource.setResult(emptyList())
                        }
                    } catch (e: Exception) {
                        Log.e("HmsMLKitDetector", "Error during HMS detection: ${e.message}", e)
                        taskSource.setException(e)
                    }
                }
                
                taskSource.task
            }
        } catch (e: Exception) {
            Log.e("HmsMLKitDetector", "Error during detection: ${e.message}", e)
            Tasks.forException(e)
        }
    }
    
    /**
     * Convert HMS MLObject to our common DetectedObjectInfo format
     */
    private fun convertHmsObjectToCommon(mlObject: MLObject): DetectedObjectInfo {
        // HMS MLObject provides border (Rect) and type possibilities
        val boundingBox = mlObject.border ?: Rect()
        val trackingId = mlObject.tracingIdentity
        
        // Convert HMS type possibilities to labels
        val labels = mlObject.typePossibility?.map { entry ->
            DetectedObjectInfo.Label(
                text = getCategoryName(entry.key),
                confidence = entry.value ?: 0f,
                index = entry.key
            )
        } ?: emptyList()
        
        return DetectedObjectInfo(
            boundingBox = boundingBox,
            trackingId = trackingId,
            labels = labels
        )
    }
    
    /**
     * Convert HMS category ID to readable name
     */
    private fun getCategoryName(category: Int): String {
        return when (category) {
            MLObject.TYPE_FACE -> "Face"
            MLObject.TYPE_FOOD -> "Food"
            MLObject.TYPE_FURNITURE -> "Furniture"
            MLObject.TYPE_PLANT -> "Plant"
            MLObject.TYPE_PLACE -> "Place"
            MLObject.TYPE_OTHER -> "Object"
            else -> "Object Detected"
        }
    }
    
    override fun stop() {
        try {
            objectAnalyzer?.stop()
            objectAnalyzer = null
            Log.d("HmsMLKitDetector", "HMS ML Kit detector stopped")
        } catch (e: Exception) {
            Log.e("HmsMLKitDetector", "Error stopping detector: ${e.message}", e)
        }
    }
    
    override fun isReady(): Boolean {
        return objectAnalyzer != null
    }
}

