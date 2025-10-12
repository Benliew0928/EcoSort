package com.example.ecosort

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.ecosort.ui.login.LoginActivity

class UserProfileActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        sharedPreferences = getSharedPreferences("EcoSortPrefs", MODE_PRIVATE)

        val tvUsername = findViewById<TextView>(R.id.tvUsername)
        val tvUserType = findViewById<TextView>(R.id.tvUserType)
        val tvStatsRecycled = findViewById<TextView>(R.id.tvStatsRecycled)
        val tvStatsEarnings = findViewById<TextView>(R.id.tvStatsEarnings)
        val btnEditProfile = findViewById<Button>(R.id.btnEditProfile)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Get user information
        val username = sharedPreferences.getString("current_username", "User") ?: "User"
        val userType = sharedPreferences.getString("current_usertype", "user") ?: "user"

        // Display user information
        tvUsername.text = username
        tvUserType.text = "Regular User"
        
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
            // Clear user session
            with(sharedPreferences.edit()) {
                putBoolean("is_logged_in", false)
                remove("current_username")
                remove("current_usertype")
                apply()
            }
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        finish()
    }
}
