package com.example.ecosort.hms

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.ecosort.R
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.data.firebase.FirebaseStorageService
import com.example.ecosort.data.firebase.FirestoreService
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.example.ecosort.data.preferences.UserPreferencesManager


@AndroidEntryPoint
class AddBinActivity : AppCompatActivity() {

    private val TAG = "AddBinActivity"
    private val CAMERA_PERMISSION_CODE = 2101
    private val CAMERA_REQUEST_CODE = 2201

    // Declare class properties (Initialized in onCreate)
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var etBinName: EditText // <-- Can be accessed directly
    private lateinit var tvLocation: TextView

    private var binLatitude: Double = 0.0
    private var binLongitude: Double = 0.0
    private var photoUri: Uri? = null

    @Inject
    lateinit var firebaseStorageService: FirebaseStorageService

    @Inject
    lateinit var firestoreService: FirestoreService

    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_bin)

        // Initialize ALL class properties using findViewById
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview)
        etBinName = findViewById(R.id.etBinName)
        tvLocation = findViewById(R.id.tvLocation)

        // Initialize button variables locally for listener setup
        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)
        val btnSubmitBin = findViewById<Button>(R.id.btnSubmitBin)

        // 1. Get location passed from MapActivity
        binLatitude = intent.getDoubleExtra("EXTRA_LATITUDE", 0.0)
        binLongitude = intent.getDoubleExtra("EXTRA_LONGITUDE", 0.0)

        // Display the received location
        tvLocation.text =
            if (binLatitude != 0.0 && binLongitude != 0.0) {
                "Location: Lat ${"%.4f".format(binLatitude)}, Lng ${"%.4f".format(binLongitude)}"
            } else {
                "Location unavailable."
            }

        // 2. Button Listeners
        btnTakePhoto.setOnClickListener {
            ensureCameraPermissionAndLaunch()
        }

        btnSubmitBin.setOnClickListener {
            // FIX: Pass ONLY the submit button. etBinName is accessed via class property.
            submitBinData(btnSubmitBin)
        }
    }

// -------------------------------------------------------------------------------------------------
//                                      SUBMISSION LOGIC
// -------------------------------------------------------------------------------------------------

    // FIX: Remove binNameInput parameter, access etBinName directly.
    private fun submitBinData(submitButton: Button) {
        val nameStr = etBinName.text.toString().trim() // Accessing etBinName property

        // Validation
        if (nameStr.isEmpty()) {
            Toast.makeText(this, "Please enter a name for the bin.", Toast.LENGTH_SHORT).show()
            return
        }
        if (photoUri == null) {
            Toast.makeText(this, "Please take a photo of the bin.", Toast.LENGTH_SHORT).show()
            return
        }
        if (binLatitude == 0.0 && binLongitude == 0.0) {
            Toast.makeText(this, "Location coordinates not set.", Toast.LENGTH_SHORT).show()
            return
        }

        submitButton.isEnabled = false
        submitButton.text = "Uploading..."

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {

                    // Fetch User ID
                    val currentUser = userPreferencesManager.getCurrentUser()
                    val submitterId = currentUser?.userId?.toString() ?: "anonymous"
                    val submitterName = currentUser?.username ?: "Anonymous User"
                    Log.d(TAG, "Bin submitted by ID: $submitterId")

                    // Image Upload Logic
                    var imageUrl = ""
                    photoUri?.let { uri ->
                        val uploadResult = firebaseStorageService.uploadImage(uri, "recycle_bin_images")
                        if (uploadResult.isSuccess) { imageUrl = uploadResult.getOrNull() ?: "" }
                    }

                    // Create the data structure for Firestore
                    val newBin: HashMap<String, Any> = hashMapOf(
                        "name" to nameStr,
                        "latitude" to binLatitude,
                        "longitude" to binLongitude,
                        "photoUrl" to imageUrl,
                        "isVerified" to false,
                        "timestamp" to Timestamp.now(),
                        "submitterId" to submitterId,
                        "submitterName" to submitterName
                    ) as HashMap<String, Any>

                    // Save to Firestore
                    val addResult = firestoreService.saveDocument("recycleBins", newBin)

                    if (addResult.isSuccess) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AddBinActivity, "Bin submitted for verification!", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    } else {
                        Log.e(TAG, "Failed to save to Firestore", addResult.exceptionOrNull())
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AddBinActivity, "Failed to save bin: ${addResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical error during submission", e)
            } finally {
                withContext(Dispatchers.Main) {
                    submitButton.isEnabled = true
                    submitButton.text = "SUBMIT FOR VERIFICATION"
                }
            }
        }
    }

// -------------------------------------------------------------------------------------------------
//                                      CAMERA LOGIC
// -------------------------------------------------------------------------------------------------

    private fun ensureCameraPermissionAndLaunch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            launchCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else if (requestCode == CAMERA_PERMISSION_CODE) {
            Toast.makeText(this, "Camera permission is required to take a photo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchCamera() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFile = File.createTempFile("BIN_${timeStamp}_", ".jpg", cacheDir)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
            photoUri = uri

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, CAMERA_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
            Toast.makeText(this, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // ivPhotoPreview is a class property and can be accessed directly here
            photoUri?.let { uri ->
                ivPhotoPreview.setImageURI(uri)
                Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}