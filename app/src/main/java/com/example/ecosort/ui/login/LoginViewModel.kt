package com.example.ecosort.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.repository.AdminRepository
import com.example.ecosort.utils.AuthMigrationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== UI STATES ====================

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val userSession: UserSession? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val needsMigration: Boolean = false,
    val migrationMessage: String? = null
)

data class RegisterUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistrationSuccessful: Boolean = false,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null
)

// ==================== VIEW MODEL ====================

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val adminRepository: AdminRepository,
    private val userPreferencesManager: com.example.ecosort.data.preferences.UserPreferencesManager
) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    init {
        checkLoginStatus()
    }

    // ==================== LOGIN STATUS ====================

    private fun checkLoginStatus() {
        viewModelScope.launch {
            userRepository.userSession.collect { session ->
                if (session != null && session.isLoggedIn) {
                    // Don't automatically set login success - let the login process handle validation
                    // This prevents bypassing user type validation
                    android.util.Log.d("LoginViewModel", "Existing session found: ${session.username} (${session.userType})")
                }
            }
        }
    }

    suspend fun getCurrentUserSession(): UserSession? {
        return try {
            val session = userRepository.userSession.first()
            if (session != null && session.isLoggedIn) {
                android.util.Log.d("LoginViewModel", "Found existing session: ${session.username} (${session.userType})")
            }
            session
        } catch (e: Exception) {
            android.util.Log.e("LoginViewModel", "Error getting current user session", e)
            null
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginUiState()
        android.util.Log.d("LoginViewModel", "Login state reset")
    }

    fun resetRegisterState() {
        _registerState.value = RegisterUiState()
        android.util.Log.d("LoginViewModel", "Register state reset")
    }

    // ==================== LOGIN ====================

    fun login(username: String, password: String, userType: UserType, context: Context) {
        // Clear previous errors
        _loginState.value = _loginState.value.copy(
            usernameError = null,
            passwordError = null,
            errorMessage = null
        )

        // Validate inputs
        val usernameValidation = validateUsername(username)
        val passwordValidation = validatePassword(password)

        if (usernameValidation != null) {
            _loginState.value = _loginState.value.copy(usernameError = usernameValidation)
            return
        }

        if (passwordValidation != null) {
            _loginState.value = _loginState.value.copy(passwordError = passwordValidation)
            return
        }

        viewModelScope.launch {
            android.util.Log.d("LoginViewModel", "Starting login process for user: $username as ${userType.name}")
            _loginState.value = _loginState.value.copy(isLoading = true, errorMessage = null)

            when (userType) {
                UserType.USER -> {
                    // Try regular user login
                    when (val result = userRepository.loginUser(username, password, context)) {
                        is Result.Success -> {
                            // Validate that the authenticated user is actually a USER, not ADMIN
                            if (result.data.userType == UserType.USER) {
                                android.util.Log.d("LoginViewModel", "User login successful for: ${result.data.username}")
                                _loginState.value = LoginUiState(
                                    isLoading = false,
                                    isLoginSuccessful = true,
                                    userSession = result.data
                                )
                            } else {
                                android.util.Log.w("LoginViewModel", "User type mismatch: expected USER, got ${result.data.userType}")
                                _loginState.value = _loginState.value.copy(
                                    isLoading = false,
                                    isLoginSuccessful = false,
                                    errorMessage = "User not found, Check Account Type?"
                                )
                                // Don't proceed with login - treat as failed authentication
                                return@launch
                            }
                        }
                        is Result.Error -> {
                            android.util.Log.w("LoginViewModel", "User login failed for: $username, error: ${result.exception.message}")
                            _loginState.value = _loginState.value.copy(
                                isLoading = false,
                                errorMessage = result.exception.message ?: "Login failed"
                            )
                        }
                        else -> {
                            _loginState.value = _loginState.value.copy(
                                isLoading = false,
                                errorMessage = "Unexpected error occurred"
                            )
                        }
                    }
                }
                UserType.ADMIN -> {
                    // Try admin login
                    when (val adminResult = adminRepository.authenticateAdmin(username, password, context)) {
                        is Result.Success -> {
                            android.util.Log.d("LoginViewModel", "Admin login successful for: ${adminResult.data.username}")
                            
                            // CRITICAL: Use negative ID for admins to avoid collision with regular users
                            // Admin ID 1 becomes -1, Admin ID 2 becomes -2, etc.
                            val sessionUserId = -adminResult.data.adminId
                            
                            // Convert AdminSession to UserSession for consistency
                            val userSession = UserSession(
                                userId = sessionUserId,  // Use negative ID to separate from regular users
                                username = adminResult.data.username,
                                userType = UserType.ADMIN,
                                token = "admin_${adminResult.data.adminId}",
                                isLoggedIn = true
                            )
                            
                            android.util.Log.d("LoginViewModel", "Admin session created: adminId=${adminResult.data.adminId}, sessionUserId=$sessionUserId")
                            
                            // Save admin session to preferences
                            userPreferencesManager.saveUserSession(userSession)
                            _loginState.value = LoginUiState(
                                isLoading = false,
                                isLoginSuccessful = true,
                                userSession = userSession
                            )
                        }
                        is Result.Error -> {
                            android.util.Log.w("LoginViewModel", "Admin login failed for: $username, error: ${adminResult.exception.message}")
                            // Check if this is a regular user trying to login as admin
                            val userResult = userRepository.loginUser(username, password, context)
                            if (userResult is Result.Success && userResult.data.userType == UserType.USER) {
                                _loginState.value = _loginState.value.copy(
                                    isLoading = false,
                                    isLoginSuccessful = false,
                                    errorMessage = "User not found, Check Account Type?"
                                )
                                // Don't proceed with login - treat as failed authentication
                                return@launch
                            } else {
                                _loginState.value = _loginState.value.copy(
                                    isLoading = false,
                                    errorMessage = adminResult.exception.message ?: "Admin login failed"
                                )
                            }
                        }
                        else -> {
                            _loginState.value = _loginState.value.copy(
                                isLoading = false,
                                errorMessage = "Unexpected error occurred during admin login"
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================== REGISTER ====================

    fun register(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        userType: UserType,
        context: Context,
        adminPasskey: String? = null
    ) {
        // Clear previous errors
        _registerState.value = _registerState.value.copy(
            usernameError = null,
            emailError = null,
            passwordError = null,
            confirmPasswordError = null,
            errorMessage = null
        )

        // Validate inputs
        val usernameValidation = validateUsername(username)
        val emailValidation = validateEmail(email)
        val passwordValidation = validatePassword(password)
        val confirmPasswordValidation = validateConfirmPassword(password, confirmPassword)

        var hasErrors = false

        if (usernameValidation != null) {
            _registerState.value = _registerState.value.copy(usernameError = usernameValidation)
            hasErrors = true
        }

        if (emailValidation != null) {
            _registerState.value = _registerState.value.copy(emailError = emailValidation)
            hasErrors = true
        }

        if (passwordValidation != null) {
            _registerState.value = _registerState.value.copy(passwordError = passwordValidation)
            hasErrors = true
        }

        if (confirmPasswordValidation != null) {
            _registerState.value = _registerState.value.copy(confirmPasswordError = confirmPasswordValidation)
            hasErrors = true
        }

        if (hasErrors) return

        viewModelScope.launch {
            _registerState.value = _registerState.value.copy(isLoading = true, errorMessage = null)

            val result = if (userType == UserType.ADMIN) {
                // For admin accounts, use AdminRepository
                if (adminPasskey == null) {
                    Result.Error(Exception("Admin passkey is required for admin registration"))
                } else {
                    adminRepository.createAdmin(username, email, password, adminPasskey, context)
                }
            } else {
                // For regular users, use UserRepository
                userRepository.registerUser(username, email, password, userType, context)
            }

            when (result) {
                is Result.Success -> {
                    _registerState.value = RegisterUiState(
                        isLoading = false,
                        isRegistrationSuccessful = true
                    )
                }
                is Result.Error -> {
                    _registerState.value = _registerState.value.copy(
                        isLoading = false,
                        errorMessage = result.exception.message ?: "Registration failed"
                    )
                }
                else -> {
                    _registerState.value = _registerState.value.copy(
                        isLoading = false,
                        errorMessage = "Unexpected error occurred"
                    )
                }
            }
        }
    }

    // ==================== VALIDATION ====================

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

    // ==================== UTILITY ====================

    fun clearError() {
        _loginState.value = _loginState.value.copy(
            errorMessage = null,
            usernameError = null,
            passwordError = null
        )
        _registerState.value = _registerState.value.copy(
            errorMessage = null,
            usernameError = null,
            emailError = null,
            passwordError = null,
            confirmPasswordError = null
        )
    }

    fun resetRegistrationState() {
        _registerState.value = RegisterUiState()
    }

    // ==================== GOOGLE USER CHECK ====================

    suspend fun checkGoogleUserExists(email: String): Result<com.example.ecosort.data.model.User?> {
        return userRepository.checkGoogleUserExists(email)
    }

    suspend fun loginGoogleUser(email: String, googleId: String): Result<com.example.ecosort.data.model.UserSession> {
        return userRepository.loginGoogleUser(email, googleId)
    }
    
    // ==================== MIGRATION ====================
    
    /**
     * Migrate an existing user to Firebase Authentication
     */
    fun migrateUser(username: String, password: String, context: Context) {
        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                // Get the user from local database
                val userResult = userRepository.getCurrentUser()
                if (userResult is Result.Success) {
                    val user = userResult.data
                    if (AuthMigrationHelper.needsMigration(user)) {
                        // Perform migration
                        val migrationResult = AuthMigrationHelper.migrateUserToFirebase(
                            user, password, userRepository
                        )
                        
                        when (migrationResult) {
                            is Result.Success -> {
                                // Migration successful, now try to login
                                login(username, password, user.userType, context)
                            }
                            is Result.Error -> {
                                _loginState.value = _loginState.value.copy(
                                    isLoading = false,
                                    errorMessage = "Migration failed: ${migrationResult.exception.message}"
                                )
                            }
                            else -> {
                                _loginState.value = _loginState.value.copy(
                                    isLoading = false,
                                    errorMessage = "Migration failed: Unexpected error"
                                )
                            }
                        }
                    } else {
                        _loginState.value = _loginState.value.copy(
                            isLoading = false,
                            errorMessage = "User does not need migration"
                        )
                    }
                } else {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        errorMessage = "User not found"
                    )
                }
            } catch (e: Exception) {
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    errorMessage = "Migration failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Check if current user needs migration
     */
    suspend fun checkMigrationNeeded(): Boolean {
        return try {
            val userResult = userRepository.getCurrentUser()
            if (userResult is Result.Success) {
                AuthMigrationHelper.needsMigration(userResult.data)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}