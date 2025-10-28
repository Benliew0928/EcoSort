package com.example.ecosort.profile

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.R
import com.example.ecosort.data.model.PrivacySettings
import com.example.ecosort.data.model.ProfileVisibility
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.utils.BottomNavigationHelper
import com.example.ecosort.data.preferences.UserPreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@AndroidEntryPoint
class PrivacySettingsActivity : AppCompatActivity() {
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    private lateinit var spinnerProfileVisibility: Spinner
    private lateinit var switchShowEmail: Switch
    private lateinit var switchShowLocation: Switch
    private lateinit var switchShowStats: Switch
    private lateinit var switchAllowMessages: Switch
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    
    private var currentUserId: Long = 0L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_settings)
        
        setupToolbar()
        setupViews()
        setupClickListeners()
        loadCurrentUser()
        loadPrivacySettings()
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Privacy Settings"
    }
    
    private fun setupViews() {
        spinnerProfileVisibility = findViewById(R.id.spinnerProfileVisibility)
        switchShowEmail = findViewById(R.id.switchShowEmail)
        switchShowLocation = findViewById(R.id.switchShowLocation)
        switchShowStats = findViewById(R.id.switchShowStats)
        switchAllowMessages = findViewById(R.id.switchAllowMessages)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        progressBar = findViewById(R.id.progressBar)
        
        // Setup profile visibility spinner
        val visibilityOptions = arrayOf("Public", "Friends Only", "Private")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, visibilityOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProfileVisibility.adapter = adapter
    }
    
    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            savePrivacySettings()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                val userSession = userPreferencesManager.getCurrentUser()
                currentUserId = userSession?.userId ?: 1L
                android.util.Log.d("PrivacySettingsActivity", "Current user ID: $currentUserId")
            } catch (e: Exception) {
                android.util.Log.e("PrivacySettingsActivity", "Error loading current user", e)
                currentUserId = 1L
            }
        }
    }
    
    private fun loadPrivacySettings() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val userResult = withContext(Dispatchers.IO) {
                    userRepository.getUserById(currentUserId)
                }
                
                if (userResult is com.example.ecosort.data.model.Result.Success) {
                    val user = userResult.data
                    val privacySettingsResult = withContext(Dispatchers.IO) {
                        userRepository.getPrivacySettings(user.id)
                    }
                    
                    withContext(Dispatchers.Main) {
                        val privacySettings = if (privacySettingsResult is com.example.ecosort.data.model.Result.Success) {
                            privacySettingsResult.data
                        } else {
                            PrivacySettings() // Use default settings
                        }
                        populateFields(privacySettings)
                        showLoading(false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        // Use default settings if user not found
                        populateFields(PrivacySettings())
                        showLoading(false)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PrivacySettingsActivity", "Error loading privacy settings", e)
                withContext(Dispatchers.Main) {
                    populateFields(PrivacySettings())
                    showLoading(false)
                    Toast.makeText(this@PrivacySettingsActivity, "Error loading settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun populateFields(privacySettings: PrivacySettings) {
        // Set profile visibility
        val visibilityIndex = when (privacySettings.profileVisibility) {
            ProfileVisibility.PUBLIC -> 0
            ProfileVisibility.FRIENDS_ONLY -> 1
            ProfileVisibility.PRIVATE -> 2
        }
        spinnerProfileVisibility.setSelection(visibilityIndex)
        
        // Set switches
        switchShowEmail.isChecked = privacySettings.showEmail
        switchShowLocation.isChecked = privacySettings.showLocation
        switchShowStats.isChecked = privacySettings.showStats
        switchAllowMessages.isChecked = privacySettings.allowMessages
    }
    
    private fun savePrivacySettings() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // Get current settings
                val userResult = withContext(Dispatchers.IO) {
                    userRepository.getUserById(currentUserId)
                }
                
                if (userResult is com.example.ecosort.data.model.Result.Success) {
                    val user = userResult.data
                    
                    // Create new privacy settings from UI
                    val newPrivacySettings = PrivacySettings(
                        profileVisibility = when (spinnerProfileVisibility.selectedItemPosition) {
                            0 -> ProfileVisibility.PUBLIC
                            1 -> ProfileVisibility.FRIENDS_ONLY
                            2 -> ProfileVisibility.PRIVATE
                            else -> ProfileVisibility.PUBLIC
                        },
                        showEmail = switchShowEmail.isChecked,
                        showLocation = switchShowLocation.isChecked,
                        showStats = switchShowStats.isChecked,
                        allowMessages = switchAllowMessages.isChecked
                    )
                    
                    // Update privacy settings
                    val updateResult = withContext(Dispatchers.IO) {
                        userRepository.updatePrivacySettings(user.id, newPrivacySettings)
                    }
                    
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        
                        if (updateResult is com.example.ecosort.data.model.Result.Success) {
                            Toast.makeText(this@PrivacySettingsActivity, "Privacy settings saved successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@PrivacySettingsActivity, "Failed to save privacy settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(this@PrivacySettingsActivity, "User not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PrivacySettingsActivity", "Error saving privacy settings", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@PrivacySettingsActivity, "Error saving settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        btnSave.isEnabled = !show
        btnCancel.isEnabled = !show
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
