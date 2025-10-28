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
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.utils.FirebaseUidHelper
import com.google.firebase.auth.FirebaseAuth
import com.example.ecosort.ui.login.LoginActivity
import com.example.ecosort.utils.ProfileImageManager
import com.example.ecosort.utils.BottomNavigationHelper
import com.example.ecosort.recycled.RecycledItemActivity
import com.example.ecosort.points.PointsActivity
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
    lateinit var adminRepository: com.example.ecosort.data.repository.AdminRepository
    
    @Inject
    lateinit var profileImageManager: ProfileImageManager
    
    @Inject
    lateinit var firestoreService: FirestoreService
    
    @Inject
    lateinit var firebaseAuthService: com.example.ecosort.data.firebase.FirebaseAuthService

    private var currentUserId: Long = 0L
    private var currentImageUrl: String? = null
    private var currentUserType: UserType = UserType.USER
    private var isRecreating = false
    
    // UI Views
    private lateinit var tvStatsRecycled: TextView
    private lateinit var tvStatsPoints: TextView


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
        
        setContentView(R.layout.activity_user_profile)
        
        // Apply saved language after setting content view to prevent recreation loops
        applySavedLanguage()
        
        // Setup back button handler
        setupBackPressedHandler()

        val tvUsername = findViewById<TextView>(R.id.tvUsername)
        val tvUserType = findViewById<TextView>(R.id.tvUserType)
        tvStatsRecycled = findViewById<TextView>(R.id.tvStatsRecycled)
        tvStatsPoints = findViewById<TextView>(R.id.tvStatsPoints)
        val btnEditProfile = findViewById<Button>(R.id.btnEditProfile)
        val btnPrivacySettings = findViewById<Button>(R.id.btnPrivacySettings)
        val btnPreferences = findViewById<Button>(R.id.btnPreferences)
        val btnFriendRequests = findViewById<Button>(R.id.btnFriendRequests)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val profileImageContainer = findViewById<LinearLayout>(R.id.profileImageContainer)
        // Profile image components will be initialized in loadProfileImage method

        // Populate user info from DataStore session
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
                if (session == null || !session.isLoggedIn) {
                    android.util.Log.w("UserProfileActivity", "No valid session found, redirecting to login")
                    startActivity(Intent(this@UserProfileActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }
                
                android.util.Log.d("UserProfileActivity", "Loading profile for user: ${session.username}, ID: ${session.userId}")
                
                currentUserId = session.userId
                currentUserType = session.userType
                tvUsername.text = session.username.ifBlank { "User" }
                tvUserType.text = if (session.userType == UserType.ADMIN) "Administrator" else "Regular User"
                
                // Load user profile data
                loadUserProfile()
            } catch (e: Exception) {
                android.util.Log.e("UserProfileActivity", "Error loading user session", e)
                Toast.makeText(this@UserProfileActivity, "Error loading profile. Please try again.", Toast.LENGTH_SHORT).show()
                // Don't finish the activity, just show error
            }
        }
        
        // Load real stats data
        loadUserStats()

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
                    android.util.Log.d("UserProfileActivity", "Starting logout process")
                    
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
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    
                    android.util.Log.d("UserProfileActivity", "Navigating to LoginActivity")
                    startActivity(intent)
                    
                    // Add a small delay to ensure the intent is processed
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 100)
                    
                } catch (e: Exception) {
                    android.util.Log.e("UserProfileActivity", "Error during logout", e)
                    Toast.makeText(this@UserProfileActivity, "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // Even if logout fails, try to navigate to login
                    try {
                        val intent = Intent(this@UserProfileActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } catch (navException: Exception) {
                        android.util.Log.e("UserProfileActivity", "Error navigating to login after logout failure", navException)
                    }
                }
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
        
        // Add click listeners for stats cards
        val cardRecycledItems = findViewById<LinearLayout>(R.id.cardRecycledItems)
        val cardPoints = findViewById<LinearLayout>(R.id.cardPoints)
        
        cardRecycledItems.setOnClickListener {
            startActivity(Intent(this, RecycledItemActivity::class.java))
        }
        
        cardPoints.setOnClickListener {
            startActivity(Intent(this, PointsActivity::class.java))
        }
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
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
        // Only refresh user profile if we're not in the middle of a language change
        // This prevents unnecessary reloads and potential loops
        if (!isFinishing && !isDestroyed && !isRecreating) {
            loadUserProfile()
            loadUserStats()
        }
    }
    
    private fun applySavedLanguage() {
        // Skip language application if already recreating or activity is finishing
        if (isRecreating || isFinishing || isDestroyed) {
            android.util.Log.d("UserProfileActivity", "Skipping language application - activity state: recreating=$isRecreating, finishing=$isFinishing, destroyed=$isDestroyed")
            return
        }
        
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
                
                android.util.Log.d("UserProfileActivity", "Current language: $currentLanguage, Saved language: ${preferences.language}")
                
                // Only apply if different and not already recreating
                if (preferences.language != currentLanguage && !isFinishing && !isDestroyed && !isRecreating) {
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
                    @Suppress("DEPRECATION")
                    resources.updateConfiguration(configuration, resources.displayMetrics)
                    
                    // Apply to application context for global effect
                    @Suppress("DEPRECATION")
                    applicationContext.resources.updateConfiguration(configuration, applicationContext.resources.displayMetrics)
                    
                    android.util.Log.d("UserProfileActivity", "Applied saved language: ${preferences.language}")
                    
                    // Set flag to prevent multiple recreations
                    isRecreating = true
                    
                    // Recreate the activity to apply language changes only once
                    recreate()
                } else {
                    android.util.Log.d("UserProfileActivity", "Language already correct or activity in invalid state: ${preferences.language}")
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
                android.util.Log.d("UserProfileActivity", "Loading profile for user $currentUserId, type: $currentUserType")
                
                // First sync current user data from Firebase to ensure latest data
                try {
                    val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
                    if (session != null && session.isLoggedIn) {
                        val syncResult = withContext(Dispatchers.IO) { 
                            userRepository.forceRefreshUserData(session.username) 
                        }
                        if (syncResult is com.example.ecosort.data.model.Result.Success) {
                            android.util.Log.d("UserProfileActivity", "Successfully synced user data from Firebase")
                        } else {
                            android.util.Log.w("UserProfileActivity", "Failed to sync user data from Firebase: ${(syncResult as com.example.ecosort.data.model.Result.Error).exception.message}")
                        }
                        
                        // Clean up any duplicate users in Firebase first
                        try {
                            val firebaseCleanupResult = withContext(Dispatchers.IO) { 
                                userRepository.cleanupFirebaseDuplicates() 
                            }
                            if (firebaseCleanupResult is com.example.ecosort.data.model.Result.Success && firebaseCleanupResult.data > 0) {
                                android.util.Log.d("UserProfileActivity", "Cleaned up ${firebaseCleanupResult.data} duplicate users in Firebase")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("UserProfileActivity", "Error cleaning up Firebase duplicates: ${e.message}")
                        }
                        
                        // Clean up any duplicate users in local database
                        try {
                            val cleanupResult = withContext(Dispatchers.IO) { 
                                userRepository.cleanupDuplicateUsers() 
                            }
                            if (cleanupResult is com.example.ecosort.data.model.Result.Success && cleanupResult.data > 0) {
                                android.util.Log.d("UserProfileActivity", "Cleaned up ${cleanupResult.data} duplicate users in local database")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("UserProfileActivity", "Error cleaning up local duplicate users: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("UserProfileActivity", "Error syncing user data from Firebase: ${e.message}")
                    // Continue loading even if sync fails
                }
                
                if (currentUserType == UserType.ADMIN) {
                    // Load admin profile
                    val adminResult = adminRepository.getAdminById(currentUserId)
                    if (adminResult is com.example.ecosort.data.model.Result.Success && adminResult.data != null) {
                        val admin = adminResult.data
                        currentImageUrl = admin.profileImageUrl
                        loadProfileImage(admin.profileImageUrl)
                        
                        android.util.Log.d("UserProfileActivity", "Loaded admin: bio='${admin.bio}', location='${admin.location}', itemsRecycled=${admin.itemsRecycled}, totalPoints=${admin.totalPoints}")
                        
                        // Stats are loaded from Firebase in loadUserStats()
                    } else {
                        android.util.Log.w("UserProfileActivity", "Failed to load admin profile: ${adminResult}")
                        // Set default image and stats for admin
                        loadProfileImage(null)
                        tvStatsRecycled.text = "0"
                        tvStatsPoints.text = "0 points"
                    }
                } else {
                    // Load regular user profile
                    val userResult = userRepository.getCurrentUser()
                    if (userResult is com.example.ecosort.data.model.Result.Success) {
                        val user = userResult.data
                        currentImageUrl = user.profileImageUrl
                        
                        android.util.Log.d("UserProfileActivity", "Loaded user: bio='${user.bio}', location='${user.location}'")
                        
                        // Load profile image
                        loadProfileImage(user.profileImageUrl)
                        
                        // Stats are loaded from Firebase in loadUserStats()
                    } else {
                        android.util.Log.e("UserProfileActivity", "Failed to load user: ${userResult}")
                        // Show error message to user
                        Toast.makeText(this@UserProfileActivity, "Failed to load user profile. Please try logging in again.", Toast.LENGTH_LONG).show()
                        
                        // Set default values
                        tvStatsRecycled.text = "0"
                        tvStatsPoints.text = "0 points"
                        loadProfileImage(null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileActivity", "Error loading profile", e)
                Toast.makeText(this@UserProfileActivity, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProfileImage(imageUrl: String?) {
        val ivProfileImage = findViewById<ImageView>(R.id.ivProfileImage)
        val tvProfilePlaceholder = findViewById<TextView>(R.id.tvProfilePlaceholder)
        
        try {
            android.util.Log.d("UserProfileActivity", "loadProfileImage called with URL: $imageUrl")
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
        android.util.Log.d("UserProfileActivity", "Starting profile image upload for ${currentUserType.name}: $currentUserId")
        lifecycleScope.launch {
            try {
                val result = if (currentUserType == UserType.ADMIN) {
                    profileImageManager.uploadAdminProfileImage(
                        this@UserProfileActivity,
                        currentUserId,
                        imageUri,
                        currentImageUrl
                    )
                } else {
                    profileImageManager.uploadProfileImage(
                        this@UserProfileActivity,
                        currentUserId,
                        imageUri,
                        currentImageUrl
                    )
                }
                
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
                val result = if (currentUserType == UserType.ADMIN) {
                    profileImageManager.deleteAdminProfileImage(currentUserId, currentImageUrl ?: "")
                } else {
                    profileImageManager.deleteProfileImage(currentUserId, currentImageUrl ?: "")
                }
                
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

    private fun loadUserStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (currentUserType == UserType.ADMIN) {
                    // For admin users, try to get Firebase UID from current Firebase session
                    val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (firebaseUser != null) {
                        val firebaseUid = firebaseUser.uid
                        android.util.Log.d("UserProfileActivity", "Admin Firebase UID: $firebaseUid")
                        
                        // Load recycled items count
                        val recycledItemsResult = firestoreService.getUserRecycledItems(firebaseUid)
                        val recycledCount = if (recycledItemsResult is com.example.ecosort.data.model.Result.Success) {
                            recycledItemsResult.data.size
                        } else {
                            0
                        }
                        
                        // Load points
                        val pointsResult = firestoreService.getUserPoints(firebaseUid)
                        val totalPoints = if (pointsResult is com.example.ecosort.data.model.Result.Success && pointsResult.data != null) {
                            (pointsResult.data["totalPoints"] as? Long)?.toInt() ?: 0
                        } else {
                            0
                        }
                        
                        withContext(Dispatchers.Main) {
                            tvStatsRecycled.text = recycledCount.toString()
                            tvStatsPoints.text = "$totalPoints points"
                        }
                    } else {
                        // Fallback to local admin data if no Firebase session
                        android.util.Log.w("UserProfileActivity", "No Firebase session for admin, using local data")
                        withContext(Dispatchers.Main) {
                            // Keep the existing local data that was set in loadProfileData()
                        }
                    }
                } else {
                    // For regular users, use the existing logic
                    val userResult = userRepository.getCurrentUser()
                    if (userResult is com.example.ecosort.data.model.Result.Error) {
                        withContext(Dispatchers.Main) {
                            tvStatsRecycled.text = "0"
                            tvStatsPoints.text = "0 points"
                        }
                        return@launch
                    }
                    
                    val user = (userResult as com.example.ecosort.data.model.Result.Success).data
                    val firebaseUid = FirebaseUidHelper.getFirebaseUid(user)
                    
                    // Load recycled items count
                    val recycledItemsResult = firestoreService.getUserRecycledItems(firebaseUid)
                    val recycledCount = if (recycledItemsResult is com.example.ecosort.data.model.Result.Success) {
                        recycledItemsResult.data.size
                    } else {
                        0
                    }
                    
                    // Load points
                    val pointsResult = firestoreService.getUserPoints(firebaseUid)
                    val totalPoints = if (pointsResult is com.example.ecosort.data.model.Result.Success && pointsResult.data != null) {
                        (pointsResult.data["totalPoints"] as? Long)?.toInt() ?: 0
                    } else {
                        0
                    }
                    
                    withContext(Dispatchers.Main) {
                        tvStatsRecycled.text = recycledCount.toString()
                        tvStatsPoints.text = "$totalPoints points"
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("UserProfileActivity", "Error loading user stats", e)
                withContext(Dispatchers.Main) {
                    tvStatsRecycled.text = "0"
                    tvStatsPoints.text = "0 points"
                }
            }
        }
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

