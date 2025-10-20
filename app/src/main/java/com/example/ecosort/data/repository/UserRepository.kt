package com.example.ecosort.data.repository

import com.example.ecosort.data.local.UserDao
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.utils.SecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val preferencesManager: UserPreferencesManager,
    private val securityManager: SecurityManager
) {

    // ==================== USER SESSION ====================

    val userSession: Flow<UserSession?> = preferencesManager.userSession

    suspend fun isLoggedIn(): Boolean {
        return preferencesManager.isLoggedIn.first()
    }

    suspend fun getCurrentUser(): Result<User> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            val user = userDao.getUserById(session.userId)
                ?: return Result.Error(Exception("User not found"))

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== AUTHENTICATION ====================

    suspend fun registerUser(
        username: String,
        email: String,
        password: String,
        userType: UserType
    ): Result<User> {
        return try {
            // Validate inputs
            if (!securityManager.isValidUsername(username)) {
                return Result.Error(Exception("Username must be 3-20 characters, alphanumeric only"))
            }

            if (!securityManager.isValidEmail(email)) {
                return Result.Error(Exception("Invalid email format"))
            }

            if (!securityManager.isValidPassword(password)) {
                return Result.Error(Exception("Password must be at least 8 characters with uppercase, lowercase, and digit"))
            }

            // Check if user already exists
            if (userDao.getUserByUsername(username) != null) {
                return Result.Error(Exception("Username already exists"))
            }

            if (userDao.getUserByEmail(email) != null) {
                return Result.Error(Exception("Email already registered"))
            }

            // Hash password and create user
            val passwordHash = securityManager.hashPassword(password)
            val user = User(
                username = username,
                email = email,
                passwordHash = passwordHash,
                userType = userType
            )

            val userId = userDao.insertUser(user)
            val createdUser = user.copy(id = userId)

            Result.Success(createdUser)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun loginUser(username: String, password: String): Result<UserSession> {
        return try {
            // Get user from database
            val user = userDao.getUserByUsername(username)
                ?: return Result.Error(Exception("User not found"))

            // Verify password
            if (!securityManager.verifyPassword(password, user.passwordHash)) {
                return Result.Error(Exception("Invalid password"))
            }

            // Generate session token
            val token = securityManager.generateSessionToken()

            // Create session
            val session = UserSession(
                userId = user.id,
                username = user.username,
                userType = user.userType,
                token = token,
                isLoggedIn = true
            )

            // Save session
            preferencesManager.saveUserSession(session)

            Result.Success(session)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun logoutUser(): Result<Unit> {
        return try {
            preferencesManager.clearUserSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== USER MANAGEMENT ====================

    suspend fun getUserById(userId: Long): Result<User> {
        return try {
            val user = userDao.getUserById(userId)
                ?: return Result.Error(Exception("User not found"))
            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val currentSession = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))
            
            val users = userDao.searchUsersByUsername(query, currentSession.username)
            Result.Success(users)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            userDao.updateUser(user)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun incrementRecycledItems(userId: Long): Result<Unit> {
        return try {
            userDao.incrementItemsRecycled(userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun addEarnings(userId: Long, amount: Double): Result<Unit> {
        return try {
            userDao.addEarnings(userId, amount)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun changePassword(
        userId: Long,
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val user = userDao.getUserById(userId)
                ?: return Result.Error(Exception("User not found"))

            // Verify old password
            if (!securityManager.verifyPassword(oldPassword, user.passwordHash)) {
                return Result.Error(Exception("Invalid current password"))
            }

            // Validate new password
            if (!securityManager.isValidPassword(newPassword)) {
                return Result.Error(Exception("New password does not meet requirements"))
            }

            // Hash new password and update
            val newPasswordHash = securityManager.hashPassword(newPassword)
            val updatedUser = user.copy(passwordHash = newPasswordHash)
            userDao.updateUser(updatedUser)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}