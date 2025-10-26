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
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseAuthService
import com.example.ecosort.utils.SecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val preferencesManager: UserPreferencesManager,
    private val securityManager: SecurityManager,
    private val firestoreService: FirestoreService,
    private val firebaseAuthService: FirebaseAuthService
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
        userType: UserType,
        context: Context
    ): Result<User> {
        return try {
            // First, try to register with Firebase (primary authentication)
            val firebaseResult = firebaseAuthService.registerUser(username, email, password, userType, context)
            
            if (firebaseResult is Result.Success) {
                val firebaseUser = firebaseResult.data
                
                // Check if user already exists locally
                val existingLocalUser = userDao.getUserByUsername(username)
                if (existingLocalUser == null) {
                    // Insert user to local database
                    val userId = userDao.insertUser(firebaseUser)
                    val createdUser = firebaseUser.copy(id = userId)
                    
                    android.util.Log.d("UserRepository", "User registered successfully with Firebase: $username")
                    return Result.Success(createdUser)
                } else {
                    // Update existing local user
                    userDao.updateUser(firebaseUser.copy(id = existingLocalUser.id))
                    android.util.Log.d("UserRepository", "Updated existing local user from Firebase: $username")
                    return Result.Success(firebaseUser.copy(id = existingLocalUser.id))
                }
            } else {
                // Firebase registration failed, try local fallback
                android.util.Log.w("UserRepository", "Firebase registration failed, trying local fallback: ${(firebaseResult as Result.Error).exception.message}")
                
                // Check if user already exists locally
                if (userDao.getUserByUsername(username) != null) {
                    return Result.Error(Exception("Username already exists"))
                }

                if (userDao.getUserByEmail(email) != null) {
                    return Result.Error(Exception("Email already registered"))
                }

                // Hash password and create user locally
                val passwordHash = securityManager.hashPassword(password)
                val user = User(
                    username = username,
                    email = email,
                    passwordHash = passwordHash,
                    userType = userType
                )

                val userId = userDao.insertUser(user)
                val createdUser = user.copy(id = userId)

                // Try to sync to Firebase later (background task)
                try {
                    syncUserToFirebase(createdUser)
                    android.util.Log.d("UserRepository", "User profile synced to Firebase: ${createdUser.username}")
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to sync user to Firebase: ${e.message}")
                    // Don't fail registration if Firebase sync fails
                }

                android.util.Log.d("UserRepository", "User registered locally (Firebase unavailable): $username")
                Result.Success(createdUser)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to register user", e)
            Result.Error(e)
        }
    }

    suspend fun loginUser(username: String, password: String, context: Context): Result<UserSession> {
        return try {
            // First, try Firebase authentication (primary method)
            val firebaseResult = firebaseAuthService.authenticateUser(username, password, context)
            
            if (firebaseResult is Result.Success) {
                val firebaseSession = firebaseResult.data
                
                // Get or create local user
                val localUser = userDao.getUserByUsername(username)
                val userId = if (localUser != null) {
                    // Update existing local user
                    userDao.updateUser(localUser.copy(
                        lastActive = System.currentTimeMillis()
                    ))
                    localUser.id
                } else {
                    // Get user data from Firebase and create locally
                    val userResult = firebaseAuthService.getUserFromFirebase(username, context)
                    if (userResult is Result.Success && userResult.data != null) {
                        val firebaseUser = userResult.data
                        userDao.insertUser(firebaseUser)
                    } else {
                        // Fallback: create minimal user
                        val fallbackUser = User(
                            username = username,
                            email = "",
                            passwordHash = "",
                            userType = firebaseSession.userType
                        )
                        userDao.insertUser(fallbackUser)
                    }
                }
                
                // Create session with local user ID
                val session = firebaseSession.copy(userId = userId)
                preferencesManager.saveUserSession(session)
                
                android.util.Log.d("UserRepository", "User logged in successfully via Firebase: $username")
                return Result.Success(session)
            } else {
                // Firebase authentication failed, try local fallback
                android.util.Log.w("UserRepository", "Firebase authentication failed, trying local fallback: ${(firebaseResult as Result.Error).exception.message}")
                
                // Get user from local database
                val user = userDao.getUserByUsername(username)
                    ?: return Result.Error(Exception("User not found"))

                // Try both new and legacy password verification
                val passwordValid = if (user.passwordHash.contains(":")) {
                    // New PBKDF2 hash format
                    securityManager.verifyPassword(password, user.passwordHash)
                } else {
                    // Legacy SHA-256 hash format
                    securityManager.verifyPasswordLegacy(password, user.passwordHash)
                }

                if (!passwordValid) {
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
                
                // Try to migrate user to Firebase in background
                try {
                    firebaseAuthService.migrateUserToFirebase(user, context)
                    android.util.Log.d("UserRepository", "User migrated to Firebase: $username")
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to migrate user to Firebase: ${e.message}")
                }

                android.util.Log.d("UserRepository", "User logged in via local fallback: $username")
                Result.Success(session)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to login user", e)
            Result.Error(e)
        }
    }

    suspend fun checkGoogleUserExists(email: String): Result<User?> {
        return try {
            val user = userDao.getUserByEmail(email)
            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun loginGoogleUser(email: String, googleId: String): Result<UserSession> {
        return try {
            // Get user from database by email
            val user = userDao.getUserByEmail(email)
                ?: return Result.Error(Exception("Google user not found"))

            // Verify Google ID matches (for Google users, passwordHash contains googleId)
            if (user.passwordHash != googleId) {
                return Result.Error(Exception("Invalid Google account"))
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

    suspend fun createGoogleUser(
        username: String,
        email: String,
        displayName: String,
        photoUrl: String,
        googleId: String,
        userType: UserType
    ): Result<UserSession> {
        return try {
            // Validate inputs
            if (!securityManager.isValidUsername(username)) {
                return Result.Error(Exception("Username must be 3-20 characters, alphanumeric only"))
            }

            if (!securityManager.isValidEmail(email)) {
                return Result.Error(Exception("Invalid email format"))
            }

            // Check if user already exists
            if (userDao.getUserByUsername(username) != null) {
                return Result.Error(Exception("Username already exists"))
            }

            if (userDao.getUserByEmail(email) != null) {
                return Result.Error(Exception("Email already registered"))
            }

            // Create user with Google information
            val user = User(
                username = username,
                email = email,
                passwordHash = googleId, // Use Google ID as password for Google users
                userType = userType,
                profileImageUrl = photoUrl,
                bio = "Google user: $displayName"
            )

            val userId = userDao.insertUser(user)
            val createdUser = user.copy(id = userId)

            // Generate session token
            val token = securityManager.generateSessionToken()

            // Create session
            val session = UserSession(
                userId = createdUser.id,
                username = createdUser.username,
                userType = createdUser.userType,
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
            
            val users = userDao.searchUsersByUsername(query, currentSession.userId)
            Result.Success(users)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            userDao.updateUser(user)
            
            // Sync user profile to Firebase
            try {
                updateUserInFirebase(user)
                android.util.Log.d("UserRepository", "User profile updated in Firebase: ${user.username}")
            } catch (e: Exception) {
                android.util.Log.w("UserRepository", "Failed to update user in Firebase: ${e.message}")
                // Don't fail update if Firebase sync fails
            }
            
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
            // Update local database
            userDao.updateProfileImage(userId, imageUrl)
            
            // Get the updated user to sync to Firebase
            val user = userDao.getUserById(userId)
            if (user != null) {
                // Sync updated user profile to Firebase
                try {
                    updateUserInFirebase(user)
                    android.util.Log.d("UserRepository", "User profile image updated in Firebase: ${user.username}")
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to update user profile image in Firebase: ${e.message}")
                    // Don't fail the operation if Firebase sync fails
                }
            }
            
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

    suspend fun addPoints(userId: Long, points: Int): Result<Unit> {
        return try {
            userDao.addPoints(userId, points)
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

    // ==================== FIREBASE SYNC ====================

    /**
     * Sync user profile to Firebase
     */
    private suspend fun syncUserToFirebase(user: User) {
        val userData = hashMapOf<String, Any>(
            "id" to user.id,
            "username" to user.username,
            "email" to user.email,
            "userType" to user.userType.name,
            "createdAt" to user.createdAt,
            "itemsRecycled" to user.itemsRecycled,
            "totalPoints" to user.totalPoints,
            "profileImageUrl" to (user.profileImageUrl ?: ""),
            "bio" to (user.bio ?: ""),
            "location" to (user.location ?: ""),
            "joinDate" to user.joinDate,
            "lastActive" to user.lastActive,
            "profileCompletion" to user.profileCompletion,
            "privacySettings" to (user.privacySettings ?: ""),
            "achievements" to (user.achievements ?: ""),
            "socialLinks" to (user.socialLinks ?: ""),
            "preferences" to (user.preferences ?: "")
        )

        val result = firestoreService.saveUserProfile(userData)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to sync user to Firebase")
        }
    }

    /**
     * Update user profile in Firebase
     */
    private suspend fun updateUserInFirebase(user: User) {
        val userData = hashMapOf<String, Any>(
            "id" to user.id,
            "username" to user.username,
            "email" to user.email,
            "userType" to user.userType.name,
            "createdAt" to user.createdAt,
            "itemsRecycled" to user.itemsRecycled,
            "totalPoints" to user.totalPoints,
            "profileImageUrl" to (user.profileImageUrl ?: ""),
            "bio" to (user.bio ?: ""),
            "location" to (user.location ?: ""),
            "joinDate" to user.joinDate,
            "lastActive" to user.lastActive,
            "profileCompletion" to user.profileCompletion,
            "privacySettings" to (user.privacySettings ?: ""),
            "achievements" to (user.achievements ?: ""),
            "socialLinks" to (user.socialLinks ?: ""),
            "preferences" to (user.preferences ?: "")
        )

        val result = firestoreService.updateUserProfile(user.id.toString(), userData)
        if (result.isFailure) {
            android.util.Log.w("UserRepository", "Failed to update user in Firebase: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Get user profile from Firebase by username
     */
    suspend fun getUserProfileFromFirebase(username: String): Result<User?> {
        return try {
            val result = firestoreService.getUserProfileByUsername(username)
            if (result.isSuccess) {
                val userData = result.getOrNull()
                if (userData != null) {
                    val user = User(
                        id = (userData["id"] as? Number)?.toLong() ?: 0L,
                        username = userData["username"] as? String ?: "",
                        email = userData["email"] as? String ?: "",
                        passwordHash = "", // Don't sync password hash
                        userType = UserType.valueOf(userData["userType"] as? String ?: "USER"),
                        createdAt = (userData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        itemsRecycled = (userData["itemsRecycled"] as? Number)?.toInt() ?: 0,
                        totalPoints = (userData["totalPoints"] as? Number)?.toInt() ?: 0,
                        profileImageUrl = userData["profileImageUrl"] as? String,
                        bio = userData["bio"] as? String,
                        location = userData["location"] as? String,
                        joinDate = (userData["joinDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        lastActive = (userData["lastActive"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        profileCompletion = (userData["profileCompletion"] as? Number)?.toInt() ?: 0,
                        privacySettings = userData["privacySettings"] as? String,
                        achievements = userData["achievements"] as? String,
                        socialLinks = userData["socialLinks"] as? String,
                        preferences = userData["preferences"] as? String
                    )
                    Result.Success(user)
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get user from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Sync user profile from Firebase to local database
     */
    suspend fun syncUserFromFirebase(username: String): Result<User> {
        return try {
            val firebaseResult = getUserProfileFromFirebase(username)
            if (firebaseResult is Result.Success) {
                val firebaseUser = firebaseResult.data
                if (firebaseUser != null) {
                    // Check if user already exists locally
                    val localUser = userDao.getUserByUsername(username)
                    if (localUser == null) {
                        // Insert new user to local database
                        val userId = userDao.insertUser(firebaseUser)
                        val syncedUser = firebaseUser.copy(id = userId)
                        android.util.Log.d("UserRepository", "Synced user from Firebase to local: $username")
                        Result.Success(syncedUser)
                    } else {
                        // Update existing user
                        userDao.updateUser(firebaseUser.copy(id = localUser.id))
                        android.util.Log.d("UserRepository", "Updated local user from Firebase: $username")
                        Result.Success(firebaseUser.copy(id = localUser.id))
                    }
                } else {
                    Result.Error(Exception("User not found in Firebase"))
                }
            } else {
                Result.Error((firebaseResult as Result.Error).exception)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Sync all user profiles from Firebase to local database
     */
    suspend fun syncAllUsersFromFirebase(): Result<Int> {
        return try {
            val result = firestoreService.getAllUserProfiles()
            if (result.isSuccess) {
                val firebaseUsers = result.getOrNull() ?: emptyList()
                var syncedCount = 0
                
                for (userData in firebaseUsers) {
                    try {
                        val username = userData["username"] as? String ?: continue
                        val syncResult = syncUserFromFirebase(username)
                        if (syncResult is Result.Success) {
                            syncedCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("UserRepository", "Failed to sync user: ${e.message}")
                    }
                }
                
                android.util.Log.d("UserRepository", "Synced $syncedCount users from Firebase")
                Result.Success(syncedCount)
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get users from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}