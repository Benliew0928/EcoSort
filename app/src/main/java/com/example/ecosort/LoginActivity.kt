package com.example.ecosort

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

class LoginActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        sharedPreferences = getSharedPreferences("EcoSortPrefs", MODE_PRIVATE)
        
        // Create demo users if they don't exist
        createDemoUsers()
        
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val rgUserType = findViewById<RadioGroup>(R.id.rgUserType)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        
        // Check if user is already logged in
        if (isUserLoggedIn()) {
            navigateToMainActivity()
            return
        }
        
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val selectedUserType = findViewById<RadioButton>(rgUserType.checkedRadioButtonId)
            
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedUserType == null) {
                Toast.makeText(this, "Please select user type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val userType = if (selectedUserType.id == R.id.rbAdmin) "admin" else "user"
            
            // Simple authentication (in real app, this would be server-side)
            if (authenticateUser(username, password, userType)) {
                saveUserSession(username, userType)
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            } else {
                Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val selectedUserType = findViewById<RadioButton>(rgUserType.checkedRadioButtonId)
            
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedUserType == null) {
                Toast.makeText(this, "Please select user type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val userType = if (selectedUserType.id == R.id.rbAdmin) "admin" else "user"
            
            // Simple registration (in real app, this would be server-side)
            if (registerUser(username, password, userType)) {
                Toast.makeText(this, "Registration successful! Please login.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Registration failed. Username might already exist.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun authenticateUser(username: String, password: String, userType: String): Boolean {
        // Simple demo authentication
        val savedPassword = sharedPreferences.getString("password_$username", "")
        val savedUserType = sharedPreferences.getString("usertype_$username", "")
        
        return password == savedPassword && userType == savedUserType
    }
    
    private fun registerUser(username: String, password: String, userType: String): Boolean {
        // Check if user already exists
        if (sharedPreferences.contains("password_$username")) {
            return false
        }
        
        // Save user data
        sharedPreferences.edit()
            .putString("password_$username", password)
            .putString("usertype_$username", userType)
            .apply()
        
        return true
    }
    
    private fun saveUserSession(username: String, userType: String) {
        sharedPreferences.edit()
            .putString("current_username", username)
            .putString("current_usertype", userType)
            .putBoolean("is_logged_in", true)
            .apply()
    }
    
    private fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean("is_logged_in", false)
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun createDemoUsers() {
        // Create demo users if they don't exist
        if (!sharedPreferences.contains("password_user123")) {
            sharedPreferences.edit()
                .putString("password_user123", "password123")
                .putString("usertype_user123", "user")
                .apply()
        }
        
        if (!sharedPreferences.contains("password_admin123")) {
            sharedPreferences.edit()
                .putString("password_admin123", "password123")
                .putString("usertype_admin123", "admin")
                .apply()
        }
    }
}
