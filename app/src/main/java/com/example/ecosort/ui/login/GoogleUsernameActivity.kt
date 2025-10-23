package com.example.ecosort.ui.login

import android.content.Intent
import android.os.Bundle
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GoogleUsernameActivity : AppCompatActivity() {

    private val viewModel: GoogleUsernameViewModel by viewModels()

    // View references
    private lateinit var etUsername: EditText
    private lateinit var tvUsernameError: android.widget.TextView
    private lateinit var rgUserType: RadioGroup
    private lateinit var btnCreateAccount: android.widget.Button
    private lateinit var btnCancel: android.widget.Button

    // Google user data passed from LoginActivity
    private var googleEmail: String = ""
    private var googleDisplayName: String = ""
    private var googlePhotoUrl: String = ""
    private var googleId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_username)

        // Get Google user data from intent
        googleEmail = intent.getStringExtra("google_email") ?: ""
        googleDisplayName = intent.getStringExtra("google_display_name") ?: ""
        googlePhotoUrl = intent.getStringExtra("google_photo_url") ?: ""
        googleId = intent.getStringExtra("google_id") ?: ""

        initViews()
        setupListeners()
        observeViewModel()
    }

    private fun initViews() {
        etUsername = findViewById(R.id.etUsername)
        tvUsernameError = findViewById(R.id.tvUsernameError)
        rgUserType = findViewById(R.id.rgUserType)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnCancel = findViewById(R.id.btnCancel)

        // Set default user type
        findViewById<android.widget.RadioButton>(R.id.rbUser).isChecked = true
    }

    private fun setupListeners() {
        // Clear errors on text change
        etUsername.doOnTextChanged { _, _, _, _ ->
            clearFieldError(tvUsernameError)
        }

        // Create account button
        btnCreateAccount.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val userType = if (rgUserType.checkedRadioButtonId == R.id.rbAdmin) {
                UserType.ADMIN
            } else {
                UserType.USER
            }

            // Validate username
            val usernameError = validateUsername(username)
            if (usernameError != null) {
                showFieldError(tvUsernameError, usernameError)
                return@setOnClickListener
            }

            // Create Google user account
            viewModel.createGoogleUser(
                username = username,
                email = googleEmail,
                displayName = googleDisplayName,
                photoUrl = googlePhotoUrl,
                googleId = googleId,
                userType = userType
            )
        }

        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.createUserState.collect { state ->
                // Show/hide loading
                btnCreateAccount.isEnabled = !state.isLoading
                btnCancel.isEnabled = !state.isLoading

                // Show field errors
                showFieldError(tvUsernameError, state.usernameError)

                // Show general error
                state.errorMessage?.let { error ->
                    Toast.makeText(this@GoogleUsernameActivity, error, Toast.LENGTH_LONG).show()
                }

                // Navigate on success
                if (state.isUserCreated) {
                    Toast.makeText(
                        this@GoogleUsernameActivity,
                        getString(R.string.account_created_successfully),
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                }
            }
        }
    }

    private fun validateUsername(username: String): String? {
        return when {
            username.isBlank() -> "Username is required"
            username.length < 3 -> "Username must be at least 3 characters"
            username.length > 20 -> "Username must be less than 20 characters"
            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
            else -> null
        }
    }

    private fun showFieldError(textView: android.widget.TextView, error: String?) {
        if (error != null) {
            textView.text = error
            textView.visibility = android.view.View.VISIBLE
        } else {
            textView.visibility = android.view.View.GONE
        }
    }

    private fun clearFieldError(textView: android.widget.TextView) {
        textView.visibility = android.view.View.GONE
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
