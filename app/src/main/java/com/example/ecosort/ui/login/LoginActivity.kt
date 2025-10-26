package com.example.ecosort.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.MainActivity
import com.example.ecosort.R
import com.example.ecosort.data.model.UserType
import com.example.ecosort.ui.admin.AdminRegistrationDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@AndroidEntryPoint
class LoginActivity : AppCompatActivity(), AdminRegistrationDialog.AdminRegistrationListener {

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
    private lateinit var btnGoogleSignIn: android.widget.Button
    
    private var isPasswordVisible = false
    
    // Google Sign-In
    private lateinit var googleSignInClient: GoogleSignInClient

    // Google Sign-In Activity Result Launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            handleGoogleSignInResult(account)
        } catch (e: ApiException) {
            handleGoogleSignInError(e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Reset login and register states to ensure clean state
        viewModel.resetLoginState()
        viewModel.resetRegisterState()

        // Check if user is already logged in
        checkExistingSession()

        configureGoogleSignIn()
        initViews()
        setupListeners()
        observeViewModel()
    }

    // ==================== INITIALIZATION ====================

    private fun checkExistingSession() {
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { 
                    viewModel.getCurrentUserSession() 
                }
                if (session != null && session.isLoggedIn) {
                    android.util.Log.d("LoginActivity", "User already logged in: ${session.username}")
                    navigateToMain()
                } else {
                    android.util.Log.d("LoginActivity", "No existing session found")
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginActivity", "Error checking existing session", e)
            }
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        // Always sign out first to ensure account picker is shown
        googleSignInClient.signOut().addOnCompleteListener {
            // After signing out, launch the sign-in intent
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        tvUsernameError = findViewById(R.id.tvUsernameError)
        tvPasswordError = findViewById(R.id.tvPasswordError)
        rgUserType = findViewById(R.id.rgUserType)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)

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
            
            // Get selected user type
            val selectedUserType = when (rgUserType.checkedRadioButtonId) {
                R.id.rbUser -> com.example.ecosort.data.model.UserType.USER
                R.id.rbAdmin -> com.example.ecosort.data.model.UserType.ADMIN
                else -> com.example.ecosort.data.model.UserType.USER // Default to USER
            }

            viewModel.login(username, password, selectedUserType, this@LoginActivity)
        }

        // Register button
        btnRegister.setOnClickListener {
            showRegisterDialog()
        }

        // Google Sign-In button
        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
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
                
                // Log button state changes for debugging
                android.util.Log.d("LoginActivity", "Login button enabled: ${!state.isLoading}, isLoading: ${state.isLoading}")

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
                    // If user is trying to register as admin, show admin passkey dialog first
                    if (userType == UserType.ADMIN) {
                        dialog.dismiss() // Close the registration dialog first
                        showAdminRegistrationDialog(username, email, password, confirmPassword)
                    } else {
                        viewModel.register(username, email, password, confirmPassword, userType, this@LoginActivity)
                    }
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

    // ==================== GOOGLE SIGN-IN ====================

    private fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            // Get user information from Google account
            val email = account.email ?: ""
            val displayName = account.displayName ?: ""
            val photoUrl = account.photoUrl?.toString() ?: ""
            val googleId = account.id ?: ""

            // Check if Google user already exists
            checkGoogleUserExists(email, displayName, photoUrl, googleId)
        } else {
            Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkGoogleUserExists(
        email: String,
        displayName: String,
        photoUrl: String,
        googleId: String
    ) {
        lifecycleScope.launch {
            try {
                when (val result = viewModel.checkGoogleUserExists(email)) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        val existingUser = result.data
                        if (existingUser != null) {
                            // User exists, log them in directly
                            loginExistingGoogleUser(email, googleId)
                        } else {
                            // User doesn't exist, show username input page
                            Toast.makeText(this@LoginActivity, getString(R.string.google_sign_in_success), Toast.LENGTH_SHORT).show()
                            navigateToUsernameInput(email, displayName, photoUrl, googleId)
                        }
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        // Error checking user, show username input page as fallback
                        Toast.makeText(this@LoginActivity, getString(R.string.google_sign_in_success), Toast.LENGTH_SHORT).show()
                        navigateToUsernameInput(email, displayName, photoUrl, googleId)
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Loading state - shouldn't happen in this context, but handle it
                        Toast.makeText(this@LoginActivity, "Checking user...", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Error checking user, show username input page as fallback
                Toast.makeText(this@LoginActivity, getString(R.string.google_sign_in_success), Toast.LENGTH_SHORT).show()
                navigateToUsernameInput(email, displayName, photoUrl, googleId)
            }
        }
    }

    private fun loginExistingGoogleUser(email: String, googleId: String) {
        lifecycleScope.launch {
            try {
                when (val result = viewModel.loginGoogleUser(email, googleId)) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        Toast.makeText(this@LoginActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        Toast.makeText(
                            this@LoginActivity,
                            "Login failed: ${result.exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Loading state - shouldn't happen in this context, but handle it
                        Toast.makeText(this@LoginActivity, "Logging in...", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Login failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToUsernameInput(
        email: String,
        displayName: String,
        photoUrl: String,
        googleId: String
    ) {
        val intent = Intent(this, GoogleUsernameActivity::class.java).apply {
            putExtra("google_email", email)
            putExtra("google_display_name", displayName)
            putExtra("google_photo_url", photoUrl)
            putExtra("google_id", googleId)
        }
        startActivity(intent)
    }

    private fun handleGoogleSignInError(e: ApiException) {
        when (e.statusCode) {
            7 -> { // NETWORK_ERROR
                Toast.makeText(this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show()
            }
            8 -> { // INTERNAL_ERROR
                Toast.makeText(this, "Internal error. Please try again.", Toast.LENGTH_SHORT).show()
            }
            5 -> { // INVALID_ACCOUNT
                Toast.makeText(this, "Invalid account. Please try again.", Toast.LENGTH_SHORT).show()
            }
            12501 -> { // SIGN_IN_CANCELLED
                Toast.makeText(this, getString(R.string.google_sign_in_cancelled), Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, getString(R.string.google_sign_in_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== ADMIN REGISTRATION ====================

    private fun showAdminRegistrationDialog(username: String, email: String, password: String, confirmPassword: String) {
        val dialog = AdminRegistrationDialog.newInstance(this)
        // Store the registration data for later use
        dialog.arguments = Bundle().apply {
            putString("username", username)
            putString("email", email)
            putString("password", password)
            putString("confirmPassword", confirmPassword)
        }
        dialog.show(supportFragmentManager, "AdminRegistrationDialog")
    }

    override fun onAdminPasskeyVerified(passkey: String) {
        // Get the stored registration data
        val dialog = supportFragmentManager.findFragmentByTag("AdminRegistrationDialog") as? AdminRegistrationDialog
        val args = dialog?.arguments
        if (args != null) {
            val username = args.getString("username", "")
            val email = args.getString("email", "")
            val password = args.getString("password", "")
            val confirmPassword = args.getString("confirmPassword", "")
            
            // Proceed with admin registration, passing the verified passkey
            viewModel.register(username, email, password, confirmPassword, UserType.ADMIN, this@LoginActivity, passkey)
        }
    }

    override fun onAdminRegistrationCancelled() {
        // User cancelled admin registration, do nothing
        Toast.makeText(this, "Admin registration cancelled", Toast.LENGTH_SHORT).show()
    }

    // ==================== NAVIGATION ====================

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}