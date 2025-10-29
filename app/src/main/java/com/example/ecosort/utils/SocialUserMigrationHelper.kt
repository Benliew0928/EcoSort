package com.example.ecosort.utils

import android.content.Context
import android.util.Log
import com.example.ecosort.data.firebase.FirebaseAuthService
import com.example.ecosort.data.local.UserDao
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.User
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.utils.SecurityManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to migrate existing social users (Google/Huawei) to have Firebase UIDs
 * This is needed for users created before the Firebase UID integration was added
 */
@Singleton
class SocialUserMigrationHelper @Inject constructor(
    private val userDao: UserDao,
    private val firebaseAuthService: FirebaseAuthService,
    private val securityManager: SecurityManager,
    private val preferencesManager: UserPreferencesManager
) {
    
    companion object {
        private const val TAG = "SocialUserMigration"
        private const val MIGRATION_PREF_KEY = "social_user_firebase_uid_migration_completed"
    }
    
    /**
     * Check if a social user needs Firebase UID migration
     * Returns true if user has no Firebase UID
     */
    suspend fun needsMigration(user: User): Boolean {
        return user.firebaseUid.isNullOrEmpty()
    }
    
    /**
     * Migrate a social user to have a Firebase UID
     * This creates a Firebase Authentication account for existing social users
     */
    suspend fun migrateSocialUser(user: User, context: Context): Result<String> {
        return try {
            Log.d(TAG, "Starting migration for social user: ${user.username}")
            
            // Check if user already has Firebase UID
            if (!user.firebaseUid.isNullOrEmpty()) {
                Log.d(TAG, "User already has Firebase UID: ${user.firebaseUid}")
                return Result.Success(user.firebaseUid)
            }
            
            // Generate a secure random password for the Firebase account
            val randomPassword = securityManager.generateSessionToken()
            
            // Create Firebase Authentication account
            val firebaseResult = firebaseAuthService.registerUser(
                username = user.username,
                email = user.email,
                password = randomPassword,
                userType = user.userType,
                context = context
            )
            
            if (firebaseResult !is Result.Success) {
                Log.e(TAG, "Failed to create Firebase account for social user: ${user.username}")
                return Result.Error(Exception("Migration failed: Could not create Firebase account"))
            }
            
            val firebaseUser = firebaseResult.data
            val firebaseUid = firebaseUser.firebaseUid
            
            if (firebaseUid.isNullOrEmpty()) {
                Log.e(TAG, "Firebase UID is null or empty after registration")
                return Result.Error(Exception("Migration failed: Could not get Firebase UID"))
            }
            
            Log.d(TAG, "Firebase account created with UID: $firebaseUid")
            
            // Update local user with Firebase UID
            val updatedUser = user.copy(firebaseUid = firebaseUid)
            userDao.updateUser(updatedUser)
            
            Log.d(TAG, "Updated local user with Firebase UID")
            
            // Update Firebase profile with additional user info
            try {
                val profileData = hashMapOf<String, Any>(
                    "profileImageUrl" to (user.profileImageUrl ?: ""),
                    "bio" to (user.bio ?: ""),
                    "socialAccountId" to user.passwordHash, // passwordHash contains social account ID
                    "itemsRecycled" to user.itemsRecycled,
                    "totalPoints" to user.totalPoints,
                    "location" to (user.location ?: "")
                )
                firebaseAuthService.updateUserProfile(firebaseUid, profileData)
                Log.d(TAG, "Updated Firebase profile with user data")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update Firebase profile (non-critical): ${e.message}")
                // Continue anyway - profile can be synced later
            }
            
            Log.d(TAG, "Migration completed successfully for user: ${user.username}")
            Result.Success(firebaseUid)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during social user migration", e)
            Result.Error(e)
        }
    }
    
    /**
     * Migrate all social users in the database who don't have Firebase UIDs
     * This should be called once during app startup or when user logs in
     */
    suspend fun migrateAllSocialUsers(context: Context): Result<Int> {
        return try {
            Log.d(TAG, "Starting batch migration of social users")
            
            // Get all users from database
            val allUsers = userDao.getAllUsers()
            
            // Filter users who need migration (no Firebase UID and have social account)
            val usersNeedingMigration = allUsers.filter { user ->
                user.firebaseUid.isNullOrEmpty() && user.passwordHash.isNotEmpty()
            }
            
            Log.d(TAG, "Found ${usersNeedingMigration.size} social users needing migration")
            
            var migratedCount = 0
            var failedCount = 0
            
            for (user in usersNeedingMigration) {
                val result = migrateSocialUser(user, context)
                if (result is Result.Success) {
                    migratedCount++
                } else {
                    failedCount++
                    Log.w(TAG, "Failed to migrate user: ${user.username}")
                }
            }
            
            Log.d(TAG, "Batch migration completed: $migratedCount succeeded, $failedCount failed")
            Result.Success(migratedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during batch social user migration", e)
            Result.Error(e)
        }
    }
    
    /**
     * Check if migration has been completed for this app installation
     */
    suspend fun isMigrationCompleted(): Boolean {
        val session = preferencesManager.getCurrentUser()
        return session != null && !session.token.isNullOrEmpty()
    }
}

