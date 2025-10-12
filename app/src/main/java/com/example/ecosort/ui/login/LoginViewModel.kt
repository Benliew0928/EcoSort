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
    val userSession: UserSession? = null
)

data class RegisterUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistrationSuccessful: Boolean = false
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
        // Validate inputs
        if (username.isBlank()) {
            _loginState.value = _loginState.value.copy(
                errorMessage = "Please enter username"
            )
            return
        }

        if (password.isBlank()) {
            _loginState.value = _loginState.value.copy(
                errorMessage = "Please enter password"
            )
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
        // Validate inputs
        if (username.isBlank()) {
            _registerState.value = _registerState.value.copy(
                errorMessage = "Please enter username"
            )
            return
        }

        if (email.isBlank()) {
            _registerState.value = _registerState.value.copy(
                errorMessage = "Please enter email"
            )
            return
        }

        if (password.isBlank()) {
            _registerState.value = _registerState.value.copy(
                errorMessage = "Please enter password"
            )
            return
        }

        if (password != confirmPassword) {
            _registerState.value = _registerState.value.copy(
                errorMessage = "Passwords do not match"
            )
            return
        }

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

    // ==================== UTILITY ====================

    fun clearError() {
        _loginState.value = _loginState.value.copy(errorMessage = null)
        _registerState.value = _registerState.value.copy(errorMessage = null)
    }

    fun resetRegistrationState() {
        _registerState.value = RegisterUiState()
    }
}