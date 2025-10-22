package com.example.ecosort.data.repository

import com.example.ecosort.data.local.UserDao
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.PrivacySettings
import com.example.ecosort.data.model.Achievement
import com.example.ecosort.data.model.SocialLinks
import com.example.ecosort.data.model.UserPreferences
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

    // ==================== PROFILE MANAGEMENT ====================

    suspend fun updateProfileBio(userId: Long, bio: String?): Result<Unit> {
        return try {
            userDao.updateBio(userId, bio)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateProfileLocation(userId: Long, location: String?): Result<Unit> {
        return try {
            userDao.updateLocation(userId, location)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateProfileImage(userId: Long, imageUrl: String?): Result<Unit> {
        return try {
            userDao.updateProfileImage(userId, imageUrl)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateLastActive(userId: Long): Result<Unit> {
        return try {
            userDao.updateLastActive(userId, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun calculateProfileCompletion(userId: Long): Result<Int> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            
            var completion = 0
            if (user.username.isNotEmpty()) completion += 20
            if (!user.email.isBlank()) completion += 20
            if (!user.bio.isNullOrBlank()) completion += 20
            if (!user.location.isNullOrBlank()) completion += 20
            if (!user.profileImageUrl.isNullOrBlank()) completion += 20
            
            userDao.updateProfileCompletion(userId, completion)
            Result.Success(completion)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updatePrivacySettings(userId: Long, privacySettings: PrivacySettings): Result<Unit> {
        return try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(privacySettings)
            userDao.updatePrivacySettings(userId, json)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getPrivacySettings(userId: Long): Result<PrivacySettings> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.privacySettings.isNullOrBlank()) {
                Result.Success(PrivacySettings()) // Return default settings
            } else {
                val gson = com.google.gson.Gson()
                val settings = gson.fromJson(user.privacySettings, PrivacySettings::class.java)
                Result.Success(settings)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun addAchievement(userId: Long, achievement: Achievement): Result<Unit> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Achievement>>() {}.type
            
            val currentAchievements = if (user.achievements.isNullOrBlank()) {
                emptyList<Achievement>()
            } else {
                gson.fromJson(user.achievements, type)
            }
            
            val updatedAchievements = currentAchievements + achievement
            val json = gson.toJson(updatedAchievements)
            userDao.updateAchievements(userId, json)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getAchievements(userId: Long): Result<List<Achievement>> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.achievements.isNullOrBlank()) {
                Result.Success(emptyList())
            } else {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<Achievement>>() {}.type
                val achievements = gson.fromJson<List<Achievement>>(user.achievements, type)
                Result.Success(achievements)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateSocialLinks(userId: Long, socialLinks: SocialLinks): Result<Unit> {
        return try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(socialLinks)
            userDao.updateSocialLinks(userId, json)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getSocialLinks(userId: Long): Result<SocialLinks> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.socialLinks.isNullOrBlank()) {
                Result.Success(SocialLinks()) // Return default social links
            } else {
                val gson = com.google.gson.Gson()
                val socialLinks = gson.fromJson(user.socialLinks, SocialLinks::class.java)
                Result.Success(socialLinks)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateUserPreferences(userId: Long, preferences: UserPreferences): Result<Unit> {
        return try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(preferences)
            userDao.updatePreferences(userId, json)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUserPreferences(userId: Long): Result<UserPreferences> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.preferences.isNullOrBlank()) {
                Result.Success(UserPreferences()) // Return default preferences
            } else {
                val gson = com.google.gson.Gson()
                val preferences = gson.fromJson(user.preferences, UserPreferences::class.java)
                Result.Success(preferences)
            }
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