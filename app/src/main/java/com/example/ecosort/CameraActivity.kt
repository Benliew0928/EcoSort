package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val btnSnap = findViewById<Button>(R.id.btnSnap)
        val btnBackCamera = findViewById<Button>(R.id.btnBackCamera)
        val btnRecycleQuick = findViewById<Button>(R.id.btnRecycleQuick)
        val btnSellQuick = findViewById<Button>(R.id.btnSellQuick)

        // Back button - return to main activity
        btnBackCamera.setOnClickListener {
            finish() // This will go back to the previous activity (MainActivity)
        }

        // Snap button - capture photo to file and save URI
        btnSnap.setOnClickListener {
            ensureCameraPermissionAndCapture()
        }

        // Quick action buttons for last scanned item
        btnRecycleQuick.setOnClickListener {
            Toast.makeText(this, "Plastic Bottle sent to recycle center", Toast.LENGTH_SHORT).show()
        }

        btnSellQuick.setOnClickListener {
            Toast.makeText(this, "Plastic Bottle listed for selling", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureCameraPermissionAndCapture() {
        val perms = arrayOf(Manifest.permission.CAMERA)
        val missing = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            ActivityCompat.requestPermissions(this, perms, 1001)
        } else {
            launchCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFile = File.createTempFile("IMG_${timeStamp}_", ".jpg", cacheDir)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
            photoUri = uri
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, 2001)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            photoUri?.let { uri ->
                // Persist last captured image URI for SellActivity / marketplace usage
                getSharedPreferences("EcoSortImages", MODE_PRIVATE).edit()
                    .putString("last_captured_uri", uri.toString())
                    .apply()
                Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
