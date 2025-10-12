package com.example.ecosort

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    private var currentUserType: String = "user"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sharedPreferences = getSharedPreferences("EcoSortPrefs", MODE_PRIVATE)
        
        // Check if user is logged in
        if (!isUserLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        currentUserType = sharedPreferences.getString("current_usertype", "user") ?: "user"
        
        // Initialize UI elements
        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnFindStations = findViewById<Button>(R.id.btnFindStations)
        val btnSell = findViewById<Button>(R.id.btnSell)
        val btnCommunity = findViewById<Button>(R.id.btnCommunity)
        val btnProfile = findViewById<Button>(R.id.btnProfile)
        val tvWelcomeMessage = findViewById<TextView>(R.id.tvWelcomeMessage)
        
        // Bottom navigation buttons
        val bottomHome = findViewById<Button>(R.id.bottomHome)
        val bottomScan = findViewById<Button>(R.id.bottomScan)
        val bottomMap = findViewById<Button>(R.id.bottomMap)
        val bottomSell = findViewById<Button>(R.id.bottomSell)
        
        // Set welcome message
        val username = sharedPreferences.getString("current_username", "User") ?: "User"
        tvWelcomeMessage.text = "Welcome back, $username!"
        
        // Handle user type specific functionality
        if (currentUserType == "admin") {
            btnProfile.setOnClickListener {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        } else {
            btnProfile.setOnClickListener {
                startActivity(Intent(this, UserProfileActivity::class.java))
            }
        }
        
        // Main navigation
        btnScan.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        
        btnFindStations.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        
        btnSell.setOnClickListener {
            startActivity(Intent(this, SellActivity::class.java))
        }
        
        btnCommunity.setOnClickListener {
            Toast.makeText(this, "Community Features - Coming Soon!", Toast.LENGTH_SHORT).show()
        }
        
        // Bottom navigation
        bottomHome.setOnClickListener {
            Toast.makeText(this, "You're already on the home screen", Toast.LENGTH_SHORT).show()
        }
        bottomScan.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        bottomMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        bottomSell.setOnClickListener {
            startActivity(Intent(this, SellActivity::class.java))
        }
    }
    
    private fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean("is_logged_in", false)
    }
    
    override fun onBackPressed() {
        // Prevent going back to login screen
        moveTaskToBack(true)
    }
}
