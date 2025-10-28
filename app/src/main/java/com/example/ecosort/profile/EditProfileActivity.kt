package com.example.ecosort.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.*
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.utils.ProfileImageManager
import com.example.ecosort.utils.BottomNavigationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class EditProfileActivity : AppCompatActivity() {

    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var adminRepository: com.example.ecosort.data.repository.AdminRepository
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    @Inject
    lateinit var profileImageManager: ProfileImageManager

    private var currentUserId: Long = 0L
    private var currentUser: User? = null
    private var currentAdmin: com.example.ecosort.data.model.Admin? = null
    private var currentImageUrl: String? = null
    private var currentUserType: UserType = UserType.USER
    private var isDataLoaded = false

    // UI Components
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvProfilePlaceholder: TextView
    private lateinit var etBio: EditText
    private lateinit var etLocation: EditText
    private lateinit var etWebsite: EditText
    private lateinit var etInstagram: EditText
    private lateinit var etTwitter: EditText
    private lateinit var etLinkedin: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProfileCompletion: TextView
    private lateinit var progressBarCompletion: ProgressBar

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadProfileImage(it) }
    }

    // Camera launcher
    private var cameraImageUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            // Image was captured successfully, now upload it
            uploadProfileImage(cameraImageUri!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        initializeViews()
        setupClickListeners()
        loadUserData()
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
    }

    override fun onResume() {
        super.onResume()
        // Only load user data if it hasn't been loaded yet and activity is not finishing
        // This prevents unnecessary reloads and data loss during image upload
        if (!isFinishing && !isDestroyed && !isDataLoaded) {
            loadUserData()
        }
    }

    private fun initializeViews() {
        ivProfileImage = findViewById(R.id.ivProfileImage)
        tvProfilePlaceholder = findViewById(R.id.tvProfilePlaceholder)
        etBio = findViewById(R.id.etBio)
        etLocation = findViewById(R.id.etLocation)
        etWebsite = findViewById(R.id.etWebsite)
        etInstagram = findViewById(R.id.etInstagram)
        etTwitter = findViewById(R.id.etTwitter)
        etLinkedin = findViewById(R.id.etLinkedin)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        progressBar = findViewById(R.id.progressBar)
        tvProfileCompletion = findViewById(R.id.tvProfileCompletion)
        progressBarCompletion = findViewById(R.id.progressBarCompletion)

        // Setup text watchers for real-time validation
        setupTextWatchers()
    }

    private fun setupClickListeners() {
        // Profile image click
        findViewById<View>(R.id.profileImageContainer).setOnClickListener {
            showImagePickerDialog()
        }

        // Save button
        btnSave.setOnClickListener {
            saveProfile()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }

        // Back button
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateProfileCompletion()
            }
        }

        etBio.addTextChangedListener(textWatcher)
        etLocation.addTextChangedListener(textWatcher)
        etWebsite.addTextChangedListener(textWatcher)
        etInstagram.addTextChangedListener(textWatcher)
        etTwitter.addTextChangedListener(textWatcher)
        etLinkedin.addTextChangedListener(textWatcher)
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
                if (session == null || !session.isLoggedIn) {
                    finish()
                    return@launch
                }
                
                currentUserId = session.userId
                currentUserType = session.userType
                
                // First sync current user data from Firebase to ensure latest data
                try {
                    val syncResult = withContext(Dispatchers.IO) { 
                        userRepository.forceRefreshUserData(session.username) 
                    }
                    if (syncResult is com.example.ecosort.data.model.Result.Success) {
                        android.util.Log.d("EditProfileActivity", "Successfully synced user data from Firebase")
                    } else {
                        android.util.Log.w("EditProfileActivity", "Failed to sync user data from Firebase: ${(syncResult as com.example.ecosort.data.model.Result.Error).exception.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("EditProfileActivity", "Error syncing user data from Firebase: ${e.message}")
                    // Continue loading even if sync fails
                }
                
                if (currentUserType == UserType.ADMIN) {
                    // Load admin profile
                    val adminResult = adminRepository.getAdminById(currentUserId)
                    if (adminResult is com.example.ecosort.data.model.Result.Success && adminResult.data != null) {
                        currentAdmin = adminResult.data
                        currentImageUrl = currentAdmin?.profileImageUrl
                        
                        android.util.Log.d("EditProfileActivity", "Loaded admin after sync - bio: '${currentAdmin?.bio}', location: '${currentAdmin?.location}'")
                        
                        populateAdminFields()
                        updateProfileCompletion()
                        isDataLoaded = true
                    } else {
                        Toast.makeText(this@EditProfileActivity, "Failed to load admin data", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    // Load regular user profile
                    val userResult = userRepository.getCurrentUser()
                    if (userResult is com.example.ecosort.data.model.Result.Success) {
                        currentUser = userResult.data
                        currentImageUrl = currentUser?.profileImageUrl
                        
                        android.util.Log.d("EditProfileActivity", "Loaded user after sync - bio: '${currentUser?.bio}', location: '${currentUser?.location}'")
                        
                        populateFields()
                        updateProfileCompletion()
                        isDataLoaded = true
                    } else {
                        Toast.makeText(this@EditProfileActivity, "Failed to load user data", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EditProfileActivity", "Error loading user data", e)
                Toast.makeText(this@EditProfileActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun populateFields() {
        currentUser?.let { user ->
            // Load profile image
            loadProfileImage(currentImageUrl)
            
            // Populate text fields
            etBio.setText(user.bio ?: "")
            etLocation.setText(user.location ?: "")
            
            // Load social links in a coroutine
            lifecycleScope.launch {
                try {
                    val socialLinksResult = withContext(Dispatchers.IO) { userRepository.getSocialLinks(user.id) }
                    if (socialLinksResult is com.example.ecosort.data.model.Result.Success) {
                        val socialLinks = socialLinksResult.data
                        etWebsite.setText(socialLinks.website ?: "")
                        etInstagram.setText(socialLinks.instagram ?: "")
                        etTwitter.setText(socialLinks.twitter ?: "")
                        etLinkedin.setText(socialLinks.linkedin ?: "")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EditProfileActivity", "Error loading social links", e)
                }
            }
        }
    }
    
    private fun populateAdminFields() {
        // Load profile image
        loadProfileImage(currentImageUrl)
        
        // Populate admin fields with actual data
        etBio.setText(currentAdmin?.bio ?: "")
        etLocation.setText(currentAdmin?.location ?: "")
        
        // Admin users don't have social links in the current model
        etWebsite.setText("")
        etInstagram.setText("")
        etTwitter.setText("")
        etLinkedin.setText("")
    }

    private fun loadProfileImage(imageUrl: String?) {
        try {
            android.util.Log.d("EditProfileActivity", "loadProfileImage called with URL: $imageUrl")
            android.util.Log.d("EditProfileActivity", "ImageView initialized: ${::ivProfileImage.isInitialized}")
            if (!imageUrl.isNullOrBlank()) {
                android.util.Log.d("EditProfileActivity", "Loading profile image: $imageUrl")
                // Add cache-busting to prevent image sharing between users
                val cacheBustedUrl = profileImageManager.addCacheBustingToUrl(imageUrl)
                android.util.Log.d("EditProfileActivity", "Cache-busted URL: $cacheBustedUrl")
                Glide.with(this)
                    .load(cacheBustedUrl as String)
                    .placeholder(R.drawable.ic_person_24)
                    .error(R.drawable.ic_person_24)
                    .circleCrop()
                    .skipMemoryCache(false)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .into(ivProfileImage)
                
                tvProfilePlaceholder.visibility = View.GONE
                ivProfileImage.visibility = View.VISIBLE
            } else {
                android.util.Log.d("EditProfileActivity", "No profile image URL, showing placeholder")
                // Set default image
                Glide.with(this)
                    .load(R.drawable.ic_person_24)
                    .circleCrop()
                    .into(ivProfileImage)
                
                tvProfilePlaceholder.visibility = View.GONE
                ivProfileImage.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            android.util.Log.e("EditProfileActivity", "Error loading profile image", e)
            // Fallback to default image
            try {
                ivProfileImage.setImageResource(R.drawable.ic_person_24)
                tvProfilePlaceholder.visibility = View.GONE
                ivProfileImage.visibility = View.VISIBLE
            } catch (fallbackException: Exception) {
                android.util.Log.e("EditProfileActivity", "Error setting fallback image", fallbackException)
                tvProfilePlaceholder.visibility = View.VISIBLE
                ivProfileImage.visibility = View.GONE
            }
        }
    }

    private fun updateProfileCompletion() {
        lifecycleScope.launch {
            try {
                val completion = calculateCurrentCompletion()
                tvProfileCompletion.text = "Profile Completion: $completion%"
                progressBarCompletion.progress = completion
            } catch (e: Exception) {
                android.util.Log.e("EditProfileActivity", "Error calculating profile completion", e)
            }
        }
    }

    private fun calculateCurrentCompletion(): Int {
        var completion = 0
        
        // Username and email are always present (20% each)
        completion += 40
        
        // Bio (20%)
        if (etBio.text.toString().trim().isNotEmpty()) {
            completion += 20
        }
        
        // Location (20%)
        if (etLocation.text.toString().trim().isNotEmpty()) {
            completion += 20
        }
        
        // Profile image (20%)
        if (currentImageUrl != null) {
            completion += 20
        }
        
        return completion
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
            cameraImageUri = photoUri
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
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val result = if (currentUserType == UserType.ADMIN) {
                    profileImageManager.uploadAdminProfileImage(
                        this@EditProfileActivity,
                        currentUserId,
                        imageUri,
                        currentImageUrl
                    )
                } else {
                    profileImageManager.uploadProfileImage(
                        this@EditProfileActivity,
                        currentUserId,
                        imageUri,
                        currentImageUrl
                    )
                }
                
                if (result.isSuccess) {
                    val newImageUrl = result.getOrNull()
                    android.util.Log.d("EditProfileActivity", "Profile image upload successful, new URL: $newImageUrl")
                    currentImageUrl = newImageUrl
                    
                    // Update the current user/admin object with the new image URL
                    if (currentUserType == UserType.ADMIN) {
                        currentAdmin = currentAdmin?.copy(profileImageUrl = newImageUrl)
                    } else {
                        currentUser = currentUser?.copy(profileImageUrl = newImageUrl)
                    }
                    
                    loadProfileImage(newImageUrl)
                    updateProfileCompletion()
                    Toast.makeText(this@EditProfileActivity, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@EditProfileActivity, "Failed to upload profile picture: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("EditProfileActivity", "Error uploading profile image", e)
                Toast.makeText(this@EditProfileActivity, "Error uploading profile picture", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun deleteProfileImage() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val result = if (currentUserType == UserType.ADMIN) {
                    profileImageManager.deleteAdminProfileImage(currentUserId, currentImageUrl ?: "")
                } else {
                    profileImageManager.deleteProfileImage(currentUserId, currentImageUrl ?: "")
                }
                
                if (result.isSuccess) {
                    currentImageUrl = null
                    
                    // Update the current user/admin object with null image URL
                    if (currentUserType == UserType.ADMIN) {
                        currentAdmin = currentAdmin?.copy(profileImageUrl = null)
                    } else {
                        currentUser = currentUser?.copy(profileImageUrl = null)
                    }
                    
                    loadProfileImage(null)
                    updateProfileCompletion()
                    Toast.makeText(this@EditProfileActivity, "Profile picture removed successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@EditProfileActivity, "Failed to remove profile picture: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("EditProfileActivity", "Error deleting profile image", e)
                Toast.makeText(this@EditProfileActivity, "Error removing profile picture", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun saveProfile() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val bio = etBio.text.toString().trim()
                val location = etLocation.text.toString().trim()
                
                android.util.Log.d("EditProfileActivity", "Saving profile for user $currentUserId")
                android.util.Log.d("EditProfileActivity", "Bio: '$bio', Location: '$location'")
                
                // Update basic profile info based on user type
                val bioResult: com.example.ecosort.data.model.Result<Unit>
                val locationResult: com.example.ecosort.data.model.Result<Unit>
                
                if (currentUserType == UserType.ADMIN) {
                    // For admin users, use admin repository methods
                    bioResult = adminRepository.updateAdminBio(currentUserId, bio.ifEmpty { null })
                    locationResult = adminRepository.updateAdminLocation(currentUserId, location.ifEmpty { null })
                } else {
                    // For regular users, use user repository methods
                    bioResult = userRepository.updateProfileBio(currentUserId, bio.ifEmpty { null })
                    locationResult = userRepository.updateProfileLocation(currentUserId, location.ifEmpty { null })
                }
                
                android.util.Log.d("EditProfileActivity", "Bio update result: ${bioResult::class.simpleName}")
                android.util.Log.d("EditProfileActivity", "Location update result: ${locationResult::class.simpleName}")
                
                if (bioResult is com.example.ecosort.data.model.Result.Error || 
                    locationResult is com.example.ecosort.data.model.Result.Error) {
                    android.util.Log.e("EditProfileActivity", "Failed to update profile info - Bio: ${bioResult}, Location: ${locationResult}")
                    Toast.makeText(this@EditProfileActivity, "Failed to update profile information", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Update social links
                val socialLinks = SocialLinks(
                    website = etWebsite.text.toString().trim().ifEmpty { null },
                    instagram = etInstagram.text.toString().trim().ifEmpty { null },
                    twitter = etTwitter.text.toString().trim().ifEmpty { null },
                    linkedin = etLinkedin.text.toString().trim().ifEmpty { null }
                )
                
                android.util.Log.d("EditProfileActivity", "Social links: $socialLinks")
                
                val socialLinksResult = userRepository.updateSocialLinks(currentUserId, socialLinks)
                android.util.Log.d("EditProfileActivity", "Social links update result: ${socialLinksResult::class.simpleName}")
                
                if (socialLinksResult is com.example.ecosort.data.model.Result.Error) {
                    android.util.Log.e("EditProfileActivity", "Failed to update social links: ${socialLinksResult}")
                    Toast.makeText(this@EditProfileActivity, "Failed to update social links", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Recalculate profile completion
                val completionResult = userRepository.calculateProfileCompletion(currentUserId)
                if (completionResult is com.example.ecosort.data.model.Result.Success) {
                    val completion = completionResult.data
                    android.util.Log.d("EditProfileActivity", "Profile completion: $completion%")
                    Toast.makeText(this@EditProfileActivity, "Profile updated successfully! (${completion}% complete)", Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.w("EditProfileActivity", "Failed to calculate profile completion: ${completionResult}")
                    Toast.makeText(this@EditProfileActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                }
                
                // Set result and finish
                setResult(RESULT_OK)
                finish()
                
            } catch (e: Exception) {
                android.util.Log.e("EditProfileActivity", "Error saving profile", e)
                Toast.makeText(this@EditProfileActivity, "Error saving profile", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
        btnCancel.isEnabled = !show
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
