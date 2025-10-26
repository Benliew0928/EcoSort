package com.example.ecosort

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.ui.login.LoginActivity
import com.example.ecosort.utils.ProfileImageManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class UserProfileActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var profileImageManager: ProfileImageManager
    
    @Inject
    lateinit var firebaseAuthService: com.example.ecosort.data.firebase.FirebaseAuthService

    private var currentUserId: Long = 0L
    private var currentImageUrl: String? = null


    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadProfileImage(it) }
    }

    // Camera launcher
    private var currentPhotoUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        android.util.Log.d("UserProfileActivity", "Camera result: success=$success, uri=$currentPhotoUri")
        if (success && currentPhotoUri != null) {
            // Image was captured successfully, upload it
            uploadProfileImage(currentPhotoUri!!)
        } else {
            android.util.Log.e("UserProfileActivity", "Camera capture failed or no URI available")
            Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved language before setting content view
        applySavedLanguage()
        
        setContentView(R.layout.activity_user_profile)
        
        // Setup back button handler
        setupBackPressedHandler()

        val tvUsername = findViewById<TextView>(R.id.tvUsername)
        val tvUserType = findViewById<TextView>(R.id.tvUserType)
        val tvStatsRecycled = findViewById<TextView>(R.id.tvStatsRecycled)
        val tvStatsPoints = findViewById<TextView>(R.id.tvStatsPoints)
        val btnEditProfile = findViewById<Button>(R.id.btnEditProfile)
        val btnPrivacySettings = findViewById<Button>(R.id.btnPrivacySettings)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnPreferences = findViewById<Button>(R.id.btnPreferences)
        val btnFriendRequests = findViewById<Button>(R.id.btnFriendRequests)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val profileImageContainer = findViewById<LinearLayout>(R.id.profileImageContainer)
        val ivProfileImage = findViewById<ImageView>(R.id.ivProfileImage)
        val tvProfilePlaceholder = findViewById<TextView>(R.id.tvProfilePlaceholder)

        // Populate user info from DataStore session
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
            if (session == null || !session.isLoggedIn) {
                startActivity(Intent(this@UserProfileActivity, LoginActivity::class.java))
                finish()
                return@launch
            }
            
            currentUserId = session.userId
            tvUsername.text = session.username.ifBlank { "User" }
            tvUserType.text = if (session.userType == UserType.ADMIN) "Administrator" else "Regular User"
            
            // Load user profile data
            loadUserProfile()
        }
        
        // Display stats (you can make these dynamic later)
        tvStatsRecycled.text = "24"
        tvStatsPoints.text = "1,250 points"

        // Profile image click listener
        profileImageContainer.setOnClickListener {
            showImagePickerDialog()
        }

        // Button click listeners
        btnEditProfile.setOnClickListener {
            val intent = Intent(this, com.example.ecosort.profile.EditProfileActivity::class.java)
            startActivityForResult(intent, 100)
        }

        btnPrivacySettings.setOnClickListener {
            val intent = Intent(this, com.example.ecosort.profile.PrivacySettingsActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        btnPreferences.setOnClickListener {
            val intent = Intent(this, com.example.ecosort.preferences.UserPreferencesActivity::class.java)
            startActivity(intent)
        }

        btnFriendRequests.setOnClickListener {
            val intent = Intent(this, com.example.ecosort.friends.FriendRequestsActivity::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { 
                        // Clear local session
                        userPreferencesManager.clearUserSession()
                        
                        // Sign out from Firebase
                        firebaseAuthService.signOut()
                        
                        android.util.Log.d("UserProfileActivity", "User session cleared and Firebase sign out completed")
                    }
                    Toast.makeText(this@UserProfileActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    
                    // Clear activity stack and navigate to login
                    val intent = Intent(this@UserProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    android.util.Log.e("UserProfileActivity", "Error during logout", e)
                    Toast.makeText(this@UserProfileActivity, "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    // Handle back button press using modern OnBackPressedDispatcher
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Refresh user profile when returning to UserProfileActivity
        // This ensures the data is up-to-date if it was changed elsewhere
        loadUserProfile()
    }
    
    private fun applySavedLanguage() {
        lifecycleScope.launch {
            try {
                val preferences = withContext(Dispatchers.IO) {
                    userPreferencesManager.getUserPreferences()
                }
                
                // Check current language
                val currentLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    resources.configuration.locale
                }
                
                val currentLanguage = when (currentLocale.language) {
                    "zh" -> "zh"
                    "ms" -> "ms"
                    else -> "en"
                }
                
                // Only apply if different
                if (preferences.language != currentLanguage) {
                    val locale = when (preferences.language) {
                        "zh" -> java.util.Locale("zh", "CN")
                        "ms" -> java.util.Locale("ms", "MY")
                        else -> java.util.Locale("en", "US")
                    }
                    
                    // Set locale globally
                    java.util.Locale.setDefault(locale)
                    
                    val configuration = android.content.res.Configuration(resources.configuration)
                    configuration.setLocale(locale)
                    
                    // Apply to current context
                    resources.updateConfiguration(configuration, resources.displayMetrics)
                    
                    // Apply to application context for global effect
                    applicationContext.resources.updateConfiguration(configuration, applicationContext.resources.displayMetrics)
                    
                    android.util.Log.d("UserProfileActivity", "Applied saved language: ${preferences.language}")
                    
                    // Recreate the activity to apply language changes
                    if (!isFinishing && !isDestroyed) {
                        recreate()
                    }
                } else {
                    android.util.Log.d("UserProfileActivity", "Language already correct: ${preferences.language}")
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileActivity", "Error applying saved language", e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Profile was updated, reload the profile data
            loadUserProfile()
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val userResult = userRepository.getCurrentUser()
                if (userResult is com.example.ecosort.data.model.Result.Success) {
                    val user = userResult.data
                    currentImageUrl = user.profileImageUrl
                    
                    // Load profile image
                    loadProfileImage(user.profileImageUrl)
                    
                    // Update stats with real data
                    findViewById<TextView>(R.id.tvStatsRecycled).text = user.itemsRecycled.toString()
                    findViewById<TextView>(R.id.tvStatsPoints).text = "${user.totalPoints} points"
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileActivity", "Error loading user profile", e)
            }
        }
    }

    private fun loadProfileImage(imageUrl: String?) {
        val ivProfileImage = findViewById<ImageView>(R.id.ivProfileImage)
        val tvProfilePlaceholder = findViewById<TextView>(R.id.tvProfilePlaceholder)
        
        try {
            if (!imageUrl.isNullOrBlank()) {
                android.util.Log.d("UserProfileActivity", "Loading profile image: $imageUrl")
                // Add cache-busting to prevent image sharing between users
                val cacheBustedUrl = profileImageManager.addCacheBustingToUrl(imageUrl)
                android.util.Log.d("UserProfileActivity", "Cache-busted URL: $cacheBustedUrl")
                // Load image with Glide
                Glide.with(this)
                    .load(cacheBustedUrl as String)
                    .placeholder(R.drawable.ic_person_24)
                    .error(R.drawable.ic_person_24)
                    .circleCrop()
                    .skipMemoryCache(false)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .into(ivProfileImage)
                
                // Hide placeholder
                tvProfilePlaceholder.visibility = android.view.View.GONE
                ivProfileImage.visibility = android.view.View.VISIBLE
            } else {
                android.util.Log.d("UserProfileActivity", "No profile image URL, showing default image")
                // Set default image instead of showing placeholder
                Glide.with(this)
                    .load(R.drawable.ic_person_24)
                    .circleCrop()
                    .into(ivProfileImage)
                
                tvProfilePlaceholder.visibility = android.view.View.GONE
                ivProfileImage.visibility = android.view.View.VISIBLE
            }
        } catch (e: Exception) {
            android.util.Log.e("UserProfileActivity", "Error loading profile image", e)
            // Fallback to default image
            try {
                ivProfileImage.setImageResource(R.drawable.ic_person_24)
                tvProfilePlaceholder.visibility = android.view.View.GONE
                ivProfileImage.visibility = android.view.View.VISIBLE
            } catch (fallbackException: Exception) {
                android.util.Log.e("UserProfileActivity", "Error setting fallback image", fallbackException)
                tvProfilePlaceholder.visibility = android.view.View.VISIBLE
                ivProfileImage.visibility = android.view.View.GONE
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> chooseFromGallery()
                    2 -> removePhoto()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun takePhoto() {
        if (checkCameraPermission()) {
            // Create temporary file for camera capture
            val photoFile = java.io.File.createTempFile(
                "profile_${System.currentTimeMillis()}",
                ".jpg",
                cacheDir
            )
            val photoUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            currentPhotoUri = photoUri
            cameraLauncher.launch(photoUri)
        } else {
            requestCameraPermission()
        }
    }

    private fun chooseFromGallery() {
        if (checkStoragePermission()) {
            imagePickerLauncher.launch("image/*")
        } else {
            requestStoragePermission()
        }
    }

    private fun removePhoto() {
        if (currentImageUrl != null) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove Profile Picture")
                .setMessage("Are you sure you want to remove your profile picture?")
                .setPositiveButton("Remove") { _, _ ->
                    deleteProfileImage()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun uploadProfileImage(imageUri: Uri) {
        android.util.Log.d("UserProfileActivity", "Starting profile image upload for user: $currentUserId")
        lifecycleScope.launch {
            try {
                val result = profileImageManager.uploadProfileImage(
                    this@UserProfileActivity,
                    currentUserId,
                    imageUri,
                    currentImageUrl
                )
                
                android.util.Log.d("UserProfileActivity", "Upload result: ${result.isSuccess}")
                
                if (result.isSuccess) {
                    val newImageUrl = result.getOrNull()
                    android.util.Log.d("UserProfileActivity", "New image URL: $newImageUrl")
                    currentImageUrl = newImageUrl
                    loadProfileImage(newImageUrl)
                    Toast.makeText(this@UserProfileActivity, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()
                    android.util.Log.e("UserProfileActivity", "Upload failed: ${error?.message}", error)
                    Toast.makeText(this@UserProfileActivity, "Failed to upload profile picture: ${error?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileActivity", "Error uploading profile image", e)
                Toast.makeText(this@UserProfileActivity, "Error uploading profile picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteProfileImage() {
        lifecycleScope.launch {
            try {
                val result = profileImageManager.deleteProfileImage(currentUserId, currentImageUrl ?: "")
                
                if (result.isSuccess) {
                    currentImageUrl = null
                    loadProfileImage(null)
                    Toast.makeText(this@UserProfileActivity, "Profile picture removed successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@UserProfileActivity, "Failed to remove profile picture: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileActivity", "Error deleting profile image", e)
                Toast.makeText(this@UserProfileActivity, "Error removing profile picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
    }

    private fun requestStoragePermission() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissions, 101)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
                }
            }
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseFromGallery()
                } else {
                    Toast.makeText(this, "Storage permission is required to select photos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

