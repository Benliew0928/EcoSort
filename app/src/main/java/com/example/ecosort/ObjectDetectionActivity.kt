package com.example.ecosort

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.ai.client.generativeai.GenerativeModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.ai.client.generativeai.type.content
import com.example.ecosort.utils.BottomNavigationHelper
import com.example.ecosort.R.id.btnCapture
import android.content.Intent
import java.io.File
import com.example.ecosort.ml.DetectedObjectInfo
import com.example.ecosort.ml.ObjectDetectorService
import com.example.ecosort.ml.DetectorFactory

// NOTE: You must have a top-level AnalysisResult.kt and OverlayView.kt file in this package.

class ObjectDetectionActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnBackCamera: Button

    private lateinit var geminiModel: GenerativeModel

    private var lastGeminiCallTime = 0L
    private val GEMINI_CALL_INTERVAL_MS = 1500L // Throttle Gemini API calls to 5 seconds

    private var latestDetectedObjects: List<DetectedObjectInfo> = emptyList()

    private var latestCapturedBitmap: Bitmap? = null
    
    // Frame dimensions for proper coordinate calculations
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0
    private var lastProcessedRotation: Int = 0

    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    // Use abstraction interface instead of specific implementation
    private var objectDetectorService: ObjectDetectorService? = null
    
    private fun initializeObjectDetector() {
        try {
            // Stop previous instance if exists
            objectDetectorService?.stop()
            
            // Create appropriate detector using factory based on build flavor
            objectDetectorService = DetectorFactory.createDetector(BuildConfig.STORE_TYPE)
            
            // Initialize the detector
            objectDetectorService?.initialize()
            
            Log.d("Detector", "Object detector initialized successfully for ${BuildConfig.STORE_TYPE}")
        } catch (e: Exception) {
            Log.e("Detector", "Failed to initialize object detector: ${e.message}", e)
            objectDetectorService = null
        }
    }
    
    // Track detection state to ensure continuous sensitivity
    private var lastDetectionTime = 0L
    private var detectionCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_detection)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)
        btnBackCamera = findViewById(R.id.btnBackCamera)

        btnBackCamera.setOnClickListener { finish() }

        val btnCapture = findViewById<Button>(R.id.btnCapture)
        btnCapture.setOnClickListener {
            handleCapture()
        }

        // Add click listener to viewFinder for manual detection reset
        viewFinder.setOnClickListener {
            resetDetectionState()
        }


        // Initialize the Gemini Generative Model with error handling
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isNullOrEmpty() || apiKey == "your_api_key_here") {
                Log.w("Gemini", "Gemini API key not configured")
                Toast.makeText(this, "AI analysis unavailable. Basic detection only.", Toast.LENGTH_LONG).show()
            } else {
                this.geminiModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = apiKey
                )
                Log.d("Gemini", "Gemini model initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("Gemini", "Failed to initialize Gemini model: ${e.message}", e)
            Toast.makeText(this, "AI analysis unavailable. Basic detection only.", Toast.LENGTH_LONG).show()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_REQUEST_CODE)
        }
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (allPermissionsGranted()) startCamera()
            else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        try {
            // Initialize object detector and reset detection state
            initializeObjectDetector()
            resetDetectionState()
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, MLKitAnalyzer()) }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                } catch (exc: Exception) {
                    Log.e("Detector", "Camera binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e("Camera", "Failed to start camera: ${e.message}", e)
        }
    }

    inner class MLKitAnalyzer : ImageAnalysis.Analyzer {



        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Update class-level frame dimensions with rotation awareness
                val rotation = imageProxy.imageInfo.rotationDegrees
                this@ObjectDetectionActivity.lastProcessedRotation = rotation
                
                if (rotation == 90 || rotation == 270) {
                    // Swap dimensions for rotated images
                    this@ObjectDetectionActivity.frameWidth = imageProxy.height
                    this@ObjectDetectionActivity.frameHeight = imageProxy.width
                } else {
                    this@ObjectDetectionActivity.frameWidth = imageProxy.width
                    this@ObjectDetectionActivity.frameHeight = imageProxy.height
                }

                // 🔑 CRITICAL FIX: Create a stable Bitmap copy for capture with size management
                val capturedBitmap = imageProxy.toBitmap()
                if (capturedBitmap != null) {
                    // Check if bitmap is too large and compress if needed
                    val maxDimension = 2048
                    if (capturedBitmap.width > maxDimension || capturedBitmap.height > maxDimension) {
                        Log.w("BitmapSize", "Captured bitmap too large: ${capturedBitmap.width}x${capturedBitmap.height}")
                        val compressedBitmap = this@ObjectDetectionActivity.compressImage(capturedBitmap)
                        capturedBitmap.recycle() // Free the large bitmap
                        this@ObjectDetectionActivity.latestCapturedBitmap = compressedBitmap
                    } else {
                        this@ObjectDetectionActivity.latestCapturedBitmap = capturedBitmap
                    }
                }

                // Use abstraction layer for detection (works with both Google and HMS)
                objectDetectorService?.detectObjects(imageProxy, rotation)
                    ?.addOnSuccessListener { results ->
                        val currentTime = System.currentTimeMillis()
                        this@ObjectDetectionActivity.detectionCount++
                        
                        if (results.isNotEmpty()) {
                            this@ObjectDetectionActivity.lastDetectionTime = currentTime
                        } else {
                            // Auto-reset if no objects detected for 5 seconds and we've processed enough frames
                            val timeSinceLastDetection = currentTime - this@ObjectDetectionActivity.lastDetectionTime
                            if (timeSinceLastDetection > 5000 && this@ObjectDetectionActivity.detectionCount > 50) {
                                Log.d("AutoReset", "Auto-resetting detection state after ${timeSinceLastDetection}ms with no objects")
                                this@ObjectDetectionActivity.resetDetectionState()
                            }
                        }

                        this@ObjectDetectionActivity.latestDetectedObjects = results

                        // 1. ENHANCED DETECTION: Map all results with better filtering
                        val analyzedResults = results
                            .filter { obj -> 
                                // Filter out very small objects (less than 0.5% of frame for better sensitivity)
                                val areaPercent = calculateAreaPercentage(obj.boundingBox, this@ObjectDetectionActivity.frameWidth, this@ObjectDetectionActivity.frameHeight)
                                areaPercent > 0.5
                            }
                            .map { obj ->
                                val boundingBoxRectF = RectF(obj.boundingBox)
                                val label = "Object Detected"
                                val areaPercent = calculateAreaPercentage(obj.boundingBox, this@ObjectDetectionActivity.frameWidth, this@ObjectDetectionActivity.frameHeight)
                                
                                Log.d("Detection", "Object detected: ${obj.boundingBox}, Area: ${areaPercent.toInt()}%")
                                
                                AnalysisResult(boundingBoxRectF, label, areaPercent.toFloat())
                            }
                        // Update OverlayView with fast (but temporary) box drawing
                        overlayView.updateResults(analyzedResults, this@ObjectDetectionActivity.frameWidth, this@ObjectDetectionActivity.frameHeight)
                    }
                    ?.addOnFailureListener { e -> Log.e("Detector", "Detection failed", e) }
                    ?.addOnCompleteListener { imageProxy.close() } // Must close ImageProxy

            } else {
                imageProxy.close()
            }
        }
    }

    private fun callGeminiApi(image: Bitmap, modelLabel: String, boxKey: String) {
        lifecycleScope.launch(Dispatchers.IO) {

            kotlinx.coroutines.delay(500)
            val prompt = """
                You are an expert waste classification specialist for Malaysia's recycling system. Analyze this image of an object preliminarily detected as '$modelLabel'.

                Provide a quick classification in this EXACT JSON format:
                {
                    "itemName": "Specific item name",
                    "binColor": "Blue|Brown|Orange|Black|Red",
                    "binType": "Paper|Glass|Plastics/Metals|General Waste|Hazardous",
                    "isRecyclable": true|false,
                    "confidence": 0.85
                }

                Rules:
                1. Be specific about the item (e.g., "Plastic Water Bottle" not just "Plastic")
                2. Use Malaysia's bin system: Blue (Paper), Brown (Glass), Orange (Plastics/Metals), Black (General Waste), Red (Hazardous)
                3. Provide realistic confidence scores (0.0-1.0)
                4. Return ONLY the JSON, no additional text
            """.trimIndent()

            try {
                // Construct the multimodal content
                val inputContent = content {
                    image(image)
                    text(prompt)
                }

                val response = geminiModel.generateContent(inputContent)
                val responseText = response.text ?: "Gemini Classification Failed"

                withContext(Dispatchers.Main) {
                    val displayText = parseQuickResult(responseText, modelLabel)
                    overlayView.updateFinalClassification(boxKey, displayText)
                    Log.d("GeminiResult", "Final Classification for $boxKey: $displayText")
                }
            } catch (e: Exception) {
                Log.e("GeminiError", "API call failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    overlayView.updateFinalClassification(boxKey, "Classification Failed")
                }
            }
        }
    }

    private fun parseQuickResult(responseText: String, fallbackLabel: String): String {
        return try {
            // Clean the response text (remove any markdown formatting)
            val cleanResponse = responseText.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            // Parse JSON response
            val jsonObject = org.json.JSONObject(cleanResponse)
            
            val itemName = jsonObject.optString("itemName", fallbackLabel)
            val binColor = jsonObject.optString("binColor", "Black")
            val binType = jsonObject.optString("binType", "General Waste")
            val confidence = jsonObject.optDouble("confidence", 0.0)

            "$itemName → $binColor ($binType) ${(confidence * 100).toInt()}%"

        } catch (e: Exception) {
            Log.e("JSONParseError", "Failed to parse quick result: ${e.message}")
            "$fallbackLabel → Check Analysis"
        }
    }

    private fun calculateAreaPercentage(boundingBox: Rect, frameWidth: Int, frameHeight: Int): Double {
        val boxArea = boundingBox.width().toDouble() * boundingBox.height().toDouble()
        val frameArea = frameWidth.toDouble() * frameHeight.toDouble()
        return if (frameArea > 0) (boxArea / frameArea) * 100 else 0.0
    }

    override fun onResume() {
        super.onResume()
        initializeObjectDetector()
        resetDetectionState()
    }

    override fun onPause() {
        super.onPause()
        objectDetectorService?.stop()
        objectDetectorService = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        objectDetectorService?.stop()
    }

    // --------------------------------------------------
    // UTILITY FUNCTIONS (Cropping and Bitmap Conversion)
    // --------------------------------------------------

    // Enhanced ImageProxy to Bitmap conversion with proper orientation handling
    @SuppressLint("UnsafeOptInUsageError")
    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()
            
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            // Handle rotation based on image info - with null safety
            val rotation = try {
                imageInfo.rotationDegrees
            } catch (e: Exception) {
                Log.w("RotationInfo", "Could not get rotation info: ${e.message}")
                0
            }
            
            if (rotation != 0 && bitmap != null) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                bitmap.recycle() // Free original bitmap
                rotatedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("BitmapConversion", "Failed to convert ImageProxy to Bitmap: ${e.message}")
            null
        }
    }

    // Enhanced cropping function with proper coordinate transformation and error handling
    private fun cropImage(fullBitmap: Bitmap, boundingBox: Rect): Bitmap? {
        return try {
            // Safety check for bitmap
            if (fullBitmap.isRecycled) {
                Log.e("CropError", "Bitmap is recycled")
                return null
            }
            
            // Check if bitmap is too large (prevent memory issues)
            val maxDimension = 2048
            if (fullBitmap.width > maxDimension || fullBitmap.height > maxDimension) {
                Log.w("CropError", "Bitmap too large: ${fullBitmap.width}x${fullBitmap.height}, max allowed: ${maxDimension}x${maxDimension}")
                // Compress the bitmap first
                val compressedBitmap = compressImage(fullBitmap)
                return cropImage(compressedBitmap, boundingBox)
            }
            
            // CRITICAL: Transform coordinates from camera frame to bitmap coordinates
            val transformedBox = transformBoundingBox(boundingBox, fullBitmap.width, fullBitmap.height)
            
            // Add padding around the detected object (20% on each side)
            val padding = 0.2f
            val paddingX = (transformedBox.width() * padding).toInt()
            val paddingY = (transformedBox.height() * padding).toInt()
            
            val cropLeft = (transformedBox.left - paddingX).coerceAtLeast(0)
            val cropTop = (transformedBox.top - paddingY).coerceAtLeast(0)
            val cropRight = (transformedBox.right + paddingX).coerceAtMost(fullBitmap.width)
            val cropBottom = (transformedBox.bottom + paddingY).coerceAtMost(fullBitmap.height)
            
            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop
            
            // Multiple validation checks
            if (cropWidth <= 0 || cropHeight <= 0) {
                Log.e("CropError", "Invalid crop dimensions: ${cropWidth}x${cropHeight}")
                return null
            }
            
            // Additional safety check for bitmap bounds
            if (cropLeft >= fullBitmap.width || cropTop >= fullBitmap.height || 
                cropRight <= 0 || cropBottom <= 0) {
                Log.e("CropError", "Crop area outside bitmap bounds")
                return null
            }
            
            Log.d("CropInfo", "Cropping: ${cropWidth}x${cropHeight} from ${fullBitmap.width}x${fullBitmap.height}")
            Log.d("CropInfo", "Original box: ${boundingBox}, Transformed box: ${transformedBox}, Padded box: ($cropLeft, $cropTop, $cropRight, $cropBottom)")
            
            Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
        } catch (e: Exception) {
            Log.e("Detector", "Bitmap cropping failed: ${e.message}", e)
            null
        }
    }

    // Transform bounding box coordinates from camera frame to bitmap coordinates
    private fun transformBoundingBox(boundingBox: Rect, bitmapWidth: Int, bitmapHeight: Int): Rect {
        // Safety check for zero dimensions
        if (frameWidth <= 0 || frameHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            Log.w("Transform", "Invalid dimensions - Frame: ${frameWidth}x${frameHeight}, Bitmap: ${bitmapWidth}x${bitmapHeight}")
            return boundingBox
        }
        
        // If frame dimensions match bitmap dimensions, no transformation needed
        if (frameWidth == bitmapWidth && frameHeight == bitmapHeight) {
            return boundingBox
        }
        
        // Get the current rotation from the last processed frame
        val rotation = lastProcessedRotation
        
        Log.d("Transform", "Frame: ${frameWidth}x${frameHeight}, Bitmap: ${bitmapWidth}x${bitmapHeight}, Rotation: ${rotation}°")
        
        // Handle rotation-based coordinate transformation
        val transformedBox = when (rotation) {
            90, 270 -> {
                // For 90° and 270° rotations, we need to account for the coordinate system change
                // The frame dimensions are already swapped in the analyzer, but the bitmap is in sensor orientation
                val scaleX = bitmapWidth.toFloat() / frameHeight.toFloat()  // Note: frameHeight is actually width for rotated
                val scaleY = bitmapHeight.toFloat() / frameWidth.toFloat()  // Note: frameWidth is actually height for rotated
                
                Log.d("Transform", "Rotated scale factors: X=$scaleX, Y=$scaleY")
                
                // For 90° rotation: (x,y) -> (y, bitmapHeight - x)
                // For 270° rotation: (x,y) -> (bitmapWidth - y, x)
                when (rotation) {
                    90 -> Rect(
                        (boundingBox.top * scaleY).toInt().coerceIn(0, bitmapWidth),
                        (bitmapHeight - boundingBox.right * scaleX).toInt().coerceIn(0, bitmapHeight),
                        (boundingBox.bottom * scaleY).toInt().coerceIn(0, bitmapWidth),
                        (bitmapHeight - boundingBox.left * scaleX).toInt().coerceIn(0, bitmapHeight)
                    )
                    270 -> Rect(
                        (bitmapWidth - boundingBox.bottom * scaleY).toInt().coerceIn(0, bitmapWidth),
                        (boundingBox.left * scaleX).toInt().coerceIn(0, bitmapHeight),
                        (bitmapWidth - boundingBox.top * scaleY).toInt().coerceIn(0, bitmapWidth),
                        (boundingBox.right * scaleX).toInt().coerceIn(0, bitmapHeight)
                    )
                    else -> boundingBox
                }
            }
            else -> {
                // For 0° and 180° rotations, use simple scaling
                val scaleX = bitmapWidth.toFloat() / frameWidth.toFloat()
                val scaleY = bitmapHeight.toFloat() / frameHeight.toFloat()
                
                Log.d("Transform", "Normal scale factors: X=$scaleX, Y=$scaleY")
                
                Rect(
                    (boundingBox.left * scaleX).toInt().coerceIn(0, bitmapWidth),
                    (boundingBox.top * scaleY).toInt().coerceIn(0, bitmapHeight),
                    (boundingBox.right * scaleX).toInt().coerceIn(0, bitmapWidth),
                    (boundingBox.bottom * scaleY).toInt().coerceIn(0, bitmapHeight)
                )
            }
        }
        
        Log.d("Transform", "Original: $boundingBox -> Transformed: $transformedBox")
        return transformedBox
    }

    // Compress image to prevent crashes from large images
    private fun compressImage(bitmap: Bitmap): Bitmap {
        return try {
            // Define maximum dimensions to prevent memory issues
            val maxWidth = 1024
            val maxHeight = 1024
            
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height
            
            Log.d("ImageCompression", "Original image: ${originalWidth}x${originalHeight}")
            
            // Calculate scaling factor to fit within max dimensions
            val scaleWidth = maxWidth.toFloat() / originalWidth.toFloat()
            val scaleHeight = maxHeight.toFloat() / originalHeight.toFloat()
            val scale = minOf(scaleWidth, scaleHeight, 1.0f) // Don't upscale
            
            val newWidth = (originalWidth * scale).toInt()
            val newHeight = (originalHeight * scale).toInt()
            
            Log.d("ImageCompression", "Compressed image: ${newWidth}x${newHeight}, Scale: $scale")
            
            // If no scaling needed, return original
            if (scale >= 1.0f) {
                return bitmap
            }
            
            // Create compressed bitmap
            val compressedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            
            // Recycle original if it's different from compressed
            if (compressedBitmap != bitmap) {
                bitmap.recycle()
            }
            
            compressedBitmap
        } catch (e: Exception) {
            Log.e("ImageCompression", "Failed to compress image: ${e.message}", e)
            // Return original bitmap if compression fails
            bitmap
        }
    }

    // Reset detection state for continuous sensitivity
    private fun resetDetectionState() {
        try {
            lastDetectionTime = System.currentTimeMillis()
            detectionCount = 0
            latestDetectedObjects = emptyList()
            
            // Clear overlay if frame dimensions are valid
            if (frameWidth > 0 && frameHeight > 0) {
                overlayView.updateResults(emptyList(), frameWidth, frameHeight)
            }
            
            Log.d("Reset", "Detection state reset - ready for new objects")
            Toast.makeText(this, "Detection refreshed. Ready to scan!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Reset", "Failed to reset detection state: ${e.message}", e)
        }
    }


    companion object {
        private const val CAMERA_REQUEST_CODE = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }



    private fun handleCapture() {
        val fullBitmap = latestCapturedBitmap ?: run {
            Toast.makeText(this, "Camera buffer empty. Wait a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Safety check for frame dimensions
        if (frameWidth == 0 || frameHeight == 0) {
            Toast.makeText(this, "Camera not ready. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        // Safety check for object detector
        if (objectDetectorService == null || !objectDetectorService!!.isReady()) {
            Toast.makeText(this, "Object detector not ready. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        // Filter detected objects to find the best one
        val validObjects = latestDetectedObjects.filter { obj ->
            // Use the original frame dimensions for area calculation
            val areaPercent = calculateAreaPercentage(obj.boundingBox, frameWidth, frameHeight)
            areaPercent > 1.0 // At least 1% of the frame for better sensitivity
        }

        val targetObject = validObjects.maxByOrNull { obj ->
            // Prefer larger objects that are more centered
            val areaPercent = calculateAreaPercentage(obj.boundingBox, frameWidth, frameHeight)
            val centerX = obj.boundingBox.centerX()
            val centerY = obj.boundingBox.centerY()
            val frameCenterX = frameWidth / 2
            val frameCenterY = frameHeight / 2
            val distanceFromCenter = kotlin.math.sqrt(
                ((centerX - frameCenterX) * (centerX - frameCenterX) + 
                 (centerY - frameCenterY) * (centerY - frameCenterY)).toDouble()
            )
            
            // Higher score for larger objects closer to center
            areaPercent - (distanceFromCenter / 100.0)
        } ?: run {
            val timeSinceLastDetection = System.currentTimeMillis() - lastDetectionTime
            val message = if (timeSinceLastDetection > 5000) {
                "No objects detected. Tap the camera view to refresh detection, then try again."
            } else {
                "No suitable object detected. Try centering the item better or tap camera to refresh."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        // 1. Extract necessary data
        val boundingBox = targetObject.boundingBox
        val modelLabel = targetObject.labels.firstOrNull()?.text ?: "Unclassified Item"
        val areaPercent = calculateAreaPercentage(boundingBox, frameWidth, frameHeight)

        Log.d("Capture", "Selected object: $modelLabel, Area: ${areaPercent.toInt()}%, Box: $boundingBox")
        Log.d("Capture", "Frame dimensions: ${frameWidth}x${frameHeight}")
        Log.d("Capture", "Full bitmap dimensions: ${fullBitmap.width}x${fullBitmap.height}")

        // 2. Perform the cropping
        val croppedBitmap = try {
            cropImage(fullBitmap, boundingBox)
        } catch (e: Exception) {
            Log.e("CropError", "Failed to crop image: ${e.message}")
            null
        }

        if (croppedBitmap != null) {
            Log.d("Capture", "Successfully cropped image: ${croppedBitmap.width}x${croppedBitmap.height}")

            val compressedBitmap = compressImage(croppedBitmap)

            // ⭐ 1. SAVE THE BITMAP TO A TEMPORARY FILE ⭐
            val filePath = saveBitmapToTempFile(this, compressedBitmap)

            if (filePath != null) {
                // 2. Pass ONLY THE FILE PATH (String) in the Intent
                val intent = Intent(this, AnalysisResultActivity::class.java).apply {
                    // Use the new constant EXTRA_BITMAP_PATH
                    putExtra(AnalysisResultActivity.EXTRA_BITMAP_PATH, filePath)
                    putExtra(AnalysisResultActivity.EXTRA_LABEL, modelLabel)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to save image locally. Cannot analyze.", Toast.LENGTH_LONG).show()
            }

            // Clean up resources
            compressedBitmap.recycle()
            croppedBitmap.recycle()

        } else {
            // Fallback: Use compressed full bitmap if cropping fails
            Log.w("Capture", "Cropping failed, using compressed full bitmap as fallback")
            val compressedBitmap = compressImage(fullBitmap)

            // ⭐ FALLBACK: SAVE FULL COMPRESSED BITMAP TO FILE ⭐
            val filePath = saveBitmapToTempFile(this, compressedBitmap)

            if (filePath != null) {
                val intent = Intent(this, AnalysisResultActivity::class.java).apply {
                    putExtra(AnalysisResultActivity.EXTRA_BITMAP_PATH, filePath)
                    putExtra(AnalysisResultActivity.EXTRA_LABEL, modelLabel)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Fallback failed. Cannot analyze.", Toast.LENGTH_LONG).show()
            }
            compressedBitmap.recycle()
        }
    }

    private fun captureFullView(): Bitmap? {
        // Get the root view of the Activity's content area
        val rootView = window.decorView.findViewById<View>(android.R.id.content)

        // Check if the root view is ready
        if (rootView.width == 0 || rootView.height == 0) return null

        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw the entire view hierarchy onto the canvas
        rootView.draw(canvas)

        return bitmap
    }

    private fun convertBitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        // Compress the bitmap to JPEG format (required for API transfer)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream) // Reduced quality to 80 for safety
        return outputStream.toByteArray()
    }


    private fun saveBitmapToTempFile(context: Context, bitmap: Bitmap): String? {
        // 1. Create a file in the app's cache directory
        val filename = "cropped_item_${System.currentTimeMillis()}.jpg"
        val file = java.io.File(context.cacheDir, filename)

        try {
            // 2. Open an output stream and compress the bitmap
            val outputStream = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()

            // 3. Return the absolute path
            return file.absolutePath
        } catch (e: Exception) {
            Log.e("FileSaveError", "Failed to save bitmap to file: ${e.message}")
            return null
        }
    }



}