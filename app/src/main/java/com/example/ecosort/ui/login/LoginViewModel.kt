package com.example.ecosort.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val userRepository: UserRepository
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

    // ==================== LOGIN ====================

    fun login(username: String, password: String) {
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
            _loginState.value = _loginState.value.copy(isLoading = true, errorMessage = null)

            when (val result = userRepository.loginUser(username, password)) {
                is Result.Success -> {
                    _loginState.value = LoginUiState(
                        isLoading = false,
                        isLoginSuccessful = true,
                        userSession = result.data
                    )
                }
                is Result.Error -> {
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
    }

    // ==================== REGISTER ====================

    fun register(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        userType: UserType
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

            when (val result = userRepository.registerUser(username, email, password, userType)) {
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
}