package com.example.ecosort.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.repository.AdminRepository
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
    val passwordError: String? = null
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
                    _loginState.value = _loginState.value.copy(
                        isLoginSuccessful = true,
                        userSession = session
                    )
                }
            }
        }
    }

    suspend fun getCurrentUserSession(): UserSession? {
        return try {
            userRepository.userSession.first()
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
                            android.util.Log.d("LoginViewModel", "User login successful for: ${result.data.username}")
                            _loginState.value = LoginUiState(
                                isLoading = false,
                                isLoginSuccessful = true,
                                userSession = result.data
                            )
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
                            // Convert AdminSession to UserSession for consistency
                            val userSession = UserSession(
                                userId = adminResult.data.adminId,
                                username = adminResult.data.username,
                                userType = UserType.ADMIN,
                                token = "admin_${adminResult.data.adminId}",
                                isLoggedIn = true
                            )
                            
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
                            _loginState.value = _loginState.value.copy(
                                isLoading = false,
                                errorMessage = adminResult.exception.message ?: "Admin login failed"
                            )
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
}