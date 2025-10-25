
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.ai.client.generativeai.GenerativeModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.ai.client.generativeai.type.content
import com.example.ecosort.R.id.btnCapture
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions // Needed for lower threshold
import android.content.Intent
import com.google.mlkit.vision.objects.DetectedObject

// NOTE: You must have a top-level AnalysisResult.kt and OverlayView.kt file in this package.

class ObjectDetectionActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnBackCamera: Button

    private lateinit var geminiModel: GenerativeModel

    private var lastGeminiCallTime = 0L
    private val GEMINI_CALL_INTERVAL_MS = 1500L // Throttle Gemini API calls to 5 seconds

    private var latestDetectedObjects: List<DetectedObject> = emptyList()

    private var latestCapturedBitmap: Bitmap? = null

    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val objectDetector: ObjectDetector by lazy {
        // Use CustomObjectDetectorOptions to explicitly enable and lower thresholds
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification() // Must be enabled to get any label (even generic ones)
            .enableMultipleObjects() // Detect more objects for better Gemini targeting
            .build()
        ObjectDetection.getClient(options)
    }

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


        // Initialize the Gemini Generative Model
        // NOTE: BuildConfig.GEMINI_API_KEY must be configured in your build.gradle
        this.geminiModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_REQUEST_CODE)
        }

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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, MLKitAnalyzer()) }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("Detector", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    inner class MLKitAnalyzer : ImageAnalysis.Analyzer {
        // frameWidth/frameHeight are used for manual scaling calculation
        private var frameWidth: Int = 0
        private var frameHeight: Int = 0



        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {

                frameWidth = imageProxy.width
                frameHeight = imageProxy.height

                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                // 🔑 CRITICAL FIX: Create a stable Bitmap copy for capture
                this@ObjectDetectionActivity.latestCapturedBitmap = imageProxy.toBitmap()

                objectDetector.process(image)
                    .addOnSuccessListener { results ->

                        this@ObjectDetectionActivity.latestDetectedObjects = results

                        // 1. FAST DRAWING PATH: Map all results (defaulting to "Unknown")
                        val analyzedResults = results.map { obj ->
                            val boundingBoxRectF = RectF(obj.boundingBox)

                            // 🔑 FIX: If any box is returned, set the label to a single placeholder.
                            // We avoid checking obj.labels.isNotEmpty() inside the map loop,
                            // as the object 'obj' exists because a box was found.
                            val label = "Object Detected"

                            val areaPercent = calculateAreaPercentage(obj.boundingBox, frameWidth, frameHeight)

                            // Pass the generic label. This is the FAST label that Gemini will overwrite.
                            AnalysisResult(boundingBoxRectF, label, areaPercent.toFloat())
                        }
                        // Update OverlayView with fast (but temporary) box drawing
                        overlayView.updateResults(analyzedResults, frameWidth, frameHeight)

//                        // 2. SMART GEMINI PATH: Process the best object
//                        val firstObject = results.firstOrNull()
//
//                        // 🔑 CRITICAL: Throttling check
//                        if (firstObject != null && System.currentTimeMillis() > lastGeminiCallTime + GEMINI_CALL_INTERVAL_MS) {
//                            lastGeminiCallTime = System.currentTimeMillis()
//
//                            val croppedBitmap = cropImage(imageProxy, firstObject.boundingBox)
//                            val modelLabel = firstObject.labels.firstOrNull()?.text ?: "Object"
//
//                            // Use the bounding box as the unique key to update the right box later
//                            val boxKey = RectF(firstObject.boundingBox).toString()
//
//                            if (croppedBitmap != null) {
//                                Log.d("GeminiImageCheck", "Sending image: ${croppedBitmap.width}x${croppedBitmap.height} pixels")
//                                callGeminiApi(croppedBitmap, modelLabel, boxKey)
//                            }
//                        }
                    }

                    .addOnFailureListener { e -> Log.e("Detector", "Detection failed", e) }
                    .addOnCompleteListener { imageProxy.close() } // Must close ImageProxy

            } else {
                imageProxy.close()
            }
        }
    }

    private fun callGeminiApi(image: Bitmap, modelLabel: String, boxKey: String) {
        lifecycleScope.launch(Dispatchers.IO) {

            kotlinx.coroutines.delay(500)
            val prompt = "You are a waste classification expert for Malaysia. The object has been preliminarily detected as '$modelLabel'. Determine the correct bin: Blue (Paper), Brown (Glass), or Orange (Plastics/Metals). If non-recyclable, state 'General Waste'. Provide ONLY a clean, short instruction for the user in this format: 'Object Type - Bin Color (Material)'."

            try {
                // Construct the multimodal content
                val inputContent = content {
                    image(image)
                    text(prompt)
                }

                val response = geminiModel.generateContent(inputContent)
                val finalClassification = response.text ?: "Gemini Classification Failed"

                withContext(Dispatchers.Main) {
                    // Update the OverlayView using the unique key
                    overlayView.updateFinalClassification(boxKey, finalClassification)
                    Log.d("GeminiResult", "Final Classification for $boxKey: $finalClassification")
                }
            } catch (e: Exception) {
                Log.e("GeminiError", "API call failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    overlayView.updateFinalClassification(boxKey, "Classification Failed")
                }
            }
        }
    }

    private fun calculateAreaPercentage(boundingBox: Rect, frameWidth: Int, frameHeight: Int): Double {
        val boxArea = boundingBox.width().toDouble() * boundingBox.height().toDouble()
        val frameArea = frameWidth.toDouble() * frameHeight.toDouble()
        return if (frameArea > 0) (boxArea / frameArea) * 100 else 0.0
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        objectDetector.close()
    }

    // --------------------------------------------------
    // UTILITY FUNCTIONS (Cropping and Bitmap Conversion)
    // --------------------------------------------------

    // Manual implementation of ImageProxy to Bitmap conversion
    @SuppressLint("UnsafeOptInUsageError")
    private fun ImageProxy.toBitmap(): Bitmap? {
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
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // Function to crop the ImageProxy using the ML Kit bounding box
    private fun cropImage(fullBitmap: Bitmap, boundingBox: Rect): Bitmap? {

        val cropLeft = boundingBox.left.coerceAtLeast(0)
        val cropTop = boundingBox.top.coerceAtLeast(0)
        val cropWidth = boundingBox.width().coerceAtMost(fullBitmap.width - cropLeft)
        val cropHeight = boundingBox.height().coerceAtMost(fullBitmap.height - cropTop)

        return try {
            Bitmap.createBitmap(
                fullBitmap,
                cropLeft,
                cropTop,
                cropWidth,
                cropHeight
            )
        } catch (e: Exception) {
            Log.e("Detector", "Bitmap cropping failed: ${e.message}")
            null
        }
    }

    // NOTE: This function is not used but was requested previously. Removed for final code cleanliness.
    // private fun convertBitmapToByteArray(bitmap: Bitmap): ByteArray { ... }


    companion object {
        private const val CAMERA_REQUEST_CODE = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }



    private fun handleCapture() {
        val fullBitmap = latestCapturedBitmap ?: run {
            Toast.makeText(this, "Camera buffer empty. Wait a moment.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetObject = latestDetectedObjects.firstOrNull() ?: run {
            Toast.makeText(this, "No object detected to analyze. Center the item.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Extract necessary data
        val boundingBox = targetObject.boundingBox
        val modelLabel = targetObject.labels.firstOrNull()?.text ?: "Unclassified Item"

        // 2. Perform the cropping
        val croppedBitmap = cropImage(fullBitmap, boundingBox)

        if (croppedBitmap != null) {
            // 🔑 NEW: Start the AnalysisResultActivity and pass the data
            val intent = Intent(this, AnalysisResultActivity::class.java).apply {
                putExtra(AnalysisResultActivity.EXTRA_BITMAP, croppedBitmap)
                putExtra(AnalysisResultActivity.EXTRA_LABEL, modelLabel)
            }
            startActivity(intent)

            // Optional: Close the camera screen
            // finish()

        } else {
            Toast.makeText(this, "Failed to capture object image.", Toast.LENGTH_SHORT).show()
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


}