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
    private lateinit var btnTogglePassword: android.widget.ImageButton
    private lateinit var tvUsernameError: android.widget.TextView
    private lateinit var tvPasswordError: android.widget.TextView
    private lateinit var rgUserType: RadioGroup
    private lateinit var btnLogin: android.widget.Button
    private lateinit var btnRegister: android.widget.Button
    
    private var isPasswordVisible = false

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
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        tvUsernameError = findViewById(R.id.tvUsernameError)
        tvPasswordError = findViewById(R.id.tvPasswordError)
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
            clearFieldError(tvUsernameError)
        }

        etPassword.doOnTextChanged { _, _, _, _ ->
            clearFieldError(tvPasswordError)
        }

        // Toggle password visibility
        btnTogglePassword.setOnClickListener {
            togglePasswordVisibility()
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

                // Show field errors
                showFieldError(tvUsernameError, state.usernameError)
                showFieldError(tvPasswordError, state.passwordError)

                // Show general error
                state.errorMessage?.let { error ->
                    Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                }

                // Navigate on success
                if (state.isLoginSuccessful) {
                    navigateToMain()
                }
            }
        }

        // Register state is now handled in the dialog itself
    }

    // ==================== REGISTER DIALOG ====================

    private fun showRegisterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_register, null)

        val etRegUsername = dialogView.findViewById<EditText>(R.id.etRegUsername)
        val etRegEmail = dialogView.findViewById<EditText>(R.id.etRegEmail)
        val etRegPassword = dialogView.findViewById<EditText>(R.id.etRegPassword)
        val etRegConfirmPassword = dialogView.findViewById<EditText>(R.id.etRegConfirmPassword)
        val btnToggleRegPassword = dialogView.findViewById<android.widget.ImageButton>(R.id.btnToggleRegPassword)
        val btnToggleRegConfirmPassword = dialogView.findViewById<android.widget.ImageButton>(R.id.btnToggleRegConfirmPassword)
        val tvRegUsernameError = dialogView.findViewById<android.widget.TextView>(R.id.tvRegUsernameError)
        val tvRegEmailError = dialogView.findViewById<android.widget.TextView>(R.id.tvRegEmailError)
        val tvRegPasswordError = dialogView.findViewById<android.widget.TextView>(R.id.tvRegPasswordError)
        val tvRegConfirmPasswordError = dialogView.findViewById<android.widget.TextView>(R.id.tvRegConfirmPasswordError)
        val rgRegUserType = dialogView.findViewById<RadioGroup>(R.id.rgRegUserType)

        var isRegPasswordVisible = false
        var isRegConfirmPasswordVisible = false

        // Set up password visibility toggles
        btnToggleRegPassword.setOnClickListener {
            isRegPasswordVisible = !isRegPasswordVisible
            togglePasswordVisibility(etRegPassword, btnToggleRegPassword, isRegPasswordVisible)
        }

        btnToggleRegConfirmPassword.setOnClickListener {
            isRegConfirmPasswordVisible = !isRegConfirmPasswordVisible
            togglePasswordVisibility(etRegConfirmPassword, btnToggleRegConfirmPassword, isRegConfirmPasswordVisible)
        }


        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Create Account")
            .setView(dialogView)
            .setPositiveButton("Register", null) // Set to null to prevent auto-close
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        // Set up the register button click listener manually
        dialog.setOnShowListener {
            val registerButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            registerButton.setOnClickListener {
                val username = etRegUsername.text.toString().trim()
                val email = etRegEmail.text.toString().trim()
                val password = etRegPassword.text.toString().trim()
                val confirmPassword = etRegConfirmPassword.text.toString().trim()
                val userType = if (rgRegUserType.checkedRadioButtonId == R.id.rbRegAdmin) {
                    UserType.ADMIN
                } else {
                    UserType.USER
                }

                // Validate all fields and show errors
                val usernameError = validateUsername(username)
                val emailError = validateEmail(email)
                val passwordError = validatePassword(password)
                val confirmPasswordError = validateConfirmPassword(password, confirmPassword)

                // Show field errors
                showFieldError(tvRegUsernameError, usernameError)
                showFieldError(tvRegEmailError, emailError)
                showFieldError(tvRegPasswordError, passwordError)
                showFieldError(tvRegConfirmPasswordError, confirmPasswordError)

                // Only proceed with registration if all fields are valid
                if (usernameError == null && emailError == null && passwordError == null && confirmPasswordError == null) {
                    viewModel.register(username, email, password, confirmPassword, userType)
                } else {
                    // Show general message about validation errors
                    Toast.makeText(this@LoginActivity, "Please fix the errors above", Toast.LENGTH_SHORT).show()
                }
                // Dialog will only close on successful registration via the observer
            }
        }

        // Clear errors on text change
        etRegUsername.doOnTextChanged { _, _, _, _ -> 
            clearFieldError(tvRegUsernameError)
        }
        etRegEmail.doOnTextChanged { _, _, _, _ -> 
            clearFieldError(tvRegEmailError)
        }
        etRegPassword.doOnTextChanged { _, _, _, _ -> 
            clearFieldError(tvRegPasswordError)
        }
        etRegConfirmPassword.doOnTextChanged { _, _, _, _ -> 
            clearFieldError(tvRegConfirmPasswordError)
        }

        dialog.show()

        // Observe register state for server errors and success
        lifecycleScope.launch {
            viewModel.registerState.collect { state ->
                // Show general error message (server/network errors)
                state.errorMessage?.let { error ->
                    Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                }
                
                // Close dialog only on successful registration
                if (state.isRegistrationSuccessful) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Registration successful! Please login.",
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.resetRegistrationState()
                    dialog.dismiss()
                }
            }
        }
    }

    // ==================== VALIDATION METHODS ====================

    private fun validateUsername(username: String): String? {
        return when {
            username.isBlank() -> "Username is required"
            username.length < 3 -> "Username must be at least 3 characters"
            username.length > 20 -> "Username must be less than 20 characters"
            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
            else -> null
        }
    }

    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email address"
            else -> null
        }
    }

    private fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "Password is required"
            password.length < 6 -> "Password must be at least 6 characters"
            password.length > 50 -> "Password must be less than 50 characters"
            !password.any { it.isDigit() } -> "Password must contain at least one number"
            !password.any { it.isLetter() } -> "Password must contain at least one letter"
            else -> null
        }
    }

    private fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isBlank() -> "Please confirm your password"
            password != confirmPassword -> "Passwords do not match"
            else -> null
        }
    }

    // ==================== UTILITY METHODS ====================

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        togglePasswordVisibility(etPassword, btnTogglePassword, isPasswordVisible)
    }

    private fun togglePasswordVisibility(
        editText: EditText,
        button: android.widget.ImageButton,
        isVisible: Boolean
    ) {
        if (isVisible) {
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            button.setImageResource(R.drawable.ic_visibility)
            button.contentDescription = "Hide password"
        } else {
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            button.setImageResource(R.drawable.ic_visibility_off)
            button.contentDescription = "Show password"
        }
        // Move cursor to end
        editText.setSelection(editText.text.length)
    }

    private fun showFieldError(textView: android.widget.TextView, error: String?) {
        if (error != null) {
            textView.text = error
            textView.visibility = View.VISIBLE
        } else {
            textView.visibility = View.GONE
        }
    }

    private fun clearFieldError(textView: android.widget.TextView) {
        textView.visibility = View.GONE
    }

    // ==================== NAVIGATION ====================

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}