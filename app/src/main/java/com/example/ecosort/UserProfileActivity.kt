package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class UserProfileActivity : AppCompatActivity() {

    private lateinit var userPreferencesManager: UserPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        userPreferencesManager = UserPreferencesManager(applicationContext)

        val tvUsername = findViewById<TextView>(R.id.tvUsername)
        val tvUserType = findViewById<TextView>(R.id.tvUserType)
        val tvStatsRecycled = findViewById<TextView>(R.id.tvStatsRecycled)
        val tvStatsEarnings = findViewById<TextView>(R.id.tvStatsEarnings)
        val btnEditProfile = findViewById<Button>(R.id.btnEditProfile)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Populate user info from DataStore session
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
            if (session == null || !session.isLoggedIn) {
                startActivity(Intent(this@UserProfileActivity, LoginActivity::class.java))
                finish()
                return@launch
            }
            tvUsername.text = session.username.ifBlank { "User" }
            tvUserType.text = if (session.userType == UserType.ADMIN) "Administrator" else "Regular User"
        }
        
        // Display stats (you can make these dynamic later)
        tvStatsRecycled.text = "24"
        tvStatsEarnings.text = "RM 156"

        // Button click listeners
        btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Edit Profile - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { userPreferencesManager.clearUserSession() }
                Toast.makeText(this@UserProfileActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@UserProfileActivity, LoginActivity::class.java))
                finish()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        finish()
    }
}

