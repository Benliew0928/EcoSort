package com.example.ecosort

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.ecosort.ui.login.LoginActivity

class AdminActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        
        sharedPreferences = getSharedPreferences("EcoSortPrefs", MODE_PRIVATE)
        
        val btnBack = findViewById<Button>(R.id.btnBackAdmin)
        val btnManageStations = findViewById<Button>(R.id.btnManageStations)
        val btnManagePickups = findViewById<Button>(R.id.btnManagePickups)
        val btnViewReports = findViewById<Button>(R.id.btnViewReports)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        
        btnBack.setOnClickListener {
            finish()
        }
        
        btnManageStations.setOnClickListener {
            Toast.makeText(this, "Station Management - Coming Soon!", Toast.LENGTH_SHORT).show()
        }
        
        btnManagePickups.setOnClickListener {
            Toast.makeText(this, "Pickup Management - Coming Soon!", Toast.LENGTH_SHORT).show()
        }
        
        btnViewReports.setOnClickListener {
            Toast.makeText(this, "Reports Dashboard - Coming Soon!", Toast.LENGTH_SHORT).show()
        }
        
        btnLogout.setOnClickListener {
            logout()
        }
    }
    
    private fun logout() {
        sharedPreferences.edit()
            .remove("current_username")
            .remove("current_usertype")
            .putBoolean("is_logged_in", false)
            .apply()
        
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
