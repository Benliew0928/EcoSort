package com.example.ecosort.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.MainActivity
import com.example.ecosort.R
import com.example.ecosort.data.model.UserType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    // View references (manual binding since we're keeping existing XML)
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var rgUserType: RadioGroup
    private lateinit var btnLogin: android.widget.Button
    private lateinit var btnRegister: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupListeners()
        observeViewModel()
    }

    // ==================== INITIALIZATION ====================

    private fun initViews() {
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        rgUserType = findViewById(R.id.rgUserType)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)

        // Set default user type
        findViewById<android.widget.RadioButton>(R.id.rbUser).isChecked = true
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        // Clear errors on text change
        etUsername.doOnTextChanged { _, _, _, _ ->
            viewModel.clearError()
        }

        etPassword.doOnTextChanged { _, _, _, _ ->
            viewModel.clearError()
        }

        // Login button
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            viewModel.login(username, password)
        }

        // Register button
        btnRegister.setOnClickListener {
            showRegisterDialog()
        }
    }

    // ==================== OBSERVE VIEWMODEL ====================

    private fun observeViewModel() {
        // Observe login state
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                // Show/hide loading
                btnLogin.isEnabled = !state.isLoading
                btnRegister.isEnabled = !state.isLoading

                // Show error
                state.errorMessage?.let { error ->
                    Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                }

                // Navigate on success
                if (state.isLoginSuccessful) {
                    navigateToMain()
                }
            }
        }

        // Observe register state
        lifecycleScope.launch {
            viewModel.registerState.collect { state ->
                if (state.isRegistrationSuccessful) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Registration successful! Please login.",
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.resetRegistrationState()
                }

                state.errorMessage?.let { error ->
                    Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==================== REGISTER DIALOG ====================

    private fun showRegisterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_register, null)

        val etRegUsername = dialogView.findViewById<EditText>(R.id.etRegUsername)
        val etRegEmail = dialogView.findViewById<EditText>(R.id.etRegEmail)
        val etRegPassword = dialogView.findViewById<EditText>(R.id.etRegPassword)
        val etRegConfirmPassword = dialogView.findViewById<EditText>(R.id.etRegConfirmPassword)
        val rgRegUserType = dialogView.findViewById<RadioGroup>(R.id.rgRegUserType)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create Account")
            .setView(dialogView)
            .setPositiveButton("Register") { dialog, _ ->
                val username = etRegUsername.text.toString().trim()
                val email = etRegEmail.text.toString().trim()
                val password = etRegPassword.text.toString().trim()
                val confirmPassword = etRegConfirmPassword.text.toString().trim()
                val userType = if (rgRegUserType.checkedRadioButtonId == R.id.rbRegAdmin) {
                    UserType.ADMIN
                } else {
                    UserType.USER
                }

                viewModel.register(username, email, password, confirmPassword, userType)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // ==================== NAVIGATION ====================

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}