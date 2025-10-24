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
import kotlin.math.roundToInt

class ObjectDetectionActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnBackCamera: Button


    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val objectDetector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
        ObjectDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_detection)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById<OverlayView>(R.id.overlayView)
        btnBackCamera = findViewById(R.id.btnBackCamera)

        btnBackCamera.setOnClickListener { finish() }




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
        private var frameWidth: Int = 0
        private var frameHeight: Int = 0

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                frameWidth = mediaImage.width
                frameHeight = mediaImage.height
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                objectDetector.process(image)
                    .addOnSuccessListener { results ->

                        val analyzedResults = results.map { obj ->
                            val boundingBoxRect = obj.boundingBox // This is the Rect from ML Kit

                            // 🔑 FIX: Explicitly convert Rect to RectF for the data class
                            val boundingBoxRectF = RectF(boundingBoxRect)

                            val label = obj.labels.firstOrNull()?.text ?: "Unknown"
                            val areaPercent = calculateAreaPercentage(boundingBoxRect, frameWidth, frameHeight)

                            // Pass the RectF instance
                            AnalysisResult(boundingBoxRectF, label, areaPercent.toFloat())
                        }
                        overlayView.updateResults(
                            analyzedResults,
                            frameWidth,    // Pass the raw image width
                            frameHeight    // Pass the raw image height
                            // NOTE: Remove rotationDegrees from this call if your OverlayView no longer accepts it
                        )
                    }
                    .addOnFailureListener { e -> Log.e("Detector", "Detection failed", e) }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun calculateAreaPercentage(boundingBox: Rect, frameWidth: Int, frameHeight: Int): Double {
        val bboxArea = boundingBox.width().toDouble() * boundingBox.height().toDouble()
        val frameArea = frameWidth.toDouble() * frameHeight.toDouble()
        return if (frameArea > 0) (bboxArea / frameArea) * 100 else 0.0
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        objectDetector.close()
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
