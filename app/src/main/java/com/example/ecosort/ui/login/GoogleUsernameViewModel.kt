package com.example.ecosort.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== UI STATE ====================

data class CreateUserUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUserCreated: Boolean = false,
    val userSession: UserSession? = null,
    val usernameError: String? = null
)

// ==================== VIEW MODEL ====================

@HiltViewModel
class GoogleUsernameViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _createUserState = MutableStateFlow(CreateUserUiState())
    val createUserState: StateFlow<CreateUserUiState> = _createUserState.asStateFlow()

    fun createGoogleUser(
        username: String,
        email: String,
        displayName: String,
        photoUrl: String,
        googleId: String,
        userType: UserType
    ) {
        viewModelScope.launch {
            _createUserState.value = _createUserState.value.copy(isLoading = true, errorMessage = null)

            when (val result = userRepository.createGoogleUser(username, email, displayName, photoUrl, googleId, userType)) {
                is Result.Success -> {
                    _createUserState.value = CreateUserUiState(
                        isLoading = false,
                        isUserCreated = true,
                        userSession = result.data
                    )
                }
                is Result.Error -> {
                    _createUserState.value = _createUserState.value.copy(
                        isLoading = false,
                        errorMessage = result.exception.message ?: "Failed to create account"
                    )
                }
                else -> {
                    _createUserState.value = _createUserState.value.copy(
                        isLoading = false,
                        errorMessage = "Unexpected error occurred"
                    )
                }
            }
        }
    }

    fun clearError() {
        _createUserState.value = _createUserState.value.copy(
            errorMessage = null,
            usernameError = null
        )
    }
}
