package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminActivity : AppCompatActivity() {
    
    private lateinit var userPreferencesManager: UserPreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        
        userPreferencesManager = UserPreferencesManager(applicationContext)
        
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
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { userPreferencesManager.clearUserSession() }
                val intent = Intent(this@AdminActivity, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }
}
