package com.example.ecosort.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.R
import com.example.ecosort.data.model.*
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.utils.LanguageManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class UserPreferencesActivity : AppCompatActivity() {
    
    @Inject
    lateinit var preferencesManager: UserPreferencesManager
    
    // UI Components
    private lateinit var spinnerTheme: Spinner
    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerFontSize: Spinner
    
    // Notification Switches
    private lateinit var switchPushNotifications: Switch
    private lateinit var switchCommunityUpdates: Switch
    private lateinit var switchFriendRequests: Switch
    private lateinit var switchMessageNotifications: Switch
    
    // Privacy Switches
    private lateinit var switchShowOnlineStatus: Switch
    private lateinit var switchAllowFriendRequests: Switch
    private lateinit var switchShowLastSeen: Switch
    private lateinit var switchAllowProfileViews: Switch
    private lateinit var switchShareRecyclingStats: Switch
    private lateinit var switchAllowDataCollection: Switch
    
    // Buttons
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var btnBack: Button
    
    private var currentPreferences: UserPreferences? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_preferences)
        
        initViews()
        setupSpinners()
        loadPreferences()
        setupListeners()
    }
    
    private fun initViews() {
        // Spinners
        spinnerTheme = findViewById(R.id.spinnerTheme)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        spinnerFontSize = findViewById(R.id.spinnerFontSize)
        
        // Notification Switches
        switchPushNotifications = findViewById(R.id.switchPushNotifications)
        switchCommunityUpdates = findViewById(R.id.switchCommunityUpdates)
        switchFriendRequests = findViewById(R.id.switchFriendRequests)
        switchMessageNotifications = findViewById(R.id.switchMessageNotifications)
        
        // Privacy Switches
        switchShowOnlineStatus = findViewById(R.id.switchShowOnlineStatus)
        switchAllowFriendRequests = findViewById(R.id.switchAllowFriendRequests)
        switchShowLastSeen = findViewById(R.id.switchShowLastSeen)
        switchAllowProfileViews = findViewById(R.id.switchAllowProfileViews)
        switchShareRecyclingStats = findViewById(R.id.switchShareRecyclingStats)
        switchAllowDataCollection = findViewById(R.id.switchAllowDataCollection)
        
        // Buttons
        btnSave = findViewById(R.id.btnSave)
        btnReset = findViewById(R.id.btnReset)
        btnBack = findViewById(R.id.btnBack)
    }
    
    private fun setupSpinners() {
        // Theme Spinner
        val themeAdapter = ArrayAdapter.createFromResource(
            this, R.array.theme_options, android.R.layout.simple_spinner_item
        )
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTheme.adapter = themeAdapter
        
        // Language Spinner
        val languageAdapter = ArrayAdapter.createFromResource(
            this, R.array.language_options, android.R.layout.simple_spinner_item
        )
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = languageAdapter
        
        // Font Size Spinner
        val fontSizeAdapter = ArrayAdapter.createFromResource(
            this, R.array.font_size_options, android.R.layout.simple_spinner_item
        )
        fontSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFontSize.adapter = fontSizeAdapter
        
    }
    
    private fun loadPreferences() {
        lifecycleScope.launch {
            try {
                currentPreferences = withContext(Dispatchers.IO) {
                    preferencesManager.getUserPreferences()
                }
                
                currentPreferences?.let { prefs ->
                    // Set spinner selections
                    spinnerTheme.setSelection(prefs.theme.ordinal)
                    spinnerLanguage.setSelection(getLanguageIndex(prefs.language))
                    spinnerFontSize.setSelection(prefs.fontSize.ordinal)
                    // Set notification switches
                    switchPushNotifications.isChecked = prefs.notifications.pushNotifications
                    switchCommunityUpdates.isChecked = prefs.notifications.communityUpdates
                    switchFriendRequests.isChecked = prefs.notifications.friendRequests
                    switchMessageNotifications.isChecked = prefs.notifications.messageNotifications
                    
                    // Set privacy switches
                    switchShowOnlineStatus.isChecked = prefs.privacy.showOnlineStatus
                    switchAllowFriendRequests.isChecked = prefs.privacy.allowFriendRequests
                    switchShowLastSeen.isChecked = prefs.privacy.showLastSeen
                    switchAllowProfileViews.isChecked = prefs.privacy.allowProfileViews
                    switchShareRecyclingStats.isChecked = prefs.privacy.shareRecyclingStats
                    switchAllowDataCollection.isChecked = prefs.privacy.allowDataCollection
                    
                }
            } catch (e: Exception) {
                android.util.Log.e("UserPreferencesActivity", "Error loading preferences", e)
                Toast.makeText(this@UserPreferencesActivity, "Error loading preferences", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupListeners() {
        // Theme change listener for instant application
        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedTheme = AppTheme.values()[position]
                applyTheme(selectedTheme)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Font size change listener for instant application
        spinnerFontSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedFontSize = FontSize.values()[position]
                applyFontSize(selectedFontSize)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Language change listener - no immediate saving
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                android.util.Log.d("UserPreferencesActivity", "Language preference selected: ${getLanguageValue(position)}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        
        switchPushNotifications.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Push notifications: $isChecked")
        }
        switchCommunityUpdates.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Community updates: $isChecked")
        }
        switchFriendRequests.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Friend requests: $isChecked")
        }
        switchMessageNotifications.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Message notifications: $isChecked")
        }
        
        switchShowOnlineStatus.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Show online status: $isChecked")
        }
        switchAllowFriendRequests.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Allow friend requests: $isChecked")
        }
        switchShowLastSeen.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Show last seen: $isChecked")
        }
        switchAllowProfileViews.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Allow profile views: $isChecked")
        }
        switchShareRecyclingStats.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Share recycling stats: $isChecked")
        }
        switchAllowDataCollection.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("UserPreferencesActivity", "Allow data collection: $isChecked")
        }
        
        
        // Save button
        btnSave.setOnClickListener {
            savePreferences()
        }
        
        // Reset button
        btnReset.setOnClickListener {
            resetToDefaults()
        }
        
        // Back button
        btnBack.setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun applyTheme(theme: AppTheme) {
        // Don't apply theme immediately - just save the preference
        // Theme will be applied when user saves all preferences
        android.util.Log.d("UserPreferencesActivity", "Theme preference selected: $theme")
    }
    
    private fun applyFontSize(fontSize: FontSize) {
        // Apply font size immediately for instant feedback
        applyFontSizeGlobally(fontSize)
        android.util.Log.d("UserPreferencesActivity", "Font size applied immediately: $fontSize")
    }
    
    
    private fun savePreferences() {
        lifecycleScope.launch {
            try {
                val newPreferences = UserPreferences(
                    theme = AppTheme.values()[spinnerTheme.selectedItemPosition],
                    language = getLanguageValue(spinnerLanguage.selectedItemPosition),
                    fontSize = FontSize.values()[spinnerFontSize.selectedItemPosition],
                    notifications = NotificationPreferences(
                        pushNotifications = switchPushNotifications.isChecked,
                        communityUpdates = switchCommunityUpdates.isChecked,
                        friendRequests = switchFriendRequests.isChecked,
                        messageNotifications = switchMessageNotifications.isChecked
                    ),
                    privacy = PrivacyPreferences(
                        showOnlineStatus = switchShowOnlineStatus.isChecked,
                        allowFriendRequests = switchAllowFriendRequests.isChecked,
                        showLastSeen = switchShowLastSeen.isChecked,
                        allowProfileViews = switchAllowProfileViews.isChecked,
                        shareRecyclingStats = switchShareRecyclingStats.isChecked,
                        allowDataCollection = switchAllowDataCollection.isChecked
                    ),
                )
                
                // Save preferences to DataStore
                withContext(Dispatchers.IO) {
                    preferencesManager.saveUserPreferences(newPreferences)
                }
                
                // Apply theme globally
                applyThemeGlobally(newPreferences.theme)
                
                // Apply font size globally
                applyFontSizeGlobally(newPreferences.fontSize)
                
                // Apply language globally
                applyLanguageGlobally(newPreferences.language)
                
                currentPreferences = newPreferences
                Toast.makeText(this@UserPreferencesActivity, getString(R.string.preferences_saved), Toast.LENGTH_SHORT).show()
                
                // Force refresh all activities to apply font size changes immediately
                refreshAllActivities()
                
            } catch (e: Exception) {
                android.util.Log.e("UserPreferencesActivity", "Error saving preferences", e)
                Toast.makeText(this@UserPreferencesActivity, getString(R.string.error_saving), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun applyThemeGlobally(theme: AppTheme) {
        when (theme) {
            AppTheme.LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                android.util.Log.d("UserPreferencesActivity", "Applied Light theme globally")
            }
            AppTheme.DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                android.util.Log.d("UserPreferencesActivity", "Applied Dark theme globally")
            }
            AppTheme.SYSTEM -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                android.util.Log.d("UserPreferencesActivity", "Applied System theme globally")
            }
        }
    }
    
    private fun applyFontSizeGlobally(fontSize: FontSize) {
        val scale = when (fontSize) {
            FontSize.SMALL -> 0.7f      // 30% reduction (1.0 - 0.3 = 0.7)
            FontSize.MEDIUM -> 1.0f     // Default size
            FontSize.LARGE -> 1.3f      // 30% increase (1.0 + 0.3 = 1.3)
        }
        
        // Apply font scale globally to the entire app
        val configuration = Configuration(resources.configuration)
        configuration.fontScale = scale
        resources.updateConfiguration(configuration, resources.displayMetrics)
        
        // Also apply to the application context for global effect
        applicationContext.resources.updateConfiguration(configuration, applicationContext.resources.displayMetrics)
        
        android.util.Log.d("UserPreferencesActivity", "Applied font size globally: $fontSize (scale: $scale)")
    }
    
    private fun refreshAllActivities() {
        // Send broadcast to all activities to refresh their UI
        val intent = Intent("com.example.ecosort.REFRESH_UI")
        intent.putExtra("action", "font_size_changed")
        sendBroadcast(intent)
        
        android.util.Log.d("UserPreferencesActivity", "Sent broadcast to refresh all activities")
    }
    
    private fun applyLanguageGlobally(language: String) {
        val locale = when (language) {
            "zh" -> java.util.Locale("zh", "CN")
            "ms" -> java.util.Locale("ms", "MY")
            else -> java.util.Locale("en", "US")
        }
        
        // Set locale globally
        java.util.Locale.setDefault(locale)
        
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        
        // Apply to current context
        resources.updateConfiguration(configuration, resources.displayMetrics)
        
        // Apply to application context for global effect
        applicationContext.resources.updateConfiguration(configuration, applicationContext.resources.displayMetrics)
        
        // Recreate the activity to apply language changes
        recreate()
        
        android.util.Log.d("UserPreferencesActivity", "Applied language globally: $language ($locale)")
    }
    
    private fun resetToDefaults() {
        val defaultPreferences = UserPreferences()
        currentPreferences = defaultPreferences
        
        // Reset UI to defaults
        spinnerTheme.setSelection(defaultPreferences.theme.ordinal)
        spinnerLanguage.setSelection(getLanguageIndex(defaultPreferences.language))
        spinnerFontSize.setSelection(defaultPreferences.fontSize.ordinal)
        // Reset switches
        switchPushNotifications.isChecked = defaultPreferences.notifications.pushNotifications
        switchCommunityUpdates.isChecked = defaultPreferences.notifications.communityUpdates
        switchFriendRequests.isChecked = defaultPreferences.notifications.friendRequests
        switchMessageNotifications.isChecked = defaultPreferences.notifications.messageNotifications
        
        switchShowOnlineStatus.isChecked = defaultPreferences.privacy.showOnlineStatus
        switchAllowFriendRequests.isChecked = defaultPreferences.privacy.allowFriendRequests
        switchShowLastSeen.isChecked = defaultPreferences.privacy.showLastSeen
        switchAllowProfileViews.isChecked = defaultPreferences.privacy.allowProfileViews
        switchShareRecyclingStats.isChecked = defaultPreferences.privacy.shareRecyclingStats
        switchAllowDataCollection.isChecked = defaultPreferences.privacy.allowDataCollection
        
        
        // Apply default theme, font size, and language globally
        applyThemeGlobally(defaultPreferences.theme)
        applyFontSizeGlobally(defaultPreferences.fontSize)
        applyLanguageGlobally(defaultPreferences.language)
        
        Toast.makeText(this, getString(R.string.preferences_reset), Toast.LENGTH_SHORT).show()
    }
    
    private fun getLanguageIndex(language: String): Int {
        val languages = resources.getStringArray(R.array.language_values)
        return languages.indexOf(language)
    }
    
    private fun getLanguageValue(index: Int): String {
        val languages = resources.getStringArray(R.array.language_values)
        return if (index >= 0 && index < languages.size) languages[index] else "en"
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
