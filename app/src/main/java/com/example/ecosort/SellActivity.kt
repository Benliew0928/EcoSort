package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.ItemCondition
import com.example.ecosort.data.model.ItemStatus
import com.example.ecosort.data.model.MarketplaceItem
import com.example.ecosort.data.model.WasteCategory
import com.example.ecosort.data.firebase.FirebaseStorageService
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseMarketplaceItem
import com.example.ecosort.data.preferences.UserPreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.widget.ImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SellActivity : AppCompatActivity() {
    private var photoUri: Uri? = null
    
    @Inject
    lateinit var firebaseStorageService: FirebaseStorageService
    
    @Inject
    lateinit var firestoreService: FirestoreService
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sell)

        val name = findViewById<EditText>(R.id.txtItemName)
        val price = findViewById<EditText>(R.id.txtItemPrice)
        val desc = findViewById<EditText>(R.id.txtItemDesc)
        val submit = findViewById<Button>(R.id.btnSubmitSell)
        val btnBackSell = findViewById<Button>(R.id.btnBackSell)
        val photoContainer = findViewById<android.widget.LinearLayout>(R.id.photoContainer)
        val imgPreview = findViewById<ImageView>(R.id.imgPreview)

        // Back button
        btnBackSell.setOnClickListener {
            finish() // Return to previous activity
        }

        photoContainer.setOnClickListener {
            ensureCameraPermissionAndCapture()
        }

        submit.setOnClickListener {
            // Validate form inputs
            if (name.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (price.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Please enter item price", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val titleStr = name.text.toString().trim()
            val priceVal = price.text.toString().toDoubleOrNull() ?: 0.0
            val descStr = desc.text.toString().trim()

            // Show loading state
            submit.isEnabled = false
            submit.text = "Posting..."
            
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        // Get current user info
                        val currentUser = userPreferencesManager.getCurrentUser()
                        val ownerId = currentUser?.userId?.toString() ?: "anonymous"
                        val ownerName = currentUser?.username ?: "Anonymous"
                        
                        android.util.Log.d("SellActivity", "Current user: $currentUser")
                        
                        // Upload image to Firebase Storage if available
                        var imageUrl = ""
                        photoUri?.let { uri ->
                            android.util.Log.d("SellActivity", "Uploading image...")
                            val uploadResult = firebaseStorageService.uploadImage(uri, "marketplace_images")
                            if (uploadResult.isSuccess) {
                                imageUrl = uploadResult.getOrNull() ?: ""
                                android.util.Log.d("SellActivity", "Image uploaded successfully: $imageUrl")
                            } else {
                                android.util.Log.e("SellActivity", "Image upload failed", uploadResult.exceptionOrNull())
                                // Use local URI as fallback
                                imageUrl = uri.toString()
                                android.util.Log.d("SellActivity", "Using local image URI as fallback: $imageUrl")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@SellActivity, "Using local image (Firebase Storage not available)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        
                        // Create Firebase marketplace item
                        val firebaseItem = FirebaseMarketplaceItem(
                            title = titleStr,
                            description = descStr,
                            price = priceVal,
                            imageUrl = imageUrl,
                            ownerId = ownerId,
                            ownerName = ownerName,
                            category = WasteCategory.OTHER.name,
                            condition = ItemCondition.GOOD.name
                        )
                        
                        android.util.Log.d("SellActivity", "Saving to Firestore...")
                        // Save to Firestore
                        val addResult = firestoreService.addMarketplaceItem(firebaseItem)
                        if (addResult.isSuccess) {
                            android.util.Log.d("SellActivity", "Saved to Firestore successfully")
                            // Also save to local database for offline access
                            val db = EcoSortDatabase.getDatabase(applicationContext)
                            val localItem = MarketplaceItem(
                                sellerId = currentUser?.userId ?: 0L,
                                sellerName = ownerName,
                                title = titleStr,
                                description = descStr,
                                price = priceVal,
                                category = WasteCategory.OTHER,
                                condition = ItemCondition.GOOD,
                                imageUrls = if (imageUrl.isNotEmpty()) listOf(imageUrl) else emptyList(),
                                status = ItemStatus.AVAILABLE
                            )
                            db.marketplaceItemDao().insertItem(localItem)
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SellActivity,
                                    "Item '$titleStr' submitted to marketplace!",
                                    Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@SellActivity, com.example.ecosort.marketplace.MarketplaceActivity::class.java))
                                finish()
                            }
                        } else {
                            android.util.Log.e("SellActivity", "Failed to save to Firestore", addResult.exceptionOrNull())
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SellActivity, "Failed to save item: ${addResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SellActivity", "Error posting item", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SellActivity, "Error posting item: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        submit.isEnabled = true
                        submit.text = "Submit"
                    }
                }
            }
        }
    }

    private fun ensureCameraPermissionAndCapture() {
        val perms = arrayOf(Manifest.permission.CAMERA)
        val missing = perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing) {
            ActivityCompat.requestPermissions(this, perms, 2101)
        } else {
            launchCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            launchCamera()
        } else if (requestCode == 2101) {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFile = File.createTempFile("SELL_${timeStamp}_", ".jpg", cacheDir)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
            photoUri = uri
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, 2201)
        } catch (_: Exception) {
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2201 && resultCode == RESULT_OK) {
            photoUri?.let { uri ->
                val imgPreview = findViewById<ImageView>(R.id.imgPreview)
                imgPreview.setImageURI(uri)
            }
        }
    }

    // Handle back button press
    override fun onBackPressed() {
        super.onBackPressed()
        finish() // Return to previous activity
    }
}
